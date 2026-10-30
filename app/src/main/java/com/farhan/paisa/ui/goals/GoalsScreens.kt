package com.farhan.paisa.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.farhan.paisa.data.Goal
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.assignedToGoals
import com.farhan.paisa.domain.freeSavings
import com.farhan.paisa.domain.monthlyNeeded
import com.farhan.paisa.domain.savedTowards
import com.farhan.paisa.domain.savingsTotal
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.add.DateDialog
import com.farhan.paisa.ui.components.CardShape
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.Meter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.Ring
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MonthYear = DateTimeFormatter.ofPattern("MMM yyyy")

private fun goalPlan(goal: Goal, saved: Long, today: LocalDate): String {
    val needed = goal.monthlyNeeded(saved, today)
    return when {
        saved >= goal.target -> "Reached"
        needed == null -> "${Money.whole(goal.target - saved)} to go"
        else -> "${Money.whole(needed)} a month to reach it by ${LocalDate.ofEpochDay(requireNotNull(goal.targetDay)).format(MonthYear)}"
    }
}

@Composable
fun GoalsScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    val today = remember { LocalDate.now() }
    var creating by remember { mutableStateOf(false) }

    FullScreen {
        NavHeader(
            title = "Goals",
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Add, "Add goal", { creating = true }) },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text("In your savings", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(Money.whole(ledger.savingsTotal()), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Set aside for goals", Money.whole(ledger.assignedToGoals()), Modifier.weight(1f))
                    StatTile("Not set aside", Money.whole(ledger.freeSavings()), Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
            }
            if (ledger.goals.isEmpty()) {
                item {
                    Group {
                        EmptyState("No goals yet", "Name what you're saving for, like an emergency fund or a trip. The money stays in your savings account.")
                    }
                }
            }
            items(ledger.goals, key = { it.id }) { goal ->
                GoalCard(ledger, goal, today) { nav.goal(goal.id) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (creating) {
        GoalSheet(
            goal = Goal(name = "", target = 0, accent = ledger.goals.size % Pop.Accents.size),
            onDismiss = { creating = false },
            onSave = { goal ->
                creating = false
                vm.write { it.saveGoal(goal) }
            },
        )
    }
}

@Composable
private fun GoalCard(ledger: Ledger, goal: Goal, today: LocalDate, onClick: () -> Unit) {
    val saved = ledger.savedTowards(goal.id)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Pop.accent(goal.accent))
            .press(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium, color = Pop.OnPop, modifier = Modifier.weight(1f))
            Text("${(saved * 100 / goal.target.coerceAtLeast(1)).coerceAtMost(100)}%", style = MaterialTheme.typography.titleMedium, color = Pop.OnPop)
        }
        Spacer(Modifier.height(12.dp))
        Text(Money.whole(saved), style = MaterialTheme.typography.headlineMedium, color = Pop.OnPop)
        Text("of ${Money.whole(goal.target)}", style = MaterialTheme.typography.bodySmall, color = Pop.OnPopSoft)
        Spacer(Modifier.height(10.dp))
        Meter(
            value = saved.toFloat() / goal.target.coerceAtLeast(1),
            color = Pop.OnPop,
            track = Pop.OnPop.copy(alpha = 0.14f),
            height = 6.dp,
        )
        Spacer(Modifier.height(4.dp))
        Text(goalPlan(goal, saved, today), style = MaterialTheme.typography.bodySmall, color = Pop.OnPopSoft)
    }
}

