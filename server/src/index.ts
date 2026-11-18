import { UnauthorizedError, verifyGoogleIdToken } from "./google";
import { deleteCredential, getCredential, setCredential, updateCredentialRole, verifyPassword } from "./credentials";
import { Registry } from "./registry";
import { readSnapshot, issueSnapshot } from "./snapshot";
import { canManageBilling, canManageTeam, PAYMENT_STATUSES, SnapshotPayload, SUBSCRIPTION_TIERS, USER_ROLES, UserRole } from "./types";
import { verifyPlayPurchase } from "./play";

export interface Env {
  AUTH_REGISTRY: KVNamespace;
  WEB_CLIENT_ID: string;
  ANDROID_PACKAGE: string;
  OWNER_EMAILS: string;
  SNAPSHOT_TTL_SECONDS?: string;
  SIGNING_KEY_PKCS8_BASE64: string;
  SIGNING_KEY_SPKI_BASE64: string;
  GOOGLE_SERVICE_ACCOUNT_JSON?: string;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: CORS_HEADERS });
}

function ttlSeconds(env: Env): number {
  const value = Number(env.SNAPSHOT_TTL_SECONDS ?? "900");
  return Number.isFinite(value) ? Math.min(86400, Math.max(60, Math.floor(value))) : 900;
}

function registryFor(env: Env): Registry {
  return new Registry(env.AUTH_REGISTRY, (env.OWNER_EMAILS ?? "").split(","));
}

async function bearer(request: Request): Promise<string | null> {
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match?.[1] ?? null;
}

async function requireSnapshot(
  request: Request,
  env: Env,
  allowExpiredGraceSeconds = 0
): Promise<SnapshotPayload> {
  const token = await bearer(request);
  if (!token) throw new UnauthorizedError("Missing bearer token");
  const payload = await readSnapshot(token, env, { expiredGraceSeconds: allowExpiredGraceSeconds });
  if (!payload) throw new UnauthorizedError("Invalid or expired authorization snapshot");
  return payload;
}

async function readBody(request: Request): Promise<Record<string, unknown>> {
  try {
    const body = await request.json();
    return body && typeof body === "object" ? (body as Record<string, unknown>) : {};
  } catch {
    throw new Error("Invalid JSON body");
  }
}

function stringField(body: Record<string, unknown>, name: string): string {
  const value = body[name];
  if (typeof value !== "string") throw new Error(`Missing or invalid '${name}'`);
  if (name === "password") {
    if (value.length === 0) throw new Error(`Missing or invalid '${name}'`);
    return value;
  }
  if (value.trim().length === 0) throw new Error(`Missing or invalid '${name}'`);
  return value.trim();
}

async function exchange(request: Request, env: Env): Promise<Response> {
  const body = await readBody(request);
  const idToken = stringField(body, "idToken");
  const nonce = typeof body.nonce === "string" && body.nonce.trim() ? body.nonce.trim() : undefined;

  const identity = await verifyGoogleIdToken(idToken, env.WEB_CLIENT_ID, nonce);
  const claims = await registryFor(env).claimsFor(identity, ttlSeconds(env));
  const snapshot = await issueSnapshot(claims, env);
  return json({ snapshot, expiresAt: claims.exp, email: claims.email, role: claims.role });
}

async function emailSignIn(request: Request, env: Env): Promise<Response> {
  const body = await readBody(request);
  const email = stringField(body, "email").toLowerCase();
  const password = stringField(body, "password");
  const registry = registryFor(env);
  const credential = await getCredential(env.AUTH_REGISTRY, email);
  if (!credential || !(await verifyPassword(credential, password))) {
    throw new UnauthorizedError("Invalid inspector credentials");
  }
  const overrideRole = (registry.isOwner(email) || (await registry.hasRoleEntry(email))) ? null : credential.role;
  const claims = await registry.claimsFor(
    { email, subject: `email:${email}`, name: null },
    ttlSeconds(env),
    overrideRole
  );
  return json({ snapshot: await issueSnapshot(claims, env), expiresAt: claims.exp, email: claims.email, role: claims.role });
}

