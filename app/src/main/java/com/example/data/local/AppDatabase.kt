package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CompanyProfileEntity::class,
        PartyEntity::class,
        ProductEntity::class,
        ProductSubCategoryEntity::class,
        WorkStepEntity::class,
        HisabEntity::class,
        HisabLineItemEntity::class,
        PaymentEntity::class,
        MiscAdjustmentEntity::class,
        SharedSlipEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun factoryHisabDao(): FactoryHisabDao
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `company_profile` (
                    `id` INTEGER NOT NULL,
                    `companyName` TEXT NOT NULL,
                    `ownerName` TEXT NOT NULL,
                    `onboardingCompleted` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `misc_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `partyId` INTEGER NOT NULL,
                    `hisabId` INTEGER,
                    `description` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `isAddition` INTEGER NOT NULL,
                    `dateMillis` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`partyId`) REFERENCES `parties`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`hisabId`) REFERENCES `hisabs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_misc_adjustments_partyId` ON `misc_adjustments` (`partyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_misc_adjustments_hisabId` ON `misc_adjustments` (`hisabId`)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shared_slips` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `hisabId` INTEGER,
                    `partyId` INTEGER NOT NULL,
                    `partyName` TEXT NOT NULL,
                    `hisabDateMillis` INTEGER NOT NULL,
                    `sharedAtMillis` INTEGER NOT NULL,
                    `totalAmount` REAL NOT NULL,
                    `netBalanceSnapshot` REAL NOT NULL,
                    `imagePath` TEXT NOT NULL,
                    `shareFormat` TEXT NOT NULL,
                    FOREIGN KEY(`partyId`) REFERENCES `parties`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`hisabId`) REFERENCES `hisabs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_slips_partyId` ON `shared_slips` (`partyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_slips_hisabId` ON `shared_slips` (`hisabId`)")
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "factory_hisab.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            INSTANCE = instance
            instance
        }
    }

    fun getRepository(context: Context): FactoryHisabRepository {
        return FactoryHisabRepository(getDatabase(context).factoryHisabDao())
    }
}
