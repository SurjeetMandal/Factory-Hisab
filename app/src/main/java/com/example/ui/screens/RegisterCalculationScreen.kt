package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.HisabEntity
import com.example.data.local.HisabLineItemEntity
import com.example.data.local.HisabWithItems
import com.example.data.local.MiscAdjustmentEntity
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PartyEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductSubCategoryEntity
import com.example.data.local.ProductWithDetails
import com.example.data.local.WorkStepEntity
import com.example.ui.components.BalanceBadge
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.PartyEditorDialog
import com.example.ui.components.ProductEditorDialog
import com.example.ui.components.SoftDialog
import com.example.ui.components.SoftSwitch
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

// 5-Column Copy Page Proportions: DATE | ITEM | PCS | RATE | TOTAL
private val COL_DATE_WIDTH = 46.dp
private val COL_PCS_WIDTH = 48.dp
private val COL_RATE_WIDTH = 48.dp
private val COL_TOTAL_WIDTH = 66.dp

sealed interface CopyPageEntry {
    data class ItemEntry(
        val item: HisabLineItemEntity,
        val dateMillis: Long
    ) : CopyPageEntry

    data class MiscEntry(
        val misc: MiscAdjustmentEntity
    ) : CopyPageEntry

    data class PaymentEntry(
        val payment: PaymentEntity
    ) : CopyPageEntry
}

sealed interface RegisterPageBlock {
    val stableKey: String

    data class OpeningCard(val amount: Double) : RegisterPageBlock {
        override val stableKey = "opening_card"
    }

    data class HisabCopyPage(
        val hisab: HisabEntity,
        val entries: List<CopyPageEntry>,
        val previousBalance: Double,
        val closingBalance: Double
    ) : RegisterPageBlock {
        override val stableKey = "copy_page_${hisab.id}"
    }

    data class StandaloneEntryCard(
        val entry: CopyPageEntry,
        val runningBalanceAfter: Double
    ) : RegisterPageBlock {
        override val stableKey = when (entry) {
            is CopyPageEntry.MiscEntry -> "standalone_misc_${entry.misc.id}"
            is CopyPageEntry.PaymentEntry -> "standalone_pay_${entry.payment.id}"
            is CopyPageEntry.ItemEntry -> "standalone_item_${entry.item.id}"
        }
    }
}

data class RegisterUiState(
    val isLoading: Boolean = true,
    val parties: List<PartyBalanceSummary> = emptyList(),
    val selectedParty: PartyBalanceSummary? = null,
    val productsForParty: List<ProductWithDetails> = emptyList(),
    val pageBlocks: List<RegisterPageBlock> = emptyList()
)

