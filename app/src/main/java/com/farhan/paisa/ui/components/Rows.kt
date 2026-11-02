package com.farhan.paisa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.balanceOf
import com.farhan.paisa.domain.cardStatus
import com.farhan.paisa.domain.myShare
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate

/** Icons offered for a category. Keys are saved with it, so an existing key must never change. */
val CategoryIconGroups: List<Pair<String, List<Pair<String, ImageVector>>>> = listOf(
    "Food and drink" to listOf(
        "food" to Icons.Rounded.Restaurant,
        "groceries" to Icons.Rounded.LocalGroceryStore,
        "coffee" to Icons.Rounded.LocalCafe,
        "fastfood" to Icons.Rounded.Fastfood,
        "pizza" to Icons.Rounded.LocalPizza,
        "drinks" to Icons.Rounded.LocalBar,
        "dessert" to Icons.Rounded.Icecream,
        "bakery" to Icons.Rounded.BakeryDining,
    ),
    "Getting around" to listOf(
        "transport" to Icons.Rounded.DirectionsBus,
        "cab" to Icons.Rounded.LocalTaxi,
        "bike" to Icons.Rounded.TwoWheeler,
        "car" to Icons.Rounded.DirectionsCar,
        "train" to Icons.Rounded.Train,
        "fuel" to Icons.Rounded.LocalGasStation,
        "parking" to Icons.Rounded.LocalParking,
        "travel" to Icons.Rounded.Flight,
        "luggage" to Icons.Rounded.Luggage,
        "hotel" to Icons.Rounded.Hotel,
    ),
    "Home and bills" to listOf(
        "home" to Icons.Rounded.Home,
        "rent" to Icons.Rounded.Apartment,
        "bills" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop,
        "internet" to Icons.Rounded.Wifi,
        "phone" to Icons.Rounded.Smartphone,
        "furniture" to Icons.Rounded.Chair,
        "cleaning" to Icons.Rounded.CleaningServices,
        "laundry" to Icons.Rounded.LocalLaundryService,
        "repairs" to Icons.Rounded.Build,
        "subscriptions" to Icons.Rounded.Subscriptions,
    ),
    "Health and care" to listOf(
        "health" to Icons.Rounded.Favorite,
        "hospital" to Icons.Rounded.LocalHospital,
        "medicine" to Icons.Rounded.Medication,
        "gym" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "haircut" to Icons.Rounded.ContentCut,
        "pets" to Icons.Rounded.Pets,
        "kids" to Icons.Rounded.ChildCare,
    ),
    "Sports and fun" to listOf(
        "sports" to Icons.Rounded.SportsSoccer,
        "cricket" to Icons.Rounded.SportsCricket,
        "basketball" to Icons.Rounded.SportsBasketball,
        "tennis" to Icons.Rounded.SportsTennis,
        "swimming" to Icons.Rounded.Pool,
        "running" to Icons.Rounded.DirectionsRun,
        "entertainment" to Icons.Rounded.Movie,
        "games" to Icons.Rounded.SportsEsports,
        "music" to Icons.Rounded.MusicNote,
        "party" to Icons.Rounded.Celebration,
    ),
    "Shopping" to listOf(
        "shopping" to Icons.Rounded.ShoppingBag,
        "clothes" to Icons.Rounded.Checkroom,
        "gadgets" to Icons.Rounded.Devices,
        "gift" to Icons.Rounded.CardGiftcard,
        "books" to Icons.Rounded.MenuBook,
    ),
    "Money and work" to listOf(
        "salary" to Icons.Rounded.Payments,
        "work" to Icons.Rounded.WorkOutline,
        "refund" to Icons.Rounded.Refresh,
        "savings" to Icons.Rounded.Savings,
        "bank" to Icons.Rounded.AccountBalance,
        "insurance" to Icons.Rounded.Shield,
        "tax" to Icons.Rounded.Receipt,
        "charity" to Icons.Rounded.VolunteerActivism,
        "education" to Icons.Rounded.School,
        "people" to Icons.Rounded.Group,
        "other" to Icons.Rounded.MoreHoriz,
    ),
)

val CategoryIcons: Map<String, ImageVector> = CategoryIconGroups.flatMap { it.second }.toMap()

fun iconFor(category: Category?): ImageVector = CategoryIcons[category?.icon] ?: Icons.Rounded.MoreHoriz

fun iconFor(account: Account): ImageVector = when {
    account.kind == AccountKind.CREDIT_CARD -> Icons.Rounded.CreditCard
    account.kind == AccountKind.CASH -> Icons.Rounded.AccountBalanceWallet
    account.role == AccountRole.SAVINGS -> Icons.Rounded.Savings
    else -> Icons.Rounded.AccountBalance
}

