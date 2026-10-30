package com.farhan.paisa.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.farhan.paisa.MainActivity
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import com.farhan.paisa.ui.account.AccountEditScreen
import com.farhan.paisa.ui.account.AccountScreen
import com.farhan.paisa.ui.account.WalletScreen
import com.farhan.paisa.ui.activity.ActivityFilter
import com.farhan.paisa.ui.activity.ActivityScreen
import com.farhan.paisa.ui.add.AddScreen
import com.farhan.paisa.ui.bills.BillsScreen
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.goals.GoalScreen
import com.farhan.paisa.ui.goals.GoalsScreen
import com.farhan.paisa.ui.home.HomeScreen
import com.farhan.paisa.ui.insights.InsightsScreen
import com.farhan.paisa.ui.onboarding.OnboardingScreen
import com.farhan.paisa.ui.people.PeopleScreen
import com.farhan.paisa.ui.people.PersonScreen
import com.farhan.paisa.ui.review.ReviewScreen
import com.farhan.paisa.ui.settings.CategoriesScreen
import com.farhan.paisa.ui.settings.SettingsScreen
import com.farhan.paisa.ui.settings.TagsScreen
import com.farhan.paisa.ui.theme.Pop
import com.farhan.paisa.ui.txn.TxnScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** Every navigation destination in one place, so screens never build route strings. */
class Nav(private val controller: NavController) {
    fun back() = controller.popBackStack()

    fun tab(route: String) = controller.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun add(
        type: TxType = TxType.EXPENSE,
        edit: Long = 0,
        from: Long = 0,
        to: Long = 0,
        amount: Long = 0,
        detected: Long = 0,
        bill: Long = 0,
    ) = controller.navigate("add?type=${type.name}&edit=$edit&from=$from&to=$to&amount=$amount&detected=$detected&bill=$bill")

    fun review() = controller.navigate("review") { launchSingleTop = true }
    fun bills() = controller.navigate("bills")
    fun goals() = controller.navigate("goals")
    fun goal(id: Long) = controller.navigate("goal/$id")

    fun txn(id: Long) = controller.navigate("txn/$id")
    fun account(id: Long) = controller.navigate("account/$id")
    fun editAccount(id: Long = 0) = controller.navigate("accountEdit?id=$id")
    fun people() = controller.navigate("people")
    fun person(id: Long) = controller.navigate("person/$id")
    fun settings() = controller.navigate("settings")
    fun categories() = controller.navigate("categories")
    fun tags() = controller.navigate("tags")

    fun filtered(tag: Long = 0, category: Long = 0, account: Long = 0, query: String = "", month: String = "") =
        controller.navigate("filtered?tag=$tag&category=$category&account=$account&query=${android.net.Uri.encode(query)}&month=$month")
}

@Composable
fun PaisaRoot(
    vm: MainViewModel,
    unlocked: Boolean,
    onUnlock: () -> Unit,
    openRequests: MutableStateFlow<String?>,
) {
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentLedger = ledger
    val currentSettings = settings

    Box(Modifier.fillMaxSize().background(Pop.Ink)) {
        if (currentLedger != null && currentSettings != null) {
            if (currentSettings.onboarded) {
                MainNav(vm, currentLedger, currentSettings, openRequests)
            } else {
                OnboardingScreen(vm)
            }
            // Drawn on top rather than swapped in, so a half-filled form survives the lock.
            if (currentSettings.lockEnabled && !unlocked) LockOverlay(onUnlock)
        }
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The prompt can only appear while the app is in front, so wait for that.
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { onUnlock() } }
    Column(
        Modifier
            .fillMaxSize()
            .background(Pop.Ink)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = Pop.Lime, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Paisa is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Unlock", onUnlock)
    }
}

private val TabRoutes = setOf("home", "activity", "insights", "wallet")

private val Push: NavGraphBuilderTransition = NavGraphBuilderTransition(
    enter = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)) },
    exit = { fadeOut(tween(120)) },
    popEnter = { fadeIn(tween(160)) },
    popExit = { slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(200)) },
)

private class NavGraphBuilderTransition(
    val enter: () -> EnterTransition,
    val exit: () -> ExitTransition,
    val popEnter: () -> EnterTransition,
    val popExit: () -> ExitTransition,
)

private fun NavGraphBuilder.pushed(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { Push.enter() },
    exitTransition = { Push.exit() },
    popEnterTransition = { Push.popEnter() },
    popExitTransition = { Push.popExit() },
) { content(it) }

private fun longArg(name: String) = navArgument(name) { type = NavType.LongType; defaultValue = 0L }
private fun textArg(name: String) = navArgument(name) { type = NavType.StringType; defaultValue = "" }

