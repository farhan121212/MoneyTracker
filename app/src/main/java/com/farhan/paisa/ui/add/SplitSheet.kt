package com.farhan.paisa.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.domain.Money
import com.farhan.paisa.ui.components.Avatar
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.InlineField
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.Segmented
import com.farhan.paisa.ui.theme.Pop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitSheet(vm: MainViewModel, ledger: Ledger, draft: Draft, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }

    fun addPerson() {
        val name = newName.trim()
        if (name.isEmpty()) return
        newName = ""
        val existing = ledger.people.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.id !in draft.splitPeople) draft.splitPeople.add(existing.id)
        } else {
            scope.launch { draft.splitPeople.add(vm.repo.addPerson(name)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text("Split ${Money.format(draft.amount)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick who shares this. The app counts only your part as spending and tracks what the others owe you.",
                style = MaterialTheme.typography.bodyMedium,
                color = Pop.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ledger.people.forEach { person ->
                    val on = person.id in draft.splitPeople
                    Chip(
                        label = person.name,
                        selected = on,
                        onClick = { if (on) draft.splitPeople.remove(person.id) else draft.splitPeople.add(person.id) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Group {
                Row(
                    Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "Add a person",
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = ::addPerson, enabled = newName.isNotBlank()) { Text("Add") }
                }
            }

            if (draft.splitPeople.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Segmented(
                    options = listOf(true to "Equally", false to "Custom"),
                    selected = draft.splitEqual,
                    onSelect = { equal ->
                        if (!equal) {
                            draft.shares().forEach { (person, amount) ->
                                draft.customShares[person] = Money.editable(amount)
                            }
                        }
                        draft.splitEqual = equal
                    },
                )
                Spacer(Modifier.height(12.dp))
                Group {
                    if (draft.splitEqual) {
                        GroupRow(
                            title = "Include me",
                            subtitle = "Turn off if you paid entirely for others",
                            trailing = {
                                Switch(
                                    checked = draft.includeMe,
                                    onCheckedChange = { draft.includeMe = it },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Pop.Lime,
                                        checkedThumbColor = Pop.OnPop,
                                    ),
                                )
                            },
                        )
                        RowDivider()
                    }
                    val shares = draft.shares().toMap()
                    draft.splitPeople.forEachIndexed { index, id ->
                        if (index > 0) RowDivider(start = 60.dp)
                        val name = ledger.personById[id]?.name.orEmpty()
                        GroupRow(
                            title = name,
                            leading = { Avatar(name, id, size = 32.dp) },
                            trailing = {
                                if (draft.splitEqual) {
                                    Text(Money.format(shares[id] ?: 0), style = MaterialTheme.typography.titleSmall)
                                } else {
                                    InlineField(
                                        value = draft.customShares[id].orEmpty(),
                                        onValueChange = { draft.customShares[id] = it },
                                        placeholder = "0",
                                        keyboardType = KeyboardType.Decimal,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(96.dp),
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val mine = draft.myShare
                Row(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        if (mine < 0) "Shares are more than the bill" else "Your share",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mine < 0) Pop.Danger else Pop.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Money.format(mine), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done", onDismiss, enabled = draft.myShare >= 0)
            Spacer(Modifier.height(12.dp))
        }
    }
}
