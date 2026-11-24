package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.ProductEntity
import com.example.data.local.ProductSubCategoryEntity
import com.example.data.local.ProductWithDetails
import com.example.data.local.WorkStepEntity
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter

data class EditableWorkStep(
    val key: Int,
    val name: String,
    val rateText: String,
    val isDefaultSelected: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditorDialog(
    partyId: Long,
    existingProduct: ProductWithDetails? = null,
    onSave: (ProductEntity, List<ProductSubCategoryEntity>, List<WorkStepEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var name by remember { mutableStateOf(existingProduct?.product?.name ?: "") }
    var baseRateText by remember {
        mutableStateOf(
            existingProduct?.product?.baseRate?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var unit by remember { mutableStateOf(existingProduct?.product?.defaultUnit ?: "pcs") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var nextKey by remember { mutableIntStateOf(100) }

    val workSteps = remember {
        mutableStateListOf<EditableWorkStep>().apply {
            existingProduct?.workSteps?.forEachIndexed { idx, ws ->
                add(
                    EditableWorkStep(
                        key = idx + 1,
                        name = ws.name,
                        rateText = if (ws.stepRate % 1.0 == 0.0) {
                            ws.stepRate.toLong().toString()
                        } else {
                            ws.stepRate.toString()
                        },
                        isDefaultSelected = ws.isDefaultSelected
                    )
                )
            }
        }
    }

    val baseRateVal = baseRateText.toDoubleOrNull() ?: 0.0
    val stepsSum = workSteps
        .filter { it.name.isNotBlank() }
        .sumOf { it.rateText.toDoubleOrNull() ?: 0.0 }
    val fullRate = baseRateVal + stepsSum

    SoftDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (existingProduct == null) "New Item" else "Edit Item",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("Item Name") },
                placeholder = { Text("e.g. New Model") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = baseRateText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            baseRateText = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Base Rate (₹)") },
                    placeholder = { Text("2.50") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("product_base_rate_input")
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("product_unit_input")
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pcs", "kg", "box", "set").forEach { opt ->
                    FilterChip(
                        selected = unit.equals(opt, ignoreCase = true),
                        onClick = { unit = opt },
                        label = { Text(opt) }
                    )
                }
            }

            HorizontalDivider(color = ledgerColors.borderSubtle)

            Text(
                text = "Optional Work Steps (e.g. Phase, Cut)",
                style = MaterialTheme.typography.titleSmall
            )

            workSteps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = step.name,
                        onValueChange = { v -> workSteps[index] = step.copy(name = v) },
                        label = { Text("Step") },
                        placeholder = { Text("Cut") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = step.rateText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                workSteps[index] = step.copy(rateText = input)
                            }
                        },
                        label = { Text("+₹") },
                        placeholder = { Text("2.00") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.8f)
                    )
                    IconButton(onClick = { workSteps.removeAt(index) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove step",
                            tint = ledgerColors.dangerRed
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    nextKey++
                    workSteps.add(EditableWorkStep(key = nextKey, name = "", rateText = "", isDefaultSelected = true))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_work_step_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Work Step")
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (stepsSum > 0) "Rate with all steps" else "Unit Rate",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${CurrencyFormatter.formatRate(fullRate)} / ${unit.ifBlank { "pcs" }}",
                    style = TabularAmountStyle.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = errorMessage!!,
                color = ledgerColors.dangerRed,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Please enter an item name"
                        return@Button
                    }
                    val parsedBase = baseRateText.toDoubleOrNull() ?: 0.0
                    val validSteps = workSteps
                        .filter { it.name.trim().isNotEmpty() }
                        .map {
                            WorkStepEntity(
                                productId = existingProduct?.product?.id ?: 0L,
                                name = it.name.trim(),
                                stepRate = it.rateText.toDoubleOrNull() ?: 0.0,
                                isDefaultSelected = true
                            )
                        }
                    val product = ProductEntity(
                        id = existingProduct?.product?.id ?: 0L,
                        partyId = partyId,
                        name = trimmedName,
                        baseRate = parsedBase,
                        defaultUnit = unit.trim().ifEmpty { "pcs" },
                        createdAt = existingProduct?.product?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(product, emptyList(), validSteps)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text(if (existingProduct == null) "Save Item" else "Update")
            }
        }
    }
}
