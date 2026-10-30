package com.farhan.paisa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Tag
import com.farhan.paisa.domain.Money
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CategoryIcons
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
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop

@Composable
fun CategoriesScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    var editing by remember { mutableStateOf<Category?>(null) }
    val spending = ledger.categories.filter { !it.income && !it.archived }
    val income = ledger.categories.filter { it.income && !it.archived }

    FullScreen {
        NavHeader(
            title = "Categories",
            onBack = { nav.back() },
            trailing = {
                CircleButton(Icons.Rounded.Add, "Add category", { editing = Category(name = "", icon = "other") })
            },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item { CategoryGroup("Spending", spending) { editing = it } }
            item {
                Spacer(Modifier.height(24.dp))
                CategoryGroup("Income", income) { editing = it }
            }
        }
    }
    editing?.let { category ->
        CategorySheet(
            category = category,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                vm.write { it.saveCategory(saved) }
            },
            onDelete = {
                editing = null
                vm.write { it.deleteCategory(category) }
            },
        )
    }
}

@Composable
private fun CategoryGroup(title: String, categories: List<Category>, onEdit: (Category) -> Unit) {
    SectionLabel(title)
    Group {
        categories.forEachIndexed { index, category ->
            if (index > 0) RowDivider(start = 68.dp)
            GroupRow(
                title = category.name,
                subtitle = if (category.monthlyLimit > 0) "Limit ${Money.whole(category.monthlyLimit)} a month" else null,
                leading = { IconBadge(iconFor(category), Pop.categoryPop(category.color)) },
                onClick = { onEdit(category) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(category.name) }
    var icon by remember { mutableStateOf(category.icon) }
    var color by remember { mutableIntStateOf(category.color) }
    var limit by remember { mutableStateOf(category.monthlyLimit.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    var income by remember { mutableStateOf(category.income) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Text(if (category.id == 0L) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Group {
                GroupTextField(name, { name = it }, "Required", label = "Name", capitalization = KeyboardCapitalization.Sentences)
                if (!income) {
                    RowDivider()
                    GroupTextField(limit, { limit = it }, "Optional", label = "Monthly limit", keyboardType = KeyboardType.Decimal)
                }
            }
            if (category.id == 0L) {
                Spacer(Modifier.height(12.dp))
                com.farhan.paisa.ui.components.Segmented(
                    options = listOf(false to "Spending", true to "Income"),
                    selected = income,
                    onSelect = { income = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryIcons.forEach { (key, image) ->
                    val on = key == icon
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (on) Pop.categoryPop(color) else Pop.Surface)
                            .press { icon = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(image, key, tint = if (on) Pop.OnPop else Pop.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(-1) + Pop.Accents.indices).forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(Pop.categoryPop(index))
                            .press { color = index }
                            .then(if (color == index) Modifier.border(2.dp, Pop.Text, CircleShape) else Modifier),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(category.copy(name = name.trim(), icon = icon, color = color, income = income, monthlyLimit = if (income) 0 else Money.parse(limit) ?: 0))
                },
            )
            if (category.id != 0L) {
                TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Delete category", color = Pop.Danger)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TagsScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    var editing by remember { mutableStateOf<Tag?>(null) }
    FullScreen {
        NavHeader("Tags", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Group {
                    if (ledger.tags.isEmpty()) {
                        EmptyState("No tags yet", "Add one while logging a payment, such as #football.")
                    }
                    ledger.tags.forEachIndexed { index, tag ->
                        if (index > 0) RowDivider()
                        val uses = ledger.txnTags.count { it.tagId == tag.id }
                        GroupRow(
                            title = "#${tag.name}",
                            subtitle = "Used $uses ${if (uses == 1) "time" else "times"}",
                            onClick = { editing = tag },
                        )
                    }
                }
            }
        }
    }
    editing?.let { tag ->
        var name by remember(tag) { mutableStateOf(tag.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit tag") },
            text = {
                Group {
                    GroupTextField(name, { name = it.replace(" ", "").replace("#", "") }, "Name", capitalization = KeyboardCapitalization.None)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        editing = null
                        vm.write { it.renameTag(tag.copy(name = name.trim())) }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editing = null
                    vm.write { it.deleteTag(tag) }
                }) { Text("Delete", color = Pop.Danger) }
            },
        )
    }
}
