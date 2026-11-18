import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker, { Env } from "../src/index";
import { setCredential } from "../src/credentials";
import { issueSnapshot, readSnapshot } from "../src/snapshot";
import { SnapshotPayload } from "../src/types";

function fakeKv(): KVNamespace {
  const store = new Map<string, string>();
  return {
    get: async (key: string) => store.get(key) ?? null,
    put: async (key: string, value: string) => { store.set(key, value); },
    delete: async (key: string) => { store.delete(key); },
  } as unknown as KVNamespace;
}

function makeEnv(kv: KVNamespace = fakeKv(), ownerEmails = "owner@example.com"): Env {
  const pair = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  const strip = (pem: string) => pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return {
    AUTH_REGISTRY: kv,
    WEB_CLIENT_ID: "web-client-id",
    ANDROID_PACKAGE: "com.safetylog.inspection.app",
    OWNER_EMAILS: ownerEmails,
    SNAPSHOT_TTL_SECONDS: "900",
    SIGNING_KEY_PKCS8_BASE64: strip(pair.privateKey),
    SIGNING_KEY_SPKI_BASE64: strip(pair.publicKey),
  };
}

function claims(overrides: Partial<SnapshotPayload> = {}): SnapshotPayload {
  const now = Math.floor(Date.now() / 1000);
  return {
    v: 1,
    sub: "sub-1",
    email: "owner@example.com",
    name: "Owner",
    role: "OWNER",
    tier: "ENTERPRISE",
    effectiveTier: "ENTERPRISE",
    paymentStatus: "ACTIVE",
    seats: 5,
    team: [],
    iat: now,
    exp: now + 900,
    jti: "jti-1",
    ...overrides,
  };
}

async function req(
  env: Env,
  path: string,
  method = "GET",
  body?: unknown,
  token?: string
): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return worker.fetch(
    new Request(`https://auth.example.com${path}`, {
      method,
      headers,
      body: typeof body === "string" ? body : body !== undefined ? JSON.stringify(body) : undefined,
    }),
    env
  );
}

test("CORS OPTIONS preflight returns 204 with DELETE allowed", async () => {
  const res = await req(makeEnv(), "/v1/admin/credential", "OPTIONS");
  assert.equal(res.status, 204);
  const methods = res.headers.get("Access-Control-Allow-Methods") ?? "";
  assert.match(methods, /\bDELETE\b/);
  assert.match(methods, /\bPUT\b/);
});

test("/health returns 200 and unknown route returns 404", async () => {
  const env = makeEnv();
  const health = await req(env, "/health", "GET");
  assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { ok: true, service: "safetylog-auth", version: 1 });

  const notFound = await req(env, "/v1/nope", "GET");
  assert.equal(notFound.status, 404);
});

test("malformed JSON returns 400 and unexpected internal error returns 500 without leaking message", async () => {
  const env = makeEnv();
  const bad = await req(env, "/v1/auth/email", "POST", "{bad json");
  assert.equal(bad.status, 400);

  const failingKv = {
    get: async () => { throw new Error("secret-internal-kv-failure"); },
    put: async () => {},
    delete: async () => {},
  } as unknown as KVNamespace;
  const errEnv = makeEnv(failingKv);
  const errRes = await req(errEnv, "/v1/auth/email", "POST", { email: "a@b.com", password: "password123" });
  assert.equal(errRes.status, 500);
  const payload = await errRes.json() as Record<string, unknown>;
  assert.equal(payload.error, "internal_error");
  assert.equal(JSON.stringify(payload).includes("secret-internal-kv-failure"), false);
});

test("/v1/auth/email preserves password whitespace and rejects wrong password", async () => {
  const kv = fakeKv();
  const env = makeEnv(kv);
  await setCredential(kv, "tech@example.com", "  spaced pass  ", "MANAGER");

  const okRes = await req(env, "/v1/auth/email", "POST", { email: "tech@example.com", password: "  spaced pass  " });
  assert.equal(okRes.status, 200);
  const okBody = await okRes.json() as { snapshot: string; role: string };
  assert.equal(okBody.role, "MANAGER");
  assert.ok(await readSnapshot(okBody.snapshot, env));

  const trimmedRes = await req(env, "/v1/auth/email", "POST", { email: "tech@example.com", password: "spaced pass" });
  assert.equal(trimmedRes.status, 401);
});

