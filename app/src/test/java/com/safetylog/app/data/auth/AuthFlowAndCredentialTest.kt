package com.safetylog.app.data.auth

import com.safetylog.app.data.dao.EquipmentWithLatestInspection
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.ui.dashboard.DashboardUiState
import com.safetylog.app.ui.dashboard.FilterStatus
import com.safetylog.app.ui.profile.ProfileUiState
import org.junit.Assert.*
import org.junit.Test

class AuthFlowAndCredentialTest {

    @Test
    fun testDashboardUiStateOnlineOfflineToggle() {
        val onlineState = DashboardUiState(isOnline = true)
        assertTrue(onlineState.isOnline)

        val offlineState = DashboardUiState(isOnline = false)
        assertFalse(offlineState.isOnline)
    }

    @Test
    fun testAuthProviderConfigurations() {
        assertEquals("Google Account / Cloud Identity", AuthProvider.GOOGLE.displayName)
        assertEquals("Microsoft Account / 365", AuthProvider.MICROSOFT.displayName)
        assertEquals("Corporate SAML / OIDC Vault", AuthProvider.CORPORATE_SSO.displayName)
    }

    @Test
    fun testAuthUserInitials() {
        val userTwoWords = AuthUser(
            userId = "1",
            email = "john@example.com",
            displayName = "John Doe"
        )
        assertEquals("JD", userTwoWords.initials)

        val userMultiSpaces = AuthUser(
            userId = "1b",
            email = "john.multi@example.com",
            displayName = "John   Doe"
        )
        assertEquals("JD", userMultiSpaces.initials)

        val userLeadingTrailingSpaces = AuthUser(
            userId = "1c",
            email = "jane@example.com",
            displayName = "   Jane   Eyre   "
        )
        assertEquals("JE", userLeadingTrailingSpaces.initials)

        val userThreeWords = AuthUser(
            userId = "2",
            email = "marcus@example.com",
            displayName = "Marcus Vance Lee"
        )
        assertEquals("MV", userThreeWords.initials)

        val userSingleWord = AuthUser(
            userId = "3",
            email = "inspector@example.com",
            displayName = "Inspector"
        )
        assertEquals("IN", userSingleWord.initials)

        val userSingleWordWithSpaces = AuthUser(
            userId = "3b",
            email = "inspector@example.com",
            displayName = "   Inspector   "
        )
        assertEquals("IN", userSingleWordWithSpaces.initials)

        val userBlank = AuthUser(
            userId = "4",
            email = "empty@example.com",
            displayName = " "
        )
        assertEquals("SL", userBlank.initials)
    }

    @Test
    fun testTeamMemberInitialsWithWhitespace() {
        val memberMultiSpace = TeamMember(
            name = "Jane   Smith",
            email = "jane@example.com"
        )
        assertEquals("JS", memberMultiSpace.initials)

        val memberSingleWordSpaces = TeamMember(
            name = "  Marcus  ",
            email = "marcus@example.com"
        )
        assertEquals("MA", memberSingleWordSpaces.initials)

        val memberBlankName = TeamMember(
            name = "   ",
            email = "  tech@example.com  "
        )
        assertEquals("TE", memberBlankName.initials)
    }

    @Test
    fun testAuthUserSessionValidity() {
        val now = System.currentTimeMillis()
        val activeUser = AuthUser(
            userId = "1",
            email = "user@example.com",
            displayName = "Active User",
            tokenExpiryEpochMs = now + 100_000L
        )
        assertTrue(activeUser.isSessionActive)

        val expiredUser = AuthUser(
            userId = "2",
            email = "user@example.com",
            displayName = "Expired User",
            tokenExpiryEpochMs = now - 100_000L
        )
        assertFalse(expiredUser.isSessionActive)

        val permanentUser = AuthUser(
            userId = "3",
            email = "user@example.com",
            displayName = "Permanent User",
            tokenExpiryEpochMs = 0L
        )
        assertTrue(permanentUser.isSessionActive)
    }

    @Test
    fun testUserRolePermissions() {
        assertTrue(UserRole.OWNER.canManageTeam)
        assertTrue(UserRole.OWNER.canManageBillingAndTeam)
        assertTrue(UserRole.OWNER.canChangeAdminPermissions)
        assertTrue(UserRole.OWNER.canManageSettings)

        assertTrue(UserRole.ACCOUNT_ADMIN.canManageTeam)
        assertTrue(UserRole.ACCOUNT_ADMIN.canManageBillingAndTeam)
        assertTrue(UserRole.ACCOUNT_ADMIN.canChangeAdminPermissions)

        assertTrue(UserRole.MANAGER.canManageTeam)
        assertFalse(UserRole.MANAGER.canManageBillingAndTeam)
        assertFalse(UserRole.MANAGER.canChangeAdminPermissions)

        assertFalse(UserRole.USER.canManageTeam)
        assertFalse(UserRole.USER.canManageBillingAndTeam)
        assertFalse(UserRole.USER.canManageSettings)
        assertTrue(UserRole.USER.canConductInspections)
    }

