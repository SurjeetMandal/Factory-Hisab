package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.PartyEntity
import com.example.ui.theme.LocalLedgerColors
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.delay

@Composable
fun PartyEditorDialog(
    existingParty: PartyEntity? = null,
    onSave: (PartyEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    var name by remember { mutableStateOf(existingParty?.name ?: "") }
    var phone by remember { mutableStateOf(existingParty?.phone ?: "") }
    var openingBalanceText by remember {
        mutableStateOf(
            existingParty?.openingBalance?.let {
                if (it == 0.0) "" else if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var notes by remember { mutableStateOf(existingParty?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    SoftDialog(onDismiss = onDismiss) {
        Text(
            text = if (existingParty == null) "Add New Party" else "Edit Party",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMsg = null
                },
                label = { Text("Party / Company Name") },
                placeholder = { Text("e.g. Lucky Pvt. Limited") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("party_name_input")
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone / WhatsApp (Optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("party_phone_input")
            )

            OutlinedTextField(
                value = openingBalanceText,
                onValueChange = { input ->
                    if (input.isEmpty() || input == "-" || input.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
                        openingBalanceText = input
                    }
                },
                label = { Text("Opening Balance ₹ (Optional)") },
                placeholder = { Text("0") },
                supportingText = { Text("Old pending balance before using this app") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("party_opening_balance_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                maxLines = 2,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = ledgerColors.dangerRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        haptics.reject()
                        errorMsg = "Party name is required"
                        return@Button
                    }
                    val opening = openingBalanceText.toDoubleOrNull() ?: 0.0
                    haptics.confirm()
                    onSave(
                        PartyEntity(
                            id = existingParty?.id ?: 0L,
                            name = trimmed,
                            phone = phone.trim(),
                            openingBalance = opening,
                            notes = notes.trim(),
                            createdAt = existingParty?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("save_party_button")
            ) {
                Text(if (existingParty == null) "Save Party" else "Update")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickPaymentDialog(
    partyName: String,
    currentPendingBalance: Double,
    onSavePayment: (amount: Double, mode: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    var amountText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("Cash") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var paymentAddedFeedback by remember { mutableStateOf(false) }

    val modes = listOf("Cash", "UPI", "Bank", "Cheque")

    LaunchedEffect(paymentAddedFeedback) {
        if (paymentAddedFeedback) {
            delay(550)
            val amt = amountText.toDoubleOrNull() ?: return@LaunchedEffect
            onSavePayment(amt, selectedMode, note.trim())
        }
    }

    SoftDialog(onDismiss = onDismiss) {
        Text("Record Payment", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$partyName • Balance: ${CurrencyFormatter.formatRupees(currentPendingBalance)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountText = input
                        errorMsg = null
                    }
                },
                label = { Text("Payment Amount (₹)") },
                placeholder = { Text("25000") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialog_payment_amount_input")
            )

            Text("Payment Mode", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            haptics.tick()
                            selectedMode = mode
                        },
                        label = { Text(mode) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note / Reference (Optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            )

            val entered = amountText.toDoubleOrNull() ?: 0.0
            if (entered > 0) {
                Surface(
                    color = ledgerColors.settledContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "New Balance After Payment",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledgerColors.settledGreen
                        )
                        Text(
                            CurrencyFormatter.formatRupees(currentPendingBalance - entered),
                            style = MaterialTheme.typography.labelLarge,
                            color = ledgerColors.settledGreen
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = ledgerColors.dangerRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            val btnColor by animateColorAsState(
                targetValue = if (paymentAddedFeedback) Color(0xFF1B5E20) else ledgerColors.settledGreen,
                animationSpec = tween(250),
                label = "dialog_pay_btn_color"
            )
            Button(
                onClick = {
                    if (paymentAddedFeedback) return@Button
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        haptics.reject()
                        errorMsg = "Enter a valid payment amount"
                        return@Button
                    }
                    haptics.confirm()
                    paymentAddedFeedback = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("dialog_save_payment_button")
            ) {
                AnimatedContent(
                    targetState = paymentAddedFeedback,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.85f, animationSpec = tween(220)) + fadeIn(tween(200))) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "dialog_pay_btn_content"
                ) { added ->
                    if (added) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Payment Added")
                        }
                    } else {
                        Text("Save Payment")
                    }
                }
            }
        }
    }
}