private data class LoadedPartyBundle(
    val partyId: Long?,
    val products: List<ProductWithDetails> = emptyList(),
    val hisabs: List<HisabWithItems> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val miscs: List<MiscAdjustmentEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModel(
    private val repository: FactoryHisabRepository
) : ViewModel() {

    private val selectedPartyId = MutableStateFlow<Long?>(null)
    private val partiesFlow = repository.observePartySummaries()

    private val activePartyIdFlow = combine(
        partiesFlow,
        selectedPartyId
    ) { parties, requestedId ->
        parties.find { it.party.id == requestedId }?.party?.id
            ?: parties.firstOrNull()?.party?.id
    }.distinctUntilChanged()

    private val partyBundleFlow = activePartyIdFlow.flatMapLatest { id ->
        if (id == null) {
            flowOf(LoadedPartyBundle(partyId = null))
        } else {
            combine(
                repository.observeProductsForParty(id),
                repository.observeHisabsForPartyChronological(id),
                repository.observePaymentsForPartyChronological(id),
                repository.observeMiscAdjustmentsForParty(id)
            ) { products, hisabs, payments, miscs ->
                LoadedPartyBundle(
                    partyId = id,
                    products = products,
                    hisabs = hisabs,
                    payments = payments,
                    miscs = miscs
                )
            }
        }
    }

    val uiState: StateFlow<RegisterUiState> = combine(
        partiesFlow,
        partyBundleFlow
    ) { parties, bundle ->
        val selected = parties.find { it.party.id == bundle.partyId } ?: parties.firstOrNull()
        val dataReady = selected == null || bundle.partyId == selected.party.id

        if (!dataReady) {
            RegisterUiState(
                isLoading = true,
                parties = parties,
                selectedParty = selected,
                productsForParty = emptyList(),
                pageBlocks = emptyList()
            )
        } else {
            val blocks = if (selected != null) {
                buildRegisterCopyPages(
                    openingBalance = selected.party.openingBalance,
                    hisabs = bundle.hisabs,
                    payments = bundle.payments,
                    miscs = bundle.miscs
                )
            } else {
                emptyList()
            }
            RegisterUiState(
                isLoading = false,
                parties = parties,
                selectedParty = selected,
                productsForParty = bundle.products,
                pageBlocks = blocks
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RegisterUiState(isLoading = true)
    )

    fun selectParty(partyId: Long) {
        selectedPartyId.value = partyId
    }

    fun addParty(party: PartyEntity) {
        viewModelScope.launch {
            val id = repository.saveParty(party)
            selectedPartyId.value = id
        }
    }

    fun saveProduct(
        product: ProductEntity,
        subCategories: List<ProductSubCategoryEntity>,
        workSteps: List<WorkStepEntity>,
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.saveProductWithDetails(product, subCategories, workSteps)
            onSaved(id)
        }
    }

    fun addItemToRegister(
        partyId: Long,
        dateMillis: Long,
        item: HisabLineItemEntity
    ) {
        viewModelScope.launch {
            val (dayStart, dayEnd) = DateFormatter.dayBounds(dateMillis)
            repository.addRegisterItem(
                partyId = partyId,
                calculationDateMillis = dateMillis,
                dayStartMillis = dayStart,
                dayEndMillis = dayEnd,
                forceNewSection = false,
                item = item
            )
        }
    }

    fun updateRegisterItem(
        partyId: Long,
        oldItem: HisabLineItemEntity,
        newItem: HisabLineItemEntity,
        newDateMillis: Long
    ) {
        viewModelScope.launch {
            val (dayStart, dayEnd) = DateFormatter.dayBounds(newDateMillis)
            repository.updateRegisterLineItem(
                partyId = partyId,
                oldItem = oldItem,
                newItem = newItem,
                newDateMillis = newDateMillis,
                dayStartMillis = dayStart,
                dayEndMillis = dayEnd
            )
        }
    }

    fun removeLineItem(item: HisabLineItemEntity) {
        viewModelScope.launch { repository.removeRegisterLineItem(item) }
    }

    fun updateHisabDate(hisabId: Long, newDateMillis: Long) {
        viewModelScope.launch { repository.updateHisabDate(hisabId, newDateMillis) }
    }

    fun addMiscAdjustment(
        partyId: Long,
        description: String,
        amount: Double,
        isAddition: Boolean,
        dateMillis: Long?
    ) {
        viewModelScope.launch {
            repository.addMiscAdjustment(
                MiscAdjustmentEntity(
                    partyId = partyId,
                    description = description,
                    amount = amount,
                    isAddition = isAddition,
                    dateMillis = dateMillis
                )
            )
        }
    }

    fun updateMiscAdjustment(adjustment: MiscAdjustmentEntity) {
        viewModelScope.launch { repository.updateMiscAdjustment(adjustment) }
    }

    fun deleteMiscAdjustment(misc: MiscAdjustmentEntity) {
        viewModelScope.launch { repository.deleteMiscAdjustment(misc) }
    }

    fun addPayment(partyId: Long, amount: Double, mode: String, note: String, dateMillis: Long) {
        viewModelScope.launch {
            repository.addPayment(
                PaymentEntity(
                    partyId = partyId,
                    amount = amount,
                    dateMillis = dateMillis,
                    paymentMode = mode,
                    note = note
                )
            )
        }
    }

    fun updatePayment(payment: PaymentEntity) {
        viewModelScope.launch { repository.updatePayment(payment) }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch { repository.deletePayment(payment) }
    }
}

private sealed class TimelineNode(val timestamp: Long, val order: Int) {
    class HisabNode(val hisabWithItems: HisabWithItems) : TimelineNode(hisabWithItems.hisab.dateMillis, 0)
    class MiscNode(val misc: MiscAdjustmentEntity) : TimelineNode(misc.dateMillis ?: misc.createdAt, 1)
    class PaymentNode(val payment: PaymentEntity) : TimelineNode(payment.dateMillis, 2)
}

private fun buildRegisterCopyPages(
    openingBalance: Double,
    hisabs: List<HisabWithItems>,
    payments: List<PaymentEntity>,
    miscs: List<MiscAdjustmentEntity>
): List<RegisterPageBlock> {
    val blocks = mutableListOf<RegisterPageBlock>()
    var running = openingBalance
    if (openingBalance != 0.0) {
        blocks.add(RegisterPageBlock.OpeningCard(openingBalance))
    }

    val attachedMiscIds = mutableSetOf<Long>()
    val attachedPaymentIds = mutableSetOf<Long>()

    // Match misc & payments on the same calendar day onto that Hisab Copy Page
    val dayLookup = hisabs.map { hisab ->
        hisab to DateFormatter.dayBounds(hisab.hisab.dateMillis)
    }

    val miscByHisab = mutableMapOf<Long, MutableList<MiscAdjustmentEntity>>()
    miscs.forEach { m ->
        val mDay = m.dateMillis
        val matched = if (mDay != null) {
            dayLookup.lastOrNull { (_, bounds) -> mDay in bounds.first..bounds.second }?.first
        } else {
            dayLookup.lastOrNull { (h, _) -> h.hisab.id == m.hisabId }?.first
        }
        if (matched != null) {
            miscByHisab.getOrPut(matched.hisab.id) { mutableListOf() }.add(m)
            attachedMiscIds.add(m.id)
        }
    }

    val paymentsByHisab = mutableMapOf<Long, MutableList<PaymentEntity>>()
    payments.forEach { p ->
        val matched = if (p.hisabId != null) {
            hisabs.find { it.hisab.id == p.hisabId }
        } else {
            // Attach advance/unlinked payments to the new hisab created on/after the payment, never back into a previous hisab
            hisabs.firstOrNull { it.hisab.dateMillis >= p.dateMillis }
        }
        if (matched != null) {
            paymentsByHisab.getOrPut(matched.hisab.id) { mutableListOf() }.add(p)
            attachedPaymentIds.add(p.id)
        }
    }

    val nodes = buildList {
        hisabs.forEach { add(TimelineNode.HisabNode(it)) }
        miscs.filter { it.id !in attachedMiscIds }.forEach { add(TimelineNode.MiscNode(it)) }
        payments.filter { it.id !in attachedPaymentIds }.forEach { add(TimelineNode.PaymentNode(it)) }
    }.sortedWith(compareBy<TimelineNode> { it.timestamp }.thenBy { it.order })

    nodes.forEach { node ->
        when (node) {
            is TimelineNode.HisabNode -> {
                val h = node.hisabWithItems.hisab
                val prevBal = running
                val pageEntries = mutableListOf<CopyPageEntry>()

                node.hisabWithItems.items.forEach { item ->
                    running += item.lineTotal
                    pageEntries.add(CopyPageEntry.ItemEntry(item, h.dateMillis))
                }
                miscByHisab[h.id].orEmpty().forEach { m ->
                    running += if (m.isAddition) m.amount else -m.amount
                    pageEntries.add(CopyPageEntry.MiscEntry(m))
                }
                paymentsByHisab[h.id].orEmpty().forEach { p ->
                    running -= p.amount
                    pageEntries.add(CopyPageEntry.PaymentEntry(p))
                }

                blocks.add(
                    RegisterPageBlock.HisabCopyPage(
                        hisab = h,
                        entries = pageEntries,
                        previousBalance = prevBal,
                        closingBalance = running
                    )
                )
            }
            is TimelineNode.MiscNode -> {
                running += if (node.misc.isAddition) node.misc.amount else -node.misc.amount
                blocks.add(
                    RegisterPageBlock.StandaloneEntryCard(
                        entry = CopyPageEntry.MiscEntry(node.misc),
                        runningBalanceAfter = running
                    )
                )
            }
            is TimelineNode.PaymentNode -> {
                running -= node.payment.amount
                blocks.add(
                    RegisterPageBlock.StandaloneEntryCard(
                        entry = CopyPageEntry.PaymentEntry(node.payment),
                        runningBalanceAfter = running
                    )
                )
            }
        }
    }

    return blocks
}

class RegisterViewModelFactory(
    private val repository: FactoryHisabRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RegisterViewModel(repository) as T
    }
}

private data class EditingItemContext(
    val item: HisabLineItemEntity,
    val dateMillis: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterCalculationScreen(
    viewModel: RegisterViewModel,
    requestedPartyId: Long?,
    onConsumedRequestedParty: () -> Unit,
    onOpenHisabSlip: (Long) -> Unit,
    contentBottomPadding: Dp
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showPartyPickerDialog by remember { mutableStateOf(false) }
    var showAddEntrySheet by remember { mutableStateOf(false) }
    var sheetDefaultDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editingItemContext by remember { mutableStateOf<EditingItemContext?>(null) }
    var showAddPartyDialog by remember { mutableStateOf(false) }

    var editingHisabDateTarget by remember { mutableStateOf<HisabEntity?>(null) }
    var editingMiscTarget by remember { mutableStateOf<MiscAdjustmentEntity?>(null) }
    var editingPaymentTarget by remember { mutableStateOf<PaymentEntity?>(null) }

    var settledPartyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousBlockCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(requestedPartyId) {
        if (requestedPartyId != null && requestedPartyId > 0L) {
            viewModel.selectParty(requestedPartyId)
            onConsumedRequestedParty()
        }
    }

    LaunchedEffect(state.isLoading, state.selectedParty?.party?.id, state.pageBlocks.size) {
        if (state.isLoading) return@LaunchedEffect
        val partyId = state.selectedParty?.party?.id
        if (partyId != null && state.pageBlocks.isNotEmpty()) {
            val lastIdx = state.pageBlocks.lastIndex
            if (settledPartyId != partyId || previousBlockCount == 0) {
                // Snap silently on initial open or party switch so tab navigation never jitters
                listState.scrollToItem(lastIdx)
            } else if (state.pageBlocks.size > previousBlockCount) {
                // Smooth scroll only when user adds a new entry on the currently displayed party
                listState.animateScrollToItem(lastIdx)
            }
            settledPartyId = partyId
            previousBlockCount = state.pageBlocks.size
        } else {
            settledPartyId = partyId
            previousBlockCount = 0
        }
    }

    val solidDateLineColor = ledgerColors.registerMargin
    val dottedLineColor = ledgerColors.inkBlue.copy(alpha = 0.28f)
    val horizontalDottedColor = ledgerColors.registerRule

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
        ) {
            // Top Floating 2-Pill Nav Bar with EXACT same height (54.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(27.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .clickable {
                            haptics.click()
                            if (state.parties.isEmpty()) {
                                showAddPartyDialog = true
                            } else {
                                showPartyPickerDialog = true
                            }
                        }
                        .testTag("register_party_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedParty?.party?.name ?: "Choose Party",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.selectedParty != null) {
                                val bal = state.selectedParty!!.currentBalance
                                val tag = when {
                                    bal > 0.01 -> "Due: ${CurrencyFormatter.formatRupees(bal)}"
                                    bal < -0.01 -> "Adv: ${CurrencyFormatter.formatRupees(abs(bal))}"
                                    else -> "Settled"
                                }
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        bal > 0.01 -> ledgerColors.pendingAmber
                                        bal < -0.01 -> ledgerColors.advanceBlue
                                        else -> ledgerColors.settledGreen
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select party"
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(27.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .clickable {
                            haptics.click()
                            if (state.selectedParty == null) {
                                showAddPartyDialog = true
                            } else {
                                editingItemContext = null
                                sheetDefaultDateMillis = System.currentTimeMillis()
                                showAddEntrySheet = true
                            }
                        }
                        .testTag("register_add_item_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .height(54.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "New Hisab",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Copy-Pages Register Area + Bottom Net Pending Balance Footer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = contentBottomPadding + 4.dp)
            ) {
                if (state.isLoading) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ledgerColors.registerPaper,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                } else if (state.selectedParty == null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ledgerColors.registerPaper,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text("Your Khata Register is Empty", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Add a party first to start recording copy pages for each date.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        haptics.click()
                                        showAddPartyDialog = true
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add First Party")
                                }
                            }
                        }
                    }
                } else if (state.pageBlocks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ledgerColors.registerPaper,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "No Copy Pages for ${state.selectedParty!!.party.name}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Tap '+ New Hisab' above to create the first dated copy page.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        editingItemContext = null
                                        sheetDefaultDateMillis = System.currentTimeMillis()
                                        showAddEntrySheet = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add First Entry")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 10.dp)
                    ) {
                        items(state.pageBlocks, key = { it.stableKey }) { block ->
                            when (block) {
                                is RegisterPageBlock.OpeningCard -> {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = ledgerColors.khataStrip,
                                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Opening Balance Carried Forward",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = CurrencyFormatter.formatRupees(block.amount, true),
                                                style = TabularAmountStyle.copy(
                                                    fontSize = 14.sp,
                                                    color = if (block.amount > 0) ledgerColors.pendingAmber else ledgerColors.advanceBlue
                                                )
                                            )
                                        }
                                    }
                                }

                                is RegisterPageBlock.HisabCopyPage -> {
                                    HisabCopyPageCard(
                                        page = block,
                                        solidDateLineColor = solidDateLineColor,
                                        dottedLineColor = dottedLineColor,
                                        horizontalDottedColor = horizontalDottedColor,
                                        onEditPageDate = { editingHisabDateTarget = block.hisab },
                                        onAddOnPageDate = {
                                            editingItemContext = null
                                            sheetDefaultDateMillis = block.hisab.dateMillis
                                            showAddEntrySheet = true
                                        },
                                        onSharePageSlip = { onOpenHisabSlip(block.hisab.id) },
                                        onEditItem = { item, dt ->
                                            editingItemContext = EditingItemContext(item, dt)
                                            sheetDefaultDateMillis = dt
                                            showAddEntrySheet = true
                                        },
                                        onEditMisc = { misc -> editingMiscTarget = misc },
                                        onEditPayment = { pay -> editingPaymentTarget = pay }
                                    )
                                }

                                is RegisterPageBlock.StandaloneEntryCard -> {
                                    StandaloneAdjustmentCard(
                                        entry = block.entry,
                                        runningBalanceAfter = block.runningBalanceAfter,
                                        onEditMisc = { misc -> editingMiscTarget = misc },
                                        onEditPayment = { pay -> editingPaymentTarget = pay }
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Net Pending Balance Bar (Share Slip button removed here since every copy page has its own)
                    val liveBal = state.selectedParty!!.currentBalance
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ledgerColors.khataStrip,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    liveBal > 0.01 -> "NET PENDING BALANCE"
                                    liveBal < -0.01 -> "ADVANCE CREDIT"
                                    else -> "ACCOUNT SETTLED"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatRupees(abs(liveBal), true),
                                style = TabularAmountStyle.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        liveBal > 0.01 -> ledgerColors.pendingAmber
                                        liveBal < -0.01 -> ledgerColors.advanceBlue
                                        else -> ledgerColors.settledGreen
                                    }
                                ),
                                modifier = Modifier.testTag("register_live_balance")
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentBottomPadding + 14.dp)
        )
    }

    if (showPartyPickerDialog) {
        SoftPartyPickerDialog(
            parties = state.parties,
            selectedPartyId = state.selectedParty?.party?.id,
            onSelectParty = { id ->
                viewModel.selectParty(id)
                showPartyPickerDialog = false
            },
            onAddNewParty = {
                showPartyPickerDialog = false
                showAddPartyDialog = true
            },
            onDismiss = { showPartyPickerDialog = false }
        )
    }

    if (showAddEntrySheet && state.selectedParty != null) {
        RegisterAddEntrySheet(
            party = state.selectedParty!!.party,
            products = state.productsForParty,
            initialDateMillis = sheetDefaultDateMillis,
            editingItem = editingItemContext,
            onSaveProduct = { prod, subs, steps, onCreated ->
                viewModel.saveProduct(prod, subs, steps, onCreated)
            },
            onAddItem = { dateMillis, lineItem ->
                viewModel.addItemToRegister(
                    partyId = state.selectedParty!!.party.id,
                    dateMillis = dateMillis,
                    item = lineItem
                )
                showAddEntrySheet = false
                editingItemContext = null
                scope.launch { snackbarHostState.showSnackbar("Added ${lineItem.productName} to copy page") }
            },
            onUpdateItem = { oldItem, newItem, dateMillis ->
                viewModel.updateRegisterItem(
                    partyId = state.selectedParty!!.party.id,
                    oldItem = oldItem,
                    newItem = newItem,
                    newDateMillis = dateMillis
                )
                showAddEntrySheet = false
                editingItemContext = null
                scope.launch { snackbarHostState.showSnackbar("Updated ${newItem.productName}") }
            },
            onDeleteItem = { item ->
                viewModel.removeLineItem(item)
                showAddEntrySheet = false
                editingItemContext = null
                scope.launch { snackbarHostState.showSnackbar("Removed ${item.productName}") }
            },
            onAddMisc = { desc, amt, isAdd, dateMillis ->
                viewModel.addMiscAdjustment(
                    partyId = state.selectedParty!!.party.id,
                    description = desc,
                    amount = amt,
                    isAddition = isAdd,
                    dateMillis = dateMillis
                )
                showAddEntrySheet = false
                scope.launch { snackbarHostState.showSnackbar("Misc entry saved") }
            },
            onAddPayment = { amt, mode, note, dateMillis ->
                viewModel.addPayment(
                    partyId = state.selectedParty!!.party.id,
                    amount = amt,
                    mode = mode,
                    note = note,
                    dateMillis = dateMillis
                )
            },
            onDismiss = {
                showAddEntrySheet = false
                editingItemContext = null
            }
        )
    }

    if (showAddPartyDialog) {
        PartyEditorDialog(
            onSave = { party ->
                viewModel.addParty(party)
                showAddPartyDialog = false
            },
            onDismiss = { showAddPartyDialog = false }
        )
    }

    editingHisabDateTarget?.let { hisab ->
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = hisab.dateMillis)
        DatePickerDialog(
            onDismissRequest = { editingHisabDateTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { newMillis ->
                            viewModel.updateHisabDate(hisab.id, DateFormatter.mergeUtcPickerDate(newMillis))
                        }
                        editingHisabDateTarget = null
                    }
                ) { Text("Update Date") }
            },
            dismissButton = {
                TextButton(onClick = { editingHisabDateTarget = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    editingMiscTarget?.let { misc ->
        EditMiscDialog(
            misc = misc,
            onSave = { updated ->
                viewModel.updateMiscAdjustment(updated)
                editingMiscTarget = null
            },
            onDelete = {
                viewModel.deleteMiscAdjustment(misc)
                editingMiscTarget = null
            },
            onDismiss = { editingMiscTarget = null }
        )
    }

    editingPaymentTarget?.let { pay ->
        EditPaymentDialog(
            payment = pay,
            onSave = { updated ->
                viewModel.updatePayment(updated)
                editingPaymentTarget = null
            },
            onDelete = {
                viewModel.deletePayment(pay)
                editingPaymentTarget = null
            },
            onDismiss = { editingPaymentTarget = null }
        )
    }
}

@Composable
private fun HisabCopyPageCard(
    page: RegisterPageBlock.HisabCopyPage,
    solidDateLineColor: Color,
    dottedLineColor: Color,
    horizontalDottedColor: Color,
    onEditPageDate: () -> Unit,
    onAddOnPageDate: () -> Unit,
    onSharePageSlip: () -> Unit,
    onEditItem: (HisabLineItemEntity, Long) -> Unit,
    onEditMisc: (MiscAdjustmentEntity) -> Unit,
    onEditPayment: (PaymentEntity) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ledgerColors.registerPaper,
        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("copy_page_${page.hisab.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top-Left Date of Copy Page + Top-Right Add & Page-Wise Share Slip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ledgerColors.khataStrip)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.tick()
                            onEditPageDate()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DateFormatter.formatSlipDate(page.hisab.dateMillis),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit page date",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.click()
                                onAddOnPageDate()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add item to this date",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.click()
                                onSharePageSlip()
                            }
                            .testTag("page_share_slip_${page.hisab.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Open slip",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Open Slip",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))

            // 5-Column Header: DATE | ITEM | PCS | RATE | TOTAL (ALL headings left-aligned at start of column)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ledgerColors.khataStrip.copy(alpha = 0.5f))
                    .draw5ColVerticalLines(
                        solidLineColor = solidDateLineColor,
                        dottedLineColor = dottedLineColor
                    )
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DATE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .width(COL_DATE_WIDTH)
                        .padding(start = 2.dp, end = 4.dp)
                )
                Text(
                    text = "ITEM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 4.dp)
                )
                Text(
                    text = "PCS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .width(COL_PCS_WIDTH)
                        .padding(start = 5.dp, end = 3.dp)
                )
                Text(
                    text = "RATE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .width(COL_RATE_WIDTH)
                        .padding(start = 5.dp, end = 3.dp)
                )
                Text(
                    text = "TOTAL",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .width(COL_TOTAL_WIDTH)
                        .padding(start = 5.dp)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))

            // 5-Column Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draw5ColVerticalLines(
                        solidLineColor = solidDateLineColor,
                        dottedLineColor = dottedLineColor
                    )
            ) {
                page.entries.forEach { entry ->
                    when (entry) {
                        is CopyPageEntry.ItemEntry -> {
                            CopyPage5ColItemRow(
                                item = entry.item,
                                dateMillis = entry.dateMillis,
                                horizontalDottedColor = horizontalDottedColor,
                                onClick = { onEditItem(entry.item, entry.dateMillis) }
                            )
                        }
                        is CopyPageEntry.MiscEntry -> {
                            val sign = if (entry.misc.isAddition) "+" else "-"
                            val col = if (entry.misc.isAddition) MaterialTheme.colorScheme.primary else ledgerColors.settledGreen
                            CopyPage5ColOtherRow(
                                dateText = entry.misc.dateMillis?.let { DateFormatter.formatCompact(it) } ?: "—",
                                itemText = "Misc ($sign): ${entry.misc.description}",
                                pcsText = "—",
                                rateText = "—",
                                totalText = "$sign${CurrencyFormatter.formatRupees(entry.misc.amount, false)}",
                                accentColor = col,
                                horizontalDottedColor = horizontalDottedColor,
                                onClick = { onEditMisc(entry.misc) }
                            )
                        }
                        is CopyPageEntry.PaymentEntry -> {
                            val note = if (entry.payment.note.isNotBlank()) " • ${entry.payment.note}" else ""
                            CopyPage5ColOtherRow(
                                dateText = DateFormatter.formatCompact(entry.payment.dateMillis),
                                itemText = "Advance Paid (${entry.payment.paymentMode})$note",
                                pcsText = "—",
                                rateText = "—",
                                totalText = "-${CurrencyFormatter.formatRupees(entry.payment.amount, false)}",
                                accentColor = ledgerColors.settledGreen,
                                horizontalDottedColor = horizontalDottedColor,
                                onClick = { onEditPayment(entry.payment) }
                            )
                        }
                    }
                }
            }

            // Copy Page Footer Summary (Page Total + Balance)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ledgerColors.khataStrip.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page Total: ${CurrencyFormatter.formatRupees(page.hisab.itemsSubtotal, true)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Balance: ${CurrencyFormatter.formatRupees(page.closingBalance, true)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (page.closingBalance > 0.01) ledgerColors.pendingAmber else ledgerColors.settledGreen
                )
            }
        }
    }
}

