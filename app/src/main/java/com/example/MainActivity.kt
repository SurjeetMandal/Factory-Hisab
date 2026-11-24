package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.CompanyProfileEntity
import com.example.data.local.DatabaseProvider
import com.example.data.local.FactoryHisabRepository
import com.example.ui.components.FloatingAiPill
import com.example.ui.components.VoiceAssistantSheet
import com.example.ui.components.rememberAppHaptics
import com.example.ui.navigation.Routes
import com.example.voice.OfflineSpeechManager
import com.example.voice.VoiceAssistantViewModel
import com.example.voice.VoiceAssistantViewModelFactory
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DashboardViewModel
import com.example.ui.screens.DashboardViewModelFactory
import com.example.ui.screens.HisabSlipScreen
import com.example.ui.screens.HisabSlipViewModel
import com.example.ui.screens.HisabSlipViewModelFactory
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.PartiesViewModel
import com.example.ui.screens.PartiesViewModelFactory
import com.example.ui.screens.PartyDetailScreen
import com.example.ui.screens.PartyDetailViewModel
import com.example.ui.screens.PartyDetailViewModelFactory
import com.example.ui.screens.PaymentsScreen
import com.example.ui.screens.PaymentsViewModel
import com.example.ui.screens.PaymentsViewModelFactory
import com.example.ui.screens.RegisterCalculationScreen
import com.example.ui.screens.RegisterViewModel
import com.example.ui.screens.RegisterViewModelFactory
import com.example.ui.screens.SharedSlipsScreen
import com.example.ui.screens.SharedSlipsViewModel
import com.example.ui.screens.SharedSlipsViewModelFactory
import com.example.ui.theme.FactoryHisabTheme
import com.example.ui.theme.LocalLedgerColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            FactoryHisabTheme {
                FactoryHisabRoot()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private data class PillNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Loaded(val profile: CompanyProfileEntity?) : ProfileLoadState
}

@Composable
fun FactoryHisabRoot() {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val profileFlow = remember(repository) { repository.observeCompanyProfile() }
    val scope = rememberCoroutineScope()

    val profileState by remember(profileFlow) {
        kotlinx.coroutines.flow.flow {
            profileFlow.collect { emit(ProfileLoadState.Loaded(it)) }
        }
    }.collectAsStateWithLifecycle(initialValue = ProfileLoadState.Loading)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val rootStage = when (val state = profileState) {
            ProfileLoadState.Loading -> 0
            is ProfileLoadState.Loaded -> {
                val p = state.profile
                if (p == null || !p.onboardingCompleted) 1 else 2
            }
        }
        Crossfade(
            targetState = rootStage,
            animationSpec = tween(durationMillis = 260),
            label = "root_stage_crossfade"
        ) { stage ->
            when (stage) {
                0 -> Box(modifier = Modifier.fillMaxSize())
                1 -> OnboardingScreen(
                    onComplete = { companyName, ownerName, firstParty ->
                        scope.launch {
                            if (firstParty != null) {
                                repository.saveParty(firstParty)
                            }
                            repository.saveCompanyProfile(
                                companyName = companyName,
                                ownerName = ownerName,
                                onboardingCompleted = true
                            )
                        }
                    }
                )
                else -> FactoryHisabMainShell(repository = repository)
            }
        }
    }
}

