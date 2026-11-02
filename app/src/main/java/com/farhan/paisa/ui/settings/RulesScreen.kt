package com.farhan.paisa.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayeeRule
import com.farhan.paisa.domain.recentPayees
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.theme.Pop

@Composable
fun RulesScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    var editing by remember { mutableStateOf<PayeeRule?>(null) }
    FullScreen {
        NavHeader(
            title = "Payee names",
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Add, "Add rule", { editing = PayeeRule(phrase = "", name = "") }) },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text(
                    "Banks use whatever name a payment comes with. Rules show it under a name of your own, so your lists stay readable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pop.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                )
                Group {
                    if (ledger.payeeRules.isEmpty()) {
                        EmptyState("No rules yet", "Add one here, or from any payment with a name you'd like to change.")
                    }
                    ledger.payeeRules.forEachIndexed { index, rule ->
                        if (index > 0) RowDivider()
                        GroupRow(title = rule.name, subtitle = "Anything with \"${rule.phrase}\"", onClick = { editing = rule })
                    }
                }
            }
        }
    }
    editing?.let { rule ->
        PayeeRuleSheet(vm, ledger, rule, onDismiss = { editing = null })
    }
}

/** Creates or edits a rule. Used from Settings and from a payment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayeeRuleSheet(vm: MainViewModel, ledger: Ledger, rule: PayeeRule, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var phrase by remember { mutableStateOf(rule.phrase) }
    var name by remember { mutableStateOf(rule.name) }
    val valid = phrase.count(Char::isLetterOrDigit) >= 3 && name.isNotBlank()
    val names = remember(ledger) { ledger.recentPayees().filterNot { it.equals(rule.phrase, ignoreCase = true) }.take(8) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text(if (rule.id == 0L) "New rule" else "Edit rule", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Group {
                GroupTextField(phrase, { phrase = it }, "Part of the name", label = "When it says", capitalization = KeyboardCapitalization.Words)
                RowDivider()
                GroupTextField(name, { name = it }, "Cab", label = "Show as", capitalization = KeyboardCapitalization.Words)
            }
            if (names.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ChipRow(names) { payee -> Chip(label = payee, selected = name == payee, onClick = { name = payee }) }
            }
            Text(
                "This also renames payments you've already logged.",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = valid,
                onClick = {
                    onDismiss()
                    vm.write { repo ->
                        val renamed = repo.savePayeeRule(rule.copy(phrase = phrase.trim(), name = name.trim()))
                        if (renamed > 0) {
                            val entries = if (renamed == 1) "1 payment" else "$renamed payments"
                            Toast.makeText(context, "Renamed $entries", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            if (rule.id != 0L) {
                TextButton(
                    onClick = {
                        onDismiss()
                        vm.write { it.deletePayeeRule(rule) }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Delete rule", color = Pop.Danger) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