@Composable
private fun MainNav(
    vm: MainViewModel,
    ledger: Ledger,
    settings: Settings,
    openRequests: MutableStateFlow<String?>,
) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(Unit) {
        openRequests.collect { request ->
            when (request) {
                MainActivity.ACTION_ADD -> nav.add()
                MainActivity.ACTION_REVIEW -> nav.review()
            }
            openRequests.value = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = controller,
            startDestination = "home",
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable("home") { HomeScreen(vm, ledger, settings, nav) }
            composable("activity") { ActivityScreen(ledger, nav, ActivityFilter()) }
            composable("insights") { InsightsScreen(ledger, settings, nav) }
            composable("wallet") { WalletScreen(ledger, nav) }

            composable(
                route = "add?type={type}&edit={edit}&from={from}&to={to}&amount={amount}&detected={detected}&bill={bill}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                    longArg("edit"), longArg("from"), longArg("to"), longArg("amount"), longArg("detected"), longArg("bill"),
                ),
                enterTransition = { slideInVertically(tween(300)) { it } },
                exitTransition = { fadeOut(tween(120)) },
                popExitTransition = { slideOutVertically(tween(260)) { it } },
            ) { back ->
                val args = requireNotNull(back.arguments)
                AddScreen(
                    vm = vm,
                    ledger = ledger,
                    nav = nav,
                    type = TxType.valueOf(args.getString("type") ?: TxType.EXPENSE.name),
                    editId = args.getLong("edit"),
                    from = args.getLong("from"),
                    to = args.getLong("to"),
                    amount = args.getLong("amount"),
                    detectedId = args.getLong("detected"),
                    billId = args.getLong("bill"),
                )
            }
            pushed(
                "filtered?tag={tag}&category={category}&account={account}&query={query}&month={month}",
                listOf(longArg("tag"), longArg("category"), longArg("account"), textArg("query"), textArg("month")),
            ) { back ->
                val args = requireNotNull(back.arguments)
                ActivityScreen(
                    ledger = ledger,
                    nav = nav,
                    initial = ActivityFilter(
                        tagId = args.getLong("tag").takeIf { it != 0L },
                        categoryId = args.getLong("category").takeIf { it != 0L },
                        accountId = args.getLong("account").takeIf { it != 0L },
                        query = args.getString("query").orEmpty(),
                        month = args.getString("month").orEmpty(),
                        pushed = true,
                    ),
                )
            }
            pushed("txn/{id}", listOf(longArg("id"))) { TxnScreen(vm, ledger, nav, it.arguments?.getLong("id") ?: 0) }
            pushed("account/{id}", listOf(longArg("id"))) { AccountScreen(ledger, nav, it.arguments?.getLong("id") ?: 0) }
            pushed("accountEdit?id={id}", listOf(longArg("id"))) { AccountEditScreen(vm, ledger, nav, it.arguments?.getLong("id") ?: 0) }
            pushed("people") { PeopleScreen(vm, ledger, nav) }
            pushed("person/{id}", listOf(longArg("id"))) { PersonScreen(vm, ledger, nav, it.arguments?.getLong("id") ?: 0) }
            pushed("review") { ReviewScreen(vm, ledger, nav) }
            pushed("bills") { BillsScreen(vm, ledger, nav) }
            pushed("goals") { GoalsScreen(vm, ledger, nav) }
            pushed("goal/{id}", listOf(longArg("id"))) { GoalScreen(vm, ledger, nav, it.arguments?.getLong("id") ?: 0) }
            pushed("settings") { SettingsScreen(vm, ledger, settings, nav) }
            pushed("categories") { CategoriesScreen(vm, ledger, nav) }
            pushed("tags") { TagsScreen(vm, ledger, nav) }
        }

        if (route in TabRoutes) {
            BottomBar(route.orEmpty(), nav, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BottomBar(route: String, nav: Nav, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Pop.Ink.copy(alpha = 0.9f), Pop.Ink)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Pop.Surface)
                .border(1.dp, Pop.Hairline, RoundedCornerShape(30.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("Home", Icons.Rounded.Home, route == "home") { nav.tab("home") }
            TabItem("Activity", Icons.Rounded.Receipt, route == "activity") { nav.tab("activity") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Pop.Lime)
                        .press { nav.add() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, "Add", tint = Pop.OnPop, modifier = Modifier.size(28.dp))
                }
            }
            TabItem("Insights", Icons.Rounded.BarChart, route == "insights") { nav.tab("insights") }
            TabItem("Wallet", Icons.Rounded.CreditCard, route == "wallet") { nav.tab("wallet") }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) Pop.Lime else Pop.TextMuted
    Column(
        Modifier
            .weight(1f)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
