package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FactoryHisabDao {

    @Query("SELECT * FROM company_profile WHERE id = 1 LIMIT 1")
    fun observeCompanyProfile(): Flow<CompanyProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompanyProfile(profile: CompanyProfileEntity)

    @Query(
        """
        SELECT p.*,
            COALESCE((SELECT SUM(h.itemsSubtotal) FROM hisabs h WHERE h.partyId = p.id), 0.0) AS totalBilled,
            COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.partyId = p.id), 0.0) AS totalPaid,
            COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = p.id AND m.isAddition = 1), 0.0) AS totalMiscAdditions,
            COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = p.id AND m.isAddition = 0), 0.0) AS totalMiscDeductions,
            COALESCE((SELECT COUNT(*) FROM hisabs h WHERE h.partyId = p.id), 0) AS hisabCount,
            COALESCE((SELECT COUNT(*) FROM products pr WHERE pr.partyId = p.id), 0) AS productCount,
            NULLIF(MAX(
                COALESCE((SELECT MAX(h.dateMillis) FROM hisabs h WHERE h.partyId = p.id), 0),
                COALESCE((SELECT MAX(pay.dateMillis) FROM payments pay WHERE pay.partyId = p.id), 0)
            ), 0) AS lastActivityMillis
        FROM parties p
        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observePartySummaries(): Flow<List<PartyBalanceSummary>>

    @Query(
        """
        SELECT p.*,
            COALESCE((SELECT SUM(h.itemsSubtotal) FROM hisabs h WHERE h.partyId = p.id), 0.0) AS totalBilled,
            COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.partyId = p.id), 0.0) AS totalPaid,
            COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = p.id AND m.isAddition = 1), 0.0) AS totalMiscAdditions,
            COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = p.id AND m.isAddition = 0), 0.0) AS totalMiscDeductions,
            COALESCE((SELECT COUNT(*) FROM hisabs h WHERE h.partyId = p.id), 0) AS hisabCount,
            COALESCE((SELECT COUNT(*) FROM products pr WHERE pr.partyId = p.id), 0) AS productCount,
            NULLIF(MAX(
                COALESCE((SELECT MAX(h.dateMillis) FROM hisabs h WHERE h.partyId = p.id), 0),
                COALESCE((SELECT MAX(pay.dateMillis) FROM payments pay WHERE pay.partyId = p.id), 0)
            ), 0) AS lastActivityMillis
        FROM parties p
        WHERE p.id = :partyId
        LIMIT 1
        """
    )
    fun observePartySummary(partyId: Long): Flow<PartyBalanceSummary?>

    @Query("SELECT * FROM parties WHERE id = :partyId LIMIT 1")
    suspend fun getPartyById(partyId: Long): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Delete
    suspend fun deleteParty(party: PartyEntity)

    @Transaction
    @Query("SELECT * FROM products WHERE partyId = :partyId ORDER BY name COLLATE NOCASE ASC")
    fun observeProductsForParty(partyId: Long): Flow<List<ProductWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubCategories(subCategories: List<ProductSubCategoryEntity>)

    @Query("DELETE FROM product_sub_categories WHERE productId = :productId")
    suspend fun deleteSubCategoriesForProduct(productId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkSteps(workSteps: List<WorkStepEntity>)

    @Query("DELETE FROM work_steps WHERE productId = :productId")
    suspend fun deleteWorkStepsForProduct(productId: Long)

    @Transaction
    suspend fun saveProductWithDetails(
        product: ProductEntity,
        subCategories: List<ProductSubCategoryEntity>,
        workSteps: List<WorkStepEntity>
    ): Long {
        val productId = if (product.id == 0L) {
            insertProduct(product)
        } else {
            updateProduct(product)
            deleteSubCategoriesForProduct(product.id)
            deleteWorkStepsForProduct(product.id)
            product.id
        }
        if (subCategories.isNotEmpty()) {
            insertSubCategories(subCategories.map { it.copy(id = 0, productId = productId) })
        }
        if (workSteps.isNotEmpty()) {
            insertWorkSteps(workSteps.map { it.copy(id = 0, productId = productId) })
        }
        return productId
    }

    @Transaction
    @Query("SELECT * FROM hisabs WHERE partyId = :partyId ORDER BY dateMillis DESC, id DESC")
    fun observeHisabsForParty(partyId: Long): Flow<List<HisabWithItems>>

    @Transaction
    @Query("SELECT * FROM hisabs WHERE partyId = :partyId ORDER BY dateMillis ASC, id ASC")
    fun observeHisabsForPartyChronological(partyId: Long): Flow<List<HisabWithItems>>

    @Query("SELECT * FROM hisabs ORDER BY dateMillis DESC")
    fun observeAllHisabs(): Flow<List<HisabEntity>>

    @Transaction
    @Query("SELECT * FROM hisabs WHERE id = :hisabId LIMIT 1")
    fun observeHisabById(hisabId: Long): Flow<HisabWithItems?>

    @Query("SELECT * FROM hisabs WHERE partyId = :partyId AND dateMillis BETWEEN :dayStart AND :dayEnd ORDER BY id DESC LIMIT 1")
    suspend fun findHisabOnDay(partyId: Long, dayStart: Long, dayEnd: Long): HisabEntity?

    @Query("SELECT COALESCE(MAX(billNumber), 0) FROM hisabs WHERE partyId = :partyId")
    suspend fun getMaxBillNumberForParty(partyId: Long): Int

    @Query(
        """
        SELECT COALESCE(p.openingBalance, 0.0)
            + COALESCE((SELECT SUM(h.itemsSubtotal) FROM hisabs h WHERE h.partyId = :partyId), 0.0)
            + COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = :partyId AND m.isAddition = 1), 0.0)
            - COALESCE((SELECT SUM(m.amount) FROM misc_adjustments m WHERE m.partyId = :partyId AND m.isAddition = 0), 0.0)
            - COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.partyId = :partyId), 0.0)
        FROM parties p
        WHERE p.id = :partyId
        """
    )
    suspend fun getCurrentBalanceForParty(partyId: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHisab(hisab: HisabEntity): Long

    @Update
    suspend fun updateHisab(hisab: HisabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHisabLineItems(items: List<HisabLineItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHisabLineItem(item: HisabLineItemEntity): Long

    @Update
    suspend fun updateHisabLineItem(item: HisabLineItemEntity)

    @Query("SELECT * FROM hisab_line_items WHERE hisabId = :hisabId")
    suspend fun getLineItemsForHisab(hisabId: Long): List<HisabLineItemEntity>

    @Delete
    suspend fun deleteHisabLineItem(item: HisabLineItemEntity)

    @Delete
    suspend fun deleteHisab(hisab: HisabEntity)

    @Query("DELETE FROM payments WHERE hisabId = :hisabId")
    suspend fun deletePaymentsForHisab(hisabId: Long)

    @Transaction
    suspend fun deleteHisabAndOptionalPayments(hisab: HisabEntity, deleteLinkedPayments: Boolean) {
        if (deleteLinkedPayments) {
            deletePaymentsForHisab(hisab.id)
        }
        deleteHisab(hisab)
    }

    @Transaction
    suspend fun addRegisterItem(
        partyId: Long,
        calculationDateMillis: Long,
        dayStartMillis: Long,
        dayEndMillis: Long,
        forceNewSection: Boolean,
        item: HisabLineItemEntity
    ): Long {
        val existing = if (forceNewSection) null else findHisabOnDay(partyId, dayStartMillis, dayEndMillis)
        return if (existing == null) {
            val previousBalance = getCurrentBalanceForParty(partyId) ?: 0.0
            val billNumber = getMaxBillNumberForParty(partyId) + 1
            val hisabId = insertHisab(
                HisabEntity(
                    partyId = partyId,
                    billNumber = billNumber,
                    dateMillis = calculationDateMillis,
                    previousBalanceSnapshot = previousBalance,
                    itemsSubtotal = item.lineTotal,
                    paymentIncluded = 0.0,
                    netBalanceSnapshot = previousBalance + item.lineTotal,
                    notes = ""
                )
            )
            insertHisabLineItem(item.copy(id = 0, hisabId = hisabId))
            hisabId
        } else {
            insertHisabLineItem(item.copy(id = 0, hisabId = existing.id))
            val newSubtotal = existing.itemsSubtotal + item.lineTotal
            updateHisab(
                existing.copy(
                    itemsSubtotal = newSubtotal,
                    netBalanceSnapshot = existing.previousBalanceSnapshot + newSubtotal - existing.paymentIncluded
                )
            )
            existing.id
        }
    }

    @Transaction
    suspend fun removeRegisterLineItem(item: HisabLineItemEntity) {
        deleteHisabLineItem(item)
        val remaining = getLineItemsForHisab(item.hisabId)
        val hisab = getHisabByIdSync(item.hisabId) ?: return
        if (remaining.isEmpty()) {
            deleteHisab(hisab)
        } else {
            val newSubtotal = remaining.sumOf { it.lineTotal }
            updateHisab(
                hisab.copy(
                    itemsSubtotal = newSubtotal,
                    netBalanceSnapshot = hisab.previousBalanceSnapshot + newSubtotal - hisab.paymentIncluded
                )
            )
        }
    }

    @Transaction
    suspend fun updateRegisterLineItem(
        partyId: Long,
        oldItem: HisabLineItemEntity,
        newItem: HisabLineItemEntity,
        newDateMillis: Long,
        dayStartMillis: Long,
        dayEndMillis: Long
    ) {
        val currentHisab = getHisabByIdSync(oldItem.hisabId)
        val sameDay = currentHisab != null && currentHisab.dateMillis in dayStartMillis..dayEndMillis
        if (sameDay && currentHisab != null) {
            updateHisabLineItem(newItem.copy(id = oldItem.id, hisabId = currentHisab.id))
            val allItems = getLineItemsForHisab(currentHisab.id)
            val newSubtotal = allItems.sumOf { it.lineTotal }
            updateHisab(
                currentHisab.copy(
                    itemsSubtotal = newSubtotal,
                    netBalanceSnapshot = currentHisab.previousBalanceSnapshot + newSubtotal - currentHisab.paymentIncluded
                )
            )
        } else {
            removeRegisterLineItem(oldItem)
            addRegisterItem(
                partyId = partyId,
                calculationDateMillis = newDateMillis,
                dayStartMillis = dayStartMillis,
                dayEndMillis = dayEndMillis,
                forceNewSection = false,
                item = newItem
            )
        }
    }

    @Transaction
    suspend fun updateHisabDate(hisabId: Long, newDateMillis: Long) {
        val hisab = getHisabByIdSync(hisabId) ?: return
        updateHisab(hisab.copy(dateMillis = newDateMillis))
    }

    @Query("SELECT * FROM hisabs WHERE id = :hisabId LIMIT 1")
    suspend fun getHisabByIdSync(hisabId: Long): HisabEntity?

    @Transaction
    suspend fun createHisabWithItemsAndOptionalPayment(
        partyId: Long,
        dateMillis: Long,
        items: List<HisabLineItemEntity>,
        paymentIncluded: Double,
        paymentMode: String,
        notes: String
    ): Long {
        val previousBalance = getCurrentBalanceForParty(partyId) ?: 0.0
        val itemsSubtotal = items.sumOf { it.lineTotal }
        val billNumber = getMaxBillNumberForParty(partyId) + 1
        val netBalance = previousBalance + itemsSubtotal - paymentIncluded

        val hisabId = insertHisab(
            HisabEntity(
                partyId = partyId,
                billNumber = billNumber,
                dateMillis = dateMillis,
                previousBalanceSnapshot = previousBalance,
                itemsSubtotal = itemsSubtotal,
                paymentIncluded = paymentIncluded,
                netBalanceSnapshot = netBalance,
                notes = notes
            )
        )
        insertHisabLineItems(items.map { it.copy(id = 0, hisabId = hisabId) })

        if (paymentIncluded > 0.0) {
            insertPayment(
                PaymentEntity(
                    partyId = partyId,
                    hisabId = hisabId,
                    amount = paymentIncluded,
                    dateMillis = dateMillis,
                    paymentMode = paymentMode,
                    note = "Paid with Hisab #$billNumber"
                )
            )
        }
        return hisabId
    }

    @Query("SELECT * FROM payments WHERE partyId = :partyId ORDER BY dateMillis DESC, id DESC")
    fun observePaymentsForParty(partyId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE partyId = :partyId ORDER BY dateMillis ASC, id ASC")
    fun observePaymentsForPartyChronological(partyId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY dateMillis DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query(
        """
        SELECT pay.*, p.name AS partyName
        FROM payments pay
        INNER JOIN parties p ON p.id = pay.partyId
        ORDER BY pay.dateMillis DESC, pay.id DESC
        """
    )
    fun observeAllPaymentsWithParty(): Flow<List<PaymentWithParty>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("SELECT * FROM misc_adjustments WHERE partyId = :partyId ORDER BY COALESCE(dateMillis, createdAt) ASC, id ASC")
    fun observeMiscAdjustmentsForParty(partyId: Long): Flow<List<MiscAdjustmentEntity>>

    @Query("SELECT * FROM misc_adjustments ORDER BY createdAt DESC")
    fun observeAllMiscAdjustments(): Flow<List<MiscAdjustmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMiscAdjustment(adjustment: MiscAdjustmentEntity): Long

    @Update
    suspend fun updateMiscAdjustment(adjustment: MiscAdjustmentEntity)

    @Delete
    suspend fun deleteMiscAdjustment(adjustment: MiscAdjustmentEntity)

    @Query("SELECT * FROM shared_slips ORDER BY sharedAtMillis DESC, id DESC")
    fun observeAllSharedSlips(): Flow<List<SharedSlipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedSlip(slip: SharedSlipEntity): Long

    @Delete
    suspend fun deleteSharedSlip(slip: SharedSlipEntity)
}