@Composable
private fun FactoryHisabMainShell(repository: FactoryHisabRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var requestedRegisterPartyId by remember { mutableStateOf<Long?>(null) }

    val navItems = remember {
        listOf(
            PillNavItem(
                route = Routes.DASHBOARD,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Rounded.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_dashboard"
            ),
            PillNavItem(
                route = Routes.REGISTER,
                labelRes = R.string.nav_register,
                selectedIcon = Icons.AutoMirrored.Rounded.MenuBook,
                unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
                testTag = "nav_register"
            ),
            PillNavItem(
                route = Routes.PARTIES,
                labelRes = R.string.nav_parties,
                selectedIcon = Icons.Rounded.Storefront,
                unselectedIcon = Icons.Outlined.Storefront,
                testTag = "nav_parties"
            ),
            PillNavItem(
                route = Routes.PAYMENTS,
                labelRes = R.string.nav_payments,
                selectedIcon = Icons.Rounded.AccountBalanceWallet,
                unselectedIcon = Icons.Outlined.AccountBalanceWallet,
                testTag = "nav_payments"
            )
        )
    }

    val isTopLevelRoute = currentRoute == Routes.DASHBOARD ||
        currentRoute == Routes.REGISTER ||
        currentRoute == Routes.PARTIES ||
        currentRoute == Routes.PAYMENTS

    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val speechManager = remember(context) { OfflineSpeechManager(context) }
    val voiceViewModel: VoiceAssistantViewModel = viewModel(
        factory = VoiceAssistantViewModelFactory(repository, speechManager)
    )
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()

    fun navigateTopLevel(route: String) {
        if (currentRoute == route) return
        haptics.tick()
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openRegisterForParty(partyId: Long?) {
        haptics.click()
        if (partyId != null && partyId > 0L) {
            requestedRegisterPartyId = partyId
        }
        if (currentRoute != Routes.REGISTER) {
            navController.navigate(Routes.REGISTER) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(16.dp))
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppNavHost(
                        navController = navController,
                        repository = repository,
                        requestedRegisterPartyId = requestedRegisterPartyId,
                        onConsumedRequestedParty = { requestedRegisterPartyId = null },
                        onOpenRegisterForParty = ::openRegisterForParty,
                        bottomBarPadding = 16.dp
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isTopLevelRoute,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 22.dp, bottom = 22.dp)
                            .zIndex(11f)
                    ) {
                        FloatingAiPill(onClick = { voiceViewModel.openAssistant() })
                    }
                }
            }
        } else {
            val floatingBarHeightPadding = if (isTopLevelRoute) 94.dp else 0.dp
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavHost(
                    navController = navController,
                    repository = repository,
                    requestedRegisterPartyId = requestedRegisterPartyId,
                    onConsumedRequestedParty = { requestedRegisterPartyId = null },
                    onOpenRegisterForParty = ::openRegisterForParty,
                    bottomBarPadding = floatingBarHeightPadding
                )

                AnimatedVisibility(
                    visible = isTopLevelRoute,
                    enter = fadeIn(tween(200)) + slideInVertically(
                        animationSpec = tween(260, easing = SilkyEase)
                    ) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically(
                        animationSpec = tween(180)
                    ) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                        .padding(end = 18.dp, bottom = 92.dp)
                        .zIndex(11f)
                ) {
                    FloatingAiPill(onClick = { voiceViewModel.openAssistant() })
                }

                AnimatedVisibility(
                    visible = isTopLevelRoute,
                    enter = fadeIn(tween(200)) + slideInVertically(
                        animationSpec = tween(260, easing = SilkyEase)
                    ) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically(
                        animationSpec = tween(180)
                    ) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f)
                ) {
                    FloatingPillBottomNav(
                        items = navItems,
                        currentRoute = currentRoute,
                        onSelect = { item -> navigateTopLevel(item.route) },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    )
                }
            }
        }

        VoiceAssistantSheet(
            viewModel = voiceViewModel,
            state = voiceState,
            onDismiss = { voiceViewModel.closeAssistant() }
        )
    }
}

private val SilkyEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private fun topLevelOrder(route: String?): Int = when (route) {
    Routes.DASHBOARD -> 0
    Routes.REGISTER -> 1
    Routes.PARTIES -> 2
    Routes.PAYMENTS -> 3
    else -> -1
}

