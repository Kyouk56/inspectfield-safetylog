import { EntitlementClaim, SnapshotPayload, SUBSCRIPTION_TIERS, TeamMemberClaim, USER_ROLES, UserRole } from "./types";

/**
 * Authoritative role / entitlement / team registry backed by Workers KV.
 * Client-side values are never consulted: the snapshot is built from this store.
 */
export class Registry {
  private readonly ownerEmails: Set<string>;

  constructor(private readonly kv: KVNamespace, ownerEmails: string[]) {
    this.ownerEmails = new Set(ownerEmails.map((email) => email.trim().toLowerCase()).filter(Boolean));
  }

  private async key(prefix: string, email: string): Promise<string> {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()));
    const hex = Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
    return `${prefix}:${hex}`;
  }

  async sha256Hex(value: string): Promise<string> {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
    return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
  }

  isOwner(email: string): boolean {
    return this.ownerEmails.has(email.trim().toLowerCase());
  }

  async hasRoleEntry(email: string): Promise<boolean> {
    const raw = await this.kv.get(await this.key("role", email));
    return raw !== null;
  }

  private async teamScopeKey(ownerEmail?: string): Promise<string> {
    const scope = (ownerEmail ?? "").trim().toLowerCase() || [...this.ownerEmails].sort().join(",");
    const digest = await this.sha256Hex(scope || "default");
    return `team:${digest}`;
  }

  async roleFor(email: string): Promise<UserRole> {
    if (this.isOwner(email)) return "OWNER";
    const raw = await this.kv.get(await this.key("role", email));
    if (!raw) return "USER";
    try {
      const parsed = JSON.parse(raw) as { role?: string };
      return USER_ROLES.includes(parsed.role as UserRole) && parsed.role !== "OWNER"
        ? (parsed.role as UserRole)
        : "USER";
    } catch {
      return "USER";
    }
  }

  async entitlementFor(email: string): Promise<EntitlementClaim> {
    const raw = await this.kv.get(await this.key("entitlement", email));
    if (!raw) return { tier: "FREE", paymentStatus: "ACTIVE", seats: 1 };
    try {
      const parsed = JSON.parse(raw) as Partial<EntitlementClaim>;
      return {
        tier: SUBSCRIPTION_TIERS.includes(parsed.tier as never) ? (parsed.tier as EntitlementClaim["tier"]) : "FREE",
        paymentStatus: (parsed.paymentStatus as EntitlementClaim["paymentStatus"]) ?? "ACTIVE",
        seats: typeof parsed.seats === "number" && Number.isFinite(parsed.seats) && parsed.seats > 0
          ? Math.min(100000, Math.max(1, Math.floor(parsed.seats)))
          : 1,
      };
    } catch {
      return { tier: "FREE", paymentStatus: "ACTIVE", seats: 1 };
    }
  }

  async getTeam(ownerEmail?: string): Promise<TeamMemberClaim[]> {
    const raw = await this.kv.get(await this.teamScopeKey(ownerEmail));
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      const out: TeamMemberClaim[] = [];
      for (const item of parsed) {
        if (!item || typeof item !== "object" || Array.isArray(item)) continue;
        const o = item as Record<string, unknown>;
        if (typeof o.email !== "string" || o.email.trim().length === 0) continue;
        if (typeof o.role !== "string" || !USER_ROLES.includes(o.role as UserRole)) continue;
        out.push({
          id: typeof o.id === "string" ? o.id : String(o.id ?? ""),
          email: String(o.email).trim(),
          name: typeof o.name === "string" ? o.name : String(o.name ?? ""),
          role: o.role as UserRole,
          isActive: typeof o.isActive === "boolean" ? o.isActive : true,
          addedEpochMs: typeof o.addedEpochMs === "number" && Number.isFinite(o.addedEpochMs) ? o.addedEpochMs : undefined,
        });
      }
      return out;
    } catch {
      return [];
    }
  }

  async team(ownerEmail?: string): Promise<TeamMemberClaim[]> {
    return this.getTeam(ownerEmail);
  }

  async claimsFor(
    identity: { email: string; subject: string; name: string | null },
    ttlSeconds: number,
    roleOverride: UserRole | null = null
  ): Promise<SnapshotPayload> {
    const email = identity.email.trim().toLowerCase();
    const requestedRole = roleOverride ?? await this.roleFor(email);
    const role = requestedRole === "OWNER" && !this.isOwner(email) ? "USER" : requestedRole;
    const entitlement = await this.entitlementFor(email);
    const team = await this.getTeam();
    const now = Math.floor(Date.now() / 1000);
    return {
      v: 1,
      sub: identity.subject,
      email,
      name: identity.name,
      role,
      tier: entitlement.tier,
      effectiveTier: entitlement.paymentStatus === "ACTIVE" || entitlement.paymentStatus === "GRACE_PERIOD"
        ? entitlement.tier
        : "FREE",
      paymentStatus: entitlement.paymentStatus,
      seats: entitlement.seats,
      team,
      iat: now,
      exp: now + ttlSeconds,
      jti: crypto.randomUUID(),
    };
  }

  async setRole(email: string, role: UserRole, assignedBy: string): Promise<void> {
    await this.kv.put(await this.key("role", email), JSON.stringify({ role, assignedBy, assignedAt: Date.now() }));
  }

  async setEntitlement(email: string, entitlement: EntitlementClaim, updatedBy: string): Promise<void> {
    const seats = Number.isFinite(entitlement.seats) && entitlement.seats > 0
      ? Math.min(100000, Math.max(1, Math.floor(entitlement.seats)))
      : 1;
    await this.kv.put(
      await this.key("entitlement", email),
      JSON.stringify({ ...entitlement, seats, updatedBy, updatedAt: Date.now() })
    );
  }

  async setTeam(...args: unknown[]): Promise<void> {
    let ownerEmail: string | undefined;
    let members: unknown;
    if (Array.isArray(args[0])) {
      members = args[0];
      if (typeof args[1] === "string") ownerEmail = args[1];
    } else if (typeof args[0] === "string") {
      ownerEmail = args[0];
      members = args[1];
    } else if (args[0] && typeof args[0] === "object" && !Array.isArray(args[0]) && "members" in (args[0] as Record<string, unknown>)) {
      members = (args[0] as Record<string, unknown>).members;
      if (typeof (args[0] as Record<string, unknown>).ownerEmail === "string") ownerEmail = (args[0] as Record<string, unknown>).ownerEmail as string;
    } else {
      members = args[0];
    }

    if (!Array.isArray(members)) throw new Error("invalid_members");

    const normalized: TeamMemberClaim[] = members.map((member) => {
      if (!member || typeof member !== "object" || Array.isArray(member)) throw new Error("invalid_team_member");
      const o = member as Record<string, unknown>;
      if (typeof o.email !== "string" || o.email.trim().length === 0) throw new Error("invalid_team_member_email");
      if (typeof o.role !== "string" || !USER_ROLES.includes(o.role as UserRole)) throw new Error("invalid_team_member_role");
      const added = o.addedEpochMs;
      const addedEpochMs = typeof added === "number" && Number.isFinite(added) ? added : Date.now();
      return {
        id: typeof o.id === "string" ? o.id : String(o.id ?? ""),
        email: o.email.trim(),
        name: typeof o.name === "string" ? o.name : String(o.name ?? ""),
        role: o.role as UserRole,
        isActive: typeof o.isActive === "boolean" ? o.isActive : true,
        addedEpochMs,
      };
    });

    // OWNER role is never assignable through team roster.
    if (normalized.some((m) => m.role === "OWNER")) throw new Error("owner_role_unassignable");

    await this.kv.put(await this.teamScopeKey(ownerEmail), JSON.stringify(normalized));
  }
}