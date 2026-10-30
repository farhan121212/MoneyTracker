package com.farhan.paisa.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.accountFor
import com.farhan.paisa.domain.owedBy
import com.farhan.paisa.domain.possibleDuplicate
import com.farhan.paisa.domain.repayerFor
import com.farhan.paisa.domain.suggestionFor
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    val pending = ledger.toReview
    FullScreen {
        NavHeader("To review", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Row(
                    Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Found in your bank SMS. Nothing changes your balances until you add it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pop.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (pending.size > 1) {
                        Text(
                            "Skip all",
                            style = MaterialTheme.typography.labelLarge,
                            color = Pop.TextSecondary,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .press { vm.write { repo -> pending.forEach { repo.dismissDetected(it) } } }
                                .padding(8.dp),
                        )
                    }
                }
            }
            if (pending.isEmpty()) {
                item { Group { EmptyState("All caught up", "New payments appear here as your bank texts you.") } }
            }
            items(pending, key = { it.id }) { detected ->
                ReviewCard(vm, ledger, detected, nav)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ReviewCard(vm: MainViewModel, ledger: Ledger, detected: Detected, nav: Nav) {
    val suggestion = remember(ledger, detected) { ledger.suggestionFor(detected) }
    val repayer = remember(ledger, detected) { ledger.repayerFor(detected) }
    val from = ledger.accountFor(detected.last4)
    val to = ledger.accountFor(detected.toLast4)
    val category = ledger.categoryById[suggestion.categoryId].takeIf { repayer == null }
    val transfer = detected.type == TxType.TRANSFER
    val date = Instant.ofEpochMilli(detected.date).atZone(ZoneId.systemDefault()).toLocalDate()
    val duplicate = remember(ledger, detected) { ledger.possibleDuplicate(detected) }

    val title = when {
        repayer != null -> "${repayer.name} paid you back"
        !transfer -> suggestion.payee.ifBlank { if (detected.type == TxType.INCOME) "Money received" else "Payment" }
        to?.kind == AccountKind.CREDIT_CARD -> "${to.name} bill"
        to != null -> "To ${to.name}"
        else -> "Moved between accounts"
    }
    val details = listOfNotNull(
        repayer?.let { "Owes you ${Money.format(ledger.owedBy(it.id))}" },
        category?.name,
        detected.method.label.takeUnless { transfer },
        from?.name ?: detected.last4.takeIf { it.isNotEmpty() }?.let { "Account $it" },
        date.format(DateTimeFormatter.ofPattern("d MMM")),
    ).joinToString(" · ")

    // One tap is only offered when everything needed is already known, otherwise it asks first.
    val ready = from != null && when {
        transfer -> to != null
        else -> repayer != null || category != null
    }

    Group {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { nav.add(detected = detected.id) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    transfer -> IconBadge(Icons.Rounded.SwapHoriz, Pop.Lavender)
                    repayer != null -> IconBadge(Icons.Rounded.Handshake, Pop.Tangerine)
                    category != null -> IconBadge(iconFor(category), Pop.categoryPop(category.color))
                    detected.type == TxType.INCOME -> IconBadge(Icons.Rounded.Handshake, Pop.Mint)
                    else -> IconBadge(iconFor(null), Pop.TextSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(details, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (detected.type == TxType.INCOME) Money.format(detected.amount, sign = true) else Money.format(detected.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (detected.type == TxType.INCOME) Pop.Good else Pop.Text,
                )
            }
            if (duplicate != null) {
                Notice("You may have logged this already: ${duplicateName(duplicate)}")
            } else if (from == null && detected.last4.isNotEmpty()) {
                Notice("Which account ends in ${detected.last4}? Tap to choose.")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Skip", Pop.Raised, Pop.Text, Modifier.weight(1f)) {
                    vm.write { it.dismissDetected(detected) }
                }
                Action(if (ready) "Add" else "Choose details", Pop.Lime, Pop.OnPop, Modifier.weight(1f)) {
                    if (ready) confirm(vm, ledger, detected) else nav.add(detected = detected.id)
                }
            }
        }
    }
}

private fun duplicateName(txn: Txn) = txn.payee.ifBlank { "same amount" }

private fun confirm(vm: MainViewModel, ledger: Ledger, detected: Detected) {
    val suggestion = ledger.suggestionFor(detected)
    val from = ledger.accountFor(detected.last4) ?: return
    val repayer = ledger.repayerFor(detected)
    val plain = detected.type != TxType.TRANSFER && repayer == null
    val txn = Txn(
        type = if (repayer != null) TxType.SETTLEMENT else detected.type,
        amount = detected.amount,
        accountId = from.id,
        toAccountId = if (detected.type == TxType.TRANSFER) ledger.accountFor(detected.toLast4)?.id else null,
        categoryId = if (plain) suggestion.categoryId else null,
        payee = if (plain) suggestion.payee else "",
        personId = repayer?.id,
        method = detected.method,
        date = detected.date,
    )
    vm.write { it.confirmDetected(detected, txn, if (plain) suggestion.tagIds else emptyList(), emptyList()) }
}

@Composable
private fun Notice(text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Pop.Warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
    }
}

@Composable
private fun Action(text: String, background: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(background)
            .press(onClick = onClick)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