private fun Modifier.draw5ColVerticalLines(
    solidLineColor: Color,
    dottedLineColor: Color
): Modifier = this.drawBehind {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
    val dateX = (6.dp + COL_DATE_WIDTH).toPx()
    drawLine(
        color = solidLineColor.copy(alpha = 0.7f),
        start = Offset(dateX, 0f),
        end = Offset(dateX, size.height),
        strokeWidth = 2f
    )
    val totalX = size.width - (6.dp + COL_TOTAL_WIDTH).toPx()
    val rateX = totalX - COL_RATE_WIDTH.toPx()
    val pcsX = rateX - COL_PCS_WIDTH.toPx()

    listOf(pcsX, rateX, totalX).forEach { x ->
        drawLine(
            color = dottedLineColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.4f,
            pathEffect = dashEffect
        )
    }
}

private fun Modifier.drawHorizontalDottedRule(color: Color): Modifier = this.drawBehind {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
    drawLine(
        color = color,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.2f,
        pathEffect = dashEffect
    )
}

@Composable
private fun CopyPage5ColItemRow(
    item: HisabLineItemEntity,
    dateMillis: Long,
    horizontalDottedColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawHorizontalDottedRule(horizontalDottedColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = DateFormatter.formatCompact(dateMillis),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(COL_DATE_WIDTH)
                .padding(start = 2.dp, end = 4.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 4.dp)
        ) {
            val sub = if (item.subCategoryName.isNotBlank()) " (${item.subCategoryName})" else ""
            Text(
                text = "${item.productName}$sub",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = CurrencyFormatter.formatQuantity(item.quantity),
            style = TabularAmountStyle.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .width(COL_PCS_WIDTH)
                .padding(start = 5.dp, end = 3.dp),
            maxLines = 1
        )

        Text(
            text = CurrencyFormatter.formatRate(item.unitRate),
            style = TabularAmountStyle.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .width(COL_RATE_WIDTH)
                .padding(start = 5.dp, end = 3.dp),
            maxLines = 1
        )

        Text(
            text = CurrencyFormatter.formatRupees(item.lineTotal, false),
            style = TabularAmountStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .width(COL_TOTAL_WIDTH)
                .padding(start = 5.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun CopyPage5ColOtherRow(
    dateText: String,
    itemText: String,
    pcsText: String,
    rateText: String,
    totalText: String,
    accentColor: Color,
    horizontalDottedColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawHorizontalDottedRule(horizontalDottedColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(COL_DATE_WIDTH)
                .padding(start = 2.dp, end = 4.dp)
        )
        Text(
            text = itemText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 4.dp)
        )
        Text(
            text = pcsText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(COL_PCS_WIDTH)
                .padding(start = 5.dp, end = 3.dp)
        )
        Text(
            text = rateText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(COL_RATE_WIDTH)
                .padding(start = 5.dp, end = 3.dp)
        )
        Text(
            text = totalText,
            style = TabularAmountStyle.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .width(COL_TOTAL_WIDTH)
                .padding(start = 5.dp),
            maxLines = 1
        )
    }
}

private data class StandaloneDisplayData(
    val title: String,
    val dateLabel: String,
    val amountText: String,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun StandaloneAdjustmentCard(
    entry: CopyPageEntry,
    runningBalanceAfter: Double,
    onEditMisc: (MiscAdjustmentEntity) -> Unit,
    onEditPayment: (PaymentEntity) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val display = when (entry) {
        is CopyPageEntry.MiscEntry -> {
            val m = entry.misc
            val sign = if (m.isAddition) "+" else "-"
            val col = if (m.isAddition) MaterialTheme.colorScheme.primary else ledgerColors.settledGreen
            StandaloneDisplayData(
                title = "Misc ($sign): ${m.description}",
                dateLabel = m.dateMillis?.let { DateFormatter.formatShort(it) } ?: "No Date",
                amountText = "$sign${CurrencyFormatter.formatRupees(m.amount, true)}",
                accentColor = col,
                onClick = { onEditMisc(m) }
            )
        }
        is CopyPageEntry.PaymentEntry -> {
            val p = entry.payment
            val note = if (p.note.isNotBlank()) " • ${p.note}" else ""
            StandaloneDisplayData(
                title = "Advance Paid (${p.paymentMode})$note",
                dateLabel = DateFormatter.formatShort(p.dateMillis),
                amountText = "-${CurrencyFormatter.formatRupees(p.amount, true)}",
                accentColor = ledgerColors.settledGreen,
                onClick = { onEditPayment(p) }
            )
        }
        is CopyPageEntry.ItemEntry -> return
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = display.onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = display.accentColor
                )
                Text(
                    text = "${display.dateLabel} • Balance ${CurrencyFormatter.formatRupees(runningBalanceAfter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = display.amountText,
                style = TabularAmountStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = display.accentColor
                )
            )
        }
    }
}

