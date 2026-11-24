package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PaymentEntity
import com.example.data.local.PaymentWithParty
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.LedgerCard
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentsUiState(
    val parties: List<PartyBalanceSummary> = emptyList(),
    val payments: List<PaymentWithParty> = emptyList(),
    val filterQuery: String = "",
    val totalCollected: Double = 0.0
)

class PaymentsViewModel(private val repository: FactoryHisabRepository) : ViewModel() {

    private val filterQuery = MutableStateFlow("")

    val uiState: StateFlow<PaymentsUiState> = combine(
        repository.observePartySummaries(),
        repository.observeAllPaymentsWithParty(),
        filterQuery
    ) { parties, allPayments, query ->
        val filtered = if (query.isBlank()) {
            allPayments
        } else {
            allPayments.filter {
                it.partyName.contains(query.trim(), ignoreCase = true)
            }
        }
        PaymentsUiState(
            parties = parties,
            payments = filtered,
            filterQuery = query,
            totalCollected = allPayments.sumOf { it.payment.amount }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PaymentsUiState()
    )

    fun updateFilter(q: String) {
        filterQuery.value = q
    }

    fun addPayment(partyId: Long, amount: Double, mode: String, note: String) {
        viewModelScope.launch {
            repository.addPayment(
                PaymentEntity(
                    partyId = partyId,
                    amount = amount,
                    dateMillis = System.currentTimeMillis(),
                    paymentMode = mode,
                    note = note
                )
            )
        }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch { repository.deletePayment(payment) }
    }
}

class PaymentsViewModelFactory(private val repository: FactoryHisabRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PaymentsViewModel(repository) as T
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentsScreen(
    viewModel: PaymentsViewModel,
    onOpenPartyDetail: (Long) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedPartyId by remember { mutableStateOf<Long?>(null) }
    var showPartyDropdown by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("Cash") }
    var noteText by remember { mutableStateOf("") }
    var paymentToDelete by remember { mutableStateOf<PaymentWithParty?>(null) }
    var paymentAddedTrigger by remember { mutableIntStateOf(0) }
    var showPaymentAddedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(paymentAddedTrigger) {
        if (paymentAddedTrigger > 0) {
            showPaymentAddedFeedback = true
            delay(1800)
            showPaymentAddedFeedback = false
        }
    }

    val selectedParty = state.parties.find { it.party.id == selectedPartyId }
        ?: state.parties.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = contentBottomPadding)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 20.dp,
                    bottom = contentBottomPadding + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Payments Ledger",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Total collected: ${CurrencyFormatter.formatRupees(state.totalCollected)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledgerColors.settledGreen
                        )
                    }
                }
                // Quick Add Payment Card (By Party Name)
                item {
                    LedgerCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = ledgerColors.settledGreen
                                )
                                Text(
                                    "Add Payment by Party Name",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            if (state.parties.isEmpty()) {
                                Text(
                                    text = "Add a party first in the Parties tab to record payments.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Box {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                        color = ledgerColors.khataStrip,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptics.click()
                                                showPartyDropdown = true
                                            }
                                            .testTag("payments_party_picker")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = selectedParty?.party?.name ?: "Select Party",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                if (selectedParty != null) {
                                                    Text(
                                                        text = "Pending Balance: ${CurrencyFormatter.formatRupees(selectedParty.currentBalance)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (selectedParty.currentBalance > 0) ledgerColors.pendingAmber else ledgerColors.settledGreen
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Pick party")
                                        }
                                    }
                                }

                                if (showPartyDropdown) {
                                    SoftPartyPickerDialog(
                                        parties = state.parties,
                                        selectedPartyId = selectedParty?.party?.id,
                                        onSelectParty = { id ->
                                            selectedPartyId = id
                                            showPartyDropdown = false
                                        },
                                        onDismiss = { showPartyDropdown = false }
                                    )
                                }

                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                            amountText = input
                                        }
                                    },
                                    label = { Text("Amount Received (₹)") },
                                    placeholder = { Text("25000") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("payments_amount_input")
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Cash", "UPI", "Bank", "Cheque").forEach { mode ->
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
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    label = { Text("Note (Optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val payBtnColor by animateColorAsState(
                                    targetValue = if (showPaymentAddedFeedback) Color(0xFF1B5E20) else ledgerColors.settledGreen,
                                    animationSpec = tween(250),
                                    label = "payments_record_btn_color"
                                )
                                Button(
                                    onClick = {
                                        val target = selectedParty ?: return@Button
                                        val amt = amountText.toDoubleOrNull()
                                        if (amt == null || amt <= 0.0) {
                                            haptics.reject()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Enter a valid payment amount")
                                            }
                                            return@Button
                                        }
                                        haptics.confirm()
                                        viewModel.addPayment(
                                            partyId = target.party.id,
                                            amount = amt,
                                            mode = selectedMode,
                                            note = noteText.trim()
                                        )
                                        amountText = ""
                                        noteText = ""
                                        paymentAddedTrigger++
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = payBtnColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("record_payment_button")
                                ) {
                                    AnimatedContent(
                                        targetState = showPaymentAddedFeedback,
                                        transitionSpec = {
                                            (scaleIn(initialScale = 0.85f, animationSpec = tween(240)) + fadeIn(tween(220))) togetherWith
                                                fadeOut(tween(150))
                                        },
                                        label = "payments_record_btn_content"
                                    ) { added ->
                                        if (added) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                                Text("Payment Added", fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Text("Record Payment")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter payments by party name
                if (state.payments.isNotEmpty() || state.filterQuery.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = state.filterQuery,
                            onValueChange = viewModel::updateFilter,
                            placeholder = { Text("Filter payment by party name") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.filterQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateFilter("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear filter")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = ledgerColors.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payments_filter_input")
                        )
                    }
                }

                if (state.payments.isEmpty()) {
                    item {
                        LedgerCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("No Payments Yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Added payments appear here and automatically update each party's balance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.payments, key = { it.payment.id }) { item ->
                        val pay = item.payment
                        LedgerCard(
                            modifier = Modifier
                                .clickable { onOpenPartyDetail(pay.partyId) }
                                .testTag("payment_history_card_${pay.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.partyName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = ledgerColors.settledContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = pay.paymentMode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ledgerColors.settledGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = DateFormatter.formatShort(pay.dateMillis) +
                                                if (pay.note.isNotBlank()) " • ${pay.note}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "+${CurrencyFormatter.formatRupees(pay.amount)}",
                                        style = TabularAmountStyle.copy(
                                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                            color = ledgerColors.settledGreen
                                        )
                                    )
                                    IconButton(
                                        onClick = { paymentToDelete = item },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("payments_delete_btn_${pay.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete payment",
                                            tint = ledgerColors.dangerRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    paymentToDelete?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete Payment?",
            message = "Delete ${CurrencyFormatter.formatRupees(item.payment.amount)} from ${item.partyName}? Their pending balance will automatically increase by this amount.",
            onConfirm = {
                viewModel.deletePayment(item.payment)
                paymentToDelete = null
            },
            onDismiss = { paymentToDelete = null }
        )
    }
}
