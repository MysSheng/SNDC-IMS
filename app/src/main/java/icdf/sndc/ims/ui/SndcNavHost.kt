package icdf.sndc.ims.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import icdf.sndc.ims.data.AppContainer
import icdf.sndc.ims.ui.agent.AgentChatViewModel
import icdf.sndc.ims.ui.assets.AssetDetailScreen
import icdf.sndc.ims.ui.assets.AssetDetailViewModel
import icdf.sndc.ims.ui.assets.AssetFormScreen
import icdf.sndc.ims.ui.assets.AssetFormViewModel
import icdf.sndc.ims.ui.assets.AssetImportViewModel
import icdf.sndc.ims.ui.assets.AssetListViewModel
import icdf.sndc.ims.ui.assets.SmartOfficeScreen
import icdf.sndc.ims.ui.auth.LoginScreen
import icdf.sndc.ims.ui.auth.LoginViewModel
import icdf.sndc.ims.ui.auth.RegisterScreen
import icdf.sndc.ims.ui.auth.RegisterViewModel
import icdf.sndc.ims.ui.home.HomeScreen
import icdf.sndc.ims.ui.home.PlaceholderScreen
import icdf.sndc.ims.ui.common.tr

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SMART_OFFICE = "smart-office"
    const val NAVIGATION = "navigation"
    const val ASSET_DETAIL = "asset/{id}"
    const val ASSET_FORM = "asset-form?id={id}&year={year}&quarter={quarter}"
    const val GUIDE = "guide?location={location}"

    fun guide(location: String?) = if (location == null) "guide" else "guide?location=${android.net.Uri.encode(location)}"

    fun assetDetail(id: String) = "asset/$id"
    fun assetForm(id: String? = null, year: Int? = null, quarter: Int? = null): String {
        val params = buildList {
            id?.let { add("id=$it") }
            if (year != null && quarter != null) { add("year=$year"); add("quarter=$quarter") }
        }
        return if (params.isEmpty()) "asset-form" else "asset-form?" + params.joinToString("&")
    }
}

@Composable
fun SndcNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val session by container.session.session.collectAsStateWithLifecycle()
    val loggedIn = session != null

    // 依登入狀態切換：登入／註冊成功 → 首頁；登出或 Token 失效 → 登入頁
    LaunchedEffect(loggedIn) {
        val target = if (loggedIn) Routes.HOME else Routes.LOGIN
        val current = nav.currentDestination?.route
        val onAuthPage = current == Routes.LOGIN || current == Routes.REGISTER
        if ((loggedIn && (current == null || onAuthPage)) || (!loggedIn && current != null && !onAuthPage)) {
            nav.navigate(target) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            val vm = viewModel { LoginViewModel(container.authRepository) }
            LoginScreen(vm, onGoRegister = { nav.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            val vm = viewModel { RegisterViewModel(container.authRepository) }
            RegisterScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                username = session?.user?.username.orEmpty(),
                onOpenSmartOffice = { nav.navigate(Routes.SMART_OFFICE) },
                onOpenNavigation = { nav.navigate(Routes.NAVIGATION) },
                onLogout = { container.authRepository.logout() },
            )
        }
        composable(Routes.SMART_OFFICE) {
            val vm = viewModel { AssetListViewModel(container.assetRepository) }
            val agentVm = viewModel { AgentChatViewModel(container.agentRepository) }
            val importVm = viewModel { AssetImportViewModel(container.assetRepository) }
            SmartOfficeScreen(
                viewModel = vm,
                agentViewModel = agentVm,
                importViewModel = importVm,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.assetDetail(id)) },
                // 新增時帶入清單目前選的盤點期別（若已選到季度）
                onCreate = { nav.navigate(Routes.assetForm(year = vm.state.value.year, quarter = vm.state.value.quarter)) },
                onEdit = { id -> nav.navigate(Routes.assetForm(id)) },
            )
        }
        composable(
            Routes.ASSET_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm = viewModel { AssetDetailViewModel(container.assetRepository, id) }
            AssetDetailScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.assetForm(id)) },
                onDeleted = { nav.popBackStack() },
                onGuide = { location -> nav.navigate(Routes.guide(location)) },
            )
        }
        composable(
            Routes.GUIDE,
            arguments = listOf(navArgument("location") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val location = entry.arguments?.getString("location")
            PlaceholderScreen(
                title = tr("室內導引", "Indoor guidance"),
                message = tr("室內導引功能尚未開放。\n\n未來將可從你目前所在的房間或地點，導引你前往", "Indoor guidance is not available yet.\n\nIt will guide you from where you are to ") +
                    (if (location.isNullOrBlank()) tr("資產的存放位置。", "the asset's location.") else "「$location」。"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.ASSET_FORM,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("year") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("quarter") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id")
            val year = entry.arguments?.getString("year")?.toIntOrNull()
            val quarter = entry.arguments?.getString("quarter")?.toIntOrNull()
            val vm = viewModel { AssetFormViewModel(container.assetRepository, id, year, quarter) }
            AssetFormScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.NAVIGATION) {
            PlaceholderScreen(
                title = tr("智慧導航系統", "Smart Navigation"),
                message = tr("功能尚未開放。", "Not available yet."),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
