/** Wire contract shared by the Worker and the Android client. Keep in sync with the client models. */

export const USER_ROLES = ["OWNER", "ACCOUNT_ADMIN", "MANAGER", "USER"] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const SUBSCRIPTION_TIERS = ["FREE", "AD_FREE", "INDIVIDUAL_PRO", "ENTERPRISE"] as const;
export type SubscriptionTier = (typeof SUBSCRIPTION_TIERS)[number];

export const PAYMENT_STATUSES = ["ACTIVE", "PAYMENT_FAILED", "CANCELLED", "GRACE_PERIOD"] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

export interface TeamMemberClaim {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  isActive: boolean;
  addedEpochMs?: number;
}

export interface EntitlementClaim {
  tier: SubscriptionTier;
  paymentStatus: PaymentStatus;
  seats: number;
}

/** Claims carried inside the signed RS256 snapshot JWT. */
export interface SnapshotPayload {
  v: 1;
  sub: string;
  email: string;
  name: string | null;
  role: UserRole;
  tier: SubscriptionTier;
  effectiveTier: SubscriptionTier;
  paymentStatus: PaymentStatus;
  seats: number;
  team: TeamMemberClaim[];
  iat: number;
  exp: number;
  jti: string;
}

export function canManageBilling(role: UserRole): boolean {
  return role === "OWNER" || role === "ACCOUNT_ADMIN";
}

export function canManageTeam(role: UserRole): boolean {
  return role === "OWNER" || role === "ACCOUNT_ADMIN" || role === "MANAGER";
}
