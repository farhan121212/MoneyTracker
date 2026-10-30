package com.farhan.paisa.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.domain.Money
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.Segmented
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop

@Composable
fun AccountEditScreen(vm: MainViewModel, ledger: Ledger, nav: Nav, id: Long) {
    val existing = ledger.accountById[id]
    var kind by remember { mutableStateOf(existing?.kind ?: AccountKind.BANK) }
    var role by remember { mutableStateOf(existing?.role ?: AccountRole.SPENDING) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var last4 by remember { mutableStateOf(existing?.last4.orEmpty()) }
    var opening by remember { mutableStateOf(existing?.opening?.takeIf { it != 0L }?.let(Money::editable).orEmpty()) }
    var limit by remember { mutableStateOf(existing?.creditLimit?.takeIf { it != 0L }?.let(Money::editable).orEmpty()) }
    var statementDay by remember { mutableStateOf(existing?.statementDay?.takeIf { it != 0 }?.toString().orEmpty()) }
    var dueDay by remember { mutableStateOf(existing?.dueDay?.takeIf { it != 0 }?.toString().orEmpty()) }
    var accent by remember { mutableIntStateOf(existing?.accent ?: (ledger.accounts.size % Pop.Accents.size)) }
    var confirmRemove by remember { mutableStateOf(false) }
    val card = kind == AccountKind.CREDIT_CARD

    fun save() {
        val account = Account(
            id = id,
            name = name.trim(),
            kind = kind,
            role = if (card) AccountRole.SPENDING else role,
            last4 = last4.trim(),
            opening = Money.parse(opening) ?: 0,
            creditLimit = if (card) Money.parse(limit) ?: 0 else 0,
            statementDay = if (card) dayOf(statementDay) else 0,
            dueDay = if (card) dayOf(dueDay) else 0,
            accent = accent,
            sortOrder = existing?.sortOrder ?: ledger.accounts.size,
            archived = existing?.archived ?: false,
        )
        vm.write { it.saveAccount(account) }
        nav.back()
    }

    FullScreen {
        NavHeader(if (existing == null) "New account" else "Edit account", { nav.back() }, icon = Icons.Rounded.Close)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            if (existing == null) {
                Segmented(
                    options = listOf(AccountKind.BANK to "Bank", AccountKind.CREDIT_CARD to "Credit card", AccountKind.CASH to "Cash"),
                    selected = kind,
                    onSelect = { kind = it },
                )
                Spacer(Modifier.height(16.dp))
            }
            Group {
                GroupTextField(name, { name = it }, "Required", label = "Name", capitalization = KeyboardCapitalization.Words)
                if (kind != AccountKind.CASH) {
                    RowDivider()
                    GroupTextField(
                        value = last4,
                        onValueChange = { text -> last4 = text.filter(Char::isDigit).take(4) },
                        placeholder = "Optional",
                        label = "Last 4 digits",
                        keyboardType = KeyboardType.Number,
                    )
                }
                RowDivider()
                GroupTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    placeholder = "₹0",
                    label = if (card) "Owed right now" else "Balance right now",
                    keyboardType = KeyboardType.Decimal,
                )
            }
            if (!card) {
                Hint("What this account is for")
                Segmented(
                    options = listOf(AccountRole.SPENDING to "Everyday", AccountRole.SAVINGS to "Savings"),
                    selected = role,
                    onSelect = { role = it },
                )
                Hint(
                    if (role == AccountRole.SAVINGS) {
                        "Money going in counts towards your savings goal."
                    } else {
                        "Use this one for daily payments."
                    },
                )
            } else {
                Spacer(Modifier.height(16.dp))
                Group {
                    GroupTextField(limit, { limit = it }, "Optional", label = "Credit limit", keyboardType = KeyboardType.Decimal)
                    RowDivider()
                    GroupTextField(
                        value = statementDay,
                        onValueChange = { text -> statementDay = text.filter(Char::isDigit).take(2) },
                        placeholder = "Day, e.g. 5",
                        label = "Statement day",
                        keyboardType = KeyboardType.Number,
                    )
                    RowDivider()
                    GroupTextField(
                        value = dueDay,
                        onValueChange = { text -> dueDay = text.filter(Char::isDigit).take(2) },
                        placeholder = "Day, e.g. 23",
                        label = "Due day",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Hint("The app reminds you before the bill is due.")
            }
            Hint("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pop.Accents.forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .press { accent = index }
                            .then(if (accent == index) Modifier.border(2.dp, Pop.Text, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (accent == index) {
                            Icon(Icons.Rounded.Check, "Selected", tint = Pop.OnPop, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (existing != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Remove account",
                    style = MaterialTheme.typography.titleSmall,
                    color = Pop.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().press { confirmRemove = true }.padding(vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            text = "Save",
            onClick = ::save,
            enabled = name.isNotBlank(),
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Gutter, vertical = 12.dp),
        )
    }

    if (confirmRemove && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${existing.name}?") },
            text = { Text("It disappears from your accounts. Its past entries stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.write { it.removeAccount(existing) }
                    nav.tab("wallet")
                }) { Text("Remove", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

private fun dayOf(text: String): Int = text.toIntOrNull()?.coerceIn(0, 31) ?: 0

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Pop.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}
