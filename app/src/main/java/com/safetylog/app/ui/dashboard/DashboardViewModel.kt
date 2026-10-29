package com.safetylog.app.ui.dashboard

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.AuthRepository
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.TeamMember
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.EquipmentWithLatestInspection
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.reminder.InspectionReminderManager
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.domain.EntitlementManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class FilterStatus {
    ALL, PASSED, DEFICIENT, DUE
}

data class DashboardUiState(
    val equipmentList: List<EquipmentWithLatestInspection> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: FilterStatus = FilterStatus.ALL,
    val isProUser: Boolean = false,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val effectiveTier: SubscriptionTier = SubscriptionTier.FREE,
    val paymentStatus: PaymentStatus = PaymentStatus.ACTIVE,
    val isAdFree: Boolean = false,
    val canShareBetweenAccounts: Boolean = false,
    val canUseCompanyOAuth: Boolean = false,
    val companyOAuthConfig: CompanyOAuthConfig = CompanyOAuthConfig(),
    val enterpriseSeats: Int = 5,
    val teamMembers: List<TeamMember> = emptyList(),
    val userRole: UserRole = UserRole.USER,
    val isAdminTestViewActive: Boolean = false,
    val authUser: com.safetylog.app.data.auth.AuthUser? = null,
    val inspectionsThisMonth: Int = 0,
    val freeLimit: Int = 5,
    val isOnline: Boolean = true,
    val isLoading: Boolean = true
) {
    val totalCount: Int get() = equipmentList.size
    val passedCount: Int get() = equipmentList.count { it.lastInspectionStatus.equals("PASSED", ignoreCase = true) }
    val deficientCount: Int get() = equipmentList.count { it.lastInspectionStatus.equals("DEFICIENT", ignoreCase = true) }
    val dueCount: Int get() = equipmentList.count { item ->
        item.lastInspectionDate == null || (item.equipment.nextReviewTimestamp in 1..<System.currentTimeMillis())
    }

    val isSubscriptionDelinquentOrCancelled: Boolean
        get() = subscriptionTier != SubscriptionTier.FREE &&
                subscriptionTier != SubscriptionTier.AD_FREE &&
                !paymentStatus.isGoodStanding

    val filteredList: List<EquipmentWithLatestInspection> get() {
        val now = System.currentTimeMillis()
        return equipmentList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.equipment.tagId.contains(searchQuery, ignoreCase = true) ||
                item.equipment.locationDescription.contains(searchQuery, ignoreCase = true) ||
                item.equipment.equipmentType.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (activeFilter) {
                FilterStatus.ALL -> true
                FilterStatus.PASSED -> item.lastInspectionStatus.equals("PASSED", ignoreCase = true)
                FilterStatus.DEFICIENT -> item.lastInspectionStatus.equals("DEFICIENT", ignoreCase = true)
                FilterStatus.DUE -> item.lastInspectionDate == null || (item.equipment.nextReviewTimestamp in 1..<now)
            }
            matchesSearch && matchesFilter
        }
    }

    val monthlyProgressFraction: Float get() {
        if (effectiveTier != SubscriptionTier.FREE) return 1f
        return (inspectionsThisMonth.toFloat() / freeLimit.toFloat()).coerceIn(0f, 1f)
    }
}

