package com.example.data.local

import kotlinx.coroutines.flow.Flow

class FactoryHisabRepository(private val dao: FactoryHisabDao) {

    fun observeCompanyProfile(): Flow<CompanyProfileEntity?> = dao.observeCompanyProfile()

    suspend fun saveCompanyProfile(companyName: String, ownerName: String, onboardingCompleted: Boolean = true) {
        dao.saveCompanyProfile(
            CompanyProfileEntity(
                id = 1,
                companyName = companyName.trim(),
                ownerName = ownerName.trim(),
                onboardingCompleted = onboardingCompleted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun observePartySummaries(): Flow<List<PartyBalanceSummary>> = dao.observePartySummaries()

    fun observePartySummary(partyId: Long): Flow<PartyBalanceSummary?> =
        dao.observePartySummary(partyId)

    suspend fun getPartyById(partyId: Long): PartyEntity? = dao.getPartyById(partyId)

    suspend fun saveParty(party: PartyEntity): Long {
        return if (party.id == 0L) {
            dao.insertParty(party)
        } else {
            dao.updateParty(party)
            party.id
        }
    }

    suspend fun deleteParty(party: PartyEntity) = dao.deleteParty(party)

    fun observeProductsForParty(partyId: Long): Flow<List<ProductWithDetails>> =
        dao.observeProductsForParty(partyId)

    suspend fun saveProductWithDetails(
        product: ProductEntity,
        subCategories: List<ProductSubCategoryEntity>,
        workSteps: List<WorkStepEntity>
    ): Long = dao.saveProductWithDetails(product, subCategories, workSteps)

    suspend fun deleteProduct(product: ProductEntity) = dao.deleteProduct(product)

    fun observeHisabsForParty(partyId: Long): Flow<List<HisabWithItems>> =
        dao.observeHisabsForParty(partyId)

    fun observeHisabsForPartyChronological(partyId: Long): Flow<List<HisabWithItems>> =
        dao.observeHisabsForPartyChronological(partyId)

    fun observeAllHisabs(): Flow<List<HisabEntity>> = dao.observeAllHisabs()

    fun observeHisabById(hisabId: Long): Flow<HisabWithItems?> = dao.observeHisabById(hisabId)

    suspend fun addRegisterItem(
        partyId: Long,
        calculationDateMillis: Long,
        dayStartMillis: Long,
        dayEndMillis: Long,
        forceNewSection: Boolean,
        item: HisabLineItemEntity
    ): Long = dao.addRegisterItem(
        partyId = partyId,
        calculationDateMillis = calculationDateMillis,
        dayStartMillis = dayStartMillis,
        dayEndMillis = dayEndMillis,
        forceNewSection = forceNewSection,
        item = item
    )

    suspend fun removeRegisterLineItem(item: HisabLineItemEntity) =
        dao.removeRegisterLineItem(item)

    suspend fun updateRegisterLineItem(
        partyId: Long,
        oldItem: HisabLineItemEntity,
        newItem: HisabLineItemEntity,
        newDateMillis: Long,
        dayStartMillis: Long,
        dayEndMillis: Long
    ) = dao.updateRegisterLineItem(
        partyId = partyId,
        oldItem = oldItem,
        newItem = newItem,
        newDateMillis = newDateMillis,
        dayStartMillis = dayStartMillis,
        dayEndMillis = dayEndMillis
    )

    suspend fun updateHisabDate(hisabId: Long, newDateMillis: Long) =
        dao.updateHisabDate(hisabId, newDateMillis)

    suspend fun createHisab(
        partyId: Long,
        dateMillis: Long,
        items: List<HisabLineItemEntity>,
        paymentIncluded: Double,
        paymentMode: String,
        notes: String
    ): Long = dao.createHisabWithItemsAndOptionalPayment(
        partyId = partyId,
        dateMillis = dateMillis,
        items = items,
        paymentIncluded = paymentIncluded,
        paymentMode = paymentMode,
        notes = notes
    )

    suspend fun deleteHisab(hisab: HisabEntity, deleteLinkedPayments: Boolean) =
        dao.deleteHisabAndOptionalPayments(hisab, deleteLinkedPayments)

    fun observePaymentsForParty(partyId: Long): Flow<List<PaymentEntity>> =
        dao.observePaymentsForParty(partyId)

    fun observePaymentsForPartyChronological(partyId: Long): Flow<List<PaymentEntity>> =
        dao.observePaymentsForPartyChronological(partyId)

    fun observeAllPayments(): Flow<List<PaymentEntity>> = dao.observeAllPayments()

    fun observeAllPaymentsWithParty(): Flow<List<PaymentWithParty>> =
        dao.observeAllPaymentsWithParty()

    suspend fun addPayment(payment: PaymentEntity): Long = dao.insertPayment(payment)

    suspend fun updatePayment(payment: PaymentEntity) = dao.updatePayment(payment)

    suspend fun deletePayment(payment: PaymentEntity) = dao.deletePayment(payment)

    fun observeMiscAdjustmentsForParty(partyId: Long): Flow<List<MiscAdjustmentEntity>> =
        dao.observeMiscAdjustmentsForParty(partyId)

    fun observeAllMiscAdjustments(): Flow<List<MiscAdjustmentEntity>> =
        dao.observeAllMiscAdjustments()

    suspend fun addMiscAdjustment(adjustment: MiscAdjustmentEntity): Long =
        dao.insertMiscAdjustment(adjustment)

    suspend fun updateMiscAdjustment(adjustment: MiscAdjustmentEntity) =
        dao.updateMiscAdjustment(adjustment)

    suspend fun deleteMiscAdjustment(adjustment: MiscAdjustmentEntity) =
        dao.deleteMiscAdjustment(adjustment)

    fun observeAllSharedSlips(): Flow<List<SharedSlipEntity>> =
        dao.observeAllSharedSlips()

    suspend fun addSharedSlip(slip: SharedSlipEntity): Long =
        dao.insertSharedSlip(slip)

    suspend fun deleteSharedSlip(slip: SharedSlipEntity) =
        dao.deleteSharedSlip(slip)
}
