package com.safetylog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.safetylog.app.ui.theme.AppThemeMode
import com.safetylog.app.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SafetyLogApp
        val notificationEquipmentId = intent?.getLongExtra("equipmentId", -1L) ?: -1L
        val notificationAction = intent?.action

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            SafetyLogTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafetyLogNavGraph(
                        app = app,
                        isDarkTheme = isDarkTheme,
                        themeMode = themeMode,
                        initialEquipmentId = notificationEquipmentId,
                        initialAction = notificationAction,
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
    onToggleTheme: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(initialEquipmentId, initialAction) {
        if (initialEquipmentId > 0) {
            if (initialAction == "ACTION_VIEW_HISTORY") {
                navController.navigate("history/$initialEquipmentId")
            } else {
                navController.navigate("inspect/$initialEquipmentId")
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        // Dashboard
        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(
                    equipmentDao = app.database.equipmentDao(),
                    inspectionDao = app.database.inspectionDao(),
                    entitlementManager = app.entitlementManager,
                    billingRepository = app.billingRepository,
                    authRepository = app.authRepository,
                    teamSyncEngine = app.teamSyncEngine,
                    reminderManager = app.reminderManager
                )
            )

            DashboardScreen(
                viewModel = dashboardViewModel,
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
                    pdfGenerator = app.pdfGenerator
                )
            )
            ReportsScreen(
                viewModel = reportViewModel,
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
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(
                    equipmentDao = app.database.equipmentDao(),
                    inspectionDao = app.database.inspectionDao(),
                    entitlementManager = app.entitlementManager,
                    billingRepository = app.billingRepository,
                    authRepository = app.authRepository,
                    teamSyncEngine = app.teamSyncEngine,
                    reminderManager = app.reminderManager
                )
            )
            SettingsScreen(
                dashboardViewModel = dashboardViewModel,
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