class DashboardViewModel(
    private val equipmentDao: EquipmentDao,
    private val inspectionDao: InspectionDao,
    private val entitlementManager: EntitlementManager,
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository,
    private val teamSyncEngine: TeamSyncEngine? = null,
    private val reminderManager: InspectionReminderManager? = null,
    private val networkMonitor: com.safetylog.app.util.NetworkMonitor? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(FilterStatus.ALL)
    private val _isAdminTestViewActive = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        equipmentDao.getEquipmentSummaries(),
        _searchQuery,
        _activeFilter,
        billingRepository.currentTier,
        billingRepository.effectiveTier,
        billingRepository.paymentStatus,
        billingRepository.enterpriseSeats,
        billingRepository.companyOAuthConfig,
        entitlementManager.countInspectionsThisMonthFlow(),
        authRepository.currentUser,
        _isAdminTestViewActive,
        billingRepository.teamMembers,
        networkMonitor?.isOnline ?: MutableStateFlow(true),
        authRepository.assignedRole
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val summaries = args[0] as List<EquipmentWithLatestInspection>
        val query = args[1] as String
        val filter = args[2] as FilterStatus
        val tier = args[3] as SubscriptionTier
        val effective = args[4] as SubscriptionTier
        val payStatus = args[5] as PaymentStatus
        val seats = args[6] as Int
        val companyConfig = args[7] as CompanyOAuthConfig
        val countThisMonth = args[8] as Int
        val authUser = args[9] as? com.safetylog.app.data.auth.AuthUser
        val isTestView = args[10] as Boolean
        @Suppress("UNCHECKED_CAST")
        val members = args[11] as? List<TeamMember> ?: emptyList()
        val online = args[12] as? Boolean ?: true
        val assigned = args[13] as? UserRole ?: UserRole.USER

        // Match authenticated user against active team members in roster, or fall back to user's assigned role
        val matchingMember = authUser?.email?.trim()?.let { userEmail ->
            members.find { it.email.equals(userEmail, ignoreCase = true) && it.isActive }
        }
        val rawRole = when {
            com.safetylog.app.data.auth.AuthorizationConfig.isAuthorizedOwner(authUser?.email) -> UserRole.OWNER
            matchingMember != null -> matchingMember.role
            authUser?.role != null -> authUser.role
            else -> assigned
        }
        val role = com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(rawRole, authUser?.email)

        DashboardUiState(
            equipmentList = summaries,
            searchQuery = query,
            activeFilter = filter,
            isProUser = effective != SubscriptionTier.FREE,
            subscriptionTier = tier,
            effectiveTier = effective,
            paymentStatus = payStatus,
            isAdFree = effective.isAdFree,
            canShareBetweenAccounts = effective.allowsSharing,
            canUseCompanyOAuth = effective.isEnterprise && payStatus.isGoodStanding,
            companyOAuthConfig = companyConfig,
            enterpriseSeats = seats,
            teamMembers = members,
            userRole = role,
            isAdminTestViewActive = isTestView,
            authUser = authUser,
            inspectionsThisMonth = countThisMonth,
            freeLimit = entitlementManager.FREE_MONTHLY_LIMIT,
            isOnline = online,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: FilterStatus) {
        _activeFilter.value = filter
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun deleteEquipment(equipment: EquipmentItem) {
        viewModelScope.launch {
            equipmentDao.deleteEquipment(equipment)
            reminderManager?.cancelReminder(equipment.id)
        }
    }

    fun createCalendarIntent(equipment: EquipmentItem): Intent? {
        return reminderManager?.createCalendarIntent(equipment)
    }

    fun rescheduleAllReminders() {
        viewModelScope.launch {
            val all = equipmentDao.getAllEquipment()
            reminderManager?.rescheduleAll(all.filter { it.reminderEnabled && it.nextReviewTimestamp > System.currentTimeMillis() })
        }
    }

    fun triggerTestNotification(): Boolean {
        return reminderManager?.sendTestNotification() ?: false
    }

    fun toggleAdminTestView() {
        _isAdminTestViewActive.value = !_isAdminTestViewActive.value
    }

    fun switchTestRole(role: UserRole) {
        val currentEmail = _uiState.value.authUser?.email
        val sanitized = com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(role, currentEmail)
        authRepository.updateUserRole(sanitized)
    }

    fun setSubscriptionTier(tier: SubscriptionTier, seats: Int = 5) {
        val currentRole = uiState.value.userRole
        if (!currentRole.canManageBilling) {
            return
        }
        billingRepository.setTier(tier)
        if (tier == SubscriptionTier.ENTERPRISE) {
            billingRepository.setEnterpriseSeats(seats)
            // The user that pays for group subscriptions becomes Account Admin (unless they are the Owner)
            if (currentRole != UserRole.OWNER) {
                authRepository.updateUserRole(UserRole.ACCOUNT_ADMIN)
            }
        }
    }

    fun launchBillingFlow(activity: Activity, tier: SubscriptionTier, seats: Int = 5): Boolean {
        val currentRole = uiState.value.userRole
        if (!currentRole.canManageBilling) {
            return false
        }
        val launched = billingRepository.launchBillingFlow(activity, tier, seats)
        if (!launched) {
            // In dev / test fallback, update role for enterprise simulation
            if (tier == SubscriptionTier.ENTERPRISE && currentRole != UserRole.OWNER) {
                authRepository.updateUserRole(UserRole.ACCOUNT_ADMIN)
            }
        }
        return launched
    }

    fun cancelSubscription() {
        if (!uiState.value.userRole.canManageBilling) return
        billingRepository.cancelSubscription()
    }

    fun markPaymentFailed() {
        if (!uiState.value.userRole.canManageBilling) return
        billingRepository.markPaymentFailed()
    }

    fun reactivateSubscription() {
        if (!uiState.value.userRole.canManageBilling) return
        billingRepository.reactivateSubscription()
    }

    fun saveCompanyOAuthConfig(config: CompanyOAuthConfig) {
        if (!uiState.value.userRole.canManageSettings) return
        billingRepository.saveCompanyOAuthConfig(config)
    }

    fun addTeamMember(email: String, name: String, role: UserRole): Result<TeamMember> {
        val currentRole = uiState.value.userRole
        if (!currentRole.canManageTeam) {
            return Result.failure(IllegalStateException("You do not have permission to manage team members."))
        }
        if (role == UserRole.OWNER) {
            return Result.failure(IllegalArgumentException("The Owner role cannot be assigned to team members."))
        }
        if (role == UserRole.ACCOUNT_ADMIN && !currentRole.canChangeAdminPermissions) {
            return Result.failure(IllegalStateException("Managers cannot assign the Account Admin role. Only Account Admins and the Owner can grant administrative permissions."))
        }
        val result = billingRepository.addTeamMember(email, name, role)
        if (result.isSuccess) {
            triggerRosterCloudSync()
        }
        return result
    }

    fun updateTeamMember(id: String, email: String, name: String, role: UserRole, isActive: Boolean): Result<TeamMember> {
        val currentRole = uiState.value.userRole
        if (!currentRole.canManageTeam) {
            return Result.failure(IllegalStateException("You do not have permission to manage team members."))
        }
        val existing = billingRepository.teamMembers.value.find { it.id == id }
            ?: return Result.failure(NoSuchElementException("Team member not found."))

        if (existing.role == UserRole.OWNER) {
            return Result.failure(IllegalStateException("The Owner account cannot be modified."))
        }
        if (existing.role == UserRole.ACCOUNT_ADMIN && !currentRole.canChangeAdminPermissions) {
            return Result.failure(IllegalStateException("Managers cannot edit Account Admins."))
        }
        if (role == UserRole.OWNER) {
            return Result.failure(IllegalArgumentException("The Owner role cannot be assigned."))
        }
        if (role == UserRole.ACCOUNT_ADMIN && !currentRole.canChangeAdminPermissions) {
            return Result.failure(IllegalStateException("Managers cannot promote members to Account Admin."))
        }
        val result = billingRepository.updateTeamMember(id, email, name, role, isActive)
        if (result.isSuccess) {
            triggerRosterCloudSync()
        }
        return result
    }

    fun removeTeamMember(id: String): Result<Boolean> {
        val currentRole = uiState.value.userRole
        if (!currentRole.canManageTeam) {
            return Result.failure(IllegalStateException("You do not have permission to manage team members."))
        }
        val existing = billingRepository.teamMembers.value.find { it.id == id }
            ?: return Result.failure(NoSuchElementException("Team member not found."))

        if (existing.role == UserRole.OWNER) {
            return Result.failure(IllegalStateException("The Owner account cannot be removed."))
        }
        if (existing.role == UserRole.ACCOUNT_ADMIN && !currentRole.canChangeAdminPermissions) {
            return Result.failure(IllegalStateException("Managers cannot remove Account Admins."))
        }
        val result = billingRepository.removeTeamMember(id)
        if (result.isSuccess) {
            triggerRosterCloudSync()
        }
        return result
    }

    private fun triggerRosterCloudSync() {
        val syncEngine = teamSyncEngine ?: return
        val config = billingRepository.companyOAuthConfig.value
        val members = billingRepository.teamMembers.value
        viewModelScope.launch {
            if (config.cloudDriveFolderUri.isNotBlank()) {
                syncEngine.saveTeamRosterToCloudDrive(config.cloudDriveFolderUri, members)
            }
            if (config.webhookUrl.isNotBlank()) {
                syncEngine.dispatchTeamRosterToWebhook(config, members)
            }
        }
    }

    fun purgeAllEquipmentForCleanTesting() {
        viewModelScope.launch {
            val all = equipmentDao.getAllEquipment()
            all.forEach { equipmentDao.deleteEquipment(it) }
        }
    }

    fun loadTestSandboxAssets() {
        viewModelScope.launch {
            val existing = equipmentDao.getAllEquipment()
            val existingTags = existing.map { it.tagId }.toSet()
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 3600 * 1000
            val sevenDays = 7L * 24 * 3600 * 1000
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date30 = dateFormat.format(Date(now + thirtyDays))
            val date7 = dateFormat.format(Date(now + sevenDays))

            val defaults = listOf(
                EquipmentItem(
                    tagId = "FE-001",
                    locationDescription = "Main Warehouse, East Pillar 4",
                    equipmentType = "10lb ABC Dry Chemical",
                    serialNumber = "AMX-89421",
                    installDate = "2023-04-12",
                    assignedChecklist = "Fire Extinguisher",
                    reviewFrequency = "Monthly",
                    assignedReviewDate = date30,
                    assignedReviewTime = "09:00 AM",
                    nextReviewTimestamp = now + thirtyDays
                ),
                EquipmentItem(
                    tagId = "FE-002",
                    locationDescription = "Server Room, Rack 2 Entry",
                    equipmentType = "5lb CO2 Extinguisher",
                    serialNumber = "CD-44120",
                    installDate = "2023-08-20",
                    assignedChecklist = "Fire Extinguisher",
                    reviewFrequency = "Monthly",
                    assignedReviewDate = date30,
                    assignedReviewTime = "10:00 AM",
                    nextReviewTimestamp = now + thirtyDays
                ),
                EquipmentItem(
                    tagId = "FE-003",
                    locationDescription = "Kitchen Cafeteria, East Exit",
                    equipmentType = "Class K Wet Chemical",
                    serialNumber = "K-99124",
                    installDate = "2024-01-15",
                    assignedChecklist = "Fire Extinguisher",
                    reviewFrequency = "Monthly",
                    assignedReviewDate = date30,
                    assignedReviewTime = "02:00 PM",
                    nextReviewTimestamp = now + thirtyDays
                ),
                EquipmentItem(
                    tagId = "AED-01",
                    locationDescription = "Main Lobby Reception Desk",
                    equipmentType = "AED Defibrillator",
                    serialNumber = "ZOLL-33291",
                    installDate = "2024-02-10",
                    assignedChecklist = "AED Defibrillator",
                    reviewFrequency = "Monthly",
                    assignedReviewDate = date30,
                    assignedReviewTime = "09:00 AM",
                    nextReviewTimestamp = now + thirtyDays
                ),
                EquipmentItem(
                    tagId = "EL-101",
                    locationDescription = "Stairwell B Emergency Exit",
                    equipmentType = "Emergency Light Unit",
                    serialNumber = "EL-77210",
                    installDate = "2024-03-01",
                    assignedChecklist = "Emergency Lighting",
                    reviewFrequency = "Monthly",
                    assignedReviewDate = date30,
                    assignedReviewTime = "11:00 AM",
                    nextReviewTimestamp = now + thirtyDays
                ),
                EquipmentItem(
                    tagId = "EW-01",
                    locationDescription = "Battery Storage & Chemical Bay",
                    equipmentType = "Emergency Eyewash Station",
                    serialNumber = "EW-44912",
                    installDate = "2024-04-18",
                    assignedChecklist = "Emergency Eye Wash",
                    reviewFrequency = "Weekly",
                    assignedReviewDate = date7,
                    assignedReviewTime = "08:30 AM",
                    nextReviewTimestamp = now + sevenDays
                )
            )
            defaults.filter { it.tagId !in existingTags }.forEach {
                equipmentDao.upsertEquipment(it)
            }
        }
    }

    class Factory(
        private val equipmentDao: EquipmentDao,
        private val inspectionDao: InspectionDao,
        private val entitlementManager: EntitlementManager,
        private val billingRepository: BillingRepository,
        private val authRepository: AuthRepository,
        private val teamSyncEngine: TeamSyncEngine? = null,
        private val reminderManager: InspectionReminderManager? = null,
        private val networkMonitor: com.safetylog.app.util.NetworkMonitor? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                equipmentDao,
                inspectionDao,
                entitlementManager,
                billingRepository,
                authRepository,
                teamSyncEngine,
                reminderManager,
                networkMonitor
            ) as T
        }
    }
}

