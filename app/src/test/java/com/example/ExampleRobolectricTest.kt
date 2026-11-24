package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.FactoryHisabRepository
import com.example.data.local.HisabLineItemEntity
import com.example.data.local.PartyEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductSubCategoryEntity
import com.example.data.local.WorkStepEntity
import com.example.util.CurrencyFormatter
import com.example.util.HandwrittenSlipRenderer
import com.example.util.SlipRenderData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FactoryHisabRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FactoryHisabRepository(db.factoryHisabDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Factory Hisab", appName)
    }

    @Test
    fun `lucky pvt ltd carryover balance and payment add delete workflow`() = runTest {
        // 1. Add Party: Lucky Pvt. Limited
        val partyId = repository.saveParty(PartyEntity(name = "Lucky Pvt. Limited"))

        // 2. Add Product "New Model" with Base 2.50 (incl. Phase) and Cut (+2.00) => 4.50
        val productId = repository.saveProductWithDetails(
            product = ProductEntity(partyId = partyId, name = "New Model", baseRate = 1.50, defaultUnit = "pcs"),
            subCategories = listOf(ProductSubCategoryEntity(productId = 0, name = "Standard", rateAdjustment = 0.0)),
            workSteps = listOf(
                WorkStepEntity(productId = 0, name = "Phase", stepRate = 1.00, isDefaultSelected = true),
                WorkStepEntity(productId = 0, name = "Cut", stepRate = 2.00, isDefaultSelected = true)
            )
        )

        val products = repository.observeProductsForParty(partyId).first()
        assertEquals(1, products.size)
        val prod = products.first()
        val fullRate = prod.product.baseRate + prod.workSteps.sumOf { it.stepRate }
        val withoutCutRate = prod.product.baseRate + prod.workSteps.filter { it.name != "Cut" }.sumOf { it.stepRate }
        assertEquals(4.50, fullRate, 0.001)
        assertEquals(2.50, withoutCutRate, 0.001)

        // 3. Create Hisab #1 totaling 45,000
        val hisab1Id = repository.createHisab(
            partyId = partyId,
            dateMillis = 1_700_000_000_000L,
            items = listOf(
                HisabLineItemEntity(
                    hisabId = 0,
                    productName = "New Model",
                    subCategoryName = "Standard",
                    appliedStepsSummary = "Phase, Cut",
                    quantity = 6000.0,
                    unitRate = 4.50,
                    lineTotal = 27000.0
                ),
                HisabLineItemEntity(
                    hisabId = 0,
                    productName = "New Model",
                    appliedStepsSummary = "Phase",
                    skippedStepsSummary = "Cut",
                    quantity = 7200.0,
                    unitRate = 2.50,
                    lineTotal = 18000.0
                )
            ),
            paymentIncluded = 0.0,
            paymentMode = "Cash",
            notes = ""
        )

        // 4. Add two payments: 25,000 and 10,000 -> Balance should be 10,000
        val pay1Id = repository.addPayment(
            PaymentEntity(partyId = partyId, amount = 25000.0, dateMillis = 1_700_000_100_000L, paymentMode = "Bank")
        )
        val pay2 = PaymentEntity(id = 0, partyId = partyId, amount = 10000.0, dateMillis = 1_700_000_200_000L, paymentMode = "UPI")
        val pay2Id = repository.addPayment(pay2)

        val summaryAfterPays = repository.observePartySummary(partyId).first()!!
        assertEquals(45000.0, summaryAfterPays.totalBilled, 0.001)
        assertEquals(35000.0, summaryAfterPays.totalPaid, 0.001)
        assertEquals(10000.0, summaryAfterPays.currentBalance, 0.001)

        // 5. New Hisab #2 should automatically carry over 10,000 previous balance
        val hisab2Id = repository.createHisab(
            partyId = partyId,
            dateMillis = 1_700_000_300_000L,
            items = listOf(
                HisabLineItemEntity(
                    hisabId = 0,
                    productName = "New Model",
                    appliedStepsSummary = "Phase, Cut",
                    quantity = 1000.0,
                    unitRate = 4.50,
                    lineTotal = 4500.0
                )
            ),
            paymentIncluded = 0.0,
            paymentMode = "Cash",
            notes = ""
        )
        val hisab2 = repository.observeHisabById(hisab2Id).first()!!
        assertEquals(10000.0, hisab2.hisab.previousBalanceSnapshot, 0.001)
        assertEquals(4500.0, hisab2.hisab.itemsSubtotal, 0.001)
        assertEquals(14500.0, hisab2.hisab.netBalanceSnapshot, 0.001)

        // 6. Delete second payment (10,000) -> Live balance increases by 10,000 to 24,500
        repository.deletePayment(pay2.copy(id = pay2Id))
        val summaryAfterDelete = repository.observePartySummary(partyId).first()!!
        assertEquals(24500.0, summaryAfterDelete.currentBalance, 0.001)

        // 7. Test Company Profile & Misc (+/-) adjustments + Register Item
        repository.saveCompanyProfile("Shree Ganesh Engineering", "Rajesh Kumar", true)
        val profile = repository.observeCompanyProfile().first()!!
        assertEquals("Shree Ganesh Engineering", profile.companyName)

        repository.addMiscAdjustment(
            com.example.data.local.MiscAdjustmentEntity(
                partyId = partyId,
                description = "Freight",
                amount = 500.0,
                isAddition = true,
                dateMillis = null
            )
        )
        repository.addMiscAdjustment(
            com.example.data.local.MiscAdjustmentEntity(
                partyId = partyId,
                description = "Discount",
                amount = 200.0,
                isAddition = false,
                dateMillis = 1_700_000_400_000L
            )
        )
        val summaryWithMisc = repository.observePartySummary(partyId).first()!!
        assertEquals(24800.0, summaryWithMisc.currentBalance, 0.001)

        // 8. Render handwritten ruled-paper bitmap and check Indian Rupee formatting
        val slipBitmap = HandwrittenSlipRenderer.renderSlipBitmap(
            context,
            SlipRenderData(
                companyName = profile.companyName,
                ownerName = profile.ownerName,
                partyName = "Lucky Pvt. Limited",
                partyPhone = "9876543210",
                hisabWithItems = hisab2,
                includeLiveBalance = true,
                currentLiveBalance = summaryWithMisc.currentBalance,
                totalPaidAfterBill = 0.0
            )
        )
        assertEquals(1080, slipBitmap.width)
        assertTrue(slipBitmap.height > 600)
        assertEquals("₹45,000", CurrencyFormatter.formatRupees(45000.0))
        assertEquals("₹4.50", CurrencyFormatter.formatRate(4.5))
        assertTrue(hisab1Id > 0L && pay1Id > 0L && productId > 0L)

        // 9. Test editing a register item recalculates hisab & party balance
        val itemToEdit = hisab2.items.first()
        repository.updateRegisterLineItem(
            partyId = partyId,
            oldItem = itemToEdit,
            newItem = itemToEdit.copy(quantity = 2000.0, lineTotal = 9000.0),
            newDateMillis = hisab2.hisab.dateMillis,
            dayStartMillis = hisab2.hisab.dateMillis - 1000L,
            dayEndMillis = hisab2.hisab.dateMillis + 1000L
        )
        val hisab2AfterEdit = repository.observeHisabById(hisab2Id).first()!!
        assertEquals(9000.0, hisab2AfterEdit.hisab.itemsSubtotal, 0.001)

        // 10. Test saving and observing shared slips
        val slipId = repository.addSharedSlip(
            com.example.data.local.SharedSlipEntity(
                hisabId = hisab2Id,
                partyId = partyId,
                partyName = "Lucky Pvt. Limited",
                hisabDateMillis = hisab2.hisab.dateMillis,
                totalAmount = 9000.0,
                netBalanceSnapshot = 29300.0,
                imagePath = "",
                shareFormat = "Image"
            )
        )
        assertTrue(slipId > 0L)
        val savedSlips = repository.observeAllSharedSlips().first()
        assertEquals(1, savedSlips.size)
        assertEquals("Lucky Pvt. Limited", savedSlips.first().partyName)

        // 11. Verify slip text summary shows Advance Paid at bottom and Balance label (not Net Balance / Later Payments)
        val newSlipSummary = HandwrittenSlipRenderer.buildTextSummary(
            SlipRenderData(
                companyName = profile.companyName,
                ownerName = profile.ownerName,
                partyName = "Lucky Pvt. Limited",
                partyPhone = "9876543210",
                hisabWithItems = hisab2AfterEdit,
                includeLiveBalance = false,
                currentLiveBalance = 29300.0,
                totalPaidAfterBill = 0.0,
                computedPrevBalance = 45000.0,
                computedMiscNet = 0.0,
                computedAdvancePaid = 25000.0,
                computedBillBalance = 29000.0
            )
        )
        assertTrue(newSlipSummary.contains("Advance Paid: -₹25,000.00"))
        assertTrue(newSlipSummary.contains("*Balance: ₹29,000.00*"))
        assertTrue(!newSlipSummary.contains("Later Payments"))
        assertTrue(!newSlipSummary.contains("Net Balance"))
    }
}
