package com.safetylog.app.data.auth

import org.junit.Assert.*
import org.junit.Test

class RoleAndSubscriptionTest {

    @Test
    fun testOwnerPermissions() {
        val role = UserRole.OWNER
        assertTrue("Owner must be identified as owner", role.isOwner)
        assertFalse("Owner is distinct from account admins", role.isAccountAdmin)
        assertTrue("Owner can manage billing and financial operations", role.canManageBilling)
        assertTrue("Owner can manage all account settings", role.canManageAllAccountSettings)
        assertTrue("Owner can manage settings (OAuth, Sync, Webhooks)", role.canManageSettings)
        assertTrue("Owner can manage team members", role.canManageTeam)
        assertTrue("Owner can change admin permissions", role.canChangeAdminPermissions)
        assertTrue("Owner can delete assets", role.canDeleteAssets)
        assertTrue("Owner can manage assets", role.canManageAssets)
        assertTrue("Owner can access developer test view", role.canAccessTestView)
        assertTrue("Owner can conduct inspections", role.canConductInspections)
    }

    @Test
    fun testAccountAdminPermissions() {
        val role = UserRole.ACCOUNT_ADMIN
        assertFalse("Account admin is separate from owner", role.isOwner)
        assertTrue("Role is identified as account admin", role.isAccountAdmin)
        assertTrue("Account admin manages group billing and money", role.canManageBilling)
        assertTrue("Account admin manages all account settings", role.canManageAllAccountSettings)
        assertTrue("Account admin can manage settings", role.canManageSettings)
        assertTrue("Account admin can manage team", role.canManageTeam)
        assertTrue("Account admin can change permissions to have other account admins, managers, and users", role.canChangeAdminPermissions)
        assertTrue("Account admin can delete assets", role.canDeleteAssets)
        assertTrue("Account admin can manage assets", role.canManageAssets)
        assertTrue("Account admin can access test view", role.canAccessTestView)
        assertTrue("Account admin can conduct inspections", role.canConductInspections)
    }

    @Test
    fun testManagerPermissions() {
        val role = UserRole.MANAGER
        assertFalse("Manager is not owner", role.isOwner)
        assertFalse("Manager is not account admin", role.isAccountAdmin)
        assertTrue("Role is manager", role.isManager)
        // Managers CANNOT do anything related to money
        assertFalse("Managers CANNOT do anything related to money or billing", role.canManageBilling)
        assertFalse("Managers cannot manage all account settings", role.canManageAllAccountSettings)
        // Managers CAN add to and configure settings
        assertTrue("Managers CAN add to and configure settings (OAuth, Sync, Webhooks)", role.canManageSettings)
        assertTrue("Managers can manage operational team members", role.canManageTeam)
        assertFalse("Managers CANNOT assign the Account Admin role", role.canChangeAdminPermissions)
        assertTrue("Managers can manage assets", role.canManageAssets)
        assertTrue("Managers can delete assets", role.canDeleteAssets)
        assertTrue("Managers can conduct inspections", role.canConductInspections)
    }

    @Test
    fun testUserPermissions() {
        val role = UserRole.USER
        assertFalse("User is not owner", role.isOwner)
        assertFalse("User is not account admin", role.isAccountAdmin)
        assertFalse("User is not manager", role.isManager)
        assertTrue("Role is field user", role.isUser)
        assertFalse("Users cannot touch money", role.canManageBilling)
        assertFalse("Users cannot manage account settings", role.canManageAllAccountSettings)
        assertFalse("Users cannot manage settings", role.canManageSettings)
        assertFalse("Users cannot manage team", role.canManageTeam)
        assertFalse("Users cannot change admin permissions", role.canChangeAdminPermissions)
        assertFalse("Users cannot access test view", role.canAccessTestView)
        assertTrue("Users can conduct equipment inspections", role.canConductInspections)
    }

