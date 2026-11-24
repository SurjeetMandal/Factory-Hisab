package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.CompanyProfileEntity
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.HisabEntity
import com.example.data.local.HisabLineItemEntity
import com.example.data.local.HisabWithItems
import com.example.data.local.MiscAdjustmentEntity
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductWithDetails
import com.example.data.local.SharedSlipEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.SoftDialog
import com.example.ui.components.SoftSwitch
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.HandwrittenSlipRenderer
import com.example.util.SlipRenderData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HisabSlipUiState(
    val companyProfile: CompanyProfileEntity? = null,
    val hisabWithItems: HisabWithItems? = null,
    val partySummary: PartyBalanceSummary? = null,
    val productsForParty: List<ProductWithDetails> = emptyList(),
    val attachedMiscs: List<MiscAdjustmentEntity> = emptyList(),
    val attachedPayments: List<PaymentEntity> = emptyList(),
    val isLatestHisab: Boolean = false,
    val computedPrevBalance: Double = 0.0,
    val computedMiscNet: Double = 0.0,
    val computedAdvancePaid: Double = 0.0,
    val computedBillBalance: Double = 0.0,
    val totalPaidAfterBill: Double = 0.0
)

private data class SlipLedgerInputs(
    val company: CompanyProfileEntity?,
    val summary: PartyBalanceSummary?,
    val hisabsChrono: List<HisabWithItems>,
    val paymentsChrono: List<PaymentEntity>,
    val miscs: List<MiscAdjustmentEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class HisabSlipViewModel(
    hisabId: Long,
    private val repository: FactoryHisabRepository
) : ViewModel() {

    private val hisabFlow = repository.observeHisabById(hisabId)

    val uiState: StateFlow<HisabSlipUiState> = hisabFlow.flatMapLatest { hisab ->
        if (hisab == null) {
            flowOf(HisabSlipUiState())
        } else {
            val partyId = hisab.hisab.partyId
            val ledgerFlow = combine(
                repository.observeCompanyProfile(),
                repository.observePartySummary(partyId),
                repository.observeHisabsForPartyChronological(partyId),
                repository.observePaymentsForPartyChronological(partyId),
                repository.observeMiscAdjustmentsForParty(partyId)
            ) { company, summary, hisabsChrono, paymentsChrono, miscs ->
                SlipLedgerInputs(company, summary, hisabsChrono, paymentsChrono, miscs)
            }

            combine(
                ledgerFlow,
                repository.observeProductsForParty(partyId)
            ) { inputs, products ->
                val targetIndex = inputs.hisabsChrono.indexOfFirst { it.hisab.id == hisab.hisab.id }
                val isLatest = targetIndex != -1 && targetIndex == inputs.hisabsChrono.lastIndex

                val miscsByHisab = mutableMapOf<Long, MutableList<MiscAdjustmentEntity>>()
                inputs.miscs.forEach { m ->
                    val target = if (m.hisabId != null) {
                        inputs.hisabsChrono.find { it.hisab.id == m.hisabId }
                    } else {
                        val mTs = m.dateMillis ?: m.createdAt
                        inputs.hisabsChrono.firstOrNull { it.hisab.dateMillis >= mTs }
                    }
                    if (target != null) {
                        miscsByHisab.getOrPut(target.hisab.id) { mutableListOf() }.add(m)
                    }
                }

                val paymentsByHisab = mutableMapOf<Long, MutableList<PaymentEntity>>()
                var unattachedAfterLatest = 0.0
                inputs.paymentsChrono.forEach { p ->
                    val target = if (p.hisabId != null) {
                        inputs.hisabsChrono.find { it.hisab.id == p.hisabId }
                    } else {
                        inputs.hisabsChrono.firstOrNull { it.hisab.dateMillis >= p.dateMillis }
                    }
                    if (target != null) {
                        paymentsByHisab.getOrPut(target.hisab.id) { mutableListOf() }.add(p)
                    } else if (isLatest && p.dateMillis > hisab.hisab.dateMillis) {
                        unattachedAfterLatest += p.amount
                    }
                }

                var running = inputs.summary?.party?.openingBalance ?: 0.0
                var prevBalForTarget = hisab.hisab.previousBalanceSnapshot
                var miscNetForTarget = 0.0
                var advancePaidForTarget = hisab.hisab.paymentIncluded
                var billBalForTarget = hisab.hisab.netBalanceSnapshot

                inputs.hisabsChrono.forEach { hWithItems ->
                    val h = hWithItems.hisab
                    val miscDelta = miscsByHisab[h.id].orEmpty()
                        .sumOf { if (it.isAddition) it.amount else -it.amount }
                    val paidDelta = paymentsByHisab[h.id].orEmpty().sumOf { it.amount }
                    if (h.id == hisab.hisab.id) {
                        prevBalForTarget = running
                        miscNetForTarget = miscDelta
                        advancePaidForTarget = paidDelta
                        billBalForTarget = running + h.itemsSubtotal + miscDelta - paidDelta
                    }
                    running += h.itemsSubtotal + miscDelta - paidDelta
                }

                HisabSlipUiState(
                    companyProfile = inputs.company,
                    hisabWithItems = hisab,
                    partySummary = inputs.summary,
                    productsForParty = products,
                    attachedMiscs = miscsByHisab[hisab.hisab.id].orEmpty(),
                    attachedPayments = paymentsByHisab[hisab.hisab.id].orEmpty(),
                    isLatestHisab = isLatest,
                    computedPrevBalance = prevBalForTarget,
                    computedMiscNet = miscNetForTarget,
                    computedAdvancePaid = advancePaidForTarget,
                    computedBillBalance = billBalForTarget,
                    totalPaidAfterBill = if (isLatest) unattachedAfterLatest else 0.0
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HisabSlipUiState()
    )

    fun recordSharedSlip(
        hisabId: Long,
        partyId: Long,
        partyName: String,
        hisabDateMillis: Long,
        totalAmount: Double,
        netBalanceSnapshot: Double,
        imagePath: String,
        shareFormat: String
    ) {
        viewModelScope.launch {
            repository.addSharedSlip(
                SharedSlipEntity(
                    hisabId = hisabId,
                    partyId = partyId,
                    partyName = partyName,
                    hisabDateMillis = hisabDateMillis,
                    sharedAtMillis = System.currentTimeMillis(),
                    totalAmount = totalAmount,
                    netBalanceSnapshot = netBalanceSnapshot,
                    imagePath = imagePath,
                    shareFormat = shareFormat
                )
            )
        }
    }

    fun updateHisabDate(hisabId: Long, newDateMillis: Long) {
        viewModelScope.launch {
            repository.updateHisabDate(hisabId, newDateMillis)
        }
    }

    fun updateLineItem(
        partyId: Long,
        oldItem: HisabLineItemEntity,
        newItem: HisabLineItemEntity,
        dateMillis: Long
    ) {
        viewModelScope.launch {
            val (dayStart, dayEnd) = DateFormatter.dayBounds(dateMillis)
            repository.updateRegisterLineItem(
                partyId = partyId,
                oldItem = oldItem,
                newItem = newItem,
                newDateMillis = dateMillis,
                dayStartMillis = dayStart,
                dayEndMillis = dayEnd
            )
        }
    }

    fun addLineItem(partyId: Long, dateMillis: Long, item: HisabLineItemEntity) {
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

    fun removeLineItem(
        item: HisabLineItemEntity,
        remainingCount: Int,
        onHisabEmpty: () -> Unit
    ) {
        viewModelScope.launch {
            repository.removeRegisterLineItem(item)
            if (remainingCount <= 1) {
                onHisabEmpty()
            }
        }
    }

    fun addMiscAdjustment(
        partyId: Long,
        hisabId: Long,
        dateMillis: Long,
        description: String,
        amount: Double,
        isAddition: Boolean
    ) {
        viewModelScope.launch {
            repository.addMiscAdjustment(
                MiscAdjustmentEntity(
                    partyId = partyId,
                    hisabId = hisabId,
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

    fun deleteMiscAdjustment(adjustment: MiscAdjustmentEntity) {
        viewModelScope.launch { repository.deleteMiscAdjustment(adjustment) }
    }

    fun addPayment(
        partyId: Long,
        hisabId: Long,
        dateMillis: Long,
        amount: Double,
        mode: String,
        note: String
    ) {
        viewModelScope.launch {
            repository.addPayment(
                PaymentEntity(
                    partyId = partyId,
                    hisabId = hisabId,
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

    fun deleteHisab(hisab: HisabEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteHisab(hisab, deleteLinkedPayments = false)
            onDeleted()
        }
    }
}

class HisabSlipViewModelFactory(
    private val hisabId: Long,
    private val repository: FactoryHisabRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HisabSlipViewModel(hisabId, repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabSlipScreen(
    viewModel: HisabSlipViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    var includeLiveBalance by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditHisabSheet by remember { mutableStateOf(false) }

    val hisab = state.hisabWithItems
    val party = state.partySummary

    val slipData = remember(
        state.companyProfile,
        hisab,
        party,
        includeLiveBalance,
        state.isLatestHisab,
        state.computedPrevBalance,
        state.computedMiscNet,
        state.computedAdvancePaid,
        state.computedBillBalance,
        state.totalPaidAfterBill
    ) {
        if (hisab != null && party != null) {
            val applyLive = state.isLatestHisab && includeLiveBalance
            SlipRenderData(
                companyName = state.companyProfile?.companyName ?: "",
                ownerName = state.companyProfile?.ownerName ?: "",
                partyName = party.party.name,
                partyPhone = party.party.phone,
                hisabWithItems = hisab,
                includeLiveBalance = applyLive,
                currentLiveBalance = party.currentBalance,
                totalPaidAfterBill = if (state.isLatestHisab) state.totalPaidAfterBill else 0.0,
                computedPrevBalance = state.computedPrevBalance,
                computedMiscNet = state.computedMiscNet,
                computedAdvancePaid = state.computedAdvancePaid,
                computedBillBalance = state.computedBillBalance
            )
        } else {
            null
        }
    }

    val renderedBitmap by produceState<Bitmap?>(initialValue = null, key1 = slipData) {
        value = if (slipData != null) {
            withContext(Dispatchers.Default) {
                HandwrittenSlipRenderer.renderSlipBitmap(context, slipData)
            }
        } else {
            null
        }
    }

    Scaffold(
        containerColor = ledgerColors.registerPaper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = party?.party?.name ?: "Hisab Slip",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hisab != null) {
                            Text(
                                text = DateFormatter.formatSlipDate(hisab.hisab.dateMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onBack()
                        },
                        modifier = Modifier.testTag("slip_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hisab != null) {
                        IconButton(
                            onClick = {
                                haptics.click()
                                showEditHisabSheet = true
                            },
                            modifier = Modifier.testTag("slip_edit_button")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Hisab",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.tick()
                                showDeleteDialog = true
                            },
                            modifier = Modifier.testTag("slip_delete_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Hisab",
                                tint = ledgerColors.dangerRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (slipData != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                val bmp = renderedBitmap ?: return@Button
                                val h = slipData.hisabWithItems.hisab
                                haptics.confirm()
                                scope.launch {
                                    val (savedPath, uri) = withContext(Dispatchers.IO) {
                                        HandwrittenSlipRenderer.savePermanentSharedSlip(
                                            context = context,
                                            bitmap = bmp,
                                            seedId = h.id
                                        )
                                    }
                                    val effectiveBalSnapshot = if (slipData.includeLiveBalance) {
                                        slipData.currentLiveBalance
                                    } else {
                                        state.computedBillBalance
                                    }
                                    viewModel.recordSharedSlip(
                                        hisabId = h.id,
                                        partyId = h.partyId,
                                        partyName = slipData.partyName,
                                        hisabDateMillis = h.dateMillis,
                                        totalAmount = h.itemsSubtotal,
                                        netBalanceSnapshot = effectiveBalSnapshot,
                                        imagePath = savedPath,
                                        shareFormat = "Image"
                                    )
                                    HandwrittenSlipRenderer.launchShareImageIntent(context, uri, slipData)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ledgerColors.settledGreen
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("share_slip_image_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Share Handwritten Slip (WhatsApp)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (slipData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                    .widthIn(max = 860.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isLatestHisab && state.totalPaidAfterBill > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
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
                                    "Include Later Advance Paid",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Adds ${CurrencyFormatter.formatRupees(state.totalPaidAfterBill)} received after this bill",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            SoftSwitch(
                                checked = includeLiveBalance,
                                onCheckedChange = { includeLiveBalance = it },
                                modifier = Modifier.testTag("toggle_live_balance_switch")
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val bmp = renderedBitmap
                    if (bmp == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Handwritten ruled-paper Hisab slip",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                .testTag("handwritten_slip_image")
                        )
                    }
                }
            }
        }
    }

    if (showEditHisabSheet && hisab != null) {
        EditHisabSlipSheet(
            hisab = hisab,
            products = state.productsForParty,
            attachedMiscs = state.attachedMiscs,
            attachedPayments = state.attachedPayments,
            onUpdateDate = { newMillis ->
                viewModel.updateHisabDate(hisab.hisab.id, newMillis)
            },
            onUpdateItem = { oldItem, newItem ->
                viewModel.updateLineItem(
                    partyId = hisab.hisab.partyId,
                    oldItem = oldItem,
                    newItem = newItem,
                    dateMillis = hisab.hisab.dateMillis
                )
            },
            onAddItem = { newItem ->
                viewModel.addLineItem(
                    partyId = hisab.hisab.partyId,
                    dateMillis = hisab.hisab.dateMillis,
                    item = newItem
                )
            },
            onDeleteItem = { item ->
                viewModel.removeLineItem(
                    item = item,
                    remainingCount = hisab.items.size,
                    onHisabEmpty = {
                        showEditHisabSheet = false
                        onBack()
                    }
                )
            },
            onAddMisc = { desc, amt, isAdd ->
                viewModel.addMiscAdjustment(
                    partyId = hisab.hisab.partyId,
                    hisabId = hisab.hisab.id,
                    dateMillis = hisab.hisab.dateMillis,
                    description = desc,
                    amount = amt,
                    isAddition = isAdd
                )
            },
            onUpdateMisc = viewModel::updateMiscAdjustment,
            onDeleteMisc = viewModel::deleteMiscAdjustment,
            onAddPayment = { amt, mode, note ->
                viewModel.addPayment(
                    partyId = hisab.hisab.partyId,
                    hisabId = hisab.hisab.id,
                    dateMillis = hisab.hisab.dateMillis,
                    amount = amt,
                    mode = mode,
                    note = note
                )
            },
            onUpdatePayment = viewModel::updatePayment,
            onDeletePayment = viewModel::deletePayment,
            onDismiss = { showEditHisabSheet = false }
        )
    }

    if (showDeleteDialog && hisab != null) {
        ConfirmDeleteDialog(
            title = "Delete Hisab (${DateFormatter.formatShort(hisab.hisab.dateMillis)})?",
            message = "Delete this Hisab (${CurrencyFormatter.formatRupees(hisab.hisab.itemsSubtotal)})? The party's live balance will update automatically.",
            onConfirm = {
                viewModel.deleteHisab(hisab.hisab) {
                    showDeleteDialog = false
                    onBack()
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHisabSlipSheet(
    hisab: HisabWithItems,
    products: List<ProductWithDetails>,
    attachedMiscs: List<MiscAdjustmentEntity>,
    attachedPayments: List<PaymentEntity>,
    onUpdateDate: (Long) -> Unit,
    onUpdateItem: (oldItem: HisabLineItemEntity, newItem: HisabLineItemEntity) -> Unit,
    onAddItem: (HisabLineItemEntity) -> Unit,
    onDeleteItem: (HisabLineItemEntity) -> Unit,
    onAddMisc: (description: String, amount: Double, isAddition: Boolean) -> Unit,
    onUpdateMisc: (MiscAdjustmentEntity) -> Unit,
    onDeleteMisc: (MiscAdjustmentEntity) -> Unit,
    onAddPayment: (amount: Double, mode: String, note: String) -> Unit,
    onUpdatePayment: (PaymentEntity) -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()

    var editTab by remember { mutableIntStateOf(0) } // 0=Items, 1=Misc (+/-), 2=Payments
    var showDatePicker by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<HisabLineItemEntity?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    var editingMisc by remember { mutableStateOf<MiscAdjustmentEntity?>(null) }
    var showAddMiscDialog by remember { mutableStateOf(false) }

    var editingPayment by remember { mutableStateOf<PaymentEntity?>(null) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Hisab", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        showDatePicker = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DateFormatter.formatShort(hisab.hisab.dateMillis), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Fixed-shape 3-tab selector: Items | Misc (+/-) | Payments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Items (${hisab.items.size})",
                    "Misc (${attachedMiscs.size})",
                    "Payments (${attachedPayments.size})"
                ).forEachIndexed { idx, label ->
                    val selected = editTab == idx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else ledgerColors.khataStrip,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else ledgerColors.borderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.tick()
                                editTab = idx
                            }
                            .testTag("slip_edit_tab_$idx")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = ledgerColors.borderSubtle)

            when (editTab) {
                0 -> {
                    hisab.items.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.khataStrip,
                            border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptics.click()
                                    editingItem = item
                                }
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
                                        text = item.productName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.formatQuantity(item.quantity)} ${item.unitLabel} × ${CurrencyFormatter.formatRate(item.unitRate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = CurrencyFormatter.formatRupees(item.lineTotal, true),
                                        style = TabularAmountStyle.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            haptics.click()
                                            editingItem = item
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit item", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            haptics.reject()
                                            onDeleteItem(item)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete item",
                                            tint = ledgerColors.dangerRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showAddItemDialog = true
                        },
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Item to This Hisab")
                    }
                }

                1 -> {
                    if (attachedMiscs.isEmpty()) {
                        Text(
                            text = "No misc additions or deductions on this slip yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        attachedMiscs.forEach { misc ->
                            val sign = if (misc.isAddition) "+" else "-"
                            val accent = if (misc.isAddition) MaterialTheme.colorScheme.primary else ledgerColors.settledGreen
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ledgerColors.khataStrip,
                                border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptics.click()
                                        editingMisc = misc
                                    }
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
                                            text = misc.description,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (misc.isAddition) "Addition (+)" else "Deduction (-)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accent
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$sign${CurrencyFormatter.formatRupees(misc.amount, true)}",
                                            style = TabularAmountStyle.copy(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            )
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.click()
                                                editingMisc = misc
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit misc", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                haptics.reject()
                                                onDeleteMisc(misc)
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete misc",
                                                tint = ledgerColors.dangerRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showAddMiscDialog = true
                        },
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("slip_add_misc_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Misc (+/-)")
                    }
                }

                else -> {
                    if (attachedPayments.isEmpty()) {
                        Text(
                            text = "No advance payment entries attached to this slip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        attachedPayments.forEach { pay ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ledgerColors.khataStrip,
                                border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptics.click()
                                        editingPayment = pay
                                    }
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
                                            text = "Advance Paid (${pay.paymentMode})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ledgerColors.settledGreen
                                        )
                                        val noteStr = if (pay.note.isNotBlank()) " • ${pay.note}" else ""
                                        Text(
                                            text = "${DateFormatter.formatShort(pay.dateMillis)}$noteStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "-${CurrencyFormatter.formatRupees(pay.amount, true)}",
                                            style = TabularAmountStyle.copy(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ledgerColors.settledGreen
                                            )
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.click()
                                                editingPayment = pay
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit payment", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                haptics.reject()
                                                onDeletePayment(pay)
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete payment",
                                                tint = ledgerColors.dangerRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showAddPaymentDialog = true
                        },
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("slip_add_payment_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ledgerColors.settledGreen)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Payment to Slip", color = ledgerColors.settledGreen)
                    }
                }
            }

            Button(
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done")
            }

            Spacer(Modifier.height(14.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = hisab.hisab.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            haptics.confirm()
                            onUpdateDate(DateFormatter.mergeUtcPickerDate(it))
                        }
                        showDatePicker = false
                    }
                ) { Text("Update Date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    editingItem?.let { target ->
        EditSlipLineItemDialog(
            title = "Edit Item",
            initialName = target.productName,
            initialUnit = target.unitLabel,
            initialQuantity = target.quantity,
            initialRate = target.unitRate,
            products = products,
            onSave = { name, unit, qty, rate ->
                onUpdateItem(
                    target,
                    target.copy(
                        productName = name,
                        unitLabel = unit,
                        quantity = qty,
                        unitRate = rate,
                        lineTotal = qty * rate
                    )
                )
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }

    if (showAddItemDialog) {
        val defaultProduct = products.firstOrNull()
        val defaultRate = defaultProduct?.let {
            it.product.baseRate + it.workSteps.filter { s -> s.isDefaultSelected }.sumOf { s -> s.stepRate }
        } ?: 0.0
        EditSlipLineItemDialog(
            title = "Add Item",
            initialName = defaultProduct?.product?.name ?: "",
            initialUnit = defaultProduct?.product?.defaultUnit ?: "pcs",
            initialQuantity = 0.0,
            initialRate = defaultRate,
            products = products,
            onSave = { name, unit, qty, rate ->
                onAddItem(
                    HisabLineItemEntity(
                        hisabId = hisab.hisab.id,
                        productName = name,
                        unitLabel = unit,
                        quantity = qty,
                        unitRate = rate,
                        lineTotal = qty * rate
                    )
                )
                showAddItemDialog = false
            },
            onDismiss = { showAddItemDialog = false }
        )
    }

    editingMisc?.let { target ->
        SlipMiscEditorDialog(
            existing = target,
            onSave = { desc, amt, isAdd ->
                onUpdateMisc(target.copy(description = desc, amount = amt, isAddition = isAdd))
                editingMisc = null
            },
            onDismiss = { editingMisc = null }
        )
    }

    if (showAddMiscDialog) {
        SlipMiscEditorDialog(
            existing = null,
            onSave = { desc, amt, isAdd ->
                onAddMisc(desc, amt, isAdd)
                showAddMiscDialog = false
            },
            onDismiss = { showAddMiscDialog = false }
        )
    }

    editingPayment?.let { target ->
        SlipPaymentEditorDialog(
            existing = target,
            onSave = { amt, mode, note ->
                onUpdatePayment(target.copy(amount = amt, paymentMode = mode, note = note))
                editingPayment = null
            },
            onDismiss = { editingPayment = null }
        )
    }

    if (showAddPaymentDialog) {
        SlipPaymentEditorDialog(
            existing = null,
            onSave = { amt, mode, note ->
                onAddPayment(amt, mode, note)
                showAddPaymentDialog = false
            },
            onDismiss = { showAddPaymentDialog = false }
        )
    }
}

@Composable
private fun SlipMiscEditorDialog(
    existing: MiscAdjustmentEntity?,
    onSave: (description: String, amount: Double, isAddition: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()

    var isAddition by remember { mutableStateOf(existing?.isAddition ?: true) }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var amtText by remember {
        mutableStateOf(
            existing?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    SoftDialog(onDismiss = onDismiss) {
        Text(if (existing == null) "Add Misc Entry" else "Edit Misc Entry", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(true to "+ Addition", false to "- Deduction").forEach { (isAdd, label) ->
                val selected = isAddition == isAdd
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else ledgerColors.khataStrip,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ledgerColors.borderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.tick()
                            isAddition = isAdd
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = desc,
            onValueChange = {
                desc = it
                errorMsg = null
            },
            label = { Text("Particulars / Reason") },
            placeholder = { Text("e.g. Freight, Packing, Shortage") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = amtText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amtText = input
                    errorMsg = null
                }
            },
            label = { Text("Amount (₹)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(errorMsg!!, color = ledgerColors.dangerRed, style = MaterialTheme.typography.bodySmall)
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
                    val amt = amtText.toDoubleOrNull()
                    if (desc.trim().isEmpty() || amt == null || amt <= 0.0) {
                        haptics.reject()
                        errorMsg = "Enter description and valid amount"
                        return@Button
                    }
                    haptics.confirm()
                    onSave(desc.trim(), amt, isAddition)
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (existing == null) "Save Misc" else "Update")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlipPaymentEditorDialog(
    existing: PaymentEntity?,
    onSave: (amount: Double, mode: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()

    var amtText by remember {
        mutableStateOf(
            existing?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var mode by remember { mutableStateOf(existing?.paymentMode ?: "Cash") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var paymentAddedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(paymentAddedFeedback) {
        if (paymentAddedFeedback) {
            delay(500)
            val amt = amtText.toDoubleOrNull() ?: return@LaunchedEffect
            onSave(amt, mode, note.trim())
        }
    }

    SoftDialog(onDismiss = onDismiss) {
        Text(if (existing == null) "Add Payment to Slip" else "Edit Payment", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amtText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amtText = input
                    errorMsg = null
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Cash", "UPI", "Bank", "Cheque").forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = {
                        haptics.tick()
                        mode = m
                    },
                    label = { Text(m) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (Optional)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(errorMsg!!, color = ledgerColors.dangerRed, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            val btnColor by animateColorAsState(
                targetValue = if (paymentAddedFeedback) Color(0xFF1B5E20) else ledgerColors.settledGreen,
                animationSpec = tween(250),
                label = "slip_pay_btn_color"
            )
            Button(
                onClick = {
                    if (paymentAddedFeedback) return@Button
                    val amt = amtText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        haptics.reject()
                        errorMsg = "Enter a valid payment amount"
                        return@Button
                    }
                    haptics.confirm()
                    if (existing == null) {
                        paymentAddedFeedback = true
                    } else {
                        onSave(amt, mode, note.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                AnimatedContent(
                    targetState = paymentAddedFeedback,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.85f, animationSpec = tween(220)) + fadeIn(tween(200))) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "slip_pay_btn_content"
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
                        Text(if (existing == null) "Save Payment" else "Update")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSlipLineItemDialog(
    title: String,
    initialName: String,
    initialUnit: String,
    initialQuantity: Double,
    initialRate: Double,
    products: List<ProductWithDetails>,
    onSave: (name: String, unit: String, qty: Double, rate: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()

    fun cleanNum(v: Double): String =
        if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    var name by remember { mutableStateOf(initialName) }
    var unit by remember { mutableStateOf(initialUnit) }
    var qtyText by remember { mutableStateOf(cleanNum(initialQuantity)) }
    var rateText by remember { mutableStateOf(cleanNum(initialRate)) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    SoftDialog(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        if (products.isNotEmpty()) {
            Text(
                "Quick Pick Saved Item",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                products.forEach { prod ->
                    val defaultRate = prod.product.baseRate +
                        prod.workSteps.filter { it.isDefaultSelected }.sumOf { it.stepRate }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ledgerColors.khataStrip,
                        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.tick()
                                name = prod.product.name
                                unit = prod.product.defaultUnit
                                rateText = cleanNum(defaultRate)
                                errorMsg = null
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prod.product.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                CurrencyFormatter.formatRate(defaultRate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMsg = null
                },
                label = { Text("Item Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            qtyText = input
                            errorMsg = null
                        }
                    },
                    label = { Text("Pieces ($unit)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            rateText = input
                            errorMsg = null
                        }
                    },
                    label = { Text("Rate (₹)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ledgerColors.borderSubtle),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            if (errorMsg != null) {
                Text(errorMsg!!, color = ledgerColors.dangerRed, style = MaterialTheme.typography.bodySmall)
            }
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
                    val trimmedName = name.trim()
                    val qty = qtyText.toDoubleOrNull()
                    val rate = rateText.toDoubleOrNull()
                    if (trimmedName.isEmpty() || qty == null || qty <= 0.0 || rate == null || rate <= 0.0) {
                        haptics.reject()
                        errorMsg = "Enter valid item name, pieces, and rate"
                        return@Button
                    }
                    haptics.confirm()
                    onSave(trimmedName, unit.ifBlank { "pcs" }, qty, rate)
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save")
            }
        }
    }
}
