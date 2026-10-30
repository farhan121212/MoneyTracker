package com.farhan.paisa.ui.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.domain.BillStatus
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.billsFor
import com.farhan.paisa.domain.suggestedBills
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CategoryIcons
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate

@Composable
fun BillsScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    val today = remember { LocalDate.now() }
    val bills = ledger.billsFor(today)
    val suggested = ledger.suggestedBills(today)
    val left = bills.filterNot { it.paid }.sumOf { it.bill.amount }
    var editing by remember { mutableStateOf<Recurring?>(null) }

    FullScreen {
        NavHeader(
            title = "Recurring bills",
            onBack = { nav.back() },
            trailing = {
                CircleButton(Icons.Rounded.Add, "Add bill", { editing = Recurring(name = "", amount = 0, day = today.dayOfMonth) })
            },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text("Still to pay this month", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(Money.whole(left), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Text(
                    "Of ${Money.whole(bills.sumOf { it.bill.amount })} a month. This comes off your safe-to-spend, so bills don't catch you out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pop.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp),
                )
                Group {
                    if (bills.isEmpty()) {
                        EmptyState("No bills yet", "Add rent, subscriptions and anything else that comes every month.")
                    }
                    bills.forEachIndexed { index, status ->
                        if (index > 0) RowDivider(start = 68.dp)
                        BillRow(ledger, status, onClick = { editing = status.bill }, onPay = { nav.add(bill = status.bill.id) })
                    }
                }
            }
            if (suggested.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Look like regular bills")
                    Group {
                        suggested.forEachIndexed { index, txn ->
                            if (index > 0) RowDivider()
                            GroupRow(
                                title = txn.payee,
                                subtitle = "${Money.format(txn.amount)} every month",
                                trailing = { Text("Add", style = MaterialTheme.typography.labelLarge, color = Pop.Lime) },
                                onClick = {
                                    val day = java.time.Instant.ofEpochMilli(txn.date).atZone(java.time.ZoneId.systemDefault()).dayOfMonth
                                    vm.write {
                                        it.saveRecurring(
                                            Recurring(
                                                name = txn.payee,
                                                amount = txn.amount,
                                                day = day,
                                                categoryId = txn.categoryId,
                                                accountId = txn.accountId,
                                                method = txn.method,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { bill ->
        BillSheet(
            ledger = ledger,
            bill = bill,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                vm.write { it.saveRecurring(saved) }
            },
            onDelete = {
                editing = null
                vm.write { it.deleteRecurring(bill) }
            },
        )
    }
}

@Composable
private fun BillRow(ledger: Ledger, status: BillStatus, onClick: () -> Unit, onPay: () -> Unit) {
    val category = ledger.categoryById[status.bill.categoryId]
    GroupRow(
        title = status.bill.name,
        subtitle = "${Money.format(status.bill.amount)} · ${dueText(status)}",
        leading = { IconBadge(iconFor(category), Pop.categoryPop(category?.color ?: -1)) },
        trailing = {
            if (status.paid) {
                Icon(Icons.Rounded.CheckCircle, "Paid", tint = Pop.Good)
            } else {
                TextButton(onClick = onPay) { Text("Pay", color = Pop.Lime) }
            }
        },
        onClick = onClick,
    )
}

fun dueText(status: BillStatus): String = when {
    status.paid -> "Paid this month"
    status.daysToDue < 0 -> "Was due on the ${ordinal(status.due.dayOfMonth)}"
    status.daysToDue == 0 -> "Due today"
    status.daysToDue == 1 -> "Due tomorrow"
    else -> "Due in ${status.daysToDue} days"
}

private fun ordinal(day: Int): String = day.toString() + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheet(
    ledger: Ledger,
    bill: Recurring,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(bill.name) }
    var amount by remember { mutableStateOf(bill.amount.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    var day by remember { mutableStateOf(bill.day.toString()) }
    var categoryId by remember { mutableStateOf(bill.categoryId) }
    var accountId by remember { mutableStateOf(bill.accountId) }
    val categories = ledger.categories.filter { !it.income && !it.archived }
    val accounts = ledger.activeAccounts
    val parsed = Money.parse(amount) ?: 0
    val parsedDay = day.toIntOrNull() ?: 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text(if (bill.id == 0L) "New bill" else "Edit bill", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Group {
                GroupTextField(name, { name = it }, "Netflix, rent", label = "Name", capitalization = KeyboardCapitalization.Words)
                RowDivider()
                GroupTextField(amount, { amount = it }, "₹0", label = "Amount", keyboardType = KeyboardType.Decimal)
                RowDivider()
                GroupTextField(
                    value = day,
                    onValueChange = { text -> day = text.filter(Char::isDigit).take(2) },
                    placeholder = "1 to 31",
                    label = "Due on day",
                    keyboardType = KeyboardType.Number,
                )
            }
            Label("Category")
            ChipRow(categories, selected = categories.indexOfFirst { it.id == categoryId }) { category ->
                Chip(
                    label = category.name,
                    icon = CategoryIcons[category.icon],
                    selected = categoryId == category.id,
                    selectedColor = Pop.categoryPop(category.color),
                    onClick = { categoryId = category.id },
                )
            }
            Label("Paid from")
            ChipRow(accounts, selected = accounts.indexOfFirst { it.id == accountId }) { account ->
                Chip(
                    label = account.name,
                    icon = iconFor(account),
                    selected = accountId == account.id,
                    selectedColor = Pop.accent(account.accent),
                    onClick = { accountId = account.id },
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = name.isNotBlank() && parsed > 0 && parsedDay in 1..31,
                onClick = {
                    onSave(bill.copy(name = name.trim(), amount = parsed, day = parsedDay, categoryId = categoryId, accountId = accountId))
                },
            )
            if (bill.id != 0L) {
                TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Delete bill", color = Pop.Danger)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pop.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}
