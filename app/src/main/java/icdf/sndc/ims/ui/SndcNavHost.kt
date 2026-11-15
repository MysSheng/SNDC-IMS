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
import icdf.sndc.ims.ui.assets.AssetDetailScreen
import icdf.sndc.ims.ui.assets.AssetDetailViewModel
import icdf.sndc.ims.ui.assets.AssetFormScreen
import icdf.sndc.ims.ui.assets.AssetFormViewModel
import icdf.sndc.ims.ui.assets.AssetListViewModel
import icdf.sndc.ims.ui.assets.SmartOfficeScreen
import icdf.sndc.ims.ui.auth.LoginScreen
import icdf.sndc.ims.ui.auth.LoginViewModel
import icdf.sndc.ims.ui.auth.RegisterScreen
import icdf.sndc.ims.ui.auth.RegisterViewModel
import icdf.sndc.ims.ui.home.HomeScreen
import icdf.sndc.ims.ui.home.PlaceholderScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SMART_OFFICE = "smart-office"
    const val NAVIGATION = "navigation"
    const val ASSET_DETAIL = "asset/{id}"
    const val ASSET_FORM = "asset-form?id={id}"

    fun assetDetail(id: String) = "asset/$id"
    fun assetForm(id: String? = null) = if (id == null) "asset-form" else "asset-form?id=$id"
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
            SmartOfficeScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.assetDetail(id)) },
                onCreate = { nav.navigate(Routes.assetForm()) },
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
            )
        }
        composable(
            Routes.ASSET_FORM,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val id = entry.arguments?.getString("id")
            val vm = viewModel { AssetFormViewModel(container.assetRepository, id) }
            AssetFormScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.NAVIGATION) {
            PlaceholderScreen(
                title = "智慧導航系統",
                message = "功能尚未開放。",
                onBack = { nav.popBackStack() },
            )
        }
    }
}