async function setCredentialForOwner(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  if (caller.role !== "OWNER") return json({ error: "owner_only" }, 403);
  const body = await readBody(request);
  const email = stringField(body, "email").toLowerCase();
  const password = stringField(body, "password");
  if (password.length < 8) return json({ error: "password_too_short" }, 400);
  const role = stringField(body, "role") as UserRole;
  if (!USER_ROLES.includes(role) || role === "OWNER") return json({ error: "invalid_role" }, 400);
  await setCredential(env.AUTH_REGISTRY, email, password, role);
  const registry = registryFor(env);
  await registry.setRole(email, role, caller.email);
  return json({ ok: true, email, role });
}

async function removeCredentialForOwner(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  if (caller.role !== "OWNER") return json({ error: "owner_only" }, 403);
  const body = await readBody(request);
  const email = stringField(body, "email").toLowerCase();
  await deleteCredential(env.AUTH_REGISTRY, email);
  await registryFor(env).setRole(email, "USER", caller.email);
  return json({ ok: true, email });
}

async function refresh(request: Request, env: Env): Promise<Response> {
  // A signed snapshot is sufficient for refresh; it is re-checked against the
  // authoritative registry on every call. A short grace window lets an app that
  // was briefly offline recover without forcing the user to sign in again.
  const previous = await requireSnapshot(request, env, 7 * 24 * 3600);
  const identity = { email: previous.email, subject: previous.sub, name: previous.name };
  const claims = await registryFor(env).claimsFor(identity, ttlSeconds(env));
  const snapshot = await issueSnapshot(claims, env);
  return json({ snapshot, expiresAt: claims.exp, role: claims.role, tier: claims.tier });
}

async function me(request: Request, env: Env): Promise<Response> {
  const payload = await requireSnapshot(request, env);
  return json({
    email: payload.email,
    role: payload.role,
    tier: payload.tier,
    effectiveTier: payload.effectiveTier,
    paymentStatus: payload.paymentStatus,
    seats: payload.seats,
    expiresAt: payload.exp,
  });
}

async function setRole(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  if (caller.role !== "OWNER") return json({ error: "owner_only" }, 403);

  const body = await readBody(request);
  const email = stringField(body, "email");
  const role = stringField(body, "role") as UserRole;
  if (!USER_ROLES.includes(role) || role === "OWNER") return json({ error: "invalid_role" }, 400);

  const registry = registryFor(env);
  await registry.setRole(email, role, caller.email);
  await updateCredentialRole(env.AUTH_REGISTRY, email, role);
  return json({ ok: true, email, role });
}

async function setEntitlement(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  if (!canManageBilling(caller.role)) return json({ error: "billing_forbidden" }, 403);

  const body = await readBody(request);
  const email = stringField(body, "email");
  const tier = stringField(body, "tier") as SnapshotPayload["tier"];
  const paymentStatus = (body.paymentStatus as SnapshotPayload["paymentStatus"]) ?? "ACTIVE";
  const seatsRaw = body.seats === undefined ? 1 : body.seats;
  if (typeof seatsRaw !== "number" || !Number.isFinite(seatsRaw)) return json({ error: "invalid_seats" }, 400);
  const seats = Math.floor(seatsRaw);
  if (seats < 1 || seats > 100000) return json({ error: "invalid_seats" }, 400);
  if (!SUBSCRIPTION_TIERS.includes(tier)) return json({ error: "invalid_tier" }, 400);
  if (!PAYMENT_STATUSES.includes(paymentStatus)) return json({ error: "invalid_payment_status" }, 400);

  await registryFor(env).setEntitlement(email, { tier, paymentStatus, seats }, caller.email);
  return json({ ok: true, email, tier, paymentStatus });
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value) &&
    (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
}

