package com.avitoohband.nutrun.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        WeightEntryEntity::class,
        FoodLogEntity::class,
        FoodTemplateEntity::class,
        WaterLogEntity::class,
        HydrationPlanEntity::class,
        WalkSessionEntity::class,
        WalkPointEntity::class,
        TrainingStateEntity::class,
        TrainingReminderSettingsEntity::class,
        ReminderDeliveryEntity::class,
        SyncOperationEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class NutRunDatabase : RoomDatabase() {
    abstract fun dao(): NutRunDao

    companion object {
        const val LEGACY_USER_ID = "legacy"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf(
                    "user_profile",
                    "weight_entries",
                    "food_logs",
                    "water_logs",
                    "hydration_plan",
                    "walk_sessions",
                    "walk_points"
                ).forEach { table ->
                    database.execSQL(
                        "ALTER TABLE $table ADD COLUMN userId TEXT NOT NULL DEFAULT '$LEGACY_USER_ID'"
                    )
                }
                database.execSQL(
                    "ALTER TABLE walk_sessions ADD COLUMN stepOffset INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_user_profile_userId ON user_profile(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_weight_entries_userId ON weight_entries(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_food_logs_userId ON food_logs(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_water_logs_userId ON water_logs(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_hydration_plan_userId ON hydration_plan(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_walk_sessions_userId ON walk_sessions(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_walk_points_userId ON walk_points(userId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_state (
                        userId TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        pendingSync INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_operations (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadJson TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        attempts INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_userId ON sync_operations(userId)")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_operations_userId_entityType_entityId " +
                        "ON sync_operations(userId, entityType, entityId)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE hydration_plan SET intervalMinutes = 60")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_reminder_settings (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        previousDayMinute INTEGER NOT NULL,
                        sameDayMinute INTEGER NOT NULL,
                        timezoneId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_training_reminder_settings_userId " +
                        "ON training_reminder_settings(userId)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_delivery (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        reminderType TEXT NOT NULL,
                        trainingDate TEXT NOT NULL,
                        deliveredAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminder_delivery_userId ON reminder_delivery(userId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_reminder_delivery_userId_reminderType_trainingDate " +
                        "ON reminder_delivery(userId, reminderType, trainingDate)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        lastUsedAtMillis INTEGER NOT NULL,
                        useCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_templates_userId ON food_templates(userId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_templates_kind ON food_templates(kind)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_templates_lastUsedAtMillis " +
                        "ON food_templates(lastUsedAtMillis)"
                )
            }
        }

        @Volatile private var instance: NutRunDatabase? = null

        fun getInstance(context: Context): NutRunDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NutRunDatabase::class.java,
                    "nutrun.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