    @Test
    fun testLegacyRoleMapping() {
        assertEquals(UserRole.OWNER, UserRole.fromLegacy("OWNER"))
        assertEquals(UserRole.OWNER, UserRole.fromLegacy("OWNER_ADMIN"))
        assertEquals(UserRole.ACCOUNT_ADMIN, UserRole.fromLegacy("ACCOUNT_ADMIN"))
        assertEquals(UserRole.ACCOUNT_ADMIN, UserRole.fromLegacy("ADMIN"))
        assertEquals(UserRole.MANAGER, UserRole.fromLegacy("MANAGER"))
        assertEquals(UserRole.MANAGER, UserRole.fromLegacy("SAFETY_MANAGER"))
        assertEquals(UserRole.USER, UserRole.fromLegacy("USER"))
        assertEquals(UserRole.USER, UserRole.fromLegacy("TECHNICIAN"))
        assertEquals(UserRole.USER, UserRole.fromLegacy("FIELD_USER"))
        assertEquals(UserRole.USER, UserRole.fromLegacy("VIEWER"))
        assertEquals(UserRole.USER, UserRole.fromLegacy("unknown_role"))
        assertEquals(UserRole.USER, UserRole.fromLegacy(null))
    }

    @Test
    fun testSubscriptionTierPricingAndFeatures() {
        // Free tier: standard, banner ads present, no account sharing
        val free = SubscriptionTier.FREE
        assertFalse("Free tier shows banner ads", free.isAdFree)
        assertFalse("Free tier cannot share between accounts", free.allowsSharing)
        assertFalse("Free tier is not enterprise", free.isEnterprise)

        // Ad-Free tier: $2.99 flat rate, banner ads removed, single account
        val adFree = SubscriptionTier.AD_FREE
        assertEquals("$2.99", adFree.priceText)
        assertEquals("One-Time Flat Rate", adFree.billingCycleText)
        assertTrue("Ad-Free tier removes banner ads", adFree.isAdFree)
        assertFalse("Ad-Free tier cannot share between accounts", adFree.allowsSharing)

        // Individual Pro tier: $0.99/mo, banner ads removed, account sharing enabled
        val indivPro = SubscriptionTier.INDIVIDUAL_PRO
        assertEquals("$0.99", indivPro.priceText)
        assertEquals("/ month", indivPro.billingCycleText)
        assertTrue("Individual Pro tier removes banner ads", indivPro.isAdFree)
        assertTrue("Individual Pro tier allows sharing between accounts", indivPro.allowsSharing)
        assertFalse("Individual Pro tier is not enterprise multi-seat", indivPro.isEnterprise)

        // Enterprise tier: $0.50/person/mo, banner ads removed, multi-seat sharing enabled
        val enterprise = SubscriptionTier.ENTERPRISE
        assertEquals("$0.50", enterprise.priceText)
        assertEquals("/ person / month", enterprise.billingCycleText)
        assertTrue("Enterprise tier removes banner ads", enterprise.isAdFree)
        assertTrue("Enterprise tier allows sharing between accounts", enterprise.allowsSharing)
        assertTrue("Enterprise tier is enterprise multi-seat", enterprise.isEnterprise)
    }

    @Test
    fun testEnterpriseSeatCalculations() {
        val costPerSeat = 0.50

        val seats1 = 1
        assertEquals(0.50, seats1 * costPerSeat, 0.001)

        val seats5 = 5
        assertEquals(2.50, seats5 * costPerSeat, 0.001)

        val seats10 = 10
        assertEquals(5.00, seats10 * costPerSeat, 0.001)

        val seats50 = 50
        assertEquals(25.00, seats50 * costPerSeat, 0.001)

        val seats100 = 100
        assertEquals(50.00, seats100 * costPerSeat, 0.001)
    }

    @Test
    fun testPlayBillingSkuConstants() {
        assertEquals("safetylog_adfree_299", com.safetylog.app.data.BillingRepository.SKU_AD_FREE)
        assertEquals("safetylog_pro_monthly_99", com.safetylog.app.data.BillingRepository.SKU_INDIVIDUAL_PRO)
        assertEquals("safetylog_enterprise_monthly_seat", com.safetylog.app.data.BillingRepository.SKU_ENTERPRISE_SEAT)
    }

    @Test
    fun testPaymentStatusGoodStanding() {
        assertTrue(PaymentStatus.ACTIVE.isGoodStanding)
        assertTrue(PaymentStatus.GRACE_PERIOD.isGoodStanding)
        assertFalse(PaymentStatus.PAYMENT_FAILED.isGoodStanding)
        assertFalse(PaymentStatus.CANCELLED.isGoodStanding)
    }