async function setTeam(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  if (!canManageTeam(caller.role)) return json({ error: "team_forbidden" }, 403);

  const body = await readBody(request);
  if (!Array.isArray(body.members)) return json({ error: "invalid_members" }, 400);
  const rawMembers = body.members as unknown[];
  if (rawMembers.some((member) => !isPlainObject(member))) return json({ error: "invalid_members" }, 400);

  const members = rawMembers as SnapshotPayload["team"];
  for (const member of members) {
    if (typeof member.email !== "string" || member.email.trim().length === 0) return json({ error: "invalid_members" }, 400);
    if (typeof member.role !== "string" || !USER_ROLES.includes(member.role as UserRole)) return json({ error: "invalid_members" }, 400);
  }
  if (members.some((member) => member.role === "OWNER")) return json({ error: "owner_role_unassignable" }, 400);
  if (members.some((member) => member.role === "ACCOUNT_ADMIN") && !canManageBilling(caller.role)) {
    return json({ error: "admin_role_forbidden" }, 403);
  }

  const registry = registryFor(env);
  try {
    await registry.setTeam(members);
  } catch {
    return json({ error: "invalid_members" }, 400);
  }

  const claims = await registry.claimsFor(
    { email: caller.email, subject: caller.sub, name: caller.name },
    ttlSeconds(env)
  );
  return json({ ok: true, count: members.length, snapshot: await issueSnapshot(claims, env) });
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function verifyPurchase(request: Request, env: Env): Promise<Response> {
  const caller = await requireSnapshot(request, env);
  const body = await readBody(request);
  const purchaseToken = stringField(body, "purchaseToken");
  const productId = stringField(body, "productId");

  const tokenHash = await sha256Hex(purchaseToken);
  const bindingKey = `purchase_token:${tokenHash}`;
  const existingBinding = await env.AUTH_REGISTRY.get(bindingKey);
  if (existingBinding && existingBinding.trim().toLowerCase() !== caller.email.trim().toLowerCase()) {
    return json({ error: "purchase_token_replay", message: "Purchase token already bound to another account" }, 403);
  }

  const result = await verifyPlayPurchase(
    env.GOOGLE_SERVICE_ACCOUNT_JSON,
    env.ANDROID_PACKAGE,
    purchaseToken,
    productId
  );
  if (!result.entitled || !result.tier) return json({ error: "purchase_not_entitled", reason: result.reason }, 402);

  const registry = registryFor(env);
  const existingEntitlement = await registry.entitlementFor(caller.email);
  const blocksOverwrite =
    result.tier === "AD_FREE" &&
    (existingEntitlement.tier === "INDIVIDUAL_PRO" || existingEntitlement.tier === "ENTERPRISE") &&
    (existingEntitlement.paymentStatus === "ACTIVE" || existingEntitlement.paymentStatus === "GRACE_PERIOD");

  if (!blocksOverwrite) {
    await registry.setEntitlement(
      caller.email,
      { tier: result.tier, paymentStatus: "ACTIVE", seats: caller.seats },
      "play_store"
    );
  }
  await env.AUTH_REGISTRY.put(bindingKey, caller.email.trim().toLowerCase());

  const claims = await registry.claimsFor(
    { email: caller.email, subject: caller.sub, name: caller.name },
    ttlSeconds(env)
  );
  return json({ ok: true, snapshot: await issueSnapshot(claims, env), tier: claims.tier });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS_HEADERS });

    try {
      const path = new URL(request.url).pathname;
      if (path === "/health" && request.method === "GET") {
        return json({ ok: true, service: "safetylog-auth", version: 1 });
      }
      if (path === "/v1/auth/exchange" && request.method === "POST") return await exchange(request, env);
      if (path === "/v1/auth/email" && request.method === "POST") return await emailSignIn(request, env);
      if (path === "/v1/admin/credential" && request.method === "POST") return await setCredentialForOwner(request, env);
      if (path === "/v1/admin/credential" && request.method === "DELETE") return await removeCredentialForOwner(request, env);
      if (path === "/v1/auth/refresh" && request.method === "POST") return await refresh(request, env);
      if (path === "/v1/me" && request.method === "GET") return await me(request, env);
      if (path === "/v1/admin/role" && request.method === "POST") return await setRole(request, env);
      if (path === "/v1/admin/entitlement" && request.method === "POST") return await setEntitlement(request, env);
      if (path === "/v1/admin/team" && request.method === "PUT") return await setTeam(request, env);
      if (path === "/v1/purchases/verify" && request.method === "POST") return await verifyPurchase(request, env);
      return json({ error: "not_found" }, 404);
    } catch (error) {
      if (error instanceof UnauthorizedError) return json({ error: "unauthorized", message: error.message }, 401);
      if (error instanceof Error && error.message.startsWith("Missing or invalid") === true) {
        return json({ error: "bad_request", message: error.message }, 400);
      }
      if (error instanceof Error && error.message === "Invalid JSON body") return json({ error: "bad_request" }, 400);
      console.error("Unhandled auth worker error", error);
      return json({ error: "internal_error" }, 500);
    }
  },
};
