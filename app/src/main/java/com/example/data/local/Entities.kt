package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val companyName: String,
    val ownerName: String,
    val onboardingCompleted: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val openingBalance: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partyId"])]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val name: String,
    val baseRate: Double,
    val defaultUnit: String = "pcs",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_sub_categories",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class ProductSubCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val name: String,
    val rateAdjustment: Double = 0.0
)

@Entity(
    tableName = "work_steps",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class WorkStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val name: String,
    val stepRate: Double,
    val isDefaultSelected: Boolean = true
)

@Entity(
    tableName = "hisabs",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partyId"])]
)
data class HisabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val billNumber: Int,
    val dateMillis: Long,
    val previousBalanceSnapshot: Double,
    val itemsSubtotal: Double,
    val paymentIncluded: Double,
    val netBalanceSnapshot: Double,
    val notes: String = ""
)

@Entity(
    tableName = "hisab_line_items",
    foreignKeys = [
        ForeignKey(
            entity = HisabEntity::class,
            parentColumns = ["id"],
            childColumns = ["hisabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["hisabId"])]
)
data class HisabLineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hisabId: Long,
    val productName: String,
    val subCategoryName: String = "",
    val appliedStepsSummary: String = "",
    val skippedStepsSummary: String = "",
    val unitLabel: String = "pcs",
    val quantity: Double,
    val unitRate: Double,
    val lineTotal: Double,
    val note: String = ""
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HisabEntity::class,
            parentColumns = ["id"],
            childColumns = ["hisabId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["hisabId"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val hisabId: Long? = null,
    val amount: Double,
    val dateMillis: Long,
    val paymentMode: String = "Cash",
    val note: String = ""
)

@Entity(
    tableName = "misc_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HisabEntity::class,
            parentColumns = ["id"],
            childColumns = ["hisabId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["hisabId"])]
)
data class MiscAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val hisabId: Long? = null,
    val description: String,
    val amount: Double,
    val isAddition: Boolean,
    val dateMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shared_slips",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HisabEntity::class,
            parentColumns = ["id"],
            childColumns = ["hisabId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["hisabId"])]
)
data class SharedSlipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hisabId: Long? = null,
    val partyId: Long,
    val partyName: String,
    val hisabDateMillis: Long,
    val sharedAtMillis: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val netBalanceSnapshot: Double,
    val imagePath: String = "",
    val shareFormat: String = "Image"
)

data class ProductWithDetails(
    @Embedded val product: ProductEntity,
    @Relation(parentColumn = "id", entityColumn = "productId")
    val subCategories: List<ProductSubCategoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "productId")
    val workSteps: List<WorkStepEntity>
)

data class HisabWithItems(
    @Embedded val hisab: HisabEntity,
    @Relation(parentColumn = "id", entityColumn = "hisabId")
    val items: List<HisabLineItemEntity>
)

data class PaymentWithParty(
    @Embedded val payment: PaymentEntity,
    val partyName: String
)

data class PartyBalanceSummary(
    @Embedded val party: PartyEntity,
    val totalBilled: Double,
    val totalPaid: Double,
    val totalMiscAdditions: Double = 0.0,
    val totalMiscDeductions: Double = 0.0,
    val hisabCount: Int,
    val productCount: Int,
    val lastActivityMillis: Long?
) {
    val currentBalance: Double
        get() = party.openingBalance + totalBilled + totalMiscAdditions - totalMiscDeductions - totalPaid
}
