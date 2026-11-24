package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.HisabEntity
import com.example.data.local.HisabWithItems
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PartyEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductSubCategoryEntity
import com.example.data.local.ProductWithDetails
import com.example.data.local.WorkStepEntity
import com.example.ui.components.BalanceBadge
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.LedgerCard
import com.example.ui.components.MetricColumn
import com.example.ui.components.PartyEditorDialog
import com.example.ui.components.ProductEditorDialog
import com.example.ui.components.QuickPaymentDialog
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

data class PartyDetailUiState(
    val summary: PartyBalanceSummary? = null,
    val hisabs: List<HisabWithItems> = emptyList(),
    val products: List<ProductWithDetails> = emptyList(),
    val payments: List<PaymentEntity> = emptyList()
)

class PartyDetailViewModel(
    private val partyId: Long,
    private val repository: FactoryHisabRepository
) : ViewModel() {

    val uiState: StateFlow<PartyDetailUiState> = combine(
        repository.observePartySummary(partyId),
        repository.observeHisabsForParty(partyId),
        repository.observeProductsForParty(partyId),
        repository.observePaymentsForParty(partyId)
    ) { summary, hisabs, products, payments ->
        PartyDetailUiState(
            summary = summary,
            hisabs = hisabs,
            products = products,
            payments = payments
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PartyDetailUiState()
    )

    fun saveParty(party: PartyEntity) {
        viewModelScope.launch { repository.saveParty(party) }
    }

    fun deleteParty(party: PartyEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteParty(party)
            onDeleted()
        }
    }

    fun saveProduct(
        product: ProductEntity,
        subCategories: List<ProductSubCategoryEntity>,
        workSteps: List<WorkStepEntity>
    ) {
        viewModelScope.launch {
            repository.saveProductWithDetails(product, subCategories, workSteps)
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch { repository.deleteProduct(product) }
    }

    fun addPayment(amount: Double, mode: String, note: String) {
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

    fun deleteHisab(hisab: HisabEntity) {
        viewModelScope.launch { repository.deleteHisab(hisab, deleteLinkedPayments = false) }
    }
}

class PartyDetailViewModelFactory(
    private val partyId: Long,
    private val repository: FactoryHisabRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PartyDetailViewModel(partyId, repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    viewModel: PartyDetailViewModel,
    onBack: () -> Unit,
    onNewHisab: (Long) -> Unit,
    onOpenHisabSlip: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val summary = state.summary

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditParty by remember { mutableStateOf(false) }
    var showDeleteParty by remember { mutableStateOf(false) }
    var showProductEditor by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductWithDetails?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var showAddPayment by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }
    var hisabToDelete by remember { mutableStateOf<HisabEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = summary?.party?.name ?: "Party Khata",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (summary != null && summary.party.phone.isNotBlank()) {
                            Text(
                                text = summary.party.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("party_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (summary != null) {
                        IconButton(
                            onClick = { showEditParty = true },
                            modifier = Modifier.testTag("edit_party_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Party")
                        }
                        IconButton(
                            onClick = { showDeleteParty = true },
                            modifier = Modifier.testTag("delete_party_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Party",
                                tint = ledgerColors.dangerRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading party ledger...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Party Summary Header Card
                LedgerCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LIVE KHATA BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${state.products.size} ${if (state.products.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BalanceBadge(balance = summary.currentBalance)
                        }

                        Surface(
                            color = ledgerColors.khataStrip,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (summary.party.openingBalance != 0.0) {
                                    MetricColumn(
                                        label = "Opening",
                                        value = CurrencyFormatter.formatRupees(summary.party.openingBalance)
                                    )
                                }
                                MetricColumn(
                                    label = "Total Billed",
                                    value = CurrencyFormatter.formatRupees(summary.totalBilled)
                                )
                                MetricColumn(
                                    label = "Total Paid",
                                    value = CurrencyFormatter.formatRupees(summary.totalPaid),
                                    valueColor = ledgerColors.settledGreen
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.click()
                                showAddPayment = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detail_add_payment_button"),
                            border = BorderStroke(1.dp, ledgerColors.borderSubtle)
                        ) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = ledgerColors.settledGreen
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Add Payment")
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            if (selectedTab != 0) haptics.tick()
                            selectedTab = 0
                        },
                        text = { Text("Hisabs (${state.hisabs.size})") },
                        modifier = Modifier.testTag("tab_hisabs")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            if (selectedTab != 1) haptics.tick()
                            selectedTab = 1
                        },
                        text = { Text("Items (${state.products.size})") },
                        modifier = Modifier.testTag("tab_products")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            if (selectedTab != 2) haptics.tick()
                            selectedTab = 2
                        },
                        text = { Text("Payments (${state.payments.size})") },
                        modifier = Modifier.testTag("tab_payments")
                    )
                }

                val tabEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dir = if (targetState >= initialState) 1 else -1
                        (slideInHorizontally(tween(240, easing = tabEase)) { dir * it / 14 } +
                            fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200, easing = tabEase)) { -dir * it / 16 } +
                                fadeOut(tween(150)))
                    },
                    label = "party_detail_tab_content",
                    modifier = Modifier.weight(1f)
                ) { tabIdx ->
                    when (tabIdx) {
                        0 -> HisabsTabList(
                            hisabs = state.hisabs,
                            onOpenSlip = {
                                haptics.click()
                                onOpenHisabSlip(it)
                            },
                            onDeleteHisab = { hisabToDelete = it },
                            onNewHisab = {
                                haptics.click()
                                onNewHisab(summary.party.id)
                            }
                        )
                        1 -> ItemsTabList(
                            products = state.products,
                            onAddProduct = {
                                haptics.click()
                                editingProduct = null
                                showProductEditor = true
                            },
                            onEditProduct = { prod ->
                                haptics.click()
                                editingProduct = prod
                                showProductEditor = true
                            },
                            onDeleteProduct = { productToDelete = it }
                        )
                        else -> PartyPaymentsTabList(
                            payments = state.payments,
                            onAddPayment = {
                                haptics.click()
                                showAddPayment = true
                            },
                            onDeletePayment = { paymentToDelete = it }
                        )
                    }
                }
            }
        }
    }

    if (showEditParty && summary != null) {
        PartyEditorDialog(
            existingParty = summary.party,
            onSave = { updated ->
                viewModel.saveParty(updated)
                showEditParty = false
            },
            onDismiss = { showEditParty = false }
        )
    }

    if (showDeleteParty && summary != null) {
        ConfirmDeleteDialog(
            title = "Delete ${summary.party.name}?",
            message = "This permanently removes this party, all its items, hisabs, and payment records.",
            onConfirm = {
                viewModel.deleteParty(summary.party) {
                    showDeleteParty = false
                    onBack()
                }
            },
            onDismiss = { showDeleteParty = false }
        )
    }

    if (showProductEditor && summary != null) {
        ProductEditorDialog(
            partyId = summary.party.id,
            existingProduct = editingProduct,
            onSave = { prod, subs, steps ->
                viewModel.saveProduct(prod, subs, steps)
                showProductEditor = false
                editingProduct = null
            },
            onDismiss = {
                showProductEditor = false
                editingProduct = null
            }
        )
    }

    productToDelete?.let { prod ->
        ConfirmDeleteDialog(
            title = "Delete Item?",
            message = "Delete '${prod.name}' from this party's catalog? Past saved hisab slips will keep their records.",
            onConfirm = {
                viewModel.deleteProduct(prod)
                productToDelete = null
            },
            onDismiss = { productToDelete = null }
        )
    }

    if (showAddPayment && summary != null) {
        QuickPaymentDialog(
            partyName = summary.party.name,
            currentPendingBalance = summary.currentBalance,
            onSavePayment = { amt, mode, note ->
                viewModel.addPayment(amt, mode, note)
                showAddPayment = false
            },
            onDismiss = { showAddPayment = false }
        )
    }

    paymentToDelete?.let { pay ->
        ConfirmDeleteDialog(
            title = "Delete Payment?",
            message = "Delete payment of ${CurrencyFormatter.formatRupees(pay.amount)}? This will automatically add the amount back to the party's pending balance.",
            onConfirm = {
                viewModel.deletePayment(pay)
                paymentToDelete = null
            },
            onDismiss = { paymentToDelete = null }
        )
    }

    hisabToDelete?.let { hisab ->
        ConfirmDeleteDialog(
            title = "Delete Hisab (${DateFormatter.formatShort(hisab.dateMillis)})?",
            message = "Remove Hisab (${CurrencyFormatter.formatRupees(hisab.itemsSubtotal)})? Any separate or linked payments stay in the party's payment log.",
            onConfirm = {
                viewModel.deleteHisab(hisab)
                hisabToDelete = null
            },
            onDismiss = { hisabToDelete = null }
        )
    }
}

