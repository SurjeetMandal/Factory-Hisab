package com.example.voice

import com.example.data.local.PartyBalanceSummary
import com.example.data.local.ProductWithDetails

enum class VoiceLanguage(val label: String, val nativeLabel: String, val localeTag: String) {
    HINGLISH("Hinglish", "Hinglish", "hi-IN"),
    HINDI("Hindi", "हिंदी", "hi-IN"),
    ENGLISH("English", "English", "en-IN")
}

enum class VoiceTaskType {
    ADD_PAYMENT,
    ADD_HISAB,
    ADD_PARTY,
    ADD_ITEM
}

enum class VoiceStep {
    MENU,
    PAYMENT_PARTY,
    PAYMENT_AMOUNT_AND_MODE,
    PAYMENT_MODE_ONLY,
    PAYMENT_CONFIRM,
    HISAB_PARTY,
    HISAB_ITEM,
    HISAB_QUANTITY,
    HISAB_RATE,
    HISAB_CONFIRM,
    PARTY_NAME,
    PARTY_OPENING_BALANCE,
    PARTY_CONFIRM,
    ITEM_PARTY,
    ITEM_NAME,
    ITEM_RATE,
    ITEM_CONFIRM,
    COMPLETED
}

data class VoiceChatMessage(
    val id: Long,
    val fromAssistant: Boolean,
    val text: String
)

enum class VoiceQuickPanel {
    NONE,
    MENU_TASKS,
    PARTY_PICKER,
    ITEM_PICKER,
    PAYMENT_MODES,
    CONFIRMATION_CARD
}

data class VoicePendingDraft(
    val task: VoiceTaskType? = null,
    val selectedParty: PartyBalanceSummary? = null,
    val selectedProduct: ProductWithDetails? = null,
    val paymentAmount: Double? = null,
    val paymentMode: String? = null,
    val hisabQuantity: Double? = null,
    val hisabRate: Double? = null,
    val hisabCustomItemName: String? = null,
    val newPartyName: String = "",
    val newPartyOpeningBalance: Double = 0.0,
    val newItemName: String = "",
    val newItemRate: Double? = null
)

data class VoiceUiState(
    val isOpen: Boolean = false,
    val language: VoiceLanguage = VoiceLanguage.HINGLISH,
    val step: VoiceStep = VoiceStep.MENU,
    val messages: List<VoiceChatMessage> = emptyList(),
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val partyRetryCount: Int = 0,
    val itemRetryCount: Int = 0,
    val draft: VoicePendingDraft = VoicePendingDraft(),
    val quickPanel: VoiceQuickPanel = VoiceQuickPanel.MENU_TASKS,
    val availableParties: List<PartyBalanceSummary> = emptyList(),
    val availableProducts: List<ProductWithDetails> = emptyList(),
    val statusHint: String = "",
    val micPermissionRequired: Boolean = false,
    val errorNotice: String? = null
)