    @Test
    fun testSelfServiceEffectiveTierLockdown() {
        // 1. Enterprise active: in good standing -> effective is Enterprise
        val effectiveActiveEnterprise = com.safetylog.app.data.BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.ENTERPRISE,
            status = PaymentStatus.ACTIVE,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.ENTERPRISE, effectiveActiveEnterprise)

        // 2. Enterprise payment failed -> locks down to FREE
        val effectiveFailedEnterprise = com.safetylog.app.data.BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.ENTERPRISE,
            status = PaymentStatus.PAYMENT_FAILED,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.FREE, effectiveFailedEnterprise)
        assertFalse("Locked to free, cannot share", effectiveFailedEnterprise.allowsSharing)
        assertFalse("Locked to free, not enterprise", effectiveFailedEnterprise.isEnterprise)

        // 3. Enterprise cancelled -> locks down to FREE
        val effectiveCancelledEnterprise = com.safetylog.app.data.BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.ENTERPRISE,
            status = PaymentStatus.CANCELLED,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.FREE, effectiveCancelledEnterprise)

        // 4. Individual Pro payment failed -> locks down to FREE
        val effectiveFailedPro = com.safetylog.app.data.BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.INDIVIDUAL_PRO,
            status = PaymentStatus.PAYMENT_FAILED,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.FREE, effectiveFailedPro)

        // 5. User previously purchased $2.99 one-time Ad-Free pass, then Enterprise failed:
        val effectiveFailedWithOneTime = com.safetylog.app.data.BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.ENTERPRISE,
            status = PaymentStatus.PAYMENT_FAILED,
            oneTimeAdFree = true
        )
        assertEquals(SubscriptionTier.AD_FREE, effectiveFailedWithOneTime)
        assertTrue(effectiveFailedWithOneTime.isAdFree)
        assertFalse("Lifetime ad-free does NOT permit enterprise sharing", effectiveFailedWithOneTime.allowsSharing)
        assertFalse("Lifetime ad-free does NOT permit company OAuth", effectiveFailedWithOneTime.isEnterprise)
    }

    @Test
    fun testCompanyOAuthConfigModel() {
        val defaultConfig = CompanyOAuthConfig()
        assertFalse("Default company config is not configured", defaultConfig.isConfigured)

        val activeConfig = CompanyOAuthConfig(
            companyName = "Apex Safety Systems",
            corporateDomain = "apexsafety.com",
            provider = CompanyOAuthProvider.MICROSOFT_ENTRA,
            tenantIdOrDomain = "tenant-uuid-1234",
            clientId = "client-id-5678",
            enforceDomainRestriction = true,
            isConfigured = true
        )
        assertTrue(activeConfig.isConfigured)
        assertEquals("Apex Safety Systems", activeConfig.companyName)
        assertEquals("apexsafety.com", activeConfig.corporateDomain)
        assertEquals(CompanyOAuthProvider.MICROSOFT_ENTRA, activeConfig.provider)
        assertTrue(activeConfig.enforceDomainRestriction)
    }

    @Test
    fun testTeamMemberCreationAndDisplay() {
        val member = TeamMember(
            email = "jane.smith@acmefire.com",
            name = "Jane Smith",
            role = UserRole.MANAGER,
            isActive = true
        )

        assertEquals("Jane Smith", member.displayTitle)
        assertEquals("JS", member.initials)
        assertEquals("jane.smith@acmefire.com", member.email)
        assertEquals(UserRole.MANAGER, member.role)
        assertTrue(member.isActive)
    }

    @Test
    fun testTeamMemberFallbackInitialsWhenNameEmpty() {
        val member = TeamMember(
            email = "technician@company.com",
            name = "",
            role = UserRole.USER
        )

        assertEquals("technician@company.com", member.displayTitle)
        assertEquals("TE", member.initials)
    }

    @Test
    fun testMoneyManagementRestrictedToAccountAdminAndOwner() {
        assertTrue(UserRole.OWNER.canManageBilling)
        assertTrue(UserRole.ACCOUNT_ADMIN.canManageBilling)
        assertFalse(UserRole.MANAGER.canManageBilling)
        assertFalse(UserRole.USER.canManageBilling)
    }

    @Test
    fun testSettingsConfigurationAllowedForManagerAndAdmins() {
        assertTrue(UserRole.OWNER.canManageSettings)
        assertTrue(UserRole.ACCOUNT_ADMIN.canManageSettings)
        assertTrue(UserRole.MANAGER.canManageSettings)
        assertFalse(UserRole.USER.canManageSettings)
    }

    @Test
    fun testChangeAdminPermissionsRestrictedToAccountAdminAndOwner() {
        assertTrue(UserRole.OWNER.canChangeAdminPermissions)
        assertTrue(UserRole.ACCOUNT_ADMIN.canChangeAdminPermissions)
        assertFalse(UserRole.MANAGER.canChangeAdminPermissions)
        assertFalse(UserRole.USER.canChangeAdminPermissions)
    }

    @Test
    fun testAuthUserDefaultRoleIsUserNotOwner() {
        val user = AuthUser(
            userId = "test_user_123",
            email = "worker@company.com",
            displayName = "Field Worker"
        )
        assertEquals("Default role for AuthUser must be USER, never OWNER", UserRole.USER, user.role)
    }

    @Test
    fun testAuthorizedOwnerRoleSwitchingSimulation() {
        val ownerEmail = "j.desrosiers88@gmail.com"
        val coOwnerEmail = "hexenginelabs@gmail.com"

        // Authorized owners can simulate and assume any of the 4 roles
        for (email in listOf(ownerEmail, coOwnerEmail)) {
            assertEquals(UserRole.OWNER, AuthorizationConfig.sanitizeRole(UserRole.OWNER, email))
            assertEquals(UserRole.ACCOUNT_ADMIN, AuthorizationConfig.sanitizeRole(UserRole.ACCOUNT_ADMIN, email))
            assertEquals(UserRole.MANAGER, AuthorizationConfig.sanitizeRole(UserRole.MANAGER, email))
            assertEquals(UserRole.USER, AuthorizationConfig.sanitizeRole(UserRole.USER, email))
        }
    }

    @Test
    fun testNonOwnerRoleSwitchingRejectsOwner() {
        val unauthorizedEmail = "hacker@example.com"
        val techEmail = "technician@safetylog.internal"

        for (email in listOf(unauthorizedEmail, techEmail, null, "")) {
            // Attempting to assume OWNER MUST be rejected and sanitized to USER
            assertEquals(UserRole.USER, AuthorizationConfig.sanitizeRole(UserRole.OWNER, email))

            // Non-owner can switch between ACCOUNT_ADMIN, MANAGER, and USER for testing/delegation
            assertEquals(UserRole.ACCOUNT_ADMIN, AuthorizationConfig.sanitizeRole(UserRole.ACCOUNT_ADMIN, email))
            assertEquals(UserRole.MANAGER, AuthorizationConfig.sanitizeRole(UserRole.MANAGER, email))
            assertEquals(UserRole.USER, AuthorizationConfig.sanitizeRole(UserRole.USER, email))
        }
    }

    @Test
    fun testTeamRosterAssignableRolesByCallerRole() {
        // Owner and Account Admin can delegate ACCOUNT_ADMIN, MANAGER, and USER
        val adminAssignable = if (UserRole.OWNER.canChangeAdminPermissions) {
            listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER)
        } else {
            listOf(UserRole.MANAGER, UserRole.USER)
        }
        assertEquals(listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER), adminAssignable)
        assertFalse("OWNER can NEVER be assigned to a team roster member", adminAssignable.contains(UserRole.OWNER))

        val accountAdminAssignable = if (UserRole.ACCOUNT_ADMIN.canChangeAdminPermissions) {
            listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER)
        } else {
            listOf(UserRole.MANAGER, UserRole.USER)
        }
        assertEquals(listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER), accountAdminAssignable)
        assertFalse("OWNER can NEVER be assigned to a team roster member", accountAdminAssignable.contains(UserRole.OWNER))

        // Manager can only delegate MANAGER and USER (cannot assign ACCOUNT_ADMIN)
        val managerAssignable = if (UserRole.MANAGER.canChangeAdminPermissions) {
            listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER)
        } else {
            listOf(UserRole.MANAGER, UserRole.USER)
        }
        assertEquals(listOf(UserRole.MANAGER, UserRole.USER), managerAssignable)
        assertFalse("Managers cannot assign ACCOUNT_ADMIN", managerAssignable.contains(UserRole.ACCOUNT_ADMIN))
        assertFalse("Managers cannot assign OWNER", managerAssignable.contains(UserRole.OWNER))
    }

    @Test
    fun testSelfServicePaywallBillingAllowedLogic() {
        // 1. Standalone user on Free tier: must be allowed to purchase Ad-Free, Individual Pro, or Enterprise
        val freeUserRole = UserRole.USER
        val freeTier = SubscriptionTier.FREE
        val isFreeUserRestricted = freeTier == SubscriptionTier.ENTERPRISE && !freeUserRole.canManageBilling
        assertFalse("Free user is NOT restricted from purchasing personal/group subscriptions", isFreeUserRestricted)
        val isBillingAllowedForFreeUser = !isFreeUserRestricted
        assertTrue("Billing must be allowed for free user upgrading", isBillingAllowedForFreeUser)

        // 2. Individual Pro subscriber (User role): allowed to upgrade or manage personal billing
        val indivProTier = SubscriptionTier.INDIVIDUAL_PRO
        val isIndivProRestricted = indivProTier == SubscriptionTier.ENTERPRISE && !freeUserRole.canManageBilling
        assertFalse("Individual Pro user is NOT corporate-restricted", isIndivProRestricted)

        // 3. Subordinate member in an active Enterprise organization (User / Manager):
        // Must be restricted from altering company enterprise billing or expanding seats
        val enterpriseTier = SubscriptionTier.ENTERPRISE
        val isEnterpriseUserRestricted = enterpriseTier == SubscriptionTier.ENTERPRISE && !UserRole.USER.canManageBilling
        assertTrue("Subordinate User in Enterprise organization is restricted", isEnterpriseUserRestricted)

        val isEnterpriseManagerRestricted = enterpriseTier == SubscriptionTier.ENTERPRISE && !UserRole.MANAGER.canManageBilling
        assertTrue("Operational Manager in Enterprise organization is restricted from billing", isEnterpriseManagerRestricted)

        // 4. Account Admin or Owner in Enterprise organization:
        // Has full authority over company billing, seats, and plans
        val isAccountAdminRestricted = enterpriseTier == SubscriptionTier.ENTERPRISE && !UserRole.ACCOUNT_ADMIN.canManageBilling
        assertFalse("Account Admin has billing authority in Enterprise", isAccountAdminRestricted)

        val isOwnerRestricted = enterpriseTier == SubscriptionTier.ENTERPRISE && !UserRole.OWNER.canManageBilling
        assertFalse("Owner has full billing authority in Enterprise", isOwnerRestricted)
    }

    @Test
    fun testPaidServicesPrivilegesMatrix() {
        // Free tier: banner ads shown, no sharing, no company OAuth
        assertFalse(SubscriptionTier.FREE.isAdFree)
        assertFalse(SubscriptionTier.FREE.allowsSharing)
        assertFalse(SubscriptionTier.FREE.isEnterprise)

        // Ad-Free pass ($2.99 one-time): banner ads removed, single account (no sharing)
        assertTrue(SubscriptionTier.AD_FREE.isAdFree)
        assertFalse(SubscriptionTier.AD_FREE.allowsSharing)
        assertFalse(SubscriptionTier.AD_FREE.isEnterprise)

        // Individual Pro ($0.99/mo): ads removed, cross-account sharing enabled
        assertTrue(SubscriptionTier.INDIVIDUAL_PRO.isAdFree)
        assertTrue(SubscriptionTier.INDIVIDUAL_PRO.allowsSharing)
        assertFalse(SubscriptionTier.INDIVIDUAL_PRO.isEnterprise)

        // Enterprise ($0.50/seat/mo): ads removed, sharing enabled, enterprise multi-seat
        assertTrue(SubscriptionTier.ENTERPRISE.isAdFree)
        assertTrue(SubscriptionTier.ENTERPRISE.allowsSharing)
        assertTrue(SubscriptionTier.ENTERPRISE.isEnterprise)
    }
}
