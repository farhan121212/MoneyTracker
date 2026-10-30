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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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

val CategoryIcons: Map<String, ImageVector> = linkedMapOf(
    "food" to Icons.Rounded.Restaurant,
    "groceries" to Icons.Rounded.LocalGroceryStore,
    "transport" to Icons.Rounded.DirectionsBus,
    "sports" to Icons.Rounded.SportsSoccer,
    "shopping" to Icons.Rounded.ShoppingBag,
    "bills" to Icons.Rounded.Bolt,
    "entertainment" to Icons.Rounded.Movie,
    "home" to Icons.Rounded.Home,
    "subscriptions" to Icons.Rounded.Subscriptions,
    "health" to Icons.Rounded.Favorite,
    "travel" to Icons.Rounded.Flight,
    "education" to Icons.Rounded.School,
    "salary" to Icons.Rounded.Payments,
    "work" to Icons.Rounded.WorkOutline,
    "refund" to Icons.Rounded.Refresh,
    "gift" to Icons.Rounded.CardGiftcard,
    "other" to Icons.Rounded.MoreHoriz,
)

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
            .background(fill)
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.92f, size.height * 0.1f)
            listOf(0.5f, 0.82f, 1.14f).forEach { factor ->
                drawCircle(
                    color = Pop.OnPop.copy(alpha = 0.07f),
                    radius = size.height * factor,
                    center = center,
                    style = Stroke(width = 18.dp.toPx()),
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
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
fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, modifier: Modifier = Modifier) {
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
