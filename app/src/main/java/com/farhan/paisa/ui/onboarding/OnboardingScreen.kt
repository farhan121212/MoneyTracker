package com.farhan.paisa.ui.onboarding

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Exports
import com.farhan.paisa.data.loadSampleData
import com.farhan.paisa.domain.Money
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var dailyName by remember { mutableStateOf("Daily expenses") }
    var dailyBalance by remember { mutableStateOf("") }
    var savingsName by remember { mutableStateOf("Savings") }
    var savingsBalance by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf("") }
    var cardOwed by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { Exports.readBackup(context, uri) }
                    .onSuccess { backup -> vm.write { it.restore(backup) } }
                    .onFailure { Toast.makeText(context, "That isn't a Paisa backup file", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.write { repo ->
            repo.insertDefaultCategories()
            repo.saveAccount(Account(name = dailyName.trim().ifBlank { "Daily expenses" }, kind = AccountKind.BANK, opening = Money.parse(dailyBalance) ?: 0, accent = 0))
            repo.saveAccount(
                Account(
                    name = savingsName.trim().ifBlank { "Savings" },
                    kind = AccountKind.BANK,
                    role = AccountRole.SAVINGS,
                    opening = Money.parse(savingsBalance) ?: 0,
                    accent = 1,
                    sortOrder = 1,
                ),
            )
            if (cardName.isNotBlank()) {
                repo.saveAccount(
                    Account(
                        name = cardName.trim(),
                        kind = AccountKind.CREDIT_CARD,
                        opening = Money.parse(cardOwed) ?: 0,
                        accent = 2,
                        sortOrder = 2,
                    ),
                )
            }
            repo.settings.update {
                it.copy(
                    name = name.trim(),
                    onboarded = true,
                    monthlyBudget = Money.parse(budget) ?: 0,
                    savingsTarget = Money.parse(target) ?: 0,
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pop.Ink)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Gutter),
    ) {
        Row(
            Modifier.padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index <= step) Pop.Lime else Pop.Raised),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Shapes(Modifier.fillMaxWidth().height(240.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Know where every rupee goes.", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Log payments in seconds, see where your money went, and keep more of it. Everything stays on your phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Pop.TextSecondary,
                    )
                    Spacer(Modifier.height(20.dp))
                    Group {
                        GroupTextField(name, { name = it }, "What should we call you?", capitalization = KeyboardCapitalization.Words)
                    }
                }
                1 -> {
                    Text("Your accounts", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One for daily spending, one that stays saved. Add what's in them right now.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Pop.TextSecondary,
                    )
                    Heading("Daily spending")
                    Group {
                        GroupTextField(dailyName, { dailyName = it }, "Account name", label = "Name", capitalization = KeyboardCapitalization.Words)
                        RowDivider()
                        GroupTextField(dailyBalance, { dailyBalance = it }, "₹0", label = "Balance", keyboardType = KeyboardType.Decimal)
                    }
                    Heading("Savings")
                    Group {
                        GroupTextField(savingsName, { savingsName = it }, "Account name", label = "Name", capitalization = KeyboardCapitalization.Words)
                        RowDivider()
                        GroupTextField(savingsBalance, { savingsBalance = it }, "₹0", label = "Balance", keyboardType = KeyboardType.Decimal)
                    }
                    Heading("Credit card, optional")
                    Group {
                        GroupTextField(cardName, { cardName = it }, "Card name", label = "Name", capitalization = KeyboardCapitalization.Words)
                        RowDivider()
                        GroupTextField(cardOwed, { cardOwed = it }, "₹0", label = "Owed now", keyboardType = KeyboardType.Decimal)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can add more cards and accounts from the Wallet tab, with due dates for reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pop.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                else -> {
                    Text("Your monthly goals", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Home shows what's left to spend each day, so overspending shows up early. You can change these any time.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Pop.TextSecondary,
                    )
                    Spacer(Modifier.height(20.dp))
                    Group {
                        GroupTextField(budget, { budget = it }, "₹0", label = "Spending limit", keyboardType = KeyboardType.Decimal)
                        RowDivider()
                        GroupTextField(target, { target = it }, "₹0", label = "Savings goal", keyboardType = KeyboardType.Decimal)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            text = if (step < 2) "Continue" else "Start",
            onClick = { if (step < 2) step += 1 else finish() },
            modifier = Modifier.padding(top = 8.dp),
        )
        if (step == 0) {
            Row {
                Text(
                    "Try sample data",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pop.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .press { vm.write { it.loadSampleData() } }
                        .padding(vertical = 16.dp),
                )
                Text(
                    "Restore a backup",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pop.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .press { openBackup.launch(arrayOf("application/json", "application/octet-stream")) }
                        .padding(vertical = 16.dp),
                )
            }
        } else {
            Text(
                "Back",
                style = MaterialTheme.typography.labelLarge,
                color = Pop.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .press { step -= 1 }
                    .padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pop.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** The poppy block composition from the welcome screen. */
@Composable
private fun Shapes(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.height / 2
        drawCircle(Pop.Lime, unit * 0.86f, Offset(unit * 0.9f, unit * 0.9f))
        drawRoundRect(
            Pop.Lavender,
            Offset(size.width - unit * 1.5f, 0f),
            Size(unit * 1.5f, unit),
            androidx.compose.ui.geometry.CornerRadius(unit * 0.28f),
        )
        rotate(18f, Offset(size.width * 0.54f, unit * 1.45f)) {
            drawRoundRect(
                Pop.Tangerine,
                Offset(size.width * 0.34f, unit * 1.1f),
                Size(unit * 1.1f, unit * 0.74f),
                androidx.compose.ui.geometry.CornerRadius(unit * 0.24f),
            )
        }
        drawCircle(Pop.Pink, unit * 0.34f, Offset(size.width - unit * 0.44f, unit * 1.56f))
    }
}
