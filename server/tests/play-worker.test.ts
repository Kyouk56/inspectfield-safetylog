import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { verifyPlayPurchase } from "../src/play";

function makeServiceAccount(): string {
  const pair = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
    publicKeyEncoding: { type: "spki", format: "pem" },
  });
  return JSON.stringify({ client_email: "svc@example.com", private_key: pair.privateKey });
}

test("Play verification rejects invalid service account configuration", async () => {
  const result = await verifyPlayPurchase(JSON.stringify({ client_email: "", private_key: "" }), "pkg", "token", "safetylog_pro_monthly_99");
  assert.equal(result.entitled, false);
  assert.match(result.reason ?? "", /Invalid service account/);
});

test("Play verification rejects unknown product ids", async () => {
  const result = await verifyPlayPurchase(makeServiceAccount(), "pkg", "token", "not_a_sku");
  assert.equal(result.entitled, false);
  assert.match(result.reason ?? "", /Unknown product/);
});

test("Play subscriptionsv2 requires expected productId in lineItems", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("oauth2.googleapis.com/token")) {
      return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify({
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      lineItems: [{ productId: "some_other_sku" }],
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const result = await verifyPlayPurchase(makeServiceAccount(), "pkg", "tok-1", "safetylog_pro_monthly_99");
    assert.equal(result.entitled, false);
    assert.match(result.reason ?? "", /product mismatch/i);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("Play subscriptionsv2 accepts matching productId in lineItems", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("oauth2.googleapis.com/token")) {
      return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify({
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      lineItems: [{ productId: "safetylog_pro_monthly_99" }],
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const result = await verifyPlayPurchase(makeServiceAccount(), "pkg", "tok-2", "safetylog_pro_monthly_99");
    assert.equal(result.entitled, true);
    assert.equal(result.tier, "INDIVIDUAL_PRO");
  } finally {
    globalThis.fetch = originalFetch;
  }
});