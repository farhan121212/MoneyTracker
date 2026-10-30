package com.farhan.paisa.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Person
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Keypad
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.owedBy
import com.farhan.paisa.domain.quickRepeats
import com.farhan.paisa.domain.recentPayees
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CategoryIcons
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.InlineField
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.Segmented
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AddScreen(
    vm: MainViewModel,
    ledger: Ledger,
    nav: Nav,
    type: TxType,
    editId: Long,
    from: Long,
    to: Long,
    amount: Long,
    detectedId: Long,
    billId: Long,
) {
    val draft = remember { Draft.create(ledger, type, editId, from, to, amount, detectedId, billId) }
    val startOnDetails = editId != 0L || amount > 0 || detectedId != 0L || billId != 0L
    var onDetails by rememberSaveable { mutableStateOf(startOnDetails) }
    val haptics = LocalHapticFeedback.current

    AnimatedContent(
        targetState = onDetails,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "addStep",
    ) { details ->
        if (details) {
            DetailsStep(
                vm = vm,
                ledger = ledger,
                draft = draft,
                onBack = { if (startOnDetails) nav.back() else onDetails = false },
                onEditAmount = { onDetails = false },
                onSave = {
                    val txn = draft.toTxn()
                    val tags = draft.tagIds.toList()
                    val shares = if (draft.type == TxType.EXPENSE) draft.shares() else emptyList()
                    val detected = draft.detected
                    vm.write { repo ->
                        if (detected != null) {
                            repo.confirmDetected(detected, txn, tags, shares)
                        } else {
                            repo.saveTxn(txn, tags, shares)
                        }
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    nav.back()
                },
            )
        } else {
            AmountStep(
                ledger = ledger,
                draft = draft,
                onClose = { nav.back() },
                onNext = { onDetails = true },
            )
        }
    }
}

@Composable
private fun AmountStep(ledger: Ledger, draft: Draft, onClose: () -> Unit, onNext: () -> Unit) {
    val repeats = remember { ledger.quickRepeats(LocalDate.now()) }
    FullScreen {
        NavHeader(if (draft.editing != null) "Edit amount" else "New", onClose, icon = Icons.Rounded.Close)
        if (draft.editing == null) {
            Segmented(
                options = listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income", TxType.TRANSFER to "Move"),
                selected = draft.type,
                onSelect = { draft.type = it },
                modifier = Modifier.padding(horizontal = Gutter),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Gutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val empty = draft.amountText.isEmpty()
            Text(
                "₹${Keypad.display(draft.amountText)}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 62.sp),
                color = if (empty) Pop.TextMuted else Pop.Text,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        if (draft.type == TxType.EXPENSE && repeats.isNotEmpty() && draft.editing == null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(repeats, key = { it.latest.id }) { repeat ->
                    Chip(
                        label = "${repeat.latest.payee} ${Money.format(repeat.latest.amount)}",
                        selected = false,
                        icon = Icons.Rounded.Repeat,
                        onClick = {
                            draft.applyRepeat(repeat.latest)
                            onNext()
                        },
                    )
                }
            }
        }
        Keys(onKey = { draft.amountText = Keypad.press(draft.amountText, it) })
        PrimaryButton(
            text = "Continue",
            onClick = onNext,
            enabled = draft.amount > 0,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
        )
    }
}

@Composable
private fun Keys(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", Keypad.BACKSPACE),
    )
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.95f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Pop.Surface)
                            .press { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == Keypad.BACKSPACE) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = Pop.Text, modifier = Modifier.size(22.dp))
                        } else {
                            Text(key, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(
    vm: MainViewModel,
    ledger: Ledger,
    draft: Draft,
    onBack: () -> Unit,
    onEditAmount: () -> Unit,
    onSave: () -> Unit,
) {
    var splitOpen by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val accounts = ledger.activeAccounts
    val verb = if (draft.editing != null) "Edit" else "New"
    val title = when (draft.type) {
        TxType.EXPENSE -> "$verb expense"
        TxType.INCOME -> "$verb income"
        TxType.TRANSFER -> if (ledger.accountById[draft.toAccountId]?.kind == AccountKind.CREDIT_CARD) "Pay card bill" else "Move money"
        TxType.SETTLEMENT -> "Edit payment"
    }

    FullScreen {
        NavHeader(title, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Text(
                Money.format(draft.amount),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .press(onClick = onEditAmount)
                    .padding(top = 4.dp, bottom = 16.dp),
            )

            when (draft.type) {
                TxType.EXPENSE -> {
                    PayeeField("Paid to", "Who or where", ledger, draft)
                    CategoryPicker(ledger, draft)
                    AccountSection("Paid from", accounts, draft.accountId, draft::selectAccount)
                    DigitsHint(draft.detected?.last4, draft.accountId)
                    SavingsWarning(ledger, draft)
                    MethodPicker(ledger, draft)
                    Label("Tags")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ledger.tags.forEach { tag ->
                            val on = tag.id in draft.tagIds
                            Chip(
                                label = "#${tag.name}",
                                selected = on,
                                selectedColor = Pop.Lavender,
                                onClick = { if (on) draft.tagIds.remove(tag.id) else draft.tagIds.add(tag.id) },
                            )
                        }
                        Chip("New tag", selected = false, onClick = { newTag = "" })
                    }
                    newTag?.let { text ->
                        Spacer(Modifier.height(8.dp))
                        Group {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InlineField(
                                    value = text,
                                    onValueChange = { newTag = it.replace(" ", "").replace("#", "") },
                                    placeholder = "football",
                                    capitalization = KeyboardCapitalization.None,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        val name = text.trim()
                                        if (name.isNotEmpty()) {
                                            scope.launch {
                                                val existing = ledger.tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                                                draft.tagIds.add(existing?.id ?: vm.repo.addTag(name))
                                            }
                                        }
                                        newTag = null
                                    },
                                ) { Text("Add") }
                            }
                        }
                    }
                    Label("Split")
                    Group {
                        GroupRow(
                            title = if (draft.splitPeople.isEmpty()) {
                                "Split this bill"
                            } else {
                                draft.splitPeople.mapNotNull { ledger.personById[it]?.name }.joinToString(", ")
                            },
                            subtitle = if (draft.splitPeople.isEmpty()) {
                                "Only your share counts as spending"
                            } else {
                                "Your share ${Money.format(draft.myShare)}"
                            },
                            onClick = { splitOpen = true },
                            trailing = {
                                Text(
                                    if (draft.splitPeople.isEmpty()) "Add" else "Edit",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Pop.Lime,
                                )
                            },
                        )
                    }
                }
                TxType.INCOME -> {
                    PayeeField("From", "Who paid you", ledger, draft)
                    RepayerPicker(ledger, draft)
                    if (draft.repayerId == null) CategoryPicker(ledger, draft)
                    AccountSection("Into", accounts, draft.accountId, draft::selectAccount)
                    DigitsHint(draft.detected?.last4, draft.accountId)
                }
                TxType.TRANSFER -> {
                    AccountSection("From", accounts.filter { it.kind != AccountKind.CREDIT_CARD }, draft.accountId, draft::selectAccount)
                    DigitsHint(draft.detected?.last4, draft.accountId)
                    AccountSection("To", accounts.filter { it.id != draft.accountId }, draft.toAccountId) { draft.toAccountId = it }
                    DigitsHint(draft.detected?.toLast4, draft.toAccountId)
                    MethodPicker(ledger, draft)
                }
                TxType.SETTLEMENT -> {
                    AccountSection("Into", accounts.filter { it.kind != AccountKind.CREDIT_CARD }, draft.accountId, draft::selectAccount)
                }
            }

            Label("Details")
            Group {
                GroupRow(
                    title = "Date",
                    trailing = {
                        Text(
                            draft.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Pop.TextSecondary,
                        )
                    },
                    onClick = { pickingDate = true },
                )
                RowDivider()
                GroupTextField(draft.note, { draft.note = it }, "Add a note")
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            text = "Save",
            onClick = onSave,
            enabled = draft.valid,
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Gutter, vertical = 12.dp),
        )
    }

    if (splitOpen) {
        SplitSheet(vm, ledger, draft, onDismiss = { splitOpen = false })
    }
    if (pickingDate) {
        DateDialog(draft.date, onPick = { draft.date = it; pickingDate = false }, onDismiss = { pickingDate = false })
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pop.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun PayeeField(label: String, placeholder: String, ledger: Ledger, draft: Draft) {
    val known = remember { ledger.recentPayees() }
    val typed = draft.payee.trim()
    val matches = known
        .filter { typed.isEmpty() || (it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true)) }
        .take(8)
    Group {
        GroupTextField(
            value = draft.payee,
            onValueChange = { text ->
                draft.payee = text
                if (known.any { it.equals(text.trim(), ignoreCase = true) }) draft.applyPayee(text)
            },
            placeholder = placeholder,
            label = label,
            capitalization = KeyboardCapitalization.Words,
        )
    }
    if (matches.isNotEmpty()) {
        ChipRow(matches, modifier = Modifier.padding(top = 8.dp)) { name ->
            Chip(name, selected = false, onClick = { draft.applyPayee(name) })
        }
    }
}

