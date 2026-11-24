package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.PartyBalanceSummary
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.WorkStepEntity
import com.example.voice.ConfirmIntent
import com.example.voice.HindiHinglishNlu
import com.example.voice.MenuParseResult
import com.example.voice.SpeechBridge
import com.example.voice.VoiceAssistantViewModel
import com.example.voice.VoiceLanguage
import com.example.voice.VoiceQuickPanel
import com.example.voice.VoiceStep
import com.example.voice.VoiceTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceAssistantTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FactoryHisabRepository
    private lateinit var fakeSpeech: FakeSpeechBridge

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = FactoryHisabRepository(db.factoryHisabDao())
        fakeSpeech = FakeSpeechBridge()
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `nlu parses devanagari hindi hinglish english numbers modes and confirmations`() {
        assertEquals(5000.0, HindiHinglishNlu.parseNumber("पांच हजार") ?: 0.0, 0.01)
        assertEquals(1500.0, HindiHinglishNlu.parseNumber("dedh hazaar") ?: 0.0, 0.01)
        assertEquals(3500.0, HindiHinglishNlu.parseNumber("sadhe teen hazaar") ?: 0.0, 0.01)
        assertEquals(10000.0, HindiHinglishNlu.parseNumber("₹10,000") ?: 0.0, 0.01)
        assertEquals(2500.0, HindiHinglishNlu.parseNumber("2.5k") ?: 0.0, 0.01)

        assertEquals("UPI", HindiHinglishNlu.parsePaymentMode("यूपीआई से आया"))
        assertEquals("Cash", HindiHinglishNlu.parsePaymentMode("naqad cash diya"))
        assertEquals("Bank", HindiHinglishNlu.parsePaymentMode("bank transfer neft"))
        assertEquals("Cheque", HindiHinglishNlu.parsePaymentMode("चेक दिया"))

        assertEquals(ConfirmIntent.YES, HindiHinglishNlu.parseConfirmation("हाँ जोड़ दो"))
        assertEquals(ConfirmIntent.YES, HindiHinglishNlu.parseConfirmation("haan theek hai"))
        assertEquals(ConfirmIntent.NO, HindiHinglishNlu.parseConfirmation("नहीं मत जोड़ो"))

        assertEquals(
            MenuParseResult.Selected(VoiceTaskType.ADD_PAYMENT),
            HindiHinglishNlu.parseMenuChoice("पेमेंट जोड़नी है")
        )
        assertEquals(
            MenuParseResult.Selected(VoiceTaskType.ADD_HISAB),
            HindiHinglishNlu.parseMenuChoice("naya hisab banana hai")
        )
        assertEquals(
            MenuParseResult.OutOfScope,
            HindiHinglishNlu.parseMenuChoice("what is the weather in delhi today")
        )

        val parties = listOf(
            PartyBalanceSummary(
                party = PartyEntity(id = 1L, name = "Lucky Pvt. Limited"),
                totalBilled = 0.0,
                totalPaid = 0.0,
                hisabCount = 0,
                productCount = 1,
                lastActivityMillis = null
            )
        )
        val matchedDevanagari = HindiHinglishNlu.findBestPartyMatch("लकी प्राइवेट लिमिटेड", parties)
        assertNotNull(matchedDevanagari)
        assertEquals(1L, matchedDevanagari?.party?.id)
        assertNull(HindiHinglishNlu.findBestPartyMatch("xyz unknown client", parties))
    }

    @Test
    fun `payment workflow enforces 3 attempt party fallback payment mode prompt and confirm before save`() = runTest {
        val partyId = repository.saveParty(PartyEntity(name = "Lucky Pvt. Limited"))
        val vm = VoiceAssistantViewModel(repository, fakeSpeech)
        advanceUntilIdle()

        vm.openAssistant()
        advanceUntilIdle()

        // Default language must be Hinglish
        assertEquals(VoiceLanguage.HINGLISH, vm.uiState.value.language)
        assertEquals(VoiceStep.MENU, vm.uiState.value.step)

        // Select "Add new payment"
        vm.submitUserInput("Payment add karna hai")
        advanceUntilIdle()
        assertEquals(VoiceStep.PAYMENT_PARTY, vm.uiState.value.step)

        // 1st miss -> repeat prompt, no party list yet
        vm.submitUserInput("Gaurav Steel")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.partyRetryCount)
        assertEquals(VoiceQuickPanel.NONE, vm.uiState.value.quickPanel)

        // 2nd miss -> repeat prompt, still no party list
        vm.submitUserInput("Maan Foundry")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.partyRetryCount)
        assertEquals(VoiceQuickPanel.NONE, vm.uiState.value.quickPanel)

        // 3rd miss -> party list fallback opens!
        vm.submitUserInput("Unknown Trader")
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.partyRetryCount)
        assertEquals(VoiceQuickPanel.PARTY_PICKER, vm.uiState.value.quickPanel)

        // User can also say or pick the party from list
        vm.submitUserInput("लकी")
        advanceUntilIdle()
        assertEquals(VoiceStep.PAYMENT_AMOUNT_AND_MODE, vm.uiState.value.step)

        // User says amount only without payment method -> prompts with available options + chips
        vm.submitUserInput("दस हजार")
        advanceUntilIdle()
        assertEquals(VoiceStep.PAYMENT_MODE_ONLY, vm.uiState.value.step)
        assertEquals(VoiceQuickPanel.PAYMENT_MODES, vm.uiState.value.quickPanel)
        val lastPrompt = vm.uiState.value.messages.last().text
        assertTrue(lastPrompt.contains("Cash") && lastPrompt.contains("UPI"))

        // Provide payment mode -> enters confirm step WITHOUT saving yet
        vm.submitUserInput("UPI")
        advanceUntilIdle()
        assertEquals(VoiceStep.PAYMENT_CONFIRM, vm.uiState.value.step)
        assertEquals(VoiceQuickPanel.CONFIRMATION_CARD, vm.uiState.value.quickPanel)
        assertEquals(0, repository.observePaymentsForParty(partyId).first().size)

        // Confirm with Hinglish "Haan" -> NOW saves payment!
        vm.submitUserInput("Haan kar do")
        advanceUntilIdle()
        val savedPayments = repository.observePaymentsForParty(partyId).first()
        assertEquals(1, savedPayments.size)
        assertEquals(10000.0, savedPayments.first().amount, 0.01)
        assertEquals("UPI", savedPayments.first().paymentMode)
        assertEquals(VoiceStep.COMPLETED, vm.uiState.value.step)
    }

    @Test
    fun `new party item and hisab flows save to room only after explicit confirmation`() = runTest {
        val vm = VoiceAssistantViewModel(repository, fakeSpeech)
        advanceUntilIdle()

        // 1. Add Party (switch to English to test English flow)
        vm.openAssistant()
        vm.switchLanguage(VoiceLanguage.ENGLISH)
        vm.submitUserInput("3")
        advanceUntilIdle()
        vm.submitUserInput("Shree Ganesh Works")
        vm.submitUserInput("skip")
        advanceUntilIdle()
        assertEquals(VoiceStep.PARTY_CONFIRM, vm.uiState.value.step)
        assertEquals(0, repository.observePartySummaries().first().size)

        vm.submitUserInput("yes")
        advanceUntilIdle()
        val parties = repository.observePartySummaries().first()
        assertEquals(1, parties.size)
        val partyId = parties.first().party.id

        // 2. Add Item under Shree Ganesh Works
        vm.restartConversation()
        vm.submitUserInput("add new item")
        advanceUntilIdle()
        vm.submitUserInput("Ganesh")
        advanceUntilIdle()
        vm.submitUserInput("Brass Valve")
        vm.submitUserInput("4.50")
        advanceUntilIdle()
        assertEquals(VoiceStep.ITEM_CONFIRM, vm.uiState.value.step)
        assertEquals(0, repository.observeProductsForParty(partyId).first().size)

        vm.submitUserInput("confirm")
        advanceUntilIdle()
        val products = repository.observeProductsForParty(partyId).first()
        assertEquals(1, products.size)
        assertEquals("Brass Valve", products.first().product.name)
        assertEquals(4.50, products.first().product.baseRate, 0.01)

        // 3. Add New Hisab using Hindi voice input
        vm.switchLanguage(VoiceLanguage.HINDI)
        vm.restartConversation()
        vm.submitUserInput("नया हिसाब बनाना है")
        advanceUntilIdle()
        vm.submitUserInput("गणेश")
        advanceUntilIdle()
        vm.submitUserInput("Brass Valve")
        advanceUntilIdle()
        vm.submitUserInput("200")
        advanceUntilIdle()
        assertEquals(VoiceStep.HISAB_CONFIRM, vm.uiState.value.step)
        assertEquals(0, repository.observeHisabsForParty(partyId).first().size)

        vm.submitUserInput("हाँ")
        advanceUntilIdle()
        val hisabs = repository.observeHisabsForParty(partyId).first()
        assertEquals(1, hisabs.size)
        assertEquals(900.0, hisabs.first().hisab.itemsSubtotal, 0.01)
    }

    @Test
    fun `cancel on confirmation does not persist data`() = runTest {
        val partyId = repository.saveParty(
            PartyEntity(name = "Lucky Pvt. Limited")
        )
        repository.saveProductWithDetails(
            product = ProductEntity(partyId = partyId, name = "Bolt", baseRate = 10.0),
            subCategories = emptyList(),
            workSteps = listOf(WorkStepEntity(productId = 0, name = "Finish", stepRate = 2.0))
        )
        val vm = VoiceAssistantViewModel(repository, fakeSpeech)
        advanceUntilIdle()

        vm.openAssistant()
        vm.submitUserInput("1")
        advanceUntilIdle()
        vm.submitUserInput("Lucky")
        vm.submitUserInput("5000 Cash")
        advanceUntilIdle()
        assertEquals(VoiceStep.PAYMENT_CONFIRM, vm.uiState.value.step)

        vm.submitUserInput("nahi cancel karo")
        advanceUntilIdle()
        assertEquals(0, repository.observePaymentsForParty(partyId).first().size)
        assertEquals(VoiceStep.MENU, vm.uiState.value.step)
    }
}

private class FakeSpeechBridge : SpeechBridge {
    val spokenHistory = mutableListOf<String>()

    override fun speak(
        text: String,
        language: VoiceLanguage,
        onStarted: () -> Unit,
        onDone: () -> Unit
    ) {
        spokenHistory.add(text)
        onStarted()
        // Keep manual submitUserInput deterministic in tests without auto-starting SpeechRecognizer
    }

    override fun stopSpeaking() = Unit

    override fun startListening(
        language: VoiceLanguage,
        onReady: () -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) = Unit

    override fun stopListening() = Unit

    override fun isSpeechRecognitionAvailable(): Boolean = false

    override fun release() = Unit
}