@Composable
private fun HisabsTabList(
    hisabs: List<HisabWithItems>,
    onOpenSlip: (Long) -> Unit,
    onDeleteHisab: (HisabEntity) -> Unit,
    onNewHisab: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onNewHisab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("detail_new_hisab_button")
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New Hisab")
            }
        }

        if (hisabs.isEmpty()) {
            item {
                LedgerCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("No Hisab Bills Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create a calculation slip by picking items and toggling work steps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(hisabs, key = { it.hisab.id }) { item ->
                val h = item.hisab
                LedgerCard(modifier = Modifier.testTag("hisab_card_${h.id}")) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DateFormatter.formatShort(h.dateMillis),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${item.items.size} ${if (item.items.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatRupees(h.itemsSubtotal, true),
                                    style = TabularAmountStyle.copy(
                                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                IconButton(
                                    onClick = { onDeleteHisab(h) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_hisab_button_${h.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Hisab",
                                        tint = ledgerColors.dangerRed
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNewHisab,
                                border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_hisab_page_button_${h.id}")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Open Hisab")
                            }
                            Button(
                                onClick = { onOpenSlip(h.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_slip_button_${h.id}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Slip")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemsTabList(
    products: List<ProductWithDetails>,
    onAddProduct: () -> Unit,
    onEditProduct: (ProductWithDetails) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onAddProduct,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_product_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add Item & Work Steps")
            }
        }

        if (products.isEmpty()) {
            item {
                LedgerCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No Items Defined", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add items (like New Model) with base rates and toggleable work steps (like Phase or Cut).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(products, key = { it.product.id }) { prodWithDetails ->
                val p = prodWithDetails.product
                val defaultStepsTotal = prodWithDetails.workSteps
                    .filter { it.isDefaultSelected }
                    .sumOf { it.stepRate }
                val defaultUnitRate = p.baseRate + defaultStepsTotal

                LedgerCard(modifier = Modifier.testTag("product_card_${p.id}")) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Base: ${CurrencyFormatter.formatRate(p.baseRate)} / ${p.defaultUnit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${CurrencyFormatter.formatRate(defaultUnitRate)}/${p.defaultUnit}",
                                    style = TabularAmountStyle.copy(
                                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (prodWithDetails.workSteps.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                prodWithDetails.workSteps.forEach { step ->
                                    Surface(
                                        color = ledgerColors.khataStrip,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, ledgerColors.borderSubtle)
                                    ) {
                                        Text(
                                            text = "${step.name} (+${CurrencyFormatter.formatRate(step.stepRate)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (prodWithDetails.subCategories.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                prodWithDetails.subCategories.forEach { sc ->
                                    val sign = if (sc.rateAdjustment >= 0) "+" else ""
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${sc.name} ($sign${CurrencyFormatter.formatRate(sc.rateAdjustment)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = ledgerColors.borderSubtle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { onEditProduct(prodWithDetails) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit item")
                            }
                            IconButton(
                                onClick = { onDeleteProduct(p) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete item",
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

@Composable
private fun PartyPaymentsTabList(
    payments: List<PaymentEntity>,
    onAddPayment: () -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onAddPayment,
                colors = ButtonDefaults.buttonColors(containerColor = ledgerColors.settledGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("tab_add_payment_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Record Payment")
            }
        }

        if (payments.isEmpty()) {
            item {
                LedgerCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No Payments Recorded", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Payments added here automatically reduce the party's balance in future hisabs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(payments, key = { it.id }) { pay ->
                LedgerCard(modifier = Modifier.testTag("party_payment_card_${pay.id}")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatRupees(pay.amount, true),
                                    style = TabularAmountStyle.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                        color = ledgerColors.settledGreen
                                    )
                                )
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
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = DateFormatter.formatShort(pay.dateMillis) +
                                    if (pay.note.isNotBlank()) " • ${pay.note}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeletePayment(pay) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_payment_button_${pay.id}")
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