@Composable
fun GoalScreen(vm: MainViewModel, ledger: Ledger, nav: Nav, id: Long) {
    val goal = ledger.goalById[id] ?: return
    val today = remember { LocalDate.now() }
    val saved = ledger.savedTowards(id)
    var moving by remember { mutableStateOf<Boolean?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val entries = ledger.goalEntries.filter { it.goalId == id }

    FullScreen {
        NavHeader(
            title = goal.name,
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Edit, "Edit", { editing = true }) },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Ring(
                        value = saved.toFloat() / goal.target.coerceAtLeast(1),
                        color = Pop.accent(goal.accent),
                        track = Pop.Raised,
                        modifier = Modifier.size(200.dp),
                        stroke = 14.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Money.whole(saved), style = MaterialTheme.typography.headlineLarge)
                        Text("of ${Money.whole(goal.target)}", style = MaterialTheme.typography.bodyMedium, color = Pop.TextSecondary)
                    }
                }
                Text(
                    goalPlan(goal, saved, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pop.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                PrimaryButton("Add money", { moving = true })
                if (saved > 0) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Take money out", { moving = false }, color = Pop.Surface, contentColor = Pop.Text)
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel("History")
                Group {
                    if (entries.isEmpty()) {
                        EmptyState("Nothing yet", "Money you set aside shows up here.")
                    }
                    val format = DateTimeFormatter.ofPattern("d MMM yyyy")
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider()
                        GroupRow(
                            title = if (entry.amount > 0) "Set aside" else "Taken out",
                            subtitle = java.time.Instant.ofEpochMilli(entry.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(format),
                            trailing = {
                                Text(
                                    Money.format(entry.amount, sign = true),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (entry.amount > 0) Pop.Good else Pop.Text,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Delete goal",
                    style = MaterialTheme.typography.titleSmall,
                    color = Pop.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().press { confirmDelete = true }.padding(vertical = 14.dp),
                )
            }
        }
    }

    moving?.let { adding ->
        MoneySheet(vm, ledger, goal, adding, saved, onDismiss = { moving = null })
    }
    if (editing) {
        GoalSheet(
            goal = goal,
            onDismiss = { editing = false },
            onSave = { updated ->
                editing = false
                vm.write { it.saveGoal(updated) }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${goal.name}?") },
            text = { Text("The money stays in your savings account. It just stops being set aside for this goal.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    nav.back()
                    vm.write { it.deleteGoal(goal) }
                }) { Text("Delete", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** Adding either claims money already in savings, or moves it across from another account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySheet(
    vm: MainViewModel,
    ledger: Ledger,
    goal: Goal,
    adding: Boolean,
    saved: Long,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    val savingsAccount = ledger.activeAccounts.firstOrNull { it.role == AccountRole.SAVINGS && it.kind != AccountKind.CREDIT_CARD }
    val sources: List<Account?> = listOf<Account?>(null) +
        ledger.activeAccounts.filter { it.kind != AccountKind.CREDIT_CARD && it.role != AccountRole.SAVINGS }
    var source by remember { mutableStateOf<Account?>(null) }
    val parsed = Money.parse(amount) ?: 0
    val free = ledger.freeSavings()
    val problem = when {
        !adding && parsed > saved -> "There's only ${Money.whole(saved)} in this goal"
        adding && source == null && parsed > free -> "Only ${Money.whole(free)} of your savings isn't set aside yet"
        adding && source != null && savingsAccount == null -> "Add a savings account first"
        else -> null
    }

    fun save() {
        val now = System.currentTimeMillis()
        val from = source
        val transfer = if (adding && from != null && savingsAccount != null) {
            Txn(
                type = TxType.TRANSFER,
                amount = parsed,
                accountId = from.id,
                toAccountId = savingsAccount.id,
                method = PayMethod.NET_BANKING,
                note = goal.name,
                date = now,
            )
        } else {
            null
        }
        vm.write { it.addToGoal(goal.id, if (adding) parsed else -parsed, now, transfer) }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Gutter)) {
            Text(if (adding) "Add to ${goal.name}" else "Take from ${goal.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (adding) {
                    "Money stays in your savings account. Goals just say what it's for."
                } else {
                    "It goes back to your savings as money that isn't set aside. Your balance doesn't change."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Pop.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Group {
                GroupTextField(amount, { amount = it }, "₹0", label = "Amount", keyboardType = KeyboardType.Decimal)
            }
            if (adding) {
                Text(
                    "Coming from",
                    style = MaterialTheme.typography.labelMedium,
                    color = Pop.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                )
                ChipRow(sources, selected = sources.indexOf(source)) { account ->
                    if (account == null) {
                        Chip("Already in savings", source == null, { source = null })
                    } else {
                        Chip(
                            label = account.name,
                            icon = iconFor(account),
                            selected = source?.id == account.id,
                            selectedColor = Pop.accent(account.accent),
                            onClick = { source = account },
                        )
                    }
                }
            }
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodySmall, color = Pop.Warning, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save", ::save, enabled = parsed > 0 && problem == null)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(goal: Goal, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(goal.name) }
    var target by remember { mutableStateOf(goal.target.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    var day by remember { mutableStateOf(goal.targetDay) }
    var accent by remember { mutableIntStateOf(goal.accent) }
    var picking by remember { mutableStateOf(false) }
    val parsed = Money.parse(target) ?: 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text(if (goal.id == 0L) "New goal" else "Edit goal", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Group {
                GroupTextField(name, { name = it }, "Emergency fund", label = "Name", capitalization = KeyboardCapitalization.Words)
                RowDivider()
                GroupTextField(target, { target = it }, "₹0", label = "Target", keyboardType = KeyboardType.Decimal)
                RowDivider()
                GroupRow(
                    title = "Reach it by",
                    trailing = {
                        Text(
                            day?.let { LocalDate.ofEpochDay(it).format(MonthYear) } ?: "Optional",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Pop.TextSecondary,
                        )
                    },
                    onClick = { picking = true },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pop.Accents.forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .press { accent = index }
                            .then(if (accent == index) Modifier.border(2.dp, Pop.Text, CircleShape) else Modifier),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = name.isNotBlank() && parsed > 0,
                onClick = { onSave(goal.copy(name = name.trim(), target = parsed, targetDay = day, accent = accent)) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    if (picking) {
        DateDialog(
            initial = day?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusMonths(6),
            onPick = {
                day = it.toEpochDay()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
