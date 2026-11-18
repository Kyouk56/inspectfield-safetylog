package com.safetylog.app

import android.content.Intent
import android.os.Bundle
import com.safetylog.app.data.reminder.InspectionReminderManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safetylog.app.ui.auth.AuthScreen
import com.safetylog.app.ui.auth.AuthViewModel
import com.safetylog.app.ui.dashboard.DashboardScreen
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.equipment.EquipmentFormScreen
import com.safetylog.app.ui.equipment.EquipmentFormViewModel
import com.safetylog.app.ui.equipment.EquipmentHistoryScreen
import com.safetylog.app.ui.inspection.InspectionFormScreen
import com.safetylog.app.ui.inspection.InspectionViewModel
import com.safetylog.app.ui.profile.ProfileScreen
import com.safetylog.app.ui.profile.ProfileViewModel
import com.safetylog.app.ui.report.ReportViewModel
import com.safetylog.app.ui.report.ReportsScreen
import com.safetylog.app.ui.settings.SettingsScreen
import com.safetylog.app.ui.theme.SafetyLogTheme
import kotlinx.coroutines.flow.MutableStateFlow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.ui.theme.AppThemeMode
import com.safetylog.app.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private val pendingNotificationData = MutableStateFlow<Pair<Long, String?>?>(null)

    /**
     * MainActivity is exported (launcher). Only intents carrying the marker that our own
     * notification PendingIntent sets may drive in-app navigation, so another installed
     * app cannot force a jump to an arbitrary equipment record.
     */
    private fun Intent.isTrustedNotificationIntent(): Boolean =
        getBooleanExtra(InspectionReminderManager.EXTRA_FROM_NOTIFICATION, false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.isTrustedNotificationIntent()) return
        val id = intent.getLongExtra("equipmentId", -1L)
        val action = intent.action
        if (id > 0) {
            pendingNotificationData.value = Pair(id, action)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SafetyLogApp
        val trusted = intent?.isTrustedNotificationIntent() == true
        val notificationEquipmentId = if (trusted) intent?.getLongExtra("equipmentId", -1L) ?: -1L else -1L
        val notificationAction = if (trusted) intent?.action else null
        if (notificationEquipmentId > 0 && pendingNotificationData.value == null) {
            pendingNotificationData.value = Pair(notificationEquipmentId, notificationAction)
        }

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDarkTheme }
                )
                onDispose {}
            }

            val pendingNotification by pendingNotificationData.collectAsStateWithLifecycle()
            val effectiveEquipmentId = pendingNotification?.first ?: -1L
            val effectiveAction = pendingNotification?.second

            SafetyLogTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafetyLogNavGraph(
                        app = app,
                        isDarkTheme = isDarkTheme,
                        themeMode = themeMode,
                        initialEquipmentId = effectiveEquipmentId,
                        initialAction = effectiveAction,
                        onNotificationConsumed = {
                            pendingNotificationData.value = null
                            intent?.removeExtra("equipmentId")
                        },
                        onToggleTheme = { ThemeManager.toggleTheme() },
                        onThemeModeChange = { ThemeManager.setThemeMode(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun SafetyLogNavGraph(
    app: SafetyLogApp,
    isDarkTheme: Boolean,
    themeMode: AppThemeMode,
    initialEquipmentId: Long = -1L,
    initialAction: String? = null,
    onNotificationConsumed: () -> Unit = {},
    onToggleTheme: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()

    // Shared DashboardViewModel so DashboardScreen and SettingsScreen share a single
    // reactive state source (including Owner Sandbox / Test View and Role overrides).
    val sharedDashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            equipmentDao = app.database.equipmentDao(),
            inspectionDao = app.database.inspectionDao(),
            entitlementManager = app.entitlementManager,
            billingRepository = app.billingRepository,
            authRepository = app.authRepository,
            authorizationRepository = app.authorizationRepository,
            teamSyncEngine = app.teamSyncEngine,
            reminderManager = app.reminderManager,
            networkMonitor = app.networkMonitor
        )
    )

    androidx.compose.runtime.LaunchedEffect(initialEquipmentId, initialAction) {
        if (initialEquipmentId > 0) {
            if (initialAction == "ACTION_VIEW_HISTORY") {
                navController.navigate("history/$initialEquipmentId")
            } else {
                navController.navigate("inspect/$initialEquipmentId")
            }
            onNotificationConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        // Dashboard
        composable("dashboard") {
            DashboardScreen(
                viewModel = sharedDashboardViewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onNavigateToAddEquipment = { navController.navigate("add_equipment") },
                onNavigateToEditEquipment = { id -> navController.navigate("edit_equipment/$id") },
                onNavigateToInspect = { id -> navController.navigate("inspect/$id") },
                onNavigateToHistory = { id -> navController.navigate("history/$id") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToAuth = { navController.navigate("auth") }
            )
        }

        // Add Equipment
        composable("add_equipment") {
            val formViewModel: EquipmentFormViewModel = viewModel(
                factory = EquipmentFormViewModel.Factory(
                    equipmentId = null,
                    equipmentDao = app.database.equipmentDao(),
                    reminderManager = app.reminderManager
                )
            )
            EquipmentFormScreen(
                viewModel = formViewModel,
                billingRepository = app.billingRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Edit Equipment
        composable(
            route = "edit_equipment/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val formViewModel: EquipmentFormViewModel = viewModel(
                key = "edit_$id",
                factory = EquipmentFormViewModel.Factory(
                    equipmentId = id,
                    equipmentDao = app.database.equipmentDao(),
                    reminderManager = app.reminderManager
                )
            )
            EquipmentFormScreen(
                viewModel = formViewModel,
                billingRepository = app.billingRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Inspection Form
        composable(
            route = "inspect/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val inspectionViewModel: InspectionViewModel = viewModel(
                key = "inspect_$id",
                factory = InspectionViewModel.Factory(
                    equipmentId = id,
                    equipmentDao = app.database.equipmentDao(),
                    inspectionDao = app.database.inspectionDao(),
                    entitlementManager = app.entitlementManager,
                    billingRepository = app.billingRepository,
                    teamSyncEngine = app.teamSyncEngine,
                    reminderManager = app.reminderManager
                )
            )
            InspectionFormScreen(
                viewModel = inspectionViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Equipment History
        composable(
            route = "history/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            EquipmentHistoryScreen(
                equipmentId = id,
                equipmentDao = app.database.equipmentDao(),
                inspectionDao = app.database.inspectionDao(),
                profileDao = app.database.profileDao(),
                billingRepository = app.billingRepository,
                authRepository = app.authRepository,
                pdfGenerator = app.pdfGenerator,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInspect = { inspectId ->
                    navController.navigate("inspect/$inspectId")
                }
            )
        }

        // Reports
        composable("reports") {
            val reportViewModel: ReportViewModel = viewModel(
                factory = ReportViewModel.Factory(
                    inspectionDao = app.database.inspectionDao(),
                    equipmentDao = app.database.equipmentDao(),
                    profileDao = app.database.profileDao(),
                    pdfGenerator = app.pdfGenerator,
                    authRepository = app.authRepository
                )
            )
            ReportsScreen(
                viewModel = reportViewModel,
                billingRepository = app.billingRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate("profile") }
            )
        }

        // Inspector Profile
        composable("profile") {
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModel.Factory(
                    profileDao = app.database.profileDao(),
                    authRepository = app.authRepository
                )
            )
            ProfileScreen(
                viewModel = profileViewModel,
                billingRepository = app.billingRepository,
                onNavigateToAuth = { navController.navigate("auth") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Security & OAuth Vault
        composable("auth") {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(
                    authRepository = app.authRepository,
                    profileDao = app.database.profileDao(),
                    billingRepository = app.billingRepository
                )
            )
            AuthScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings & System Control
        composable("settings") {
            SettingsScreen(
                dashboardViewModel = sharedDashboardViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                teamSyncEngine = app.teamSyncEngine,
                secureStorage = app.secureTokenStorage,
                onNavigateToAuth = { navController.navigate("auth") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
