package com.farhan.paisa.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.LimitKind
import com.farhan.paisa.domain.LimitStatus
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.byCategory
import com.farhan.paisa.domain.byTag
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.elapsed
import com.farhan.paisa.domain.limitStatuses
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.CategoryIcons
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.Meter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.Segmented
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.components.rememberToday
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One limit that's being added or changed. An id of 0 means nothing is picked yet. */
private data class LimitEdit(val kind: LimitKind, val id: Long, val limit: Long, val isNew: Boolean)

private val ShortDate = DateTimeFormatter.ofPattern("d MMM")

fun limitColor(status: LimitStatus) = when {
    status.left < 0 -> Pop.Danger
    status.percent >= 80 -> Pop.Warning
    else -> Pop.Lime
}

@Composable
fun LimitsScreen(vm: MainViewModel, ledger: Ledger, settings: Settings, nav: Nav) {
    val today = rememberToday()
    val cycles = remember(ledger, settings) { ledger.cycles(settings) }
    val period = cycles.around(today)
    val statuses = remember(ledger, period) { ledger.limitStatuses(period) }
    var editing by remember { mutableStateOf<LimitEdit?>(null) }

    FullScreen {
        NavHeader(
            title = "Limits",
            onBack = { nav.back() },
            trailing = {
                CircleButton(Icons.Rounded.Add, "Add limit", { editing = LimitEdit(LimitKind.CATEGORY, 0, 0, true) })
            },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text(
                    "For ${period.label}. Your overall limit covers everything, while these catch one kind of spending running away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pop.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                )
                Group {
                    if (statuses.isEmpty()) {
                        EmptyState("No limits yet", "Tap the plus button to set one for a category or a tag, like food or #cab.")
                    }
                    statuses.forEachIndexed { index, status ->
                        if (index > 0) RowDivider()
                        LimitRow(status, period, today) { editing = LimitEdit(status.kind, status.id, status.limit, false) }
                    }
                }
            }
        }
    }

    editing?.let { edit ->
        LimitSheet(ledger, cycles.before(period), edit, onDismiss = { editing = null }) { kind, id, amount ->
            editing = null
            vm.write { repo ->
                when (kind) {
                    LimitKind.CATEGORY -> ledger.categoryById[id]?.let { repo.saveCategory(it.copy(monthlyLimit = amount)) }
                    LimitKind.TAG -> ledger.tagById[id]?.let { repo.renameTag(it.copy(monthlyLimit = amount)) }
                }
            }
        }
    }
}

/** Shared with Home, so a limit reads the same everywhere. */
@Composable
fun LimitRow(status: LimitStatus, period: Period, today: LocalDate, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .press(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                "${Money.whole(status.spent)} of ${Money.whole(status.limit)}",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextSecondary,
            )
        }
        Meter(
            value = status.used,
            color = limitColor(status),
            height = 6.dp,
            marker = period.elapsed(today).toFloat() / period.days,
        )
        Text(
            when {
                status.left < 0 -> "${Money.whole(-status.left)} over"
                else -> "${Money.whole(status.left)} left until ${period.end.format(ShortDate)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.left < 0) Pop.Danger else Pop.TextSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(
    ledger: Ledger,
    previous: Period,
    edit: LimitEdit,
    onDismiss: () -> Unit,
    onSave: (LimitKind, Long, Long) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(edit.kind) }
    var id by remember { mutableStateOf(edit.id) }
    var amount by remember { mutableStateOf(edit.limit.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    val categories = ledger.categories.filter { !it.income && !it.archived && (it.monthlyLimit == 0L || it.id == edit.id) }
    val tags = ledger.tags.filter { it.monthlyLimit == 0L || it.id == edit.id }
    val lastMonth = remember(ledger, previous, kind, id) {
        when (kind) {
            LimitKind.CATEGORY -> ledger.byCategory(previous).firstOrNull { it.key == id }?.amount
            LimitKind.TAG -> ledger.byTag(previous).firstOrNull { it.key == id }?.amount
        }
    }
    val title = when (edit.kind) {
        LimitKind.CATEGORY -> ledger.categoryById[edit.id]?.name
        LimitKind.TAG -> ledger.tagById[edit.id]?.let { "#${it.name}" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text(if (edit.isNew) "New limit" else title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (edit.isNew) {
                Segmented(
                    options = listOf(LimitKind.CATEGORY to "Category", LimitKind.TAG to "Tag"),
                    selected = kind,
                    onSelect = {
                        kind = it
                        id = 0
                    },
                )
                Spacer(Modifier.height(12.dp))
                if (kind == LimitKind.CATEGORY) {
                    ChipRow(categories, selected = categories.indexOfFirst { it.id == id }) { category ->
                        Chip(
                            label = category.name,
                            icon = CategoryIcons[category.icon],
                            selected = id == category.id,
                            selectedColor = Pop.categoryPop(category.color),
                            onClick = { id = category.id },
                        )
                    }
                } else if (tags.isEmpty()) {
                    Text(
                        "Every tag already has a limit, or you haven't added one yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pop.TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    ChipRow(tags, selected = tags.indexOfFirst { it.id == id }) { tag ->
                        Chip(label = "#${tag.name}", selected = id == tag.id, selectedColor = Pop.Lavender, onClick = { id = tag.id })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Group {
                GroupTextField(amount, { amount = it }, "₹0", label = "Each month", keyboardType = KeyboardType.Decimal)
            }
            if (lastMonth != null) {
                Text(
                    "You spent ${Money.whole(lastMonth)} on this last month",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = id != 0L && (Money.parse(amount) ?: 0) > 0,
                onClick = { onSave(kind, id, Money.parse(amount) ?: 0) },
            )
            if (!edit.isNew) {
                TextButton(onClick = { onSave(kind, id, 0) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Remove limit", color = Pop.Danger)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
