package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PartyEntity
import com.example.ui.components.BalanceBadge
import com.example.ui.components.LedgerCard
import com.example.ui.components.PartyEditorDialog
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.LocalLedgerColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PartiesUiState(
    val searchQuery: String = "",
    val parties: List<PartyBalanceSummary> = emptyList(),
    val totalReceivable: Double = 0.0,
    val totalBilledAll: Double = 0.0,
    val totalPaidAll: Double = 0.0
)

class PartiesViewModel(private val repository: FactoryHisabRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<PartiesUiState> = combine(
        repository.observePartySummaries(),
        searchQuery
    ) { allParties, query ->
        val filtered = if (query.isBlank()) {
            allParties
        } else {
            allParties.filter {
                it.party.name.contains(query.trim(), ignoreCase = true) ||
                    it.party.phone.contains(query.trim(), ignoreCase = true)
            }
        }
        val totalReceivable = allParties.sumOf { maxOf(0.0, it.currentBalance) }
        val totalBilled = allParties.sumOf { it.totalBilled + it.party.openingBalance }
        val totalPaid = allParties.sumOf { it.totalPaid }
        PartiesUiState(
            searchQuery = query,
            parties = filtered,
            totalReceivable = totalReceivable,
            totalBilledAll = totalBilled,
            totalPaidAll = totalPaid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PartiesUiState()
    )

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun saveParty(party: PartyEntity) {
        viewModelScope.launch {
            repository.saveParty(party)
        }
    }
}

class PartiesViewModelFactory(private val repository: FactoryHisabRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PartiesViewModel(repository) as T
    }
}

@Composable
fun PartiesScreen(
    viewModel: PartiesViewModel,
    onOpenPartyDetail: (Long) -> Unit,
    onStartHisabForParty: (Long) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val haptics = rememberAppHaptics()

    var showAddPartyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptics.click()
                    showAddPartyDialog = true
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(bottom = contentBottomPadding + 64.dp)
                    .testTag("add_party_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_party))
            }
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
                    top = 14.dp,
                    bottom = contentBottomPadding + 148.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Parties & Items",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${state.parties.size} ${if (state.parties.size == 1) "party" else "parties"} • Tap a party to open items, hisabs & payments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.parties.isNotEmpty() || state.searchQuery.isNotEmpty()) {
                    item {
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
                                    if (state.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search party by name",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BasicTextField(
                                        value = state.searchQuery,
                                        onValueChange = viewModel::updateSearch,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("party_search_input")
                                    )
                                }
                                if (state.searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { viewModel.updateSearch("") }
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.parties.isEmpty()) {
                    item {
                        EmptyPartiesCard(
                            isSearchResult = state.searchQuery.isNotBlank(),
                            onAddParty = { showAddPartyDialog = true }
                        )
                    }
                } else {
                    items(state.parties, key = { it.party.id }) { summary ->
                        CompactPartyCard(
                            summary = summary,
                            onClick = {
                                haptics.click()
                                onOpenPartyDetail(summary.party.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddPartyDialog) {
        PartyEditorDialog(
            onSave = { party ->
                viewModel.saveParty(party)
                showAddPartyDialog = false
            },
            onDismiss = { showAddPartyDialog = false }
        )
    }
}

@Composable
private fun CompactPartyCard(
    summary: PartyBalanceSummary,
    onClick: () -> Unit
) {
    LedgerCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("party_card_${summary.party.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${summary.productCount} ${if (summary.productCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BalanceBadge(balance = summary.currentBalance)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Open party details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyPartiesCard(
    isSearchResult: Boolean,
    onAddParty: () -> Unit
) {
    LedgerCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isSearchResult) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_ledger_1795508159108),
                    contentDescription = "Factory khata desk illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            Text(
                text = if (isSearchResult) "No matching party found" else stringResource(R.string.empty_parties_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isSearchResult) {
                    "Try searching with a different party name or clear your filter."
                } else {
                    stringResource(R.string.empty_parties_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isSearchResult) {
                Button(
                    onClick = onAddParty,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_add_party_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Your First Party")
                }
            }
        }
    }
}
