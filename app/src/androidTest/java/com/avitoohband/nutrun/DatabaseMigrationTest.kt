package com.avitoohband.nutrun

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avitoohband.nutrun.data.NutRunDatabase
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NutRunDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrationOneToTwoPreservesRowsAsUnclaimedLegacyData() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            insert(
                "user_profile",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "current")
                    put("email", "legacy@example.com")
                    put("birthDate", "1990-01-01")
                    put("biologicalSex", "MALE")
                    put("heightCm", 180.0)
                    put("weightKg", 80.0)
                    put("activityLevel", "MODERATE")
                    put("goal", "MAINTAIN")
                    put("unitSystem", "METRIC")
                    put("calorieTarget", 2500)
                    put("updatedAtMillis", 1L)
                }
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            NutRunDatabase.MIGRATION_1_2
        )
        migrated.query("SELECT userId FROM user_profile").use { cursor ->
            cursor.moveToFirst()
            assertEquals(NutRunDatabase.LEGACY_USER_ID, cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM sync_operations").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrationTwoToThreePreservesDataAndAddsReminderTables() {
        helper.createDatabase(DATABASE_NAME_V3, 2).apply {
            insert(
                "hydration_plan",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "hydration:user")
                    put("userId", "user")
                    put("goalMl", 2_500)
                    put("servingMl", 300)
                    put("wakingStartMinute", 480)
                    put("wakingEndMinute", 1320)
                    put("intervalMinutes", 120)
                    put("remindersEnabled", 1)
                }
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME_V3,
            3,
            true,
            NutRunDatabase.MIGRATION_2_3
        )
        migrated.query("SELECT goalMl, servingMl, intervalMinutes FROM hydration_plan").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2_500, cursor.getInt(0))
            assertEquals(300, cursor.getInt(1))
            assertEquals(60, cursor.getInt(2))
        }
        migrated.query("SELECT COUNT(*) FROM training_reminder_settings").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrationThreeToFourAddsFoodTemplatesWithoutChangingExistingFood() {
        helper.createDatabase(DATABASE_NAME_V4, 3).apply {
            insert(
                "food_logs",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "food-1")
                    put("userId", "user")
                    put("localDate", "2026-07-24")
                    put("mealType", "BREAKFAST")
                    putNull("catalogId")
                    put("name", "Oats")
                    putNull("brand")
                    put("servingGrams", 80.0)
                    put("calories", 300)
                    put("proteinGrams", 10.0)
                    put("carbohydrateGrams", 50.0)
                    put("fatGrams", 6.0)
                    put("updatedAtMillis", 1L)
                    put("pendingSync", 1)
                }
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME_V4,
            4,
            true,
            NutRunDatabase.MIGRATION_3_4
        )
        migrated.query("SELECT name, calories FROM food_logs WHERE id = 'food-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Oats", cursor.getString(0))
            assertEquals(300, cursor.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM food_templates").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrationFourToFivePreservesTrainingStateAndAddsSupplementReminderSettings() {
        val payload = "{\"supplements\":[]}"
        helper.createDatabase(DATABASE_NAME_V5, 4).apply {
            insert(
                "training_state",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("userId", "user")
                    put("payloadJson", payload)
                    put("updatedAtMillis", 1L)
                    put("pendingSync", 0)
                }
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME_V5,
            5,
            true,
            NutRunDatabase.MIGRATION_4_5
        )
        assertQueryValue(
            migrated,
            "SELECT payloadJson FROM training_state WHERE userId = 'user'",
            payload
        )
        assertQueryValue(migrated, "SELECT COUNT(*) FROM supplement_reminder_settings", 0)
        migrated.close()
    }

    private fun assertQueryValue(database: SupportSQLiteDatabase, query: String, expected: Any) {
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            when (expected) {
                is String -> assertEquals(expected, cursor.getString(0))
                is Int -> assertEquals(expected, cursor.getInt(0))
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "migration-test"
        private const val DATABASE_NAME_V3 = "migration-test-v3"
        private const val DATABASE_NAME_V4 = "migration-test-v4"
        private const val DATABASE_NAME_V5 = "migration-test-v5"
    }
}
