package com.example.voice

import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.HisabLineItemEntity
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PartyEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductWithDetails
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceAssistantViewModel(
    private val repository: FactoryHisabRepository,
    private val speechBridge: SpeechBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var nextMessageId = 1L
    private var hasMicPermission = false
    private var autoListenAfterPrompt = true

    init {
        viewModelScope.launch {
            repository.observePartySummaries().collect { parties ->
                _uiState.update { it.copy(availableParties = parties) }
            }
        }
    }

    fun setHasMicPermission(granted: Boolean) {
        hasMicPermission = granted
        if (granted) {
            _uiState.update { it.copy(micPermissionRequired = false) }
        }
    }

    fun openAssistant() {
        autoListenAfterPrompt = true
        _uiState.update {
            it.copy(
                isOpen = true,
                step = VoiceStep.MENU,
                messages = emptyList(),
                partialTranscript = "",
                partyRetryCount = 0,
                itemRetryCount = 0,
                draft = VoicePendingDraft(),
                quickPanel = VoiceQuickPanel.MENU_TASKS,
                availableProducts = emptyList(),
                errorNotice = null
            )
        }
        val lang = _uiState.value.language
        sayPrompt(menuGreetingText(lang), expectReply = true)
    }

    fun closeAssistant() {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        _uiState.update {
            it.copy(
                isOpen = false,
                isSpeaking = false,
                isListening = false,
                partialTranscript = ""
            )
        }
    }

    fun switchLanguage(language: VoiceLanguage) {
        if (_uiState.value.language == language) return
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        _uiState.update {
            it.copy(
                language = language,
                isSpeaking = false,
                isListening = false,
                partialTranscript = "",
                errorNotice = null
            )
        }
        val prompt = currentStepReprompt(_uiState.value)
        sayPrompt(prompt, expectReply = _uiState.value.step != VoiceStep.COMPLETED)
    }

    fun restartConversation() {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        openAssistant()
    }

    fun toggleMicListening() {
        val state = _uiState.value
        if (state.isListening) {
            speechBridge.stopListening()
            _uiState.update { it.copy(isListening = false, partialTranscript = "") }
            return
        }
        speechBridge.stopSpeaking()
        _uiState.update { it.copy(isSpeaking = false, errorNotice = null) }
        beginListeningInternal()
    }

    fun submitUserInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        appendUserMessage(trimmed)
        _uiState.update {
            it.copy(
                isListening = false,
                isSpeaking = false,
                partialTranscript = "",
                errorNotice = null
            )
        }
        processInput(trimmed)
    }

    fun selectMenuTask(task: VoiceTaskType) {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        val lang = _uiState.value.language
        appendUserMessage(taskLabel(task, lang))
        startTask(task)
    }

    fun selectPartyFromList(party: PartyBalanceSummary) {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        appendUserMessage(party.party.name)
        onPartyResolved(party)
    }

    fun selectProductFromList(product: ProductWithDetails) {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        appendUserMessage(product.product.name)
        onProductResolved(product)
    }

    fun selectPaymentModeChip(mode: String) {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        appendUserMessage(mode)
        onPaymentModeProvided(mode)
    }

    fun confirmPendingAction() {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        val lang = _uiState.value.language
        appendUserMessage(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Haan, add kar do"
                VoiceLanguage.HINDI -> "हाँ, जोड़ दें"
                VoiceLanguage.ENGLISH -> "Yes, confirm"
            }
        )
        executePendingSave()
    }

    fun cancelPendingAction() {
        speechBridge.stopListening()
        speechBridge.stopSpeaking()
        val lang = _uiState.value.language
        appendUserMessage(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Nahi, cancel karo"
                VoiceLanguage.HINDI -> "नहीं, रद्द करें"
                VoiceLanguage.ENGLISH -> "No, cancel"
            }
        )
        _uiState.update {
            it.copy(
                step = VoiceStep.MENU,
                draft = VoicePendingDraft(),
                quickPanel = VoiceQuickPanel.MENU_TASKS,
                partyRetryCount = 0,
                itemRetryCount = 0
            )
        }
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Theek hai, add nahi kiya. Ab batayein: Payment add karein, Naya Hisab, Nayi Party, ya Naya Item?"
                VoiceLanguage.HINDI -> "ठीक है, कुछ भी नहीं जोड़ा गया। बताइए: पेमेंट जोड़ें, नया हिसाब, नई पार्टी, या नया आइटम?"
                VoiceLanguage.ENGLISH -> "Okay, cancelled. What would you like to do: add payment, add new Hisab, add new party, or add new item?"
            },
            expectReply = true
        )
    }

    private fun processInput(raw: String) {
        val state = _uiState.value
        when (state.step) {
            VoiceStep.MENU -> handleMenuInput(raw)
            VoiceStep.PAYMENT_PARTY,
            VoiceStep.HISAB_PARTY,
            VoiceStep.ITEM_PARTY -> handlePartyLookupInput(raw)
            VoiceStep.PAYMENT_AMOUNT_AND_MODE -> handlePaymentAmountAndModeInput(raw)
            VoiceStep.PAYMENT_MODE_ONLY -> handlePaymentModeOnlyInput(raw)
            VoiceStep.HISAB_ITEM -> handleHisabItemInput(raw)
            VoiceStep.HISAB_QUANTITY -> handleHisabQuantityInput(raw)
            VoiceStep.HISAB_RATE -> handleHisabRateInput(raw)
            VoiceStep.PARTY_NAME -> handleNewPartyNameInput(raw)
            VoiceStep.PARTY_OPENING_BALANCE -> handleNewPartyBalanceInput(raw)
            VoiceStep.ITEM_NAME -> handleNewItemNameInput(raw)
            VoiceStep.ITEM_RATE -> handleNewItemRateInput(raw)
            VoiceStep.PAYMENT_CONFIRM,
            VoiceStep.HISAB_CONFIRM,
            VoiceStep.PARTY_CONFIRM,
            VoiceStep.ITEM_CONFIRM -> handleConfirmationInput(raw)
            VoiceStep.COMPLETED -> handleMenuInput(raw)
        }
    }

    private fun handleMenuInput(raw: String) {
        val lang = _uiState.value.language
        when (val parsed = HindiHinglishNlu.parseMenuChoice(raw)) {
            is MenuParseResult.Selected -> startTask(parsed.task)
            MenuParseResult.OutOfScope -> {
                _uiState.update { it.copy(quickPanel = VoiceQuickPanel.MENU_TASKS) }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Sorry, main sirf 4 app ke kaam kar sakta hoon: Payment add karna, Naya Hisab banana, Nayi Party add karna, ya Naya Item add karna. Please inmein se ek chuniye."
                        VoiceLanguage.HINDI -> "माफ़ कीजिए, मैं केवल ऐप के 4 काम कर सकता हूँ: पेमेंट जोड़ना, नया हिसाब बनाना, नई पार्टी जोड़ना, या नया आइटम जोड़ना। कृपया इनमें से एक चुनें।"
                        VoiceLanguage.ENGLISH -> "Sorry, I can only help with 4 things inside this app: adding payment, adding new Hisab, adding new party, or adding new item. Please choose one of these."
                    },
                    expectReply = true
                )
            }
            MenuParseResult.Unclear -> {
                _uiState.update { it.copy(quickPanel = VoiceQuickPanel.MENU_TASKS) }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Samajh nahi aaya. Please 1 se 4 mein se ek chuniye: Payment, Hisab, Party, ya Item."
                        VoiceLanguage.HINDI -> "समझ नहीं आया। कृपया पेमेंट, हिसाब, पार्टी, या आइटम में से एक चुनें।"
                        VoiceLanguage.ENGLISH -> "I didn't catch that. Please choose one of these: add payment, add new Hisab, add new party, or add new item."
                    },
                    expectReply = true
                )
            }
        }
    }

    private fun startTask(task: VoiceTaskType) {
        viewModelScope.launch {
            val lang = _uiState.value.language
            val parties = _uiState.value.availableParties.ifEmpty {
                repository.observePartySummaries().first().also { loaded ->
                    _uiState.update { it.copy(availableParties = loaded) }
                }
            }
            when (task) {
                VoiceTaskType.ADD_PAYMENT -> {
                    if (parties.isEmpty()) {
                        promptNoPartiesAvailable(lang)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            step = VoiceStep.PAYMENT_PARTY,
                            partyRetryCount = 0,
                            draft = VoicePendingDraft(task = VoiceTaskType.ADD_PAYMENT),
                            quickPanel = VoiceQuickPanel.NONE
                        )
                    }
                    sayPrompt(askPartyNameText(lang, task), expectReply = true)
                }
                VoiceTaskType.ADD_HISAB -> {
                    if (parties.isEmpty()) {
                        promptNoPartiesAvailable(lang)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            step = VoiceStep.HISAB_PARTY,
                            partyRetryCount = 0,
                            itemRetryCount = 0,
                            draft = VoicePendingDraft(task = VoiceTaskType.ADD_HISAB),
                            quickPanel = VoiceQuickPanel.NONE
                        )
                    }
                    sayPrompt(askPartyNameText(lang, task), expectReply = true)
                }
                VoiceTaskType.ADD_PARTY -> {
                    _uiState.update {
                        it.copy(
                            step = VoiceStep.PARTY_NAME,
                            draft = VoicePendingDraft(task = VoiceTaskType.ADD_PARTY),
                            quickPanel = VoiceQuickPanel.NONE
                        )
                    }
                    sayPrompt(
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Nayi party ka naam bataiye."
                            VoiceLanguage.HINDI -> "नई पार्टी का नाम बताइए।"
                            VoiceLanguage.ENGLISH -> "Please tell me the new party name."
                        },
                        expectReply = true
                    )
                }
                VoiceTaskType.ADD_ITEM -> {
                    if (parties.isEmpty()) {
                        promptNoPartiesAvailable(lang)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            step = VoiceStep.ITEM_PARTY,
                            partyRetryCount = 0,
                            draft = VoicePendingDraft(task = VoiceTaskType.ADD_ITEM),
                            quickPanel = VoiceQuickPanel.NONE
                        )
                    }
                    sayPrompt(askPartyNameText(lang, task), expectReply = true)
                }
            }
        }
    }

    private fun promptNoPartiesAvailable(lang: VoiceLanguage) {
        _uiState.update {
            it.copy(
                step = VoiceStep.MENU,
                quickPanel = VoiceQuickPanel.MENU_TASKS
            )
        }
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Abhi koi party add nahi hai. Pehle 'Add Party' chunkar ek party banaiye."
                VoiceLanguage.HINDI -> "अभी कोई पार्टी नहीं जुड़ी है। पहले नई पार्टी जोड़ें चुनकर पार्टी बनाइए।"
                VoiceLanguage.ENGLISH -> "There are no parties added yet. Please choose Add New Party first."
            },
            expectReply = true
        )
    }

    private fun handlePartyLookupInput(raw: String) {
        val state = _uiState.value
        val lang = state.language

        if (HindiHinglishNlu.isShowListCommand(raw)) {
            _uiState.update { it.copy(quickPanel = VoiceQuickPanel.PARTY_PICKER) }
            sayPrompt(partyListFallbackText(lang), expectReply = false)
            return
        }

        val matched = HindiHinglishNlu.findBestPartyMatch(raw, state.availableParties)
        if (matched != null) {
            onPartyResolved(matched)
            return
        }

        registerPartyLookupMiss()
    }

    private fun registerPartyLookupMiss() {
        val state = _uiState.value
        val lang = state.language
        val nextAttempts = state.partyRetryCount + 1

        if (nextAttempts < 3) {
            _uiState.update {
                it.copy(
                    partyRetryCount = nextAttempts,
                    quickPanel = VoiceQuickPanel.NONE
                )
            }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Sorry, main sahi se sun nahi paaya. Please party ka naam dobara bataiye."
                    VoiceLanguage.HINDI -> "माफ़ कीजिए, मैं साफ सुन नहीं पाया। कृपया पार्टी का नाम फिर से बताइए।"
                    VoiceLanguage.ENGLISH -> "Repeat the name, sorry, I'm unable to listen clearly."
                },
                expectReply = true
            )
        } else {
            _uiState.update {
                it.copy(
                    partyRetryCount = nextAttempts,
                    quickPanel = VoiceQuickPanel.PARTY_PICKER
                )
            }
            sayPrompt(partyListFallbackText(lang), expectReply = false)
        }
    }

    private fun onPartyResolved(party: PartyBalanceSummary) {
        val state = _uiState.value
        val lang = state.language
        val updatedDraft = state.draft.copy(selectedParty = party)

        when (state.draft.task) {
            VoiceTaskType.ADD_PAYMENT -> {
                _uiState.update {
                    it.copy(
                        step = VoiceStep.PAYMENT_AMOUNT_AND_MODE,
                        partyRetryCount = 0,
                        draft = updatedDraft,
                        quickPanel = VoiceQuickPanel.NONE
                    )
                }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Okay, ${party.party.name} ke liye amount aur payment method bataiye."
                        VoiceLanguage.HINDI -> "ठीक है, ${party.party.name} के लिए रकम और पेमेंट मेथड बताइए।"
                        VoiceLanguage.ENGLISH -> "Okay, for ${party.party.name}, give me the amount and the payment method."
                    },
                    expectReply = true
                )
            }
            VoiceTaskType.ADD_HISAB -> {
                viewModelScope.launch {
                    val products = repository.observeProductsForParty(party.party.id).first()
                    _uiState.update {
                        it.copy(
                            step = VoiceStep.HISAB_ITEM,
                            partyRetryCount = 0,
                            itemRetryCount = 0,
                            draft = updatedDraft,
                            availableProducts = products,
                            quickPanel = VoiceQuickPanel.NONE
                        )
                    }
                    val prompt = if (products.isEmpty()) {
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "${party.party.name} mein abhi koi item saved nahi hai. Aap naye hisab ke liye item ka naam bol sakte hain."
                            VoiceLanguage.HINDI -> "${party.party.name} में अभी कोई आइटम सेव नहीं है। आप नए हिसाब के लिए आइटम का नाम बोल सकते हैं।"
                            VoiceLanguage.ENGLISH -> "${party.party.name} has no saved items yet. Please speak the item name for this Hisab."
                        }
                    } else {
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Okay, ${party.party.name} ke hisab ke liye kaunsa item add karna hai? Item ka naam bataiye."
                            VoiceLanguage.HINDI -> "ठीक है, ${party.party.name} के हिसाब के लिए कौन-सा आइटम जोड़ना है? आइटम का नाम बताइए।"
                            VoiceLanguage.ENGLISH -> "Okay, which item should I add to ${party.party.name}'s Hisab?"
                        }
                    }
                    sayPrompt(prompt, expectReply = true)
                }
            }
            VoiceTaskType.ADD_ITEM -> {
                _uiState.update {
                    it.copy(
                        step = VoiceStep.ITEM_NAME,
                        partyRetryCount = 0,
                        draft = updatedDraft,
                        quickPanel = VoiceQuickPanel.NONE
                    )
                }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Okay, ${party.party.name} ke andar naye item ka naam bataiye."
                        VoiceLanguage.HINDI -> "ठीक है, ${party.party.name} के अंदर नए आइटम का नाम बताइए।"
                        VoiceLanguage.ENGLISH -> "Okay, what is the new item name for ${party.party.name}?"
                    },
                    expectReply = true
                )
            }
            else -> Unit
        }
    }

    private fun handlePaymentAmountAndModeInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val parsed = HindiHinglishNlu.parsePaymentAmountAndMode(raw)
        val amount = parsed.amount ?: state.draft.paymentAmount
        val mode = parsed.mode ?: state.draft.paymentMode

        when {
            amount != null && amount > 0.0 && mode != null -> {
                moveToPaymentConfirmation(amount = amount, mode = mode)
            }
            amount != null && amount > 0.0 && mode == null -> {
                _uiState.update {
                    it.copy(
                        step = VoiceStep.PAYMENT_MODE_ONLY,
                        draft = it.draft.copy(paymentAmount = amount),
                        quickPanel = VoiceQuickPanel.PAYMENT_MODES
                    )
                }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Amount ${CurrencyFormatter.formatRupees(amount)} note ho gaya. Payment method aap inmein se select kar sakte hain: Cash, UPI, Bank, ya Cheque. Kaunsa method hai?"
                        VoiceLanguage.HINDI -> "${CurrencyFormatter.formatRupees(amount)} नोट हो गया। आप इनमें से पेमेंट मेथड चुन सकते हैं: कैश, यूपीआई, बैंक, या चेक। कौन-सा मेथड है?"
                        VoiceLanguage.ENGLISH -> "Got ${CurrencyFormatter.formatRupees(amount)}. The payment methods you can select are: Cash, UPI, Bank, or Cheque. Which one is it?"
                    },
                    expectReply = true
                )
            }
            amount == null && mode != null -> {
                _uiState.update {
                    it.copy(
                        draft = it.draft.copy(paymentMode = mode),
                        quickPanel = VoiceQuickPanel.NONE
                    )
                }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "$mode select ho gaya. Kitna amount aaya hai, woh bataiye."
                        VoiceLanguage.HINDI -> "$mode चुन लिया गया। कितनी रकम मिली है, वह बताइए।"
                        VoiceLanguage.ENGLISH -> "Selected $mode. Please tell me the payment amount."
                    },
                    expectReply = true
                )
            }
            else -> {
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Amount samajh nahi aaya. Jaise boliye '10,000 UPI' ya '5000 Cash'."
                        VoiceLanguage.HINDI -> "रकम समझ नहीं आई। कृपया जैसे '10,000 यूपीआई' या '5000 कैश' बोलिए।"
                        VoiceLanguage.ENGLISH -> "I couldn't catch the amount. Please say something like '10,000 by UPI' or '5000 Cash'."
                    },
                    expectReply = true
                )
            }
        }
    }

    private fun handlePaymentModeOnlyInput(raw: String) {
        val mode = HindiHinglishNlu.parsePaymentMode(raw)
        if (mode != null) {
            onPaymentModeProvided(mode)
        } else {
            val lang = _uiState.value.language
            _uiState.update { it.copy(quickPanel = VoiceQuickPanel.PAYMENT_MODES) }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Payment method unclear tha. Inmein se boliye ya tap kijiye: Cash, UPI, Bank, ya Cheque."
                    VoiceLanguage.HINDI -> "पेमेंट मेथड साफ नहीं हुआ। इनमें से बोलिए या चुनिए: कैश, यूपीआई, बैंक, या चेक।"
                    VoiceLanguage.ENGLISH -> "The payment method wasn't clear. You can select Cash, UPI, Bank, or Cheque."
                },
                expectReply = true
            )
        }
    }

    private fun onPaymentModeProvided(mode: String) {
        val amount = _uiState.value.draft.paymentAmount
        if (amount != null && amount > 0.0) {
            moveToPaymentConfirmation(amount = amount, mode = mode)
        } else {
            _uiState.update {
                it.copy(
                    step = VoiceStep.PAYMENT_AMOUNT_AND_MODE,
                    draft = it.draft.copy(paymentMode = mode),
                    quickPanel = VoiceQuickPanel.NONE
                )
            }
            val lang = _uiState.value.language
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Payment method $mode hai. Ab amount bataiye."
                    VoiceLanguage.HINDI -> "पेमेंट मेथड $mode है। अब रकम बताइए।"
                    VoiceLanguage.ENGLISH -> "Payment method is $mode. Now please tell me the amount."
                },
                expectReply = true
            )
        }
    }

    private fun moveToPaymentConfirmation(amount: Double, mode: String) {
        val state = _uiState.value
        val lang = state.language
        val partyName = state.draft.selectedParty?.party?.name ?: return
        _uiState.update {
            it.copy(
                step = VoiceStep.PAYMENT_CONFIRM,
                draft = it.draft.copy(paymentAmount = amount, paymentMode = mode),
                quickPanel = VoiceQuickPanel.CONFIRMATION_CARD
            )
        }
        val formatted = CurrencyFormatter.formatRupees(amount)
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Confirm kijiye: Party $partyName, amount $formatted, aur payment method $mode. Kya aap yeh payment add karna chahte hain?"
                VoiceLanguage.HINDI -> "कृपया कन्फर्म करें: पार्टी $partyName, रकम $formatted, और पेमेंट मेथड $mode। क्या आप यह पेमेंट जोड़ना चाहते हैं?"
                VoiceLanguage.ENGLISH -> "This is the payment through $mode, amount $formatted, for party $partyName. Will you want to add that?"
            },
            expectReply = true
        )
    }

    private fun handleHisabItemInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val products = state.availableProducts

        if (products.isEmpty()) {
            val cleanName = HindiHinglishNlu.cleanEntityNameInput(raw)
            if (cleanName.length < 2) {
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Item ka naam sahi se boliye."
                        VoiceLanguage.HINDI -> "कृपया आइटम का नाम साफ बोलिए।"
                        VoiceLanguage.ENGLISH -> "Please tell me the item name clearly."
                    },
                    expectReply = true
                )
                return
            }
            _uiState.update {
                it.copy(
                    step = VoiceStep.HISAB_QUANTITY,
                    draft = it.draft.copy(selectedProduct = null, hisabCustomItemName = cleanName),
                    quickPanel = VoiceQuickPanel.NONE
                )
            }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Item '$cleanName' ke kitne pieces likhne hain?"
                    VoiceLanguage.HINDI -> "आइटम '$cleanName' के कितने पीस जोड़ने हैं?"
                    VoiceLanguage.ENGLISH -> "How many pieces of $cleanName should I add?"
                },
                expectReply = true
            )
            return
        }

        val matched = HindiHinglishNlu.findBestProductMatch(raw, products)
        if (matched != null) {
            onProductResolved(matched)
            return
        }

        val nextAttempts = state.itemRetryCount + 1
        if (nextAttempts < 3) {
            _uiState.update {
                it.copy(
                    itemRetryCount = nextAttempts,
                    quickPanel = VoiceQuickPanel.NONE
                )
            }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Sorry, item ka naam clear nahi mila. Please item ka naam dobara bataiye."
                    VoiceLanguage.HINDI -> "माफ़ कीजिए, आइटम का नाम साफ नहीं सुन पाया। कृपया फिर से बताइए।"
                    VoiceLanguage.ENGLISH -> "Repeat the item name, sorry, I'm unable to listen clearly."
                },
                expectReply = true
            )
        } else {
            _uiState.update {
                it.copy(
                    itemRetryCount = nextAttempts,
                    quickPanel = VoiceQuickPanel.ITEM_PICKER
                )
            }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Item match nahi ho paaya. Please neeche di gayi item list se select kijiye."
                    VoiceLanguage.HINDI -> "आइटम नहीं मिल पाया। कृपया नीचे दी गई आइटम लिस्ट से चुनिए।"
                    VoiceLanguage.ENGLISH -> "I couldn't hear the item name clearly. Please select from the item list below."
                },
                expectReply = false
            )
        }
    }

    private fun onProductResolved(product: ProductWithDetails) {
        val lang = _uiState.value.language
        val defaultRate = product.product.baseRate +
            product.workSteps.filter { it.isDefaultSelected }.sumOf { it.stepRate }
        val effectiveRate = if (defaultRate > 0.0) defaultRate else product.product.baseRate
        _uiState.update {
            it.copy(
                step = VoiceStep.HISAB_QUANTITY,
                itemRetryCount = 0,
                draft = it.draft.copy(
                    selectedProduct = product,
                    hisabCustomItemName = product.product.name,
                    hisabRate = effectiveRate
                ),
                quickPanel = VoiceQuickPanel.NONE
            )
        }
        val rateText = CurrencyFormatter.formatRate(effectiveRate)
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "${product.product.name} (rate $rateText) ke kitne pieces add karne hain?"
                VoiceLanguage.HINDI -> "${product.product.name} (रेट $rateText) के कितने पीस जोड़ने हैं?"
                VoiceLanguage.ENGLISH -> "Got ${product.product.name} at $rateText. How many pieces?"
            },
            expectReply = true
        )
    }

    private fun handleHisabQuantityInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val qty = HindiHinglishNlu.parseNumber(raw)
        if (qty == null || qty <= 0.0) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Pieces ki sankhya samajh nahi aayi. Jaise '500' ya 'paanch sau' boliye."
                    VoiceLanguage.HINDI -> "पीस की संख्या समझ नहीं आई। कृपया जैसे '500' या 'पांच सौ' बोलिए।"
                    VoiceLanguage.ENGLISH -> "I didn't catch the quantity. Please tell me the number of pieces."
                },
                expectReply = true
            )
            return
        }

        val currentRate = state.draft.hisabRate
        if (currentRate != null && currentRate > 0.0) {
            moveToHisabConfirmation(qty = qty, rate = currentRate)
        } else {
            _uiState.update {
                it.copy(
                    step = VoiceStep.HISAB_RATE,
                    draft = it.draft.copy(hisabQuantity = qty),
                    quickPanel = VoiceQuickPanel.NONE
                )
            }
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "${CurrencyFormatter.formatQuantity(qty)} pieces ke liye ek piece ka rate kitne rupaye hai?"
                    VoiceLanguage.HINDI -> "${CurrencyFormatter.formatQuantity(qty)} पीस के लिए एक पीस का रेट कितना है?"
                    VoiceLanguage.ENGLISH -> "What is the rate per piece for ${CurrencyFormatter.formatQuantity(qty)} pieces?"
                },
                expectReply = true
            )
        }
    }

    private fun handleHisabRateInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val rate = HindiHinglishNlu.parseNumber(raw)
        val qty = state.draft.hisabQuantity ?: 0.0
        if (rate == null || rate <= 0.0) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Rate samajh nahi aaya. Ek piece ka rate rupaye mein bataiye."
                    VoiceLanguage.HINDI -> "रेट समझ नहीं आया। कृपया एक पीस का रेट बताइए।"
                    VoiceLanguage.ENGLISH -> "Please tell me a valid rate per piece."
                },
                expectReply = true
            )
            return
        }
        moveToHisabConfirmation(qty = qty, rate = rate)
    }

    private fun moveToHisabConfirmation(qty: Double, rate: Double) {
        val state = _uiState.value
        val lang = state.language
        val partyName = state.draft.selectedParty?.party?.name ?: return
        val itemName = state.draft.selectedProduct?.product?.name ?: state.draft.hisabCustomItemName ?: return
        val total = qty * rate
        _uiState.update {
            it.copy(
                step = VoiceStep.HISAB_CONFIRM,
                draft = it.draft.copy(hisabQuantity = qty, hisabRate = rate),
                quickPanel = VoiceQuickPanel.CONFIRMATION_CARD
            )
        }
        val qtyStr = CurrencyFormatter.formatQuantity(qty)
        val rateStr = CurrencyFormatter.formatRate(rate)
        val totalStr = CurrencyFormatter.formatRupees(total)
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Confirm kijiye: Party $partyName ke Hisab mein $itemName, $qtyStr pieces, rate $rateStr, total $totalStr add karna hai. Kya main add kar doon?"
                VoiceLanguage.HINDI -> "कृपया कन्फर्म करें: पार्टी $partyName के हिसाब में $itemName, $qtyStr पीस, रेट $rateStr, कुल $totalStr जोड़ना है। क्या मैं जोड़ दूँ?"
                VoiceLanguage.ENGLISH -> "Please confirm: add $itemName, $qtyStr pieces at $rateStr, total $totalStr to $partyName's Hisab. Will you want to add that?"
            },
            expectReply = true
        )
    }

    private fun handleNewPartyNameInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val cleanName = HindiHinglishNlu.cleanEntityNameInput(raw)
        if (cleanName.length < 2) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Party ka naam clear nahi tha. Please dobara bataiye."
                    VoiceLanguage.HINDI -> "पार्टी का नाम साफ नहीं था। कृपया फिर से बताइए।"
                    VoiceLanguage.ENGLISH -> "Please repeat the new party name clearly."
                },
                expectReply = true
            )
            return
        }

        val existing = state.availableParties.firstOrNull {
            it.party.name.equals(cleanName, ignoreCase = true)
        }
        if (existing != null) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "'${existing.party.name}' pehle se list mein hai. Koi aur naya naam bataiye."
                    VoiceLanguage.HINDI -> "'${existing.party.name}' पहले से मौजूद है। कृपया कोई नया नाम बताइए।"
                    VoiceLanguage.ENGLISH -> "${existing.party.name} already exists. Please speak a different party name."
                },
                expectReply = true
            )
            return
        }

        _uiState.update {
            it.copy(
                step = VoiceStep.PARTY_OPENING_BALANCE,
                draft = it.draft.copy(newPartyName = cleanName),
                quickPanel = VoiceQuickPanel.NONE
            )
        }
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Party '$cleanName' ka opening balance kitna hai? Agar zero hai toh 'zero' ya 'skip' boliye."
                VoiceLanguage.HINDI -> "पार्टी '$cleanName' का ओपनिंग बैलेंस कितना है? अगर शून्य है तो 'जीरो' या 'स्किप' बोलिए।"
                VoiceLanguage.ENGLISH -> "What is the opening balance for $cleanName? Say zero or skip if none."
            },
            expectReply = true
        )
    }

    private fun handleNewPartyBalanceInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val balance = if (HindiHinglishNlu.isSkipCommand(raw)) {
            0.0
        } else {
            HindiHinglishNlu.parseNumber(raw)
        }

        if (balance == null || balance < 0.0) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Balance samajh nahi aaya. Amount boliye ya 'zero' boliye."
                    VoiceLanguage.HINDI -> "बैलेंस समझ नहीं आया। रकम बोलिए या 'जीरो' बोलिए।"
                    VoiceLanguage.ENGLISH -> "Please say an opening balance amount, or say zero."
                },
                expectReply = true
            )
            return
        }

        val partyName = state.draft.newPartyName
        _uiState.update {
            it.copy(
                step = VoiceStep.PARTY_CONFIRM,
                draft = it.draft.copy(newPartyOpeningBalance = balance),
                quickPanel = VoiceQuickPanel.CONFIRMATION_CARD
            )
        }
        val balStr = CurrencyFormatter.formatRupees(balance)
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Confirm kijiye: Nayi party '$partyName' add karni hai, opening balance $balStr. Kya aap ise add karna chahte hain?"
                VoiceLanguage.HINDI -> "कृपया कन्फर्म करें: नई पार्टी '$partyName' जोड़नी है, ओपनिंग बैलेंस $balStr। क्या आप इसे जोड़ना चाहते हैं?"
                VoiceLanguage.ENGLISH -> "Please confirm: add new party $partyName with opening balance $balStr. Will you want to add that?"
            },
            expectReply = true
        )
    }

    private fun handleNewItemNameInput(raw: String) {
        val lang = _uiState.value.language
        val cleanName = HindiHinglishNlu.cleanEntityNameInput(raw)
        if (cleanName.length < 2) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Item ka naam clear nahi tha. Please dobara bataiye."
                    VoiceLanguage.HINDI -> "आइटम का नाम साफ नहीं था। कृपया फिर से बताइए।"
                    VoiceLanguage.ENGLISH -> "Please say the new item name clearly."
                },
                expectReply = true
            )
            return
        }
        _uiState.update {
            it.copy(
                step = VoiceStep.ITEM_RATE,
                draft = it.draft.copy(newItemName = cleanName),
                quickPanel = VoiceQuickPanel.NONE
            )
        }
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "'$cleanName' ka rate kitne rupaye per piece hai?"
                VoiceLanguage.HINDI -> "'$cleanName' का रेट कितने रुपये प्रति पीस है?"
                VoiceLanguage.ENGLISH -> "What is the rate per piece for $cleanName?"
            },
            expectReply = true
        )
    }

    private fun handleNewItemRateInput(raw: String) {
        val state = _uiState.value
        val lang = state.language
        val rate = HindiHinglishNlu.parseNumber(raw)
        if (rate == null || rate <= 0.0) {
            sayPrompt(
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Rate samajh nahi aaya. Jaise '4.50' ya '12 rupaye' boliye."
                    VoiceLanguage.HINDI -> "रेट समझ नहीं आया। कृपया जैसे '4.50' या '12 रुपये' बोलिए।"
                    VoiceLanguage.ENGLISH -> "Please tell me a valid rate per piece."
                },
                expectReply = true
            )
            return
        }
        val partyName = state.draft.selectedParty?.party?.name ?: return
        val itemName = state.draft.newItemName
        _uiState.update {
            it.copy(
                step = VoiceStep.ITEM_CONFIRM,
                draft = it.draft.copy(newItemRate = rate),
                quickPanel = VoiceQuickPanel.CONFIRMATION_CARD
            )
        }
        val rateStr = CurrencyFormatter.formatRate(rate)
        sayPrompt(
            when (lang) {
                VoiceLanguage.HINGLISH -> "Confirm kijiye: Party $partyName ke andar naya item '$itemName', rate $rateStr per piece add karna hai. Kya main add kar doon?"
                VoiceLanguage.HINDI -> "कृपया कन्फर्म करें: पार्टी $partyName के अंदर नया आइटम '$itemName', रेट $rateStr प्रति पीस जोड़ना है। क्या मैं जोड़ दूँ?"
                VoiceLanguage.ENGLISH -> "Please confirm: add item $itemName at $rateStr per piece under $partyName. Will you want to add that?"
            },
            expectReply = true
        )
    }

    private fun handleConfirmationInput(raw: String) {
        val lang = _uiState.value.language
        when (HindiHinglishNlu.parseConfirmation(raw)) {
            ConfirmIntent.YES -> executePendingSave()
            ConfirmIntent.NO -> cancelPendingAction()
            ConfirmIntent.UNCLEAR -> {
                _uiState.update { it.copy(quickPanel = VoiceQuickPanel.CONFIRMATION_CARD) }
                sayPrompt(
                    when (lang) {
                        VoiceLanguage.HINGLISH -> "Please 'Haan' ya 'Nahi' boliye, ya neeche Confirm button dabaiye."
                        VoiceLanguage.HINDI -> "कृपया 'हाँ' या 'नहीं' बोलिए, या नीचे कन्फर्म बटन दबाइए।"
                        VoiceLanguage.ENGLISH -> "Please say Yes to add or No to cancel."
                    },
                    expectReply = true
                )
            }
        }
    }

    private fun executePendingSave() {
        val state = _uiState.value
        val draft = state.draft
        val lang = state.language

        viewModelScope.launch {
            val savedText: String? = when (state.step) {
                VoiceStep.PAYMENT_CONFIRM -> {
                    val party = draft.selectedParty
                    val amount = draft.paymentAmount
                    val mode = draft.paymentMode ?: "Cash"
                    if (party != null && amount != null && amount > 0.0) {
                        repository.addPayment(
                            PaymentEntity(
                                partyId = party.party.id,
                                amount = amount,
                                dateMillis = System.currentTimeMillis(),
                                paymentMode = mode
                            )
                        )
                        val amtStr = CurrencyFormatter.formatRupees(amount)
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Done! ${party.party.name} ke liye $amtStr ($mode) payment add ho gayi."
                            VoiceLanguage.HINDI -> "हो गया! ${party.party.name} के लिए $amtStr ($mode) पेमेंट जुड़ गई है।"
                            VoiceLanguage.ENGLISH -> "Done! Added $amtStr ($mode) payment for ${party.party.name}."
                        }
                    } else null
                }
                VoiceStep.HISAB_CONFIRM -> {
                    val party = draft.selectedParty
                    val qty = draft.hisabQuantity
                    val rate = draft.hisabRate
                    val product = draft.selectedProduct
                    val itemName = product?.product?.name ?: draft.hisabCustomItemName
                    if (party != null && qty != null && rate != null && !itemName.isNullOrBlank()) {
                        val now = System.currentTimeMillis()
                        val (dayStart, dayEnd) = DateFormatter.dayBounds(now)
                        val total = qty * rate
                        repository.addRegisterItem(
                            partyId = party.party.id,
                            calculationDateMillis = now,
                            dayStartMillis = dayStart,
                            dayEndMillis = dayEnd,
                            forceNewSection = false,
                            item = HisabLineItemEntity(
                                hisabId = 0,
                                productName = itemName,
                                unitLabel = product?.product?.defaultUnit ?: "pcs",
                                quantity = qty,
                                unitRate = rate,
                                lineTotal = total
                            )
                        )
                        val totalStr = CurrencyFormatter.formatRupees(total)
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Done! ${party.party.name} ke aaj ke Hisab mein $itemName ($totalStr) add ho gaya."
                            VoiceLanguage.HINDI -> "हो गया! ${party.party.name} के आज के हिसाब में $itemName ($totalStr) जुड़ गया है।"
                            VoiceLanguage.ENGLISH -> "Done! Added $itemName ($totalStr) to ${party.party.name}'s Hisab."
                        }
                    } else null
                }
                VoiceStep.PARTY_CONFIRM -> {
                    val name = draft.newPartyName.trim()
                    if (name.isNotBlank()) {
                        repository.saveParty(
                            PartyEntity(
                                name = name,
                                openingBalance = draft.newPartyOpeningBalance
                            )
                        )
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Done! Nayi party '$name' add ho gayi hai."
                            VoiceLanguage.HINDI -> "हो गया! नई पार्टी '$name' जुड़ गई है।"
                            VoiceLanguage.ENGLISH -> "Done! New party $name has been added."
                        }
                    } else null
                }
                VoiceStep.ITEM_CONFIRM -> {
                    val party = draft.selectedParty
                    val itemName = draft.newItemName.trim()
                    val rate = draft.newItemRate
                    if (party != null && itemName.isNotBlank() && rate != null && rate > 0.0) {
                        repository.saveProductWithDetails(
                            product = ProductEntity(
                                partyId = party.party.id,
                                name = itemName,
                                baseRate = rate,
                                defaultUnit = "pcs"
                            ),
                            subCategories = emptyList(),
                            workSteps = emptyList()
                        )
                        val rateStr = CurrencyFormatter.formatRate(rate)
                        when (lang) {
                            VoiceLanguage.HINGLISH -> "Done! ${party.party.name} ke liye '$itemName' ($rateStr) add ho gaya."
                            VoiceLanguage.HINDI -> "हो गया! ${party.party.name} के लिए '$itemName' ($rateStr) जुड़ गया है।"
                            VoiceLanguage.ENGLISH -> "Done! Added item $itemName at $rateStr under ${party.party.name}."
                        }
                    } else null
                }
                else -> null
            }

            if (savedText != null) {
                _uiState.update {
                    it.copy(
                        step = VoiceStep.COMPLETED,
                        quickPanel = VoiceQuickPanel.MENU_TASKS,
                        draft = VoicePendingDraft(),
                        partyRetryCount = 0,
                        itemRetryCount = 0
                    )
                }
                sayPrompt(savedText, expectReply = false)
            }
        }
    }

    private fun sayPrompt(text: String, expectReply: Boolean) {
        appendAssistantMessage(text)
        val lang = _uiState.value.language
        _uiState.update { it.copy(isSpeaking = true, isListening = false) }
        speechBridge.speak(
            text = text,
            language = lang,
            onStarted = {
                _uiState.update { it.copy(isSpeaking = true) }
            },
            onDone = {
                _uiState.update { it.copy(isSpeaking = false) }
                if (expectReply && autoListenAfterPrompt && _uiState.value.isOpen) {
                    beginListeningInternal()
                }
            }
        )
    }

    private fun beginListeningInternal() {
        if (!hasMicPermission) {
            _uiState.update { it.copy(micPermissionRequired = true, isListening = false) }
            return
        }
        if (!speechBridge.isSpeechRecognitionAvailable()) {
            val lang = _uiState.value.language
            _uiState.update {
                it.copy(
                    isListening = false,
                    errorNotice = when (lang) {
                        VoiceLanguage.HINGLISH -> "Mic speech service available nahi hai — aap neeche type ya tap kar sakte hain."
                        VoiceLanguage.HINDI -> "स्पीच सर्विस उपलब्ध नहीं है — आप नीचे टाइप या टैप कर सकते हैं।"
                        VoiceLanguage.ENGLISH -> "Speech service unavailable on this device — you can type or tap below."
                    }
                )
            }
            return
        }

        val lang = _uiState.value.language
        _uiState.update { it.copy(isListening = true, partialTranscript = "", errorNotice = null) }
        speechBridge.startListening(
            language = lang,
            onReady = {
                _uiState.update { it.copy(isListening = true) }
            },
            onPartial = { partial ->
                _uiState.update { it.copy(partialTranscript = partial) }
            },
            onResult = { result ->
                _uiState.update { it.copy(isListening = false, partialTranscript = "") }
                submitUserInput(result)
            },
            onError = { errorCode ->
                _uiState.update { it.copy(isListening = false, partialTranscript = "") }
                handleSpeechRecognizerError(errorCode)
            }
        )
    }

    private fun handleSpeechRecognizerError(errorCode: Int) {
        val state = _uiState.value
        val lang = state.language
        if (!state.isOpen) return

        // On party name steps, an unclear/no-match audio attempt counts toward the 3-attempt rule!
        if ((state.step == VoiceStep.PAYMENT_PARTY ||
                state.step == VoiceStep.HISAB_PARTY ||
                state.step == VoiceStep.ITEM_PARTY) &&
            (errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        ) {
            registerPartyLookupMiss()
            return
        }

        val hint = when (errorCode) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                _uiState.update { it.copy(micPermissionRequired = true) }
                when (lang) {
                    VoiceLanguage.HINGLISH -> "Mic permission chahiye."
                    VoiceLanguage.HINDI -> "माइक की अनुमति चाहिए।"
                    VoiceLanguage.ENGLISH -> "Microphone permission is required."
                }
            }
            else -> when (lang) {
                VoiceLanguage.HINGLISH -> "Awaaz clear nahi aayi — Mic dabakar phir bolein ya type karein."
                VoiceLanguage.HINDI -> "आवाज़ साफ नहीं आई — माइक दबाकर फिर बोलें या टाइप करें।"
                VoiceLanguage.ENGLISH -> "Didn't catch audio — tap Mic to try again or type below."
            }
        }
        _uiState.update { it.copy(errorNotice = hint) }
    }

    private fun appendAssistantMessage(text: String) {
        val msg = VoiceChatMessage(id = nextMessageId++, fromAssistant = true, text = text)
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private fun appendUserMessage(text: String) {
        val msg = VoiceChatMessage(id = nextMessageId++, fromAssistant = false, text = text)
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private fun menuGreetingText(lang: VoiceLanguage): String = when (lang) {
        VoiceLanguage.HINGLISH ->
            "Main aapki kaise help kar sakta hoon? Main 4 kaam kar sakta hoon: 1. Payment add karna, 2. Naya Hisab banana, 3. Nayi Party add karna, 4. Naya Item add karna. Please inmein se ek chuniye."
        VoiceLanguage.HINDI ->
            "मैं आपकी कैसे मदद कर सकता हूँ? मैं 4 काम कर सकता हूँ: 1. पेमेंट जोड़ना, 2. नया हिसाब बनाना, 3. नई पार्टी जोड़ना, 4. नया आइटम जोड़ना। कृपया इनमें से एक चुनें।"
        VoiceLanguage.ENGLISH ->
            "How can I help you? I can help you with: 1. Adding payment, 2. Adding new Hisab, 3. Adding new party, 4. Adding new item. Please choose one of these."
    }

    private fun askPartyNameText(lang: VoiceLanguage, task: VoiceTaskType): String = when (lang) {
        VoiceLanguage.HINGLISH -> when (task) {
            VoiceTaskType.ADD_PAYMENT -> "Kis party ki payment add karni hai? Party ka naam bataiye."
            VoiceTaskType.ADD_HISAB -> "Kis party ka naya Hisab banana hai? Party ka naam bataiye."
            VoiceTaskType.ADD_ITEM -> "Kis party ke andar naya item add karna hai? Party ka naam bataiye."
            else -> "Party ka naam bataiye."
        }
        VoiceLanguage.HINDI -> when (task) {
            VoiceTaskType.ADD_PAYMENT -> "किस पार्टी की पेमेंट जोड़नी है? पार्टी का नाम बताइए।"
            VoiceTaskType.ADD_HISAB -> "किस पार्टी का नया हिसाब बनाना है? पार्टी का नाम बताइए।"
            VoiceTaskType.ADD_ITEM -> "किस पार्टी के अंदर नया आइटम जोड़ना है? पार्टी का नाम बताइए।"
            else -> "पार्टी का नाम बताइए।"
        }
        VoiceLanguage.ENGLISH -> "Give me the party name."
    }

    private fun partyListFallbackText(lang: VoiceLanguage): String = when (lang) {
        VoiceLanguage.HINGLISH ->
            "Lagta hai yeh party naam exist nahi karta ya main clear sun nahi paaya. Confirmation ke liye please neeche di gayi party list se select kijiye."
        VoiceLanguage.HINDI ->
            "शायद यह पार्टी नाम मौजूद नहीं है या मैं साफ सुन नहीं पाया। कृपया नीचे दी गई पार्टी लिस्ट से चुनिए।"
        VoiceLanguage.ENGLISH ->
            "The party name must not exist or I am unable to hear the party name clearly. Please check the party list below for confirmation."
    }

    private fun taskLabel(task: VoiceTaskType, lang: VoiceLanguage): String = when (lang) {
        VoiceLanguage.HINGLISH -> when (task) {
            VoiceTaskType.ADD_PAYMENT -> "Payment add karna"
            VoiceTaskType.ADD_HISAB -> "Naya Hisab banana"
            VoiceTaskType.ADD_PARTY -> "Nayi Party add karna"
            VoiceTaskType.ADD_ITEM -> "Naya Item add karna"
        }
        VoiceLanguage.HINDI -> when (task) {
            VoiceTaskType.ADD_PAYMENT -> "पेमेंट जोड़ना"
            VoiceTaskType.ADD_HISAB -> "नया हिसाब बनाना"
            VoiceTaskType.ADD_PARTY -> "नई पार्टी जोड़ना"
            VoiceTaskType.ADD_ITEM -> "नया आइटम जोड़ना"
        }
        VoiceLanguage.ENGLISH -> when (task) {
            VoiceTaskType.ADD_PAYMENT -> "Add new payment"
            VoiceTaskType.ADD_HISAB -> "Add new Hisab"
            VoiceTaskType.ADD_PARTY -> "Add new party"
            VoiceTaskType.ADD_ITEM -> "Add new item"
        }
    }

    private fun currentStepReprompt(state: VoiceUiState): String {
        val lang = state.language
        val partyName = state.draft.selectedParty?.party?.name.orEmpty()
        return when (state.step) {
            VoiceStep.MENU, VoiceStep.COMPLETED -> menuGreetingText(lang)
            VoiceStep.PAYMENT_PARTY -> askPartyNameText(lang, VoiceTaskType.ADD_PAYMENT)
            VoiceStep.HISAB_PARTY -> askPartyNameText(lang, VoiceTaskType.ADD_HISAB)
            VoiceStep.ITEM_PARTY -> askPartyNameText(lang, VoiceTaskType.ADD_ITEM)
            VoiceStep.PAYMENT_AMOUNT_AND_MODE -> when (lang) {
                VoiceLanguage.HINGLISH -> "$partyName ke liye amount aur payment method bataiye."
                VoiceLanguage.HINDI -> "$partyName के लिए रकम और पेमेंट मेथड बताइए।"
                VoiceLanguage.ENGLISH -> "Give me the amount and the payment method for $partyName."
            }
            VoiceStep.PAYMENT_MODE_ONLY -> when (lang) {
                VoiceLanguage.HINGLISH -> "Payment method chuniye: Cash, UPI, Bank, ya Cheque."
                VoiceLanguage.HINDI -> "पेमेंट मेथड चुनिए: कैश, यूपीआई, बैंक, या चेक।"
                VoiceLanguage.ENGLISH -> "Please select a payment method: Cash, UPI, Bank, or Cheque."
            }
            VoiceStep.PAYMENT_CONFIRM -> when (lang) {
                VoiceLanguage.HINGLISH -> "Kya aap yeh payment add karna chahte hain?"
                VoiceLanguage.HINDI -> "क्या आप यह पेमेंट जोड़ना चाहते हैं?"
                VoiceLanguage.ENGLISH -> "Will you want to add that payment?"
            }
            VoiceStep.HISAB_ITEM -> when (lang) {
                VoiceLanguage.HINGLISH -> "$partyName ke hisab ke liye item ka naam bataiye."
                VoiceLanguage.HINDI -> "$partyName के हिसाब के लिए आइटम का नाम बताइए।"
                VoiceLanguage.ENGLISH -> "Which item should I add to $partyName's Hisab?"
            }
            VoiceStep.HISAB_QUANTITY -> when (lang) {
                VoiceLanguage.HINGLISH -> "Kitne pieces add karne hain?"
                VoiceLanguage.HINDI -> "कितने पीस जोड़ने हैं?"
                VoiceLanguage.ENGLISH -> "How many pieces?"
            }
            VoiceStep.HISAB_RATE -> when (lang) {
                VoiceLanguage.HINGLISH -> "Ek piece ka rate kitna hai?"
                VoiceLanguage.HINDI -> "एक पीस का रेट कितना है?"
                VoiceLanguage.ENGLISH -> "What is the rate per piece?"
            }
            VoiceStep.HISAB_CONFIRM,
            VoiceStep.PARTY_CONFIRM,
            VoiceStep.ITEM_CONFIRM -> when (lang) {
                VoiceLanguage.HINGLISH -> "Kya main ise add kar doon?"
                VoiceLanguage.HINDI -> "क्या मैं इसे जोड़ दूँ?"
                VoiceLanguage.ENGLISH -> "Will you want to add that?"
            }
            VoiceStep.PARTY_NAME -> when (lang) {
                VoiceLanguage.HINGLISH -> "Nayi party ka naam bataiye."
                VoiceLanguage.HINDI -> "नई पार्टी का नाम बताइए।"
                VoiceLanguage.ENGLISH -> "Please tell me the new party name."
            }
            VoiceStep.PARTY_OPENING_BALANCE -> when (lang) {
                VoiceLanguage.HINGLISH -> "Opening balance bataiye ya zero boliye."
                VoiceLanguage.HINDI -> "ओपनिंग बैलेंस बताइए या जीरो बोलिए।"
                VoiceLanguage.ENGLISH -> "What is the opening balance, or say zero?"
            }
            VoiceStep.ITEM_NAME -> when (lang) {
                VoiceLanguage.HINGLISH -> "Naye item ka naam bataiye."
                VoiceLanguage.HINDI -> "नए आइटम का नाम बताइए।"
                VoiceLanguage.ENGLISH -> "What is the new item name?"
            }
            VoiceStep.ITEM_RATE -> when (lang) {
                VoiceLanguage.HINGLISH -> "Item ka rate per piece bataiye."
                VoiceLanguage.HINDI -> "आइटम का रेट प्रति पीस बताइए।"
                VoiceLanguage.ENGLISH -> "What is the rate per piece?"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechBridge.release()
    }
}

class VoiceAssistantViewModelFactory(
    private val repository: FactoryHisabRepository,
    private val speechBridge: SpeechBridge
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VoiceAssistantViewModel(repository, speechBridge) as T
    }
}