@Composable
private fun CategoryPicker(ledger: Ledger, draft: Draft) {
    val income = draft.type == TxType.INCOME
    val categories = ledger.categories.filter { it.income == income && !it.archived }
    Label("Category")
    ChipRow(categories, selected = categories.indexOfFirst { it.id == draft.categoryId }) { category ->
        Chip(
            label = category.name,
            icon = CategoryIcons[category.icon],
            selected = draft.categoryId == category.id,
            selectedColor = Pop.categoryPop(category.color),
            onClick = { draft.categoryId = category.id },
        )
    }
}

@Composable
private fun AccountSection(label: String, accounts: List<Account>, selected: Long?, onSelect: (Long) -> Unit) {
    Label(label)
    ChipRow(accounts, selected = accounts.indexOfFirst { it.id == selected }) { account ->
        Chip(
            label = account.name,
            icon = iconFor(account),
            selected = selected == account.id,
            selectedColor = Pop.accent(account.accent),
            onClick = { onSelect(account.id) },
        )
    }
}

@Composable
private fun MethodPicker(ledger: Ledger, draft: Draft) {
    val kind = ledger.accountById[draft.accountId]?.kind
    val methods = when (kind) {
        AccountKind.CREDIT_CARD -> listOf(PayMethod.CARD, PayMethod.UPI, PayMethod.AUTO_DEBIT)
        AccountKind.CASH, null -> return
        AccountKind.BANK -> listOf(PayMethod.UPI, PayMethod.CARD, PayMethod.NET_BANKING, PayMethod.AUTO_DEBIT)
    }
    Label("Paid by")
    ChipRow(methods, selected = methods.indexOf(draft.method)) { method ->
        Chip(
            label = if (kind == AccountKind.BANK && method == PayMethod.CARD) "Debit card" else method.label,
            selected = draft.method == method,
            onClick = { draft.method = method },
        )
    }
}

