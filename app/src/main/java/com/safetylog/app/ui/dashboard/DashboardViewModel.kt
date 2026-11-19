package com.safetylog.app.ui.dashboard

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.ApiResult
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
import com.safetylog.app.util.ServerAuthorization
import com.safetylog.app.util.TestControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class FilterStatus {
    ALL, PASSED, DEFICIENT, DUE
}

@Immutable
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
    val authoritativeRole: UserRole = UserRole.USER,
    val isAdminTestViewActive: Boolean = false,
    val authUser: com.safetylog.app.data.auth.AuthUser? = null,
    val inspectionsThisMonth: Int = 0,
    val freeLimit: Int = 5,
    val isOnline: Boolean = true,
    val isLoading: Boolean = true
) {
    val canPreviewLowerRoles: Boolean = authoritativeRole != UserRole.USER

    // Counts and the filtered list are computed once per state instance instead of on every
    // recomposition/read. Being body initializers (not custom getters) keeps `copy()` correct.
    val totalCount: Int = equipmentList.size
    val passedCount: Int = equipmentList.count { it.lastInspectionStatus.equals("PASSED", ignoreCase = true) }
    val deficientCount: Int = equipmentList.count { it.lastInspectionStatus.equals("DEFICIENT", ignoreCase = true) }
    val dueCount: Int = equipmentList.count { item ->
        item.lastInspectionDate == null || (item.equipment.nextReviewTimestamp in 1..System.currentTimeMillis())
    }

    val isSubscriptionDelinquentOrCancelled: Boolean =
        subscriptionTier != SubscriptionTier.FREE &&
                subscriptionTier != SubscriptionTier.AD_FREE &&
                !paymentStatus.isGoodStanding

    val filteredList: List<EquipmentWithLatestInspection> = run {
        val now = System.currentTimeMillis()
        equipmentList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.equipment.tagId.contains(searchQuery, ignoreCase = true) ||
                item.equipment.locationDescription.contains(searchQuery, ignoreCase = true) ||
                item.equipment.equipmentType.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (activeFilter) {
                FilterStatus.ALL -> true
                FilterStatus.PASSED -> item.lastInspectionStatus.equals("PASSED", ignoreCase = true)
                FilterStatus.DEFICIENT -> item.lastInspectionStatus.equals("DEFICIENT", ignoreCase = true)
                FilterStatus.DUE -> item.lastInspectionDate == null || (item.equipment.nextReviewTimestamp in 1..now)
            }
            matchesSearch && matchesFilter
        }
    }

    val monthlyProgressFraction: Float =
        if (effectiveTier != SubscriptionTier.FREE) {
            1f
        } else {
            (inspectionsThisMonth.toFloat() / freeLimit.toFloat()).coerceIn(0f, 1f)
        }
}

private data class EquipmentCoreState(
    val summaries: List<EquipmentWithLatestInspection>,
    val query: String,
    val filter: FilterStatus
)

private data class SubscriptionState(
    val tier: SubscriptionTier,
    val effective: SubscriptionTier,
    val payStatus: PaymentStatus
)

private data class TeamState(
    val seats: Int,
    val companyConfig: CompanyOAuthConfig,
    val countThisMonth: Int,
    val members: List<TeamMember>
)

private data class EntitlementState(
    val subscription: SubscriptionState,
    val team: TeamState
)

private data class AuthState(
    val authUser: com.safetylog.app.data.auth.AuthUser?,
    val isTestView: Boolean,
    val online: Boolean,
    val assigned: UserRole,
    val roleOverride: UserRole?
)