@Composable
fun SoftPartyPickerDialog(
    parties: List<PartyBalanceSummary>,
    selectedPartyId: Long?,
    onSelectParty: (Long) -> Unit,
    onAddNewParty: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var search by remember { mutableStateOf("") }
    val filtered = remember(parties, search) {
        if (search.isBlank()) parties
        else parties.filter { it.party.name.contains(search.trim(), ignoreCase = true) }
    }

    SoftDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Choose Party", style = MaterialTheme.typography.titleLarge)
            if (onAddNewParty != null) {
                TextButton(onClick = onAddNewParty) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Party")
                }
            }
        }

        if (parties.size > 4) {
            Spacer(Modifier.height(8.dp))
            SlimSearchBox(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search party by name"
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.party.id }) { summary ->
                val isSelected = summary.party.id == selectedPartyId
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else ledgerColors.khataStrip,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else ledgerColors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectParty(summary.party.id) }
                        .testTag("picker_party_${summary.party.id}")
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
                                text = summary.party.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${summary.productCount} ${if (summary.productCount == 1) "item" else "items"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        BalanceBadge(balance = summary.currentBalance)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun SoftProductPickerDialog(
    products: List<ProductWithDetails>,
    selectedProductId: Long?,
    onSelectProduct: (ProductWithDetails) -> Unit,
    onCreateNewProduct: () -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var search by remember { mutableStateOf("") }
    val filtered = remember(products, search) {
        if (search.isBlank()) products
        else products.filter { it.product.name.contains(search.trim(), ignoreCase = true) }
    }

    SoftDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Choose Item (${products.size})", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onCreateNewProduct) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }

        Spacer(Modifier.height(8.dp))
        SlimSearchBox(
            query = search,
            onQueryChange = { search = it },
            placeholder = "Search item by name"
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.product.id }) { prod ->
                val isSelected = prod.product.id == selectedProductId
                val defaultTotal = prod.product.baseRate + prod.workSteps.filter { it.isDefaultSelected }.sumOf { it.stepRate }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else ledgerColors.khataStrip,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else ledgerColors.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProduct(prod) }
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
                                text = prod.product.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val stepsTxt = if (prod.workSteps.isNotEmpty()) {
                                "${prod.workSteps.size} steps • Base ${CurrencyFormatter.formatRate(prod.product.baseRate)}"
                            } else {
                                "Base ${CurrencyFormatter.formatRate(prod.product.baseRate)} / ${prod.product.defaultUnit}"
                            }
                            Text(
                                text = stepsTxt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = CurrencyFormatter.formatRate(defaultTotal),
                            style = TabularAmountStyle.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun SlimSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun SheetSectionHeading(
    title: String,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        trailingAction?.invoke()
    }
}

@Composable
private fun FixedOptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else ledgerColors.khataStrip,
        animationSpec = tween(180),
        label = "tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else ledgerColors.borderSubtle,
        animationSpec = tween(180),
        label = "tile_border"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RegisterAddEntrySheet(
    party: PartyEntity,
    products: List<ProductWithDetails>,
    initialDateMillis: Long,
    editingItem: EditingItemContext?,
    onSaveProduct: (ProductEntity, List<ProductSubCategoryEntity>, List<WorkStepEntity>, (Long) -> Unit) -> Unit,
    onAddItem: (dateMillis: Long, item: HisabLineItemEntity) -> Unit,
    onUpdateItem: (oldItem: HisabLineItemEntity, newItem: HisabLineItemEntity, dateMillis: Long) -> Unit,
    onDeleteItem: (HisabLineItemEntity) -> Unit,
    onAddMisc: (description: String, amount: Double, isAddition: Boolean, dateMillis: Long?) -> Unit,
    onAddPayment: (amount: Double, mode: String, note: String, dateMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val isEditMode = editingItem != null

    var entryMode by remember { mutableIntStateOf(0) } // 0=Item, 1=Misc(+/-), 2=Payment
    var showDatePicker by remember { mutableStateOf(false) }
    var chosenDateMillis by remember { mutableLongStateOf(editingItem?.dateMillis ?: initialDateMillis) }

    // Item Builder State (Saved Products only — Quick Custom removed)
    var showProductPicker by remember { mutableStateOf(false) }
    var showNewProductDialog by remember { mutableStateOf(false) }

    val matchedInitialProduct = remember(products, editingItem) {
        if (editingItem != null) {
            products.find { it.product.name.equals(editingItem.item.productName, ignoreCase = true) }
        } else {
            products.firstOrNull()
        }
    }

    var selectedProductId by remember { mutableStateOf(matchedInitialProduct?.product?.id) }
    var selectedSubCatId by remember {
        mutableStateOf(
            if (editingItem != null && matchedInitialProduct != null && editingItem.item.subCategoryName.isNotBlank()) {
                matchedInitialProduct.subCategories.find {
                    it.name.equals(editingItem.item.subCategoryName, ignoreCase = true)
                }?.id
            } else null
        )
    }
    var selectedStepIds by remember {
        mutableStateOf(
            if (editingItem != null && matchedInitialProduct != null) {
                val appliedNames = editingItem.item.appliedStepsSummary
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                matchedInitialProduct.workSteps
                    .filter { it.name.trim().lowercase() in appliedNames }
                    .map { it.id }
                    .toSet()
            } else {
                matchedInitialProduct?.workSteps?.filter { it.isDefaultSelected }?.map { it.id }?.toSet() ?: emptySet()
            }
        )
    }

    val selectedProduct = products.find { it.product.id == selectedProductId } ?: products.firstOrNull()
    val activeSubCat = selectedProduct?.subCategories?.find { it.id == selectedSubCatId }

    fun computeProductRate(prod: ProductWithDetails?, sub: ProductSubCategoryEntity?, stepIds: Set<Long>): Double {
        if (prod == null) return 0.0
        return prod.product.baseRate +
            (sub?.rateAdjustment ?: 0.0) +
            prod.workSteps.filter { it.id in stepIds }.sumOf { it.stepRate }
    }

    fun formatCleanNum(value: Double): String {
        return if (value == 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    var rateText by remember {
        mutableStateOf(
            if (editingItem != null) formatCleanNum(editingItem.item.unitRate)
            else formatCleanNum(computeProductRate(selectedProduct, activeSubCat, selectedStepIds))
        )
    }
    var quantityText by remember {
        mutableStateOf(if (editingItem != null) formatCleanNum(editingItem.item.quantity) else "")
    }

    // Misc (+/-) state
    var miscIsAddition by remember { mutableStateOf(true) }
    var miscDescription by remember { mutableStateOf("") }
    var miscAmountText by remember { mutableStateOf("") }
    var miscIncludeDate by remember { mutableStateOf(true) }

    // Payment state
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var paymentNote by remember { mutableStateOf("") }
    var paymentAddedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(paymentAddedFeedback) {
        if (paymentAddedFeedback) {
            delay(650)
            onDismiss()
        }
    }

    var sheetError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditMode) "Edit Register Item" else "Add to Register",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DateFormatter.formatShort(chosenDateMillis), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!isEditMode) {
                // Fixed-shape 3-tab selector (never changes shape on click)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Item", "Misc (+/-)", "Payment").forEachIndexed { idx, label ->
                        FixedOptionTile(
                            label = label,
                            selected = entryMode == idx,
                            onClick = {
                                entryMode = idx
                                sheetError = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_mode_$idx")
                        )
                    }
                }
            }

            when (entryMode) {
                0 -> {
                    // Section 1: ITEM
                    SheetSectionHeading(
                        title = "1. ITEM",
                        trailingAction = {
                            TextButton(
                                onClick = { showNewProductDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Item")
                            }
                        }
                    )

                    if (products.isEmpty() && !isEditMode) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.khataStrip,
                            border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("No Items for ${party.name}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Create an item with base rate and work steps to add entries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showNewProductDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Item")
                                }
                            }
                        }
                    } else if (selectedProduct != null) {
                        // Scalable Searchable Item Selector Card (works cleanly for 2 or 200+ items)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.khataStrip,
                            border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showProductPicker = true }
                                .testTag("sheet_product_selector")
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
                                        text = selectedProduct.product.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Base ${CurrencyFormatter.formatRate(selectedProduct.product.baseRate)} / ${selectedProduct.product.defaultUnit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Change",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Choose item",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Section 2: SUB-CATEGORY (only if product has variants)
                        if (selectedProduct.subCategories.isNotEmpty()) {
                            HorizontalDivider(color = ledgerColors.borderSubtle)
                            SheetSectionHeading(title = "2. SUB-CATEGORY")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FixedOptionTile(
                                    label = "Standard",
                                    selected = selectedSubCatId == null,
                                    onClick = {
                                        selectedSubCatId = null
                                        rateText = formatCleanNum(
                                            computeProductRate(selectedProduct, null, selectedStepIds)
                                        )
                                    }
                                )
                                selectedProduct.subCategories.forEach { sc ->
                                    FixedOptionTile(
                                        label = sc.name,
                                        selected = selectedSubCatId == sc.id,
                                        onClick = {
                                            selectedSubCatId = sc.id
                                            rateText = formatCleanNum(
                                                computeProductRate(selectedProduct, sc, selectedStepIds)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Section 3: WORK STEPS (high-contrast SoftSwitch rows — never morphs shape)
                        if (selectedProduct.workSteps.isNotEmpty()) {
                            HorizontalDivider(color = ledgerColors.borderSubtle)
                            val secNum = if (selectedProduct.subCategories.isNotEmpty()) 3 else 2
                            SheetSectionHeading(title = "$secNum. WORK STEPS")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedProduct.workSteps.forEach { step ->
                                    val checked = step.id in selectedStepIds
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = ledgerColors.khataStrip,
                                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val nextSet = if (checked) selectedStepIds - step.id else selectedStepIds + step.id
                                                    selectedStepIds = nextSet
                                                    rateText = formatCleanNum(
                                                        computeProductRate(selectedProduct, activeSubCat, nextSet)
                                                    )
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = step.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "+${CurrencyFormatter.formatRate(step.stepRate)} per ${selectedProduct.product.defaultUnit}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            SoftSwitch(
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    val nextSet = if (isChecked) selectedStepIds + step.id else selectedStepIds - step.id
                                                    selectedStepIds = nextSet
                                                    rateText = formatCleanNum(
                                                        computeProductRate(selectedProduct, activeSubCat, nextSet)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedProduct != null || isEditMode) {
                        HorizontalDivider(color = ledgerColors.borderSubtle)
                        val qtySecNum = 2 +
                            (if (selectedProduct?.subCategories?.isNotEmpty() == true) 1 else 0) +
                            (if (selectedProduct?.workSteps?.isNotEmpty() == true) 1 else 0)
                        SheetSectionHeading(title = "$qtySecNum. PIECES & RATE")

                        val unitLabel = selectedProduct?.product?.defaultUnit ?: editingItem?.item?.unitLabel ?: "pcs"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        quantityText = input
                                        sheetError = null
                                    }
                                },
                                label = { Text("Pieces ($unitLabel)") },
                                placeholder = { Text("1000") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sheet_quantity_input")
                            )

                            OutlinedTextField(
                                value = rateText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        rateText = input
                                        sheetError = null
                                    }
                                },
                                label = { Text("Rate (₹)") },
                                placeholder = { Text("4.50") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sheet_rate_input")
                            )
                        }

                        val qty = quantityText.toDoubleOrNull() ?: 0.0
                        val rateVal = rateText.toDoubleOrNull() ?: 0.0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isEditMode && editingItem != null) {
                                OutlinedButton(
                                    onClick = { onDeleteItem(editingItem.item) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ledgerColors.dangerRed),
                                    border = BorderStroke(1.dp, ledgerColors.dangerRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("sheet_delete_item_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }

                            Button(
                                onClick = {
                                    if (qty <= 0.0) {
                                        haptics.reject()
                                        sheetError = "Enter a valid number of pieces"
                                        return@Button
                                    }
                                    if (rateVal <= 0.0) {
                                        haptics.reject()
                                        sheetError = "Enter a valid rate"
                                        return@Button
                                    }
                                    val newItem = if (selectedProduct != null) {
                                        val applied = selectedProduct.workSteps
                                            .filter { it.id in selectedStepIds }
                                            .joinToString(", ") { it.name }
                                        val skipped = selectedProduct.workSteps
                                            .filter { it.id !in selectedStepIds }
                                            .joinToString(", ") { it.name }
                                        HisabLineItemEntity(
                                            hisabId = editingItem?.item?.hisabId ?: 0L,
                                            productName = selectedProduct.product.name,
                                            subCategoryName = activeSubCat?.name ?: "",
                                            appliedStepsSummary = applied,
                                            skippedStepsSummary = skipped,
                                            unitLabel = selectedProduct.product.defaultUnit,
                                            quantity = qty,
                                            unitRate = rateVal,
                                            lineTotal = qty * rateVal,
                                            note = ""
                                        )
                                    } else {
                                        editingItem!!.item.copy(
                                            quantity = qty,
                                            unitRate = rateVal,
                                            lineTotal = qty * rateVal
                                        )
                                    }

                                    haptics.confirm()
                                    if (isEditMode && editingItem != null) {
                                        onUpdateItem(editingItem.item, newItem, chosenDateMillis)
                                    } else {
                                        onAddItem(chosenDateMillis, newItem)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("sheet_add_item_submit")
                            ) {
                                val actionLabel = if (isEditMode) "Update" else "Add Item"
                                Text("$actionLabel • ${CurrencyFormatter.formatRupees(qty * rateVal, true)}")
                            }
                        }
                    }
                }

                1 -> {
                    SheetSectionHeading(title = "1. TYPE")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FixedOptionTile(
                            label = "+ Addition",
                            selected = miscIsAddition,
                            onClick = { miscIsAddition = true },
                            modifier = Modifier.weight(1f)
                        )
                        FixedOptionTile(
                            label = "- Deduction",
                            selected = !miscIsAddition,
                            onClick = { miscIsAddition = false },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = ledgerColors.borderSubtle)
                    SheetSectionHeading(title = "2. DETAILS")

                    OutlinedTextField(
                        value = miscDescription,
                        onValueChange = {
                            miscDescription = it
                            sheetError = null
                        },
                        label = { Text("Particulars / Reason") },
                        placeholder = { Text("e.g. Freight, Packing, Shortage") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_misc_desc")
                    )

                    OutlinedTextField(
                        value = miscAmountText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                miscAmountText = input
                                sheetError = null
                            }
                        },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_misc_amount")
                    )

                    HorizontalDivider(color = ledgerColors.borderSubtle)
                    SheetSectionHeading(title = "3. DATE COLUMN")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show date in Date column", style = MaterialTheme.typography.bodyMedium)
                        SoftSwitch(checked = miscIncludeDate, onCheckedChange = { miscIncludeDate = it })
                    }

                    Button(
                        onClick = {
                            val amt = miscAmountText.toDoubleOrNull()
                            if (miscDescription.trim().isEmpty() || amt == null || amt <= 0.0) {
                                haptics.reject()
                                sheetError = "Enter description and valid amount"
                                return@Button
                            }
                            haptics.confirm()
                            onAddMisc(
                                miscDescription.trim(),
                                amt,
                                miscIsAddition,
                                if (miscIncludeDate) chosenDateMillis else null
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sheet_misc_submit")
                    ) {
                        Text(if (miscIsAddition) "Add Misc (+)" else "Subtract Misc (-)")
                    }
                }

                2 -> {
                    SheetSectionHeading(title = "1. AMOUNT RECEIVED")
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                paymentAmountText = input
                                sheetError = null
                            }
                        },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_payment_amount")
                    )

                    HorizontalDivider(color = ledgerColors.borderSubtle)
                    SheetSectionHeading(title = "2. PAYMENT MODE")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Cash", "UPI", "Bank", "Cheque").forEach { mode ->
                            FixedOptionTile(
                                label = mode,
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode }
                            )
                        }
                    }

                    HorizontalDivider(color = ledgerColors.borderSubtle)
                    SheetSectionHeading(title = "3. NOTE (OPTIONAL)")
                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        label = { Text("Note") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val sheetPayBtnColor by animateColorAsState(
                        targetValue = if (paymentAddedFeedback) Color(0xFF1B5E20) else ledgerColors.settledGreen,
                        animationSpec = tween(250),
                        label = "sheet_pay_btn_color"
                    )
                    Button(
                        onClick = {
                            if (paymentAddedFeedback) return@Button
                            val amt = paymentAmountText.toDoubleOrNull()
                            if (amt == null || amt <= 0.0) {
                                haptics.reject()
                                sheetError = "Enter a valid payment amount"
                                return@Button
                            }
                            haptics.confirm()
                            onAddPayment(amt, paymentMode, paymentNote.trim(), chosenDateMillis)
                            paymentAddedFeedback = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = sheetPayBtnColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sheet_payment_submit")
                    ) {
                        AnimatedContent(
                            targetState = paymentAddedFeedback,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.85f, animationSpec = tween(220)) + fadeIn(tween(200))) togetherWith
                                    fadeOut(tween(150))
                            },
                            label = "sheet_pay_btn_content"
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
                                Text("Record Payment")
                            }
                        }
                    }
                }
            }

            if (sheetError != null) {
                Text(
                    text = sheetError!!,
                    color = ledgerColors.dangerRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = chosenDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            chosenDateMillis = DateFormatter.mergeUtcPickerDate(it)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showProductPicker) {
        SoftProductPickerDialog(
            products = products,
            selectedProductId = selectedProduct?.product?.id,
            onSelectProduct = { prod ->
                selectedProductId = prod.product.id
                selectedSubCatId = null
                val defaults = prod.workSteps.filter { it.isDefaultSelected }.map { it.id }.toSet()
                selectedStepIds = defaults
                rateText = formatCleanNum(computeProductRate(prod, null, defaults))
                showProductPicker = false
            },
            onCreateNewProduct = {
                showProductPicker = false
                showNewProductDialog = true
            },
            onDismiss = { showProductPicker = false }
        )
    }

    if (showNewProductDialog) {
        ProductEditorDialog(
            partyId = party.id,
            onSave = { prod, subs, steps ->
                onSaveProduct(prod, subs, steps) { newId ->
                    selectedProductId = newId
                    selectedSubCatId = null
                    val totalRate = prod.baseRate + steps.sumOf { it.stepRate }
                    rateText = formatCleanNum(totalRate)
                }
                showNewProductDialog = false
            },
            onDismiss = { showNewProductDialog = false }
        )
    }
}

