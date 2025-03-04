package com.brentpanther.bitcoinwidget.db

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brentpanther.bitcoinwidget.WidgetApplication
import java.io.File

@Database(version = 5, entities = [Widget::class, Configuration::class], exportSchema = true)
abstract class WidgetDatabase : RoomDatabase() {

    abstract fun widgetDao(): WidgetDao

    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Widget ADD COLUMN widgetType TEXT NOT NULL DEFAULT 'PRICE'")
                database.execSQL("ALTER TABLE Widget ADD COLUMN showAmountLabel INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE Widget ADD COLUMN amountHeld REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Widget ADD COLUMN useInverse INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // not all devices support rename column yet
                database.execSQL("""
                    CREATE TABLE `Widget_New` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `widgetId` INTEGER NOT NULL, `widgetType` TEXT NOT NULL, `exchange` TEXT NOT NULL,
                    `coin` TEXT NOT NULL, `currency` TEXT NOT NULL, `coinCustomId` TEXT, `coinCustomName` TEXT, `currencyCustomName` TEXT, `showExchangeLabel` INTEGER NOT NULL, 
                    `showCoinLabel` INTEGER NOT NULL, `showIcon` INTEGER NOT NULL, `numDecimals` INTEGER NOT NULL, `currencySymbol` TEXT, `theme` TEXT NOT NULL, 
                    `nightMode` TEXT NOT NULL, `coinUnit` TEXT, `currencyUnit` TEXT, `customIcon` TEXT, `portraitTextSize` INTEGER, `landscapeTextSize` INTEGER, `lastValue` TEXT, 
                    `amountHeld` REAL, `showAmountLabel` INTEGER NOT NULL, `useInverse` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `state` TEXT NOT NULL)
                """)
                database.execSQL("""
                    INSERT INTO Widget_New (id, widgetId, widgetType, exchange, coin, currency, coinCustomId, coinCustomName, currencyCustomName,
                    showExchangeLabel, showCoinLabel, showIcon, numDecimals, currencySymbol, theme, nightMode, coinUnit, currencyUnit,
                    customIcon, portraitTextSize, landscapeTextSize, lastValue, amountHeld, showAmountLabel, useInverse, lastUpdated, state)
                    SELECT id, widgetId, widgetType, exchange, coin, currency, coinCustomId, coinCustomName, currencyCustomName,
                    showExchangeLabel, showCoinLabel, showIcon, -1, currencySymbol, theme, nightMode, coinUnit, currencyUnit,
                    customIcon, portraitTextSize, landscapeTextSize, lastValue, amountHeld, showAmountLabel, useInverse, lastUpdated, state 
                    FROM Widget
                """)
                database.execSQL("DROP TABLE Widget")
                database.execSQL("ALTER TABLE Widget_New RENAME TO Widget")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Widget_widgetId` ON `Widget` (`widgetId`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT coinCustomId, customIcon FROM Widget ORDER BY id")
                while (cursor.moveToNext()) {
                    val coinCustomId = cursor.getString(0)
                    val customIcon = cursor.getString(1)
                    val file = File(WidgetApplication.instance.filesDir, "icons/$customIcon")
                    if (file.exists()) {
                        val newFile = File(WidgetApplication.instance.filesDir, "icons/$coinCustomId")
                        file.renameTo(newFile)
                    }
                }
            }
        }

        @Volatile
        private var INSTANCE: WidgetDatabase? = null

        fun getInstance(context: Context): WidgetDatabase {
            val callback = object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    DatabaseInitializer.create(
                        db,
                        PreferenceManager.getDefaultSharedPreferences(context),
                        context.getSharedPreferences("bitcoinwidget", Context.MODE_PRIVATE)
                    )
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    DataMigration.migrate(db)
                    super.onOpen(db)
                }
            }
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, WidgetDatabase::class.java, "widgetdb"
                ).addCallback(callback)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }


    }
}