test("/v1/admin/credential validates owner, password length, role, and supports DELETE", async () => {
  const kv = fakeKv();
  const env = makeEnv(kv, "owner@example.com");
  const ownerToken = await issueSnapshot(claims({ email: "owner@example.com", role: "OWNER" }), env);
  const userToken = await issueSnapshot(claims({ email: "user@example.com", role: "USER" }), env);

  const nonOwner = await req(env, "/v1/admin/credential", "POST", { email: "a@b.com", password: "password123", role: "USER" }, userToken);
  assert.equal(nonOwner.status, 403);

  const shortPw = await req(env, "/v1/admin/credential", "POST", { email: "a@b.com", password: "short", role: "USER" }, ownerToken);
  assert.equal(shortPw.status, 400);

  const ownerRole = await req(env, "/v1/admin/credential", "POST", { email: "a@b.com", password: "password123", role: "OWNER" }, ownerToken);
  assert.equal(ownerRole.status, 400);

  const created = await req(env, "/v1/admin/credential", "POST", { email: "a@b.com", password: "password123", role: "MANAGER" }, ownerToken);
  assert.equal(created.status, 200);

  const deleted = await req(env, "/v1/admin/credential", "DELETE", { email: "a@b.com" }, ownerToken);
  assert.equal(deleted.status, 200);

  const afterDelete = await req(env, "/v1/auth/email", "POST", { email: "a@b.com", password: "password123" });
  assert.equal(afterDelete.status, 401);
});

test("/v1/admin/role syncs credential role and /v1/admin/entitlement validates seat bounds", async () => {
  const kv = fakeKv();
  const env = makeEnv(kv, "owner@example.com");
  const ownerToken = await issueSnapshot(claims({ email: "owner@example.com", role: "OWNER" }), env);

  await req(env, "/v1/admin/credential", "POST", { email: "tech@example.com", password: "password123", role: "USER" }, ownerToken);
  const roleRes = await req(env, "/v1/admin/role", "POST", { email: "tech@example.com", role: "MANAGER" }, ownerToken);
  assert.equal(roleRes.status, 200);

  const signInRes = await req(env, "/v1/auth/email", "POST", { email: "tech@example.com", password: "password123" });
  assert.equal(signInRes.status, 200);
  const signInBody = await signInRes.json() as { role: string };
  assert.equal(signInBody.role, "MANAGER");

  const zeroSeats = await req(env, "/v1/admin/entitlement", "POST", { email: "tech@example.com", tier: "ENTERPRISE", seats: 0 }, ownerToken);
  assert.equal(zeroSeats.status, 400);
  const hugeSeats = await req(env, "/v1/admin/entitlement", "POST", { email: "tech@example.com", tier: "ENTERPRISE", seats: 100001 }, ownerToken);
  assert.equal(hugeSeats.status, 400);
});

test("/v1/admin/team returns updated snapshot and rejects malformed members", async () => {
  const kv = fakeKv();
  const env = makeEnv(kv, "owner@example.com");
  const ownerToken = await issueSnapshot(claims({ email: "owner@example.com", role: "OWNER" }), env);

  const badMember = await req(env, "/v1/admin/team", "PUT", { members: [null] }, ownerToken);
  assert.equal(badMember.status, 400);

  const okTeam = await req(env, "/v1/admin/team", "PUT", {
    members: [{ id: "m1", email: "member@example.com", name: "Member", role: "USER", isActive: true, addedEpochMs: 1000 }],
  }, ownerToken);
  assert.equal(okTeam.status, 200);
  const body = await okTeam.json() as { ok: boolean; count: number; snapshot: string };
  assert.equal(body.ok, true);
  assert.equal(body.count, 1);
  const verified = await readSnapshot(body.snapshot, env);
  assert.equal(verified?.team.length, 1);
  assert.equal(verified?.team[0]?.addedEpochMs, 1000);
});

test("/v1/purchases/verify blocks cross-account replay and protects active Pro from AD_FREE overwrite", async () => {
  const kv = fakeKv();
  const pair = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
    publicKeyEncoding: { type: "spki", format: "pem" },
  });
  const env: Env = {
    ...makeEnv(kv, "owner@example.com"),
    GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({ client_email: "svc@example.com", private_key: pair.privateKey }),
  };

  const userAToken = await issueSnapshot(claims({ email: "usera@example.com", role: "USER", tier: "INDIVIDUAL_PRO" }), env);
  const userBToken = await issueSnapshot(claims({ email: "userb@example.com", role: "USER", tier: "FREE" }), env);

  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("oauth2.googleapis.com/token")) {
      return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }), { status: 200 });
    }
    return new Response(JSON.stringify({
      purchaseState: 0,
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      lineItems: [{ productId: "safetylog_pro_monthly_99" }],
    }), { status: 200 });
  }) as typeof fetch;

  try {
    const first = await req(env, "/v1/purchases/verify", "POST", { purchaseToken: "shared-tok", productId: "safetylog_pro_monthly_99" }, userAToken);
    assert.equal(first.status, 200);

    const replay = await req(env, "/v1/purchases/verify", "POST", { purchaseToken: "shared-tok", productId: "safetylog_pro_monthly_99" }, userBToken);
    assert.equal(replay.status, 403);

    const adFree = await req(env, "/v1/purchases/verify", "POST", { purchaseToken: "adfree-tok", productId: "safetylog_adfree_299" }, userAToken);
    assert.equal(adFree.status, 200);
    const adFreeBody = await adFree.json() as { tier: string };
    assert.equal(adFreeBody.tier, "INDIVIDUAL_PRO");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