@Composable
private fun FloatingPillBottomNav(
    items: List<PillNavItem>,
    currentRoute: String?,
    onSelect: (PillNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val pillBg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pill_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "pill_fg"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pill_icon_scale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(54.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(pillBg)
                        .clickable { onSelect(item) }
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(item.labelRes),
                            tint = contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    repository: FactoryHisabRepository,
    requestedRegisterPartyId: Long?,
    onConsumedRequestedParty: () -> Unit,
    onOpenRegisterForParty: (Long?) -> Unit,
    bottomBarPadding: Dp,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            val fromIdx = topLevelOrder(initialState.destination.route)
            val toIdx = topLevelOrder(targetState.destination.route)
            if (fromIdx >= 0 && toIdx >= 0) {
                val dir = if (toIdx >= fromIdx) 1 else -1
                slideInHorizontally(animationSpec = tween(250, easing = SilkyEase)) { dir * it / 16 } +
                    fadeIn(animationSpec = tween(220, easing = SilkyEase))
            } else {
                slideInHorizontally(animationSpec = tween(280, easing = SilkyEase)) { it / 5 } +
                    fadeIn(animationSpec = tween(220, easing = SilkyEase))
            }
        },
        exitTransition = {
            val fromIdx = topLevelOrder(initialState.destination.route)
            val toIdx = topLevelOrder(targetState.destination.route)
            if (fromIdx >= 0 && toIdx >= 0) {
                val dir = if (toIdx >= fromIdx) -1 else 1
                slideOutHorizontally(animationSpec = tween(230, easing = SilkyEase)) { dir * it / 18 } +
                    fadeOut(animationSpec = tween(170))
            } else {
                slideOutHorizontally(animationSpec = tween(280, easing = SilkyEase)) { -it / 12 } +
                    fadeOut(animationSpec = tween(180))
            }
        },
        popEnterTransition = {
            val fromIdx = topLevelOrder(initialState.destination.route)
            val toIdx = topLevelOrder(targetState.destination.route)
            if (fromIdx >= 0 && toIdx >= 0) {
                val dir = if (toIdx >= fromIdx) 1 else -1
                slideInHorizontally(animationSpec = tween(250, easing = SilkyEase)) { dir * it / 16 } +
                    fadeIn(animationSpec = tween(220, easing = SilkyEase))
            } else {
                slideInHorizontally(animationSpec = tween(260, easing = SilkyEase)) { -it / 12 } +
                    fadeIn(animationSpec = tween(210, easing = SilkyEase))
            }
        },
        popExitTransition = {
            val fromIdx = topLevelOrder(initialState.destination.route)
            val toIdx = topLevelOrder(targetState.destination.route)
            if (fromIdx >= 0 && toIdx >= 0) {
                val dir = if (toIdx >= fromIdx) -1 else 1
                slideOutHorizontally(animationSpec = tween(230, easing = SilkyEase)) { dir * it / 18 } +
                    fadeOut(animationSpec = tween(170))
            } else {
                slideOutHorizontally(animationSpec = tween(260, easing = SilkyEase)) { it / 5 } +
                    fadeOut(animationSpec = tween(180))
            }
        }
    ) {
        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(
                factory = DashboardViewModelFactory(repository)
            )
            DashboardScreen(
                viewModel = vm,
                onOpenRegister = onOpenRegisterForParty,
                onOpenPartyDetail = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId))
                },
                onOpenSharedSlips = {
                    navController.navigate(Routes.SHARED_SLIPS)
                },
                contentBottomPadding = bottomBarPadding
            )
        }

        composable(Routes.REGISTER) {
            val vm: RegisterViewModel = viewModel(
                factory = RegisterViewModelFactory(repository)
            )
            RegisterCalculationScreen(
                viewModel = vm,
                requestedPartyId = requestedRegisterPartyId,
                onConsumedRequestedParty = onConsumedRequestedParty,
                onOpenHisabSlip = { hisabId ->
                    navController.navigate(Routes.hisabSlip(hisabId))
                },
                contentBottomPadding = bottomBarPadding
            )
        }

        composable(Routes.PARTIES) {
            val vm: PartiesViewModel = viewModel(
                factory = PartiesViewModelFactory(repository)
            )
            PartiesScreen(
                viewModel = vm,
                onOpenPartyDetail = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId))
                },
                onStartHisabForParty = { partyId ->
                    onOpenRegisterForParty(partyId)
                },
                contentBottomPadding = bottomBarPadding
            )
        }

        composable(Routes.PAYMENTS) {
            val vm: PaymentsViewModel = viewModel(
                factory = PaymentsViewModelFactory(repository)
            )
            PaymentsScreen(
                viewModel = vm,
                onOpenPartyDetail = { partyId ->
                    navController.navigate(Routes.partyDetail(partyId))
                },
                contentBottomPadding = bottomBarPadding
            )
        }

        composable(
            route = Routes.PARTY_DETAIL,
            arguments = listOf(
                navArgument("partyId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
            val vm: PartyDetailViewModel = viewModel(
                factory = PartyDetailViewModelFactory(partyId, repository)
            )
            PartyDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onNewHisab = { id ->
                    onOpenRegisterForParty(id)
                },
                onOpenHisabSlip = { hisabId ->
                    navController.navigate(Routes.hisabSlip(hisabId))
                }
            )
        }

        composable(
            route = Routes.HISAB_SLIP,
            arguments = listOf(
                navArgument("hisabId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val hisabId = backStackEntry.arguments?.getLong("hisabId") ?: 0L
            val vm: HisabSlipViewModel = viewModel(
                factory = HisabSlipViewModelFactory(hisabId, repository)
            )
            HisabSlipScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SHARED_SLIPS) {
            val vm: SharedSlipsViewModel = viewModel(
                factory = SharedSlipsViewModelFactory(repository)
            )
            SharedSlipsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenLiveHisabSlip = { hisabId ->
                    navController.navigate(Routes.hisabSlip(hisabId))
                }
            )
        }
    }
}
