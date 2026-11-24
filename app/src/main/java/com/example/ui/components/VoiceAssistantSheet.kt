package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.LocalLedgerColors
import com.example.util.CurrencyFormatter
import com.example.voice.VoiceAssistantViewModel
import com.example.voice.VoiceLanguage
import com.example.voice.VoicePendingDraft
import com.example.voice.VoiceQuickPanel
import com.example.voice.VoiceStep
import com.example.voice.VoiceTaskType
import com.example.voice.VoiceUiState

@Composable
fun FloatingAiPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.click()
            onClick()
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("ai_assistant_pill"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Open offline voice AI",
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Ask AI",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSheet(
    viewModel: VoiceAssistantViewModel,
    state: VoiceUiState,
    onDismiss: () -> Unit
) {
    if (!state.isOpen) return

    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val ledgerColors = LocalLedgerColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var typedText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setHasMicPermission(granted)
        if (granted) {
            viewModel.toggleMicListening()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setHasMicPermission(granted)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isListening || state.isSpeaking) {
                                    Icons.Rounded.GraphicEq
                                } else {
                                    Icons.Rounded.AutoAwesome
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Hisab Voice Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val statusLabel = when {
                            state.isListening -> "Listening (${state.language.label})..."
                            state.isSpeaking -> "Speaking (${state.language.label})..."
                            else -> "Offline • 4 Tasks • ${state.language.label}"
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isListening) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            viewModel.restartConversation()
                        },
                        modifier = Modifier.testTag("ai_restart_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Restart voice assistant"
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("ai_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close voice assistant"
                        )
                    }
                }
            }

            // Language Switcher Row (Default: Hinglish)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceLanguage.entries.forEach { lang ->
                    val selected = state.language == lang
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(180),
                        label = "lang_pill_bg"
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "lang_pill_fg"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable {
                                haptics.tick()
                                viewModel.switchLanguage(lang)
                            }
                            .testTag("ai_lang_${lang.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lang.nativeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = fg
                        )
                    }
                }
            }

            // Conversation Transcript
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 230.dp)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.fromAssistant) Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = if (msg.fromAssistant) 4.dp else 16.dp,
                                    topEnd = if (msg.fromAssistant) 16.dp else 4.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 16.dp
                                ),
                                color = if (msg.fromAssistant) {
                                    ledgerColors.khataStrip
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.widthIn(max = 420.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    if (state.partialTranscript.isNotBlank()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${state.partialTranscript}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contextual Quick Panels (4 Menu Tasks / Party List Fallback / Payment Mode Chips / Confirmation Card)
            when (state.quickPanel) {
                VoiceQuickPanel.MENU_TASKS -> {
                    MenuQuickGrid(
                        language = state.language,
                        onSelectTask = {
                            haptics.click()
                            viewModel.selectMenuTask(it)
                        }
                    )
                }

                VoiceQuickPanel.PARTY_PICKER -> {
                    PartyFallbackList(
                        state = state,
                        onSelectParty = {
                            haptics.confirm()
                            viewModel.selectPartyFromList(it)
                        }
                    )
                }

                VoiceQuickPanel.ITEM_PICKER -> {
                    ProductFallbackList(
                        state = state,
                        onSelectProduct = {
                            haptics.confirm()
                            viewModel.selectProductFromList(it)
                        }
                    )
                }

                VoiceQuickPanel.PAYMENT_MODES -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Cash", "UPI", "Bank", "Cheque").forEach { mode ->
                            Surface(
                                onClick = {
                                    haptics.click()
                                    viewModel.selectPaymentModeChip(mode)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_mode_$mode")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = mode,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                VoiceQuickPanel.CONFIRMATION_CARD -> {
                    ConfirmationPreviewCard(
                        step = state.step,
                        draft = state.draft,
                        onConfirm = {
                            haptics.confirm()
                            viewModel.confirmPendingAction()
                        },
                        onCancel = {
                            haptics.tick()
                            viewModel.cancelPendingAction()
                        }
                    )
                }

                VoiceQuickPanel.NONE -> Unit
            }

            AnimatedVisibility(
                visible = state.micPermissionRequired || state.errorNotice != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ledgerColors.pendingContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.micPermissionRequired) {
                                "Allow microphone permission to speak, or type below."
                            } else {
                                state.errorNotice.orEmpty()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ledgerColors.pendingAmber,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.micPermissionRequired) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Allow Mic")
                            }
                        }
                    }
                }
            }

            // Bottom Mic + Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_pulse_scale"
                )
                val activeScale by animateFloatAsState(
                    targetValue = if (state.isListening) pulseScale else 1f,
                    animationSpec = spring(),
                    label = "mic_active_scale"
                )

                Surface(
                    onClick = {
                        haptics.click()
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.setHasMicPermission(true)
                            viewModel.toggleMicListening()
                        }
                    },
                    shape = CircleShape,
                    color = if (state.isListening) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(50.dp)
                        .graphicsLayer {
                            scaleX = activeScale
                            scaleY = activeScale
                        }
                        .testTag("ai_mic_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (state.isListening) "Stop listening" else "Tap to speak"
                        )
                    }
                }

                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    placeholder = {
                        Text(
                            text = when (state.language) {
                                VoiceLanguage.HINGLISH -> "Speak or type in Hinglish..."
                                VoiceLanguage.HINDI -> "बोलें या हिंदी में लिखें..."
                                VoiceLanguage.ENGLISH -> "Speak or type here..."
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (typedText.isNotBlank()) {
                                viewModel.submitUserInput(typedText)
                                typedText = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (typedText.isNotBlank()) {
                                    haptics.tick()
                                    viewModel.submitUserInput(typedText)
                                    typedText = ""
                                }
                            },
                            modifier = Modifier.testTag("ai_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send"
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_text_input")
                )
            }
        }
    }
}