class DashboardViewModel(
    private val equipmentDao: EquipmentDao,
    private val inspectionDao: InspectionDao,
    private val entitlementManager: EntitlementManager,
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository,
    private val authorizationRepository: com.safetylog.app.data.auth.AuthorizationRepository,
    private val teamSyncEngine: TeamSyncEngine? = null,
    private val reminderManager: InspectionReminderManager? = null,
    private val networkMonitor: com.safetylog.app.util.NetworkMonitor? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(FilterStatus.ALL)
    private val _isAdminTestViewActive = MutableStateFlow(false)
    private val _userRoleOverride = MutableStateFlow<UserRole?>(null)

    private val equipmentCoreFlow: Flow<EquipmentCoreState> = combine(
        equipmentDao.getEquipmentSummaries(),
        _searchQuery,
        _activeFilter
    ) { summaries, query, filter ->
        EquipmentCoreState(summaries, query, filter)
    }

    private val entitlementFlow: Flow<EntitlementState> = combine(
        combine(
            billingRepository.currentTier,
            billingRepository.effectiveTier,
            billingRepository.paymentStatus
        ) { tier, effective, payStatus ->
            SubscriptionState(tier, effective, payStatus)
        },
        combine(
            billingRepository.enterpriseSeats,
            billingRepository.companyOAuthConfig,
            entitlementManager.countInspectionsThisMonthFlow(),
            billingRepository.teamMembers
        ) { seats, companyConfig, countThisMonth, members ->
            TeamState(seats, companyConfig, countThisMonth, members)
        }
    ) { subscription, team ->
        EntitlementState(subscription, team)
    }

    private val authStateFlow: Flow<AuthState> = combine(
        authRepository.currentUser,
        _isAdminTestViewActive,
        networkMonitor?.isOnline ?: MutableStateFlow(true),
        authRepository.assignedRole,
        _userRoleOverride
    ) { authUser, isTestView, online, assigned, roleOverride ->
        AuthState(authUser, isTestView, online, assigned, roleOverride)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        equipmentCoreFlow,
        entitlementFlow,
        authStateFlow,
        authorizationRepository.snapshot
    ) { core, entitlement, auth, signed ->
        // When configured, the server-signed snapshot is the ONLY source of truth.
        // Local preferences are ignored entirely rather than merged.
        val authoritative = ServerAuthorization.isConfigured && signed != null
        val tier = if (authoritative) signed.tier else entitlement.subscription.tier
        val effective = if (authoritative) signed.effectiveTier else entitlement.subscription.effective
        val payStatus = if (authoritative) signed.paymentStatus else entitlement.subscription.payStatus
        val members = if (authoritative) signed.team else entitlement.team.members
        val seats = if (authoritative) signed.seats else entitlement.team.seats
        val authUser = auth.authUser

        // 1. Determine baseline natural role (ignored when a signed decision exists)
        val matchingMember = authUser?.email?.trim()?.let { userEmail ->
            members.find { it.email.equals(userEmail, ignoreCase = true) && it.isActive }
        }
        val naturalRole = when {
            // In the public release OWNER is granted only by verified Google OAuth
            // (see AuthRepository.signInWithGoogleLive). A self-declared email from the
            // inspector form must never satisfy the allowlist here.
            TestControls.isEnabled && com.safetylog.app.data.auth.AuthorizationConfig.isAuthorizedOwner(authUser?.email) -> UserRole.OWNER
            matchingMember != null -> matchingMember.role
            authUser?.role != null -> authUser.role
            else -> auth.assigned
        }

        val authoritativeRole = if (!TestControls.isEnabled) {
            if (authoritative) {
                signed.role
            } else if (authUser == null || ServerAuthorization.isConfigured) {
                // Never let stale legacy persisted roles elevate an unauthenticated/unverified release user.
                UserRole.USER
            } else {
                com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(naturalRole, authUser.email)
            }
        } else {
            if (authoritative) {
                signed.role
            } else {
                com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(naturalRole, authUser?.email)
            }
        }

        val role = if (!TestControls.isEnabled) {
            auth.roleOverride?.takeIf { it.ordinal >= authoritativeRole.ordinal } ?: authoritativeRole
        } else {
            if (authoritative) {
                signed.role
            } else {
                com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(auth.roleOverride ?: naturalRole, authUser?.email)
            }
        }

        DashboardUiState(
            equipmentList = core.summaries,
            searchQuery = core.query,
            activeFilter = core.filter,
            isProUser = effective != SubscriptionTier.FREE,
            subscriptionTier = tier,
            effectiveTier = effective,
            paymentStatus = payStatus,
            isAdFree = effective.isAdFree,
            canShareBetweenAccounts = effective.allowsSharing,
            canUseCompanyOAuth = effective.isEnterprise && payStatus.isGoodStanding,
            companyOAuthConfig = entitlement.team.companyConfig,
            enterpriseSeats = seats,
            teamMembers = members,
            userRole = role,
            authoritativeRole = authoritativeRole,
            isAdminTestViewActive = auth.isTestView,
            authUser = authUser,
            inspectionsThisMonth = entitlement.team.countThisMonth,
            freeLimit = entitlementManager.FREE_MONTHLY_LIMIT,
            isOnline = auth.online,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        // Re-check authority with the server when the dashboard is created. Background
        // sign-in already exchanges the ID token; this keeps a resumed session fresh.
        viewModelScope.launch {
            if (ServerAuthorization.isConfigured && networkMonitor?.isOnline?.value != false) {
                authorizationRepository.refresh()
            }
        }
        // Release-mode role previews must never survive sign-out / unauthenticated state.
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user == null) {
                    _userRoleOverride.value = null
                }
            }
        }
    }

    /**
     * Fail-closed gate for privileged mutations: with server authority enabled, a valid
     * signed snapshot AND connectivity are required. Offline inspections remain allowed.
     */
    private fun canMutate(): Boolean {
        val signed = authorizationRepository.snapshot.value
        return com.safetylog.app.data.auth.AuthorizationPolicy.privilegedActionAllowed(
            serverConfigured = ServerAuthorization.isConfigured,
            hasSignedSnapshot = signed != null,
            snapshotExpired = signed?.isExpired ?: true,
            online = networkMonitor?.isOnline?.value != false,
            localFallbackAllowed = authorizationRepository.allowsLocalFallback
        )
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: FilterStatus) {
        _activeFilter.value = filter
    }

    fun signOut() {
        _userRoleOverride.value = null
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun deleteEquipment(equipment: EquipmentItem) {
        if (!canMutate() || !uiState.value.userRole.canDeleteAssets) return
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
        if (!TestControls.isEnabled) {
            val currentAuthoritative = uiState.value.authoritativeRole
            // Release: allow downward-only preview (higher ordinal = lower privilege). Never
            // mutate the repository and never escalate above the authoritative role.
            if (role.ordinal >= currentAuthoritative.ordinal) {
                _userRoleOverride.value = if (role == currentAuthoritative) null else role
            }
            return
        }

        val currentEmail = authRepository.currentUser.value?.email ?: uiState.value.authUser?.email
        val sanitized = com.safetylog.app.data.auth.AuthorizationConfig.sanitizeRole(role, currentEmail)
        _userRoleOverride.value = sanitized
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.updateUserRole(sanitized)
        }
    }

    fun resetRoleToDefault() {
        _userRoleOverride.value = null
    }

    fun setSubscriptionTier(tier: SubscriptionTier, seats: Int = 5) {
        if (!canMutate()) return
        val currentRole = uiState.value.userRole
        val currentTier = uiState.value.subscriptionTier
        // In an active Enterprise organization, only Account Admins and the Owner can alter company billing/seats
        if (currentTier == SubscriptionTier.ENTERPRISE && !currentRole.canManageBilling) {
            return
        }
        billingRepository.setTier(tier)
        // Simulation-only: picking Enterprise used to hand out ACCOUNT_ADMIN even when
        // BillingRepository rejected the paid tier (which it always does in release).
        // Real Enterprise entitlements come from Play via handleValidPurchase(), so only
        // test builds may promote a role from a direct tier selection.
        if (TestControls.isEnabled && tier == SubscriptionTier.ENTERPRISE && currentRole != UserRole.OWNER) {
            billingRepository.setEnterpriseSeats(seats)
            _userRoleOverride.value = UserRole.ACCOUNT_ADMIN
            viewModelScope.launch(Dispatchers.IO) {
                authRepository.updateUserRole(UserRole.ACCOUNT_ADMIN)
            }
        }
    }

    fun launchBillingFlow(activity: Activity, tier: SubscriptionTier, seats: Int = 5): Boolean {
        if (!canMutate()) return false
        val currentRole = uiState.value.userRole
        val currentTier = uiState.value.subscriptionTier
        if (currentTier == SubscriptionTier.ENTERPRISE && !currentRole.canManageBilling) {
            return false
        }
        val launched = billingRepository.launchBillingFlow(activity, tier, seats)
        if (!launched && TestControls.isEnabled) {
            // In dev / test fallback, update role for enterprise simulation
            if (tier == SubscriptionTier.ENTERPRISE && currentRole != UserRole.OWNER) {
                _userRoleOverride.value = UserRole.ACCOUNT_ADMIN
                viewModelScope.launch(Dispatchers.IO) {
                    authRepository.updateUserRole(UserRole.ACCOUNT_ADMIN)
                }
            }
        }
        return launched
    }

    fun cancelSubscription() {
        if (!canMutate()) return
        val currentRole = uiState.value.userRole
        val currentTier = uiState.value.subscriptionTier
        if (currentTier == SubscriptionTier.ENTERPRISE && !currentRole.canManageBilling) return
        billingRepository.cancelSubscription()
    }

    fun markPaymentFailed() {
        if (!canMutate()) return
        val currentRole = uiState.value.userRole
        val currentTier = uiState.value.subscriptionTier
        if (currentTier == SubscriptionTier.ENTERPRISE && !currentRole.canManageBilling) return
        billingRepository.markPaymentFailed()
    }

    fun reactivateSubscription() {
        if (!canMutate()) return
        val currentRole = uiState.value.userRole
        val currentTier = uiState.value.subscriptionTier
        if (currentTier == SubscriptionTier.ENTERPRISE && !currentRole.canManageBilling) return
        billingRepository.reactivateSubscription()
    }

    fun saveCompanyOAuthConfig(config: CompanyOAuthConfig) {
        if (!canMutate()) return
        if (!uiState.value.userRole.canManageSettings) return
        // Webhook secrets are Keystore-encrypted before persistence; keep that work off Main.
        viewModelScope.launch(Dispatchers.IO) {
            billingRepository.saveCompanyOAuthConfig(config)
        }
    }

    suspend fun addTeamMember(email: String, name: String, role: UserRole): Result<TeamMember> {
        if (!canMutate()) return Result.failure(IllegalStateException("Authorization check required"))
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
        val previous = billingRepository.teamMembers.value
        val result = billingRepository.addTeamMember(email, name, role)
        if (result.isFailure) return result
        val serverError = syncRosterToServer()
        if (serverError != null) {
            withContext(Dispatchers.IO) {
                billingRepository.saveTeamMembersToPrefs(previous)
            }
            return Result.failure(serverError)
        }
        triggerRosterCloudSync()
        return result
    }

    suspend fun updateTeamMember(id: String, email: String, name: String, role: UserRole, isActive: Boolean): Result<TeamMember> {
        if (!canMutate()) return Result.failure(IllegalStateException("Authorization check required"))
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
        val previous = billingRepository.teamMembers.value
        val result = billingRepository.updateTeamMember(id, email, name, role, isActive)
        if (result.isFailure) return result
        val serverError = syncRosterToServer()
        if (serverError != null) {
            withContext(Dispatchers.IO) {
                billingRepository.saveTeamMembersToPrefs(previous)
            }
            return Result.failure(serverError)
        }
        triggerRosterCloudSync()
        return result
    }

    suspend fun removeTeamMember(id: String): Result<Boolean> {
        if (!canMutate()) return Result.failure(IllegalStateException("Authorization check required"))
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
        val previous = billingRepository.teamMembers.value
        val result = billingRepository.removeTeamMember(id)
        if (result.isFailure) return result
        val serverError = syncRosterToServer()
        if (serverError != null) {
            withContext(Dispatchers.IO) {
                billingRepository.saveTeamMembersToPrefs(previous)
            }
            return Result.failure(serverError)
        }
        triggerRosterCloudSync()
        return result
    }

    /** Push the (already mutated) roster to the server; returns an error to roll back on. */
    private suspend fun syncRosterToServer(): Throwable? {
        if (!ServerAuthorization.isConfigured) return null
        return when (val result = authorizationRepository.updateTeam(billingRepository.teamMembers.value)) {
            is ApiResult.Success -> null
            is ApiResult.HttpError -> IllegalStateException("Server rejected the roster change: ${result.message}")
            is ApiResult.NetworkError -> IllegalStateException("Roster changes require a connection to the authorization server.")
            is ApiResult.InvalidResponse -> IllegalStateException("Invalid response from the authorization server.")
        }
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
        private val authorizationRepository: com.safetylog.app.data.auth.AuthorizationRepository,
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
                authorizationRepository,
                teamSyncEngine,
                reminderManager,
                networkMonitor
            ) as T
        }
    }
}