/** Someone paying you back isn't income, so it clears what they owe instead. */
@Composable
private fun RepayerPicker(ledger: Ledger, draft: Draft) {
    val owing = ledger.people.filter { ledger.owedBy(it.id) > 0 }
    if (owing.isEmpty()) return
    Label("Is someone paying you back?")
    ChipRow(listOf<Person?>(null) + owing, selected = owing.indexOfFirst { it.id == draft.repayerId } + 1) { person ->
        if (person == null) {
            Chip("No", draft.repayerId == null, { draft.repayerId = null })
        } else {
            Chip(
                label = "${person.name} ${Money.format(ledger.owedBy(person.id))}",
                selected = draft.repayerId == person.id,
                selectedColor = Pop.Tangerine,
                onClick = { draft.repayerId = person.id },
            )
        }
    }
}

/** Shown until you pick the account for digits the app hasn't seen before. */
@Composable
private fun DigitsHint(digits: String?, selected: Long?) {
    if (digits.isNullOrBlank() || selected != null) return
    Text(
        "Which account ends in $digits? The app will remember.",
        style = MaterialTheme.typography.bodySmall,
        color = Pop.Warning,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** Your savings account is meant to stay untouched, so a nudge appears before spending from it. */
@Composable
private fun SavingsWarning(ledger: Ledger, draft: Draft) {
    if (ledger.accountById[draft.accountId]?.role != AccountRole.SAVINGS) return
    Row(
        Modifier.padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Pop.Warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("This comes out of your savings", style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