    @Test
    fun testProfileUiStateDefaultsAndErrors() {
        val state = ProfileUiState(
            companyName = "Apex Safety",
            inspectorName = "Marcus Vance",
            contactEmail = "marcus@apex.com",
            errorMessage = "Sample error",
            isSaving = false
        )
        assertEquals("Apex Safety", state.companyName)
        assertEquals("Marcus Vance", state.inspectorName)
        assertEquals("marcus@apex.com", state.contactEmail)
        assertEquals("Sample error", state.errorMessage)
        assertFalse(state.isSavedSuccess)
        assertFalse(state.isSaving)

        val savingState = state.copy(isSaving = true)
        assertTrue(savingState.isSaving)
    }

    @Test
    fun testDashboardUiStateFilteringAndCounts() {
        val now = System.currentTimeMillis()
        val eq1 = EquipmentItem(
            id = 1,
            tagId = "EXT-001",
            equipmentType = "Fire Extinguisher",
            locationDescription = "Floor 1 Hallway",
            reviewFrequency = "Monthly",
            nextReviewTimestamp = now + 86400000L
        )
        val eq2 = EquipmentItem(
            id = 2,
            tagId = "FAK-002",
            equipmentType = "First Aid Kit",
            locationDescription = "Kitchen",
            reviewFrequency = "Monthly",
            nextReviewTimestamp = now - 1000L // Due
        )
        val eq3 = EquipmentItem(
            id = 3,
            tagId = "AEL-003",
            equipmentType = "Emergency Light",
            locationDescription = "Stairwell B",
            reviewFrequency = "Monthly",
            nextReviewTimestamp = now + 86400000L
        )

        val item1 = EquipmentWithLatestInspection(
            equipment = eq1,
            lastInspectionDate = now - 100000L,
            lastInspectionStatus = "PASSED"
        )
        val item2 = EquipmentWithLatestInspection(
            equipment = eq2,
            lastInspectionDate = now - 500000L,
            lastInspectionStatus = "DEFICIENT"
        )
        val item3 = EquipmentWithLatestInspection(
            equipment = eq3,
            lastInspectionDate = null,
            lastInspectionStatus = null
        )

        val list = listOf(item1, item2, item3)
        val dashboardState = DashboardUiState(
            equipmentList = list,
            activeFilter = FilterStatus.ALL
        )

        assertEquals(3, dashboardState.totalCount)
        assertEquals(1, dashboardState.passedCount)
        assertEquals(1, dashboardState.deficientCount)
        assertEquals(2, dashboardState.dueCount) // eq2 is overdue, eq3 has never been inspected

        // Filter PASSED
        val passedState = dashboardState.copy(activeFilter = FilterStatus.PASSED)
        assertEquals(1, passedState.filteredList.size)
        assertEquals("EXT-001", passedState.filteredList[0].equipment.tagId)

        // Filter DEFICIENT
        val deficientState = dashboardState.copy(activeFilter = FilterStatus.DEFICIENT)
        assertEquals(1, deficientState.filteredList.size)
        assertEquals("FAK-002", deficientState.filteredList[0].equipment.tagId)

        // Filter DUE
        val dueState = dashboardState.copy(activeFilter = FilterStatus.DUE)
        assertEquals(2, dueState.filteredList.size)

        // Search Query
        val searchState = dashboardState.copy(searchQuery = "Kitchen")
        assertEquals(1, searchState.filteredList.size)
        assertEquals("FAK-002", searchState.filteredList[0].equipment.tagId)
    }

    @Test
    fun testBuildConfigReleaseVersion() {
        assertEquals("1.0.2", com.safetylog.app.BuildConfig.VERSION_NAME)
        assertEquals(5, com.safetylog.app.BuildConfig.VERSION_CODE)
        assertEquals("com.safetylog.inspection.app", com.safetylog.app.BuildConfig.APPLICATION_ID)
    }

    @Test
    fun testCrashLogVaultVersionHeaderFormat() {
        val expectedHeaderLine = "App Version: ${com.safetylog.app.BuildConfig.VERSION_NAME} (Build ${com.safetylog.app.BuildConfig.VERSION_CODE})"
        assertEquals("App Version: 1.0.2 (Build 5)", expectedHeaderLine)
    }
}