@Composable
private fun EditMiscDialog(
    misc: MiscAdjustmentEntity,
    onSave: (MiscAdjustmentEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var desc by remember { mutableStateOf(misc.description) }
    var amtText by remember {
        mutableStateOf(
            if (misc.amount % 1.0 == 0.0) misc.amount.toLong().toString() else misc.amount.toString()
        )
    }
    var isAdd by remember { mutableStateOf(misc.isAddition) }
    var includeDate by remember { mutableStateOf(misc.dateMillis != null) }

    SoftDialog(onDismiss = onDismiss) {
        Text("Edit Misc Entry", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FixedOptionTile(
                label = "+ Addition",
                selected = isAdd,
                onClick = { isAdd = true },
                modifier = Modifier.weight(1f)
            )
            FixedOptionTile(
                label = "- Deduction",
                selected = !isAdd,
                onClick = { isAdd = false },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Particulars") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = amtText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amtText = input
                }
            },
            label = { Text("Amount (₹)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show date in Date column", style = MaterialTheme.typography.bodyMedium)
            SoftSwitch(checked = includeDate, onCheckedChange = { includeDate = it })
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ledgerColors.dangerRed),
                border = BorderStroke(1.dp, ledgerColors.dangerRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                onClick = {
                    val amt = amtText.toDoubleOrNull() ?: return@Button
                    if (desc.trim().isEmpty() || amt <= 0.0) return@Button
                    onSave(
                        misc.copy(
                            description = desc.trim(),
                            amount = amt,
                            isAddition = isAdd,
                            dateMillis = if (includeDate) (misc.dateMillis ?: misc.createdAt) else null
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Update")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPaymentDialog(
    payment: PaymentEntity,
    onSave: (PaymentEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var amtText by remember {
        mutableStateOf(
            if (payment.amount % 1.0 == 0.0) payment.amount.toLong().toString() else payment.amount.toString()
        )
    }
    var mode by remember { mutableStateOf(payment.paymentMode) }
    var note by remember { mutableStateOf(payment.note) }

    SoftDialog(onDismiss = onDismiss) {
        Text("Edit Payment", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amtText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amtText = input
                }
            },
            label = { Text("Amount (₹)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Cash", "UPI", "Bank", "Cheque").forEach { m ->
                FixedOptionTile(label = m, selected = mode == m, onClick = { mode = m })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ledgerColors.dangerRed),
                border = BorderStroke(1.dp, ledgerColors.dangerRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                onClick = {
                    val amt = amtText.toDoubleOrNull() ?: return@Button
                    if (amt <= 0.0) return@Button
                    onSave(
                        payment.copy(
                            amount = amt,
                            paymentMode = mode,
                            note = note.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ledgerColors.settledGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Update")
            }
        }
    }
}
