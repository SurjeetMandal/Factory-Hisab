package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.CompanyProfileEntity
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.PartyBalanceSummary
import com.example.ui.components.BalanceBadge
import com.example.ui.components.LedgerCard
import com.example.ui.components.SoftDialog
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class DashboardUiState(
    val companyProfile: CompanyProfileEntity? = null,
    val monthlyGain: Double = 0.0,
    val monthlyCollected: Double = 0.0,
    val yearlyGain: Double = 0.0,
    val yearlyCollected: Double = 0.0,
    val pendingBalance: Double = 0.0,
    val totalAdvance: Double = 0.0,
    val sharedSlipsCount: Int = 0,
    val parties: List<PartyBalanceSummary> = emptyList()
)

class DashboardViewModel(private val repository: FactoryHisabRepository) : ViewModel() {

    private val ledgerStatsFlow = combine(
        repository.observeCompanyProfile(),
        repository.observePartySummaries(),
        repository.observeAllHisabs(),
        repository.observeAllPayments(),
        repository.observeAllMiscAdjustments()
    ) { profile, parties, hisabs, payments, miscs ->
        val (monthStart, monthEnd) = DateFormatter.currentMonthRange()
        val (yearStart, yearEnd) = DateFormatter.currentYearRange()

        val monthlyHisabs = hisabs
            .filter { it.dateMillis in monthStart until monthEnd }
            .sumOf { it.itemsSubtotal }
        val monthlyMiscNet = miscs
            .filter { (it.dateMillis ?: it.createdAt) in monthStart until monthEnd }
            .sumOf { if (it.isAddition) it.amount else -it.amount }
        val monthlyCollected = payments
            .filter { it.dateMillis in monthStart until monthEnd }
            .sumOf { it.amount }

        val yearlyHisabs = hisabs
            .filter { it.dateMillis in yearStart until yearEnd }
            .sumOf { it.itemsSubtotal }
        val yearlyMiscNet = miscs
            .filter { (it.dateMillis ?: it.createdAt) in yearStart until yearEnd }
            .sumOf { if (it.isAddition) it.amount else -it.amount }
        val yearlyCollected = payments
            .filter { it.dateMillis in yearStart until yearEnd }
            .sumOf { it.amount }

        val pending = parties.sumOf { if (it.currentBalance > 0.0) it.currentBalance else 0.0 }
        val advance = parties.sumOf { if (it.currentBalance < 0.0) abs(it.currentBalance) else 0.0 }

        DashboardUiState(
            companyProfile = profile,
            monthlyGain = monthlyHisabs + monthlyMiscNet,
            monthlyCollected = monthlyCollected,
            yearlyGain = yearlyHisabs + yearlyMiscNet,
            yearlyCollected = yearlyCollected,
            pendingBalance = pending,
            totalAdvance = advance,
            parties = parties
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        ledgerStatsFlow,
        repository.observeAllSharedSlips()
    ) { stats, slips ->
        stats.copy(sharedSlipsCount = slips.size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun updateCompanyProfile(companyName: String, ownerName: String) {
        viewModelScope.launch {
            repository.saveCompanyProfile(companyName, ownerName, onboardingCompleted = true)
        }
    }
}

class DashboardViewModelFactory(private val repository: FactoryHisabRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(repository) as T
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenRegister: (Long?) -> Unit,
    onOpenPartyDetail: (Long) -> Unit,
    onOpenSharedSlips: () -> Unit,
    contentBottomPadding: Dp
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    var showEditCompanyDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = contentBottomPadding + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Soft Company Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.companyProfile?.companyName?.ifBlank { "Factory Hisab" }
                                ?: "Factory Hisab",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val owner = state.companyProfile?.ownerName.orEmpty()
                        Text(
                            text = if (owner.isNotBlank()) "Prop. $owner • Factory Overview" else "Factory Overview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.click()
                            showEditCompanyDialog = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_company_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit company details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Soft 2x2 KPI Grid: Monthly Gain, Yearly Gain, Pending Balance, Advance
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SoftKpiCard(
                            title = "Monthly Gain",
                            amount = CurrencyFormatter.formatRupees(state.monthlyGain),
                            subtitle = "Collected: ${CurrencyFormatter.formatRupees(state.monthlyCollected)}",
                            icon = Icons.Rounded.CalendarMonth,
                            accentColor = ledgerColors.settledGreen,
                            containerColor = ledgerColors.settledContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_monthly_gain")
                        )
                        SoftKpiCard(
                            title = "Yearly Gain",
                            amount = CurrencyFormatter.formatRupees(state.yearlyGain),
                            subtitle = "Collected: ${CurrencyFormatter.formatRupees(state.yearlyCollected)}",
                            icon = Icons.AutoMirrored.Rounded.TrendingUp,
                            accentColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_yearly_gain")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SoftKpiCard(
                            title = "Pending Balance",
                            amount = CurrencyFormatter.formatRupees(state.pendingBalance),
                            subtitle = "Payment to receive",
                            icon = Icons.Rounded.AccountBalanceWallet,
                            accentColor = ledgerColors.pendingAmber,
                            containerColor = ledgerColors.pendingContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_pending_balance")
                        )
                        SoftKpiCard(
                            title = "Advance",
                            amount = CurrencyFormatter.formatRupees(state.totalAdvance),
                            subtitle = "Excess paid by parties",
                            icon = Icons.Rounded.Savings,
                            accentColor = ledgerColors.advanceBlue,
                            containerColor = ledgerColors.advanceContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_advance")
                        )
                    }
                }
            }

            // Saved Shared Slips Entry Card
            item {
                LedgerCard(
                    modifier = Modifier
                        .clickable {
                            haptics.click()
                            onOpenSharedSlips()
                        }
                        .testTag("dashboard_saved_slips_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.CollectionsBookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Saved Shared Slips",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (state.sharedSlipsCount == 0) {
                                        "Slips you share appear here"
                                    } else {
                                        "${state.sharedSlipsCount} ${if (state.sharedSlipsCount == 1) "slip" else "slips"} saved"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Open saved shared slips",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Party Balances Snapshot
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Party Balances (${state.parties.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap party for register",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.parties.isEmpty()) {
                item {
                    LedgerCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("No Parties Added Yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Go to the Parties tab or Hisab Register to add your first party.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.parties, key = { it.party.id }) { summary ->
                    LedgerCard(
                        modifier = Modifier
                            .clickable {
                                haptics.click()
                                onOpenRegister(summary.party.id)
                            }
                            .testTag("dashboard_party_row_${summary.party.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.party.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${summary.productCount} ${if (summary.productCount == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            BalanceBadge(balance = summary.currentBalance)
                        }
                    }
                }
            }
        }
    }

    if (showEditCompanyDialog) {
        EditCompanyDialog(
            initialCompany = state.companyProfile?.companyName ?: "",
            initialOwner = state.companyProfile?.ownerName ?: "",
            onSave = { comp, owner ->
                viewModel.updateCompanyProfile(comp, owner)
                showEditCompanyDialog = false
            },
            onDismiss = { showEditCompanyDialog = false }
        )
    }
}

@Composable
private fun SoftKpiCard(
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalLedgerColors.current.borderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = containerColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = amount,
                style = TabularAmountStyle.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    color = accentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditCompanyDialog(
    initialCompany: String,
    initialOwner: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var company by remember { mutableStateOf(initialCompany) }
    var owner by remember { mutableStateOf(initialOwner) }
    SoftDialog(onDismiss = onDismiss) {
        Text("Factory Details", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it },
                label = { Text("Owner Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (company.trim().isNotEmpty()) {
                        onSave(company.trim(), owner.trim())
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save")
            }
        }
    }
}
