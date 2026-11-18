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

  private isOwner(email: string): boolean {
    return this.ownerEmails.has(email.trim().toLowerCase());
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
        seats: typeof parsed.seats === "number" && parsed.seats > 0 ? parsed.seats : 1,
      };
    } catch {
      return { tier: "FREE", paymentStatus: "ACTIVE", seats: 1 };
    }
  }

  async team(): Promise<TeamMemberClaim[]> {
    const raw = await this.kv.get("team:default");
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? (parsed as TeamMemberClaim[]) : [];
    } catch {
      return [];
    }
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
    const team = await this.team();
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
    await this.kv.put(
      await this.key("entitlement", email),
      JSON.stringify({ ...entitlement, updatedBy, updatedAt: Date.now() })
    );
  }

  async setTeam(members: TeamMemberClaim[]): Promise<void> {
    await this.kv.put("team:default", JSON.stringify(members));
  }
}