fun Ledger.titleOf(txn: Txn): String = when (txn.type) {
    TxType.TRANSFER -> {
        val to = accountById[txn.toAccountId]
        if (to?.kind == AccountKind.CREDIT_CARD) "${to.name} bill" else "To ${to?.name ?: "account"}"
    }
    TxType.SETTLEMENT -> "${personById[txn.personId]?.name ?: "Someone"} paid back"
    else -> txn.payee.ifBlank { categoryById[txn.categoryId]?.name ?: if (txn.type == TxType.INCOME) "Income" else "Expense" }
}

fun Ledger.subtitleOf(txn: Txn): String {
    val account = accountById[txn.accountId]?.name
    val parts = when (txn.type) {
        TxType.EXPENSE -> listOf(categoryById[txn.categoryId]?.name, txn.method.label, account)
        TxType.INCOME -> listOf(categoryById[txn.categoryId]?.name, account)
        TxType.TRANSFER -> listOf("From $account", txn.method.label)
        TxType.SETTLEMENT -> listOf("Into $account")
    }
    return parts.filterNotNull().joinToString(" · ")
}

@Composable
fun TxnBadge(ledger: Ledger, txn: Txn, size: Dp = 40.dp) {
    when (txn.type) {
        TxType.TRANSFER -> IconBadge(Icons.Rounded.SwapHoriz, Pop.Lavender, size = size)
        TxType.SETTLEMENT -> IconBadge(Icons.Rounded.Handshake, Pop.Tangerine, size = size)
        else -> {
            val category = ledger.categoryById[txn.categoryId]
            IconBadge(iconFor(category), Pop.categoryPop(category?.color ?: -1), size = size)
        }
    }
}

@Composable
fun TxnRow(ledger: Ledger, txn: Txn, onClick: () -> Unit) {
    val mine = ledger.myShare(txn)
    val split = txn.type == TxType.EXPENSE && mine != txn.amount
    GroupRow(
        title = ledger.titleOf(txn),
        subtitle = ledger.subtitleOf(txn),
        leading = { TxnBadge(ledger, txn) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                val (text, color) = when (txn.type) {
                    TxType.EXPENSE -> Money.format(-txn.amount) to Pop.Text
                    TxType.TRANSFER -> Money.format(txn.amount) to Pop.TextSecondary
                    else -> Money.format(txn.amount, sign = true) to Pop.Good
                }
                Text(text, style = MaterialTheme.typography.titleSmall, color = color)
                if (split) {
                    Text("You ${Money.format(mine)}", style = MaterialTheme.typography.labelSmall, color = Pop.TextSecondary)
                }
            }
        },
        onClick = onClick,
    )
}

/** A bright, card-shaped summary of an account. Text stays near black for contrast on the fill. */
@Composable
fun AccountCard(
    ledger: Ledger,
    account: Account,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val fill = Pop.accent(account.accent)
    val card = account.kind == AccountKind.CREDIT_CARD
    val status = if (card) ledger.cardStatus(account, today) else null
    val amount = status?.owed ?: ledger.balanceOf(account)
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(fill, lerp(fill, Color.Black, 0.14f))))
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawCardArt(account.pattern) }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Pop.OnPop,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            account.last4.isNotBlank() -> "•••• ${account.last4}"
                            card -> "Credit card"
                            account.kind == AccountKind.CASH -> "Cash"
                            account.role == AccountRole.SAVINGS -> "Savings"
                            else -> "Everyday"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Pop.OnPopSoft,
                    )
                }
                Icon(iconFor(account), null, tint = Pop.OnPop, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (card) "Owed" else "Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = Pop.OnPopSoft,
                    )
                    Text(Money.whole(amount), style = MaterialTheme.typography.headlineMedium, color = Pop.OnPop, maxLines = 1)
                }
                if (status != null) {
                    dueLabel(status.overdue, status.daysToDue, status.billed)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = Pop.OnPop,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

fun dueLabel(overdue: Boolean, daysToDue: Int?, billed: Long): String? = when {
    billed <= 0 || daysToDue == null -> null
    overdue -> "Overdue"
    daysToDue == 0 -> "Due today"
    daysToDue == 1 -> "Due tomorrow"
    else -> "Due in $daysToDue days"
}

@Composable
fun AddAccountCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(CardShape).background(Pop.Surface).press(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        CircleButton(Icons.Rounded.Add, "Add account", onClick, background = Pop.Raised)
        Spacer(Modifier.height(8.dp))
        Text("Add", style = MaterialTheme.typography.labelLarge, color = Pop.TextSecondary)
    }
}

/** A coloured dot next to neutral text, so identity in charts never relies on tinted labels. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pop.TextSecondary)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.clip(GroupShape).background(Pop.Surface).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        if (detail != null) {
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Pop.TextMuted)
        }
    }
}
