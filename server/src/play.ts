import { pemPrivateKeyToDer } from "./base64";
import { signJwt } from "./crypto";
import { SubscriptionTier } from "./types";

/** Verifies Play Store purchases server-side via the Google Play Developer API. */

const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
const PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

// Keep in sync with BillingRepository.SKU_* constants.
const SUBSCRIPTION_SKUS: Record<string, SubscriptionTier> = {
  safetylog_pro_monthly_99: "INDIVIDUAL_PRO",
  safetylog_enterprise_monthly_seat: "ENTERPRISE",
};
const PRODUCT_SKUS: Record<string, SubscriptionTier> = {
  safetylog_adfree_299: "AD_FREE",
};

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

export interface PurchaseVerification {
  entitled: boolean;
  tier: SubscriptionTier | null;
  reason?: string;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

async function serviceAccountAccessToken(sa: ServiceAccount): Promise<string> {
  if (cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000) return cachedAccessToken.token;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemPrivateKeyToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(
    {
      iss: sa.client_email,
      scope: PUBLISHER_SCOPE,
      aud: TOKEN_ENDPOINT,
      iat: now,
      exp: now + 3600,
    },
    key
  );

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth token request failed (${response.status})`);
  const data = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!data.access_token) throw new Error("Google OAuth response contained no access token");

  cachedAccessToken = {
    token: data.access_token,
    expiresAt: Date.now() + (data.expires_in ?? 3600) * 1000,
  };
  return cachedAccessToken.token;
}

export async function verifyPlayPurchase(
  serviceAccountJson: string | undefined,
  androidPackage: string,
  purchaseToken: string,
  productId: string
): Promise<PurchaseVerification> {
  if (!serviceAccountJson) return { entitled: false, tier: null, reason: "Play verification is not configured" };

  const subscriptionTier = SUBSCRIPTION_SKUS[productId];
  const productTier = PRODUCT_SKUS[productId];
  if (!subscriptionTier && !productTier) return { entitled: false, tier: null, reason: "Unknown product id" };

  let sa: ServiceAccount;
  try {
    sa = JSON.parse(serviceAccountJson) as ServiceAccount;
  } catch {
    return { entitled: false, tier: null, reason: "Malformed service account configuration" };
  }

  const token = await serviceAccountAccessToken(sa);
  const encodedToken = encodeURIComponent(purchaseToken);
  const url = subscriptionTier
    ? `${PLAY_API}/${androidPackage}/purchases/subscriptionsv2/tokens/${encodedToken}`
    : `${PLAY_API}/${androidPackage}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodedToken}`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}`, "User-Agent": "safetylog-auth-worker/1.0" },
  });
  if (response.status === 404) return { entitled: false, tier: null, reason: "Purchase not found" };
  if (!response.ok) throw new Error(`Play Developer API request failed (${response.status})`);

  const purchase = (await response.json()) as { subscriptionState?: string; purchaseState?: number };
  if (subscriptionTier) {
    const active = purchase.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
      purchase.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
    return active
      ? { entitled: true, tier: subscriptionTier }
      : { entitled: false, tier: null, reason: "Subscription is not active" };
  }
  return purchase.purchaseState === 0
    ? { entitled: true, tier: productTier ?? null }
    : { entitled: false, tier: null, reason: "Product was not purchased" };
}
