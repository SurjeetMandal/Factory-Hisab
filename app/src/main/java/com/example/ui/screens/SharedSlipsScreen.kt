package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.SharedSlipEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.LedgerCard
import com.example.ui.components.SoftDialog
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.TabularAmountStyle
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.HandwrittenSlipRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SharedSlipsUiState(
    val slips: List<SharedSlipEntity> = emptyList(),
    val searchQuery: String = ""
)

class SharedSlipsViewModel(private val repository: FactoryHisabRepository) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<SharedSlipsUiState> = combine(
        repository.observeAllSharedSlips(),
        searchQuery
    ) { allSlips, q ->
        val filtered = if (q.isBlank()) {
            allSlips
        } else {
            allSlips.filter { it.partyName.contains(q.trim(), ignoreCase = true) }
        }
        SharedSlipsUiState(slips = filtered, searchQuery = q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SharedSlipsUiState()
    )

    fun updateSearch(q: String) {
        searchQuery.value = q
    }

    fun deleteSharedSlip(slip: SharedSlipEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (slip.imagePath.isNotBlank()) {
                    runCatching { File(slip.imagePath).delete() }
                }
            }
            repository.deleteSharedSlip(slip)
        }
    }
}

class SharedSlipsViewModelFactory(private val repository: FactoryHisabRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SharedSlipsViewModel(repository) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSlipsScreen(
    viewModel: SharedSlipsViewModel,
    onBack: () -> Unit,
    onOpenLiveHisabSlip: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current

    var previewSlip by remember { mutableStateOf<SharedSlipEntity?>(null) }
    var slipToDelete by remember { mutableStateOf<SharedSlipEntity?>(null) }

    fun reshareSlip(slip: SharedSlipEntity) {
        val uri = HandwrittenSlipRenderer.getShareUriForExistingFile(context, slip.imagePath)
        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${slip.partyName} • ${DateFormatter.formatSlipDate(slip.hisabDateMillis)} • Subtotal ${CurrencyFormatter.formatRupees(slip.totalAmount, true)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Saved Slip"))
        } else if (slip.hisabId != null) {
            onOpenLiveHisabSlip(slip.hisabId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text("Saved Shared Slips")
                        Text(
                            text = "${state.slips.size} ${if (state.slips.size == 1) "slip" else "slips"} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("shared_slips_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.slips.isNotEmpty() || state.searchQuery.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            text = "Search slip by party name",
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
                                            .testTag("shared_slips_search_input")
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

                if (state.slips.isEmpty()) {
                    item {
                        LedgerCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (state.searchQuery.isNotBlank()) "No Matching Slips" else "No Shared Slips Yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (state.searchQuery.isNotBlank()) {
                                        "Try clearing your search filter."
                                    } else {
                                        "Every handwritten Hisab slip you share is automatically saved here for quick access."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(state.slips, key = { it.id }) { slip ->
                        SavedSlipCard(
                            slip = slip,
                            onPreview = { previewSlip = slip },
                            onReshare = { reshareSlip(slip) },
                            onDelete = { slipToDelete = slip }
                        )
                    }
                }
            }
        }
    }

    previewSlip?.let { slip ->
        SavedSlipPreviewDialog(
            slip = slip,
            onReshare = { reshareSlip(slip) },
            onOpenLive = if (slip.hisabId != null) {
                {
                    previewSlip = null
                    onOpenLiveHisabSlip(slip.hisabId)
                }
            } else null,
            onDismiss = { previewSlip = null }
        )
    }

    slipToDelete?.let { slip ->
        ConfirmDeleteDialog(
            title = "Delete Saved Slip?",
            message = "Remove the saved slip copy for ${slip.partyName} (${DateFormatter.formatShort(slip.hisabDateMillis)})?",
            onConfirm = {
                viewModel.deleteSharedSlip(slip)
                slipToDelete = null
            },
            onDismiss = { slipToDelete = null }
        )
    }
}

@Composable
private fun SavedSlipCard(
    slip: SharedSlipEntity,
    onPreview: () -> Unit,
    onReshare: () -> Unit,
    onDelete: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val thumbBitmap by produceState<Bitmap?>(initialValue = null, key1 = slip.imagePath) {
        value = if (slip.imagePath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(slip.imagePath)
                    if (file.exists()) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                    } else null
                }.getOrNull()
            }
        } else null
    }

    LedgerCard(
        modifier = Modifier
            .clickable(onClick = onPreview)
            .testTag("saved_slip_card_${slip.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ledgerColors.registerPaper),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!.asImageBitmap(),
                        contentDescription = "Saved slip thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slip.partyName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hisab: ${DateFormatter.formatSlipDate(slip.hisabDateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Shared ${DateFormatter.formatShort(slip.sharedAtMillis)} • ${slip.shareFormat}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Total ${CurrencyFormatter.formatRupees(slip.totalAmount, true)}",
                    style = TabularAmountStyle.copy(
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onReshare,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share saved slip",
                        tint = ledgerColors.settledGreen
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete saved slip",
                        tint = ledgerColors.dangerRed
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSlipPreviewDialog(
    slip: SharedSlipEntity,
    onReshare: () -> Unit,
    onOpenLive: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val fullBitmap by produceState<Bitmap?>(initialValue = null, key1 = slip.imagePath) {
        value = if (slip.imagePath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(slip.imagePath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }.getOrNull()
            }
        } else null
    }

    SoftDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slip.partyName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateFormatter.formatSlipDate(slip.hisabDateMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (fullBitmap != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Image(
                    bitmap = fullBitmap!!.asImageBitmap(),
                    contentDescription = "Saved handwritten slip preview",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(fullBitmap!!.width.toFloat() / fullBitmap!!.height.toFloat())
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        } else {
            Surface(
                color = ledgerColors.khataStrip,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Saved as ${slip.shareFormat} summary • ${CurrencyFormatter.formatRupees(slip.totalAmount, true)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenLive != null) {
                TextButton(
                    onClick = onOpenLive,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Live View")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onReshare,
                colors = ButtonDefaults.buttonColors(containerColor = ledgerColors.settledGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Again")
            }
        }
    }
}