@Composable
private fun MenuQuickGrid(
    language: VoiceLanguage,
    onSelectTask: (VoiceTaskType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskActionTile(
                icon = Icons.Rounded.AccountBalanceWallet,
                title = when (language) {
                    VoiceLanguage.HINDI -> "पेमेंट जोड़ें"
                    VoiceLanguage.HINGLISH -> "Add Payment"
                    VoiceLanguage.ENGLISH -> "Add Payment"
                },
                onClick = { onSelectTask(VoiceTaskType.ADD_PAYMENT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_task_payment")
            )
            TaskActionTile(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = when (language) {
                    VoiceLanguage.HINDI -> "नया हिसाब"
                    VoiceLanguage.HINGLISH -> "New Hisab"
                    VoiceLanguage.ENGLISH -> "New Hisab"
                },
                onClick = { onSelectTask(VoiceTaskType.ADD_HISAB) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_task_hisab")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskActionTile(
                icon = Icons.Rounded.PersonAdd,
                title = when (language) {
                    VoiceLanguage.HINDI -> "नई पार्टी"
                    VoiceLanguage.HINGLISH -> "Add Party"
                    VoiceLanguage.ENGLISH -> "Add Party"
                },
                onClick = { onSelectTask(VoiceTaskType.ADD_PARTY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_task_party")
            )
            TaskActionTile(
                icon = Icons.Rounded.Inventory2,
                title = when (language) {
                    VoiceLanguage.HINDI -> "नया आइटम"
                    VoiceLanguage.HINGLISH -> "Add Item"
                    VoiceLanguage.ENGLISH -> "Add Item"
                },
                onClick = { onSelectTask(VoiceTaskType.ADD_ITEM) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_task_item")
            )
        }
    }
}

@Composable
private fun TaskActionTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledgerColors.borderSubtle),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PartyFallbackList(
    state: VoiceUiState,
    onSelectParty: (com.example.data.local.PartyBalanceSummary) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Select Party from List",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 170.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.availableParties, key = { it.party.id }) { summary ->
                Surface(
                    onClick = { onSelectParty(summary) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_party_pick_${summary.party.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = summary.party.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        BalanceBadge(balance = summary.currentBalance)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductFallbackList(
    state: VoiceUiState,
    onSelectProduct: (com.example.data.local.ProductWithDetails) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Select Item from List",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 170.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.availableProducts, key = { it.product.id }) { product ->
                val defaultRate = product.product.baseRate +
                    product.workSteps.filter { it.isDefaultSelected }.sumOf { it.stepRate }
                val effectiveRate = if (defaultRate > 0.0) defaultRate else product.product.baseRate
                Surface(
                    onClick = { onSelectProduct(product) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ledgerColors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_item_pick_${product.product.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${CurrencyFormatter.formatRate(effectiveRate)}/${product.product.defaultUnit}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationPreviewCard(
    step: VoiceStep,
    draft: VoicePendingDraft,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ledgerColors.settledContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, ledgerColors.settledGreen.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val summaryTitle = when (step) {
                VoiceStep.PAYMENT_CONFIRM -> "Confirm Payment Entry"
                VoiceStep.HISAB_CONFIRM -> "Confirm New Hisab Entry"
                VoiceStep.PARTY_CONFIRM -> "Confirm New Party"
                VoiceStep.ITEM_CONFIRM -> "Confirm New Item"
                else -> "Confirm Entry"
            }
            Text(
                text = summaryTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ledgerColors.settledGreen
            )
            when (step) {
                VoiceStep.PAYMENT_CONFIRM -> {
                    Text(
                        text = "${draft.selectedParty?.party?.name.orEmpty()} • ${
                            CurrencyFormatter.formatRupees(draft.paymentAmount ?: 0.0)
                        } (${draft.paymentMode ?: "Cash"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                VoiceStep.HISAB_CONFIRM -> {
                    val qty = draft.hisabQuantity ?: 0.0
                    val rate = draft.hisabRate ?: 0.0
                    val item = draft.selectedProduct?.product?.name ?: draft.hisabCustomItemName.orEmpty()
                    Text(
                        text = "${draft.selectedParty?.party?.name.orEmpty()} • $item\n${
                            CurrencyFormatter.formatQuantity(qty)
                        } pcs @ ${CurrencyFormatter.formatRate(rate)} = ${
                            CurrencyFormatter.formatRupees(qty * rate)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                VoiceStep.PARTY_CONFIRM -> {
                    Text(
                        text = "${draft.newPartyName} • Opening: ${
                            CurrencyFormatter.formatRupees(draft.newPartyOpeningBalance)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                VoiceStep.ITEM_CONFIRM -> {
                    Text(
                        text = "${draft.selectedParty?.party?.name.orEmpty()} • ${draft.newItemName} (${
                            CurrencyFormatter.formatRate(draft.newItemRate ?: 0.0)
                        }/pcs)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("ai_cancel_save")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ledgerColors.settledGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("ai_confirm_save")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm & Add")
                }
            }
        }
    }
}
