package com.safetylog.app.data.auth

enum class UserRole(
    val title: String,
    val description: String,
    val badgeColorHex: Long
) {
    OWNER(
        title = "Owner",
        description = "Supreme platform owner with full authority over account, architecture, billing, and team permissions.",
        badgeColorHex = 0xFF7C3AED // Deep Purple / Sovereign
    ),
    ACCOUNT_ADMIN(
        title = "Account Admin",
        description = "Billing and administrative authority. Manages group subscriptions, seat allocations, all account settings, and permission assignments for account admins, managers, and users.",
        badgeColorHex = 0xFFEA580C // SafetyOrange
    ),
    MANAGER(
        title = "Manager",
        description = "Operational administrator. Configures settings, company OAuth, sync integrations, and team roster, without access to financial or billing actions.",
        badgeColorHex = 0xFF0284C7 // TechBlue
    ),
    USER(
        title = "User",
        description = "Field technician and inspector. Performs equipment checklist inspections, logs defects, and submits signed audit records.",
        badgeColorHex = 0xFF16A34A // StatusPassed
    );

    val isOwner: Boolean get() = this == OWNER
    val isAccountAdmin: Boolean get() = this == ACCOUNT_ADMIN
    val isManager: Boolean get() = this == MANAGER
    val isUser: Boolean get() = this == USER

    // Financial / Money: Only Owner and Account Admin can manage billing, subscriptions, and seat expansions. Managers CANNOT.
    val canManageBilling: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN

    // All account settings: Owner and Account Admin can manage all account settings
    val canManageAllAccountSettings: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN

    // Settings (OAuth, Sync, Webhooks): Owner, Account Admin, and Manager can configure settings
    val canManageSettings: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN || this == MANAGER

    // Team management: Owner, Account Admin, and Manager can manage team members
    val canManageTeam: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN || this == MANAGER

    // Permission delegation: Owner and Account Admin can assign Account Admin, Manager, and User roles. Managers CANNOT assign Account Admin.
    val canChangeAdminPermissions: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN

    val canAccessTestView: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN
    val canDeleteAssets: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN || this == MANAGER
    val canManageAssets: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN || this == MANAGER
    val canConductInspections: Boolean get() = true

    // Backwards compatibility helper
    val canManageBillingAndTeam: Boolean get() = this == OWNER || this == ACCOUNT_ADMIN

    companion object {
        fun fromLegacy(name: String?): UserRole = when (name?.trim()?.uppercase()) {
            "OWNER", "OWNER_ADMIN" -> OWNER
            "ACCOUNT_ADMIN", "ADMIN" -> ACCOUNT_ADMIN
            "MANAGER", "SAFETY_MANAGER" -> MANAGER
            "USER", "TECHNICIAN", "FIELD_USER", "VIEWER" -> USER
            else -> USER
        }
    }
}

/**
 * Strict platform ownership and role-access control rules.
 * Enforces that only verified owner accounts can possess or assume the OWNER role.
 */
object AuthorizationConfig {
    val AUTHORIZED_OWNER_EMAILS = setOf(
        "j.desrosiers88@gmail.com",
        "hexenginelabs@gmail.com"
    )

    fun isAuthorizedOwner(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return AUTHORIZED_OWNER_EMAILS.contains(email.trim().lowercase())
    }

    /**
     * Sanitizes role assignment. If OWNER is requested, it is only permitted if the email
     * is explicitly present in AUTHORIZED_OWNER_EMAILS; otherwise it automatically defaults
     * down to USER (or the provided fallback role).
     */
    fun sanitizeRole(role: UserRole, email: String?, fallback: UserRole = UserRole.USER): UserRole {
        if (role == UserRole.OWNER) {
            return if (isAuthorizedOwner(email)) UserRole.OWNER else fallback
        }
        return role
    }
}

enum class SubscriptionTier(
    val title: String,
    val subtitle: String,
    val priceText: String,
    val billingCycleText: String,
    val isAdFree: Boolean,
    val allowsSharing: Boolean,
    val isEnterprise: Boolean
) {
    FREE(
        title = "Standard Free",
        subtitle = "Basic life-safety inspection logging with banner sponsorship.",
        priceText = "Free",
        billingCycleText = "Banner Ad-Supported",
        isAdFree = false,
        allowsSharing = false,
        isEnterprise = false
    ),
    AD_FREE(
        title = "Ad-Free License",
        subtitle = "Permanently removes all banner ads across the entire app.",
        priceText = "$2.99",
        billingCycleText = "One-Time Flat Rate",
        isAdFree = true,
        allowsSharing = false,
        isEnterprise = false
    ),
    INDIVIDUAL_PRO(
        title = "Individual Share & Sync",
        subtitle = "Remove ads + share audit logs & sync vaults between individual user accounts.",
        priceText = "$0.99",
        billingCycleText = "/ month",
        isAdFree = true,
        allowsSharing = true,
        isEnterprise = false
    ),
    ENTERPRISE(
        title = "Enterprise Multi-Seat",
        subtitle = "Remove ads + multi-account vault sharing & seat synchronization across company inspectors.",
        priceText = "$0.50",
        billingCycleText = "/ person / month",
        isAdFree = true,
        allowsSharing = true,
        isEnterprise = true
    )
}

enum class AuthProvider(val displayName: String, val brandColorHex: Long) {
    GOOGLE("Google Account / Cloud Identity", 0xFF4285F4),
    MICROSOFT("Microsoft Account / 365", 0xFF00A4EF),
    CORPORATE_SSO("Corporate SAML / OIDC Vault", 0xFF0F9D58),
    ENTERPRISE_SSO("Corporate SAML / OIDC Vault", 0xFF0F9D58)
}

data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.GOOGLE,
    val role: UserRole = UserRole.USER,
    val idToken: String? = null,
    val accessToken: String? = null,
    val tokenExpiryEpochMs: Long = 0L,
    val lastLoginEpochMs: Long = 0L,
    val isHardwareEncrypted: Boolean = true,
    val keyAlias: String = "safetylog_master_keystore_v1"
) {
    val isSessionActive: Boolean
        get() = tokenExpiryEpochMs == 0L || tokenExpiryEpochMs > System.currentTimeMillis()

    val initials: String
        get() {
            val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return if (words.size >= 2) {
                "${words[0].take(1)}${words[1].take(1)}".uppercase()
            } else if (words.size == 1) {
                words[0].take(2).uppercase().ifBlank { "SL" }
            } else {
                "SL"
            }
        }
}

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    object Cancelled : AuthResult()
}

enum class PaymentStatus(
    val title: String,
    val description: String,
    val isGoodStanding: Boolean
) {
    ACTIVE(
        title = "Active",
        description = "Subscription is active and in good standing.",
        isGoodStanding = true
    ),
    PAYMENT_FAILED(
        title = "Payment Failed",
        description = "Payment could not be delivered. Functions locked to Free use.",
        isGoodStanding = false
    ),
    CANCELLED(
        title = "Cancelled",
        description = "Subscription was cancelled. Account locked to Free use.",
        isGoodStanding = false
    ),
    GRACE_PERIOD(
        title = "Grace Period",
        description = "Payment retry in progress.",
        isGoodStanding = true
    )
}

enum class CompanyOAuthProvider(
    val displayName: String,
    val protocol: String,
    val placeholderDomain: String
) {
    CORPORATE_SAML("Corporate SAML 2.0 IdP", "SAML 2.0", "sso.company.com"),
    MICROSOFT_ENTRA("Microsoft Entra ID (Azure AD)", "OIDC / OAuth 2.0", "tenant.onmicrosoft.com"),
    OKTA_WORKFORCE("Okta Workforce Identity", "OIDC", "company.okta.com"),
    GOOGLE_WORKSPACE("Google Workspace Domain Lock", "OIDC OAuth 2.0", "company.com")
}

data class CompanyOAuthConfig(
    val companyName: String = "",
    val corporateDomain: String = "",
    val provider: CompanyOAuthProvider = CompanyOAuthProvider.CORPORATE_SAML,
    val tenantIdOrDomain: String = "",
    val clientId: String = "",
    val ssoEntryPointUrl: String = "",
    val enforceDomainRestriction: Boolean = true,
    val isConfigured: Boolean = false,
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val webhookAutoDispatch: Boolean = false,
    val cloudDriveFolderUri: String = "",
    val cloudDriveFolderName: String = "",
    val cloudDriveAutoBackup: Boolean = false
)

data class TeamMember(
    val id: String = java.util.UUID.randomUUID().toString(),
    val email: String,
    val name: String = "",
    val role: UserRole = UserRole.USER,
    val addedEpochMs: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    val displayTitle: String
        get() = name.ifBlank { email }

    val initials: String
        get() {
            if (name.isNotBlank()) {
                val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size >= 2) {
                    return "${words[0].take(1)}${words[1].take(1)}".uppercase()
                }
                if (words.size == 1) {
                    return words[0].take(2).uppercase().ifBlank { "TM" }
                }
            }
            return email.trim().take(2).uppercase().ifBlank { "TM" }
        }
}

