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

    companion object {
        private const val DATABASE_NAME = "migration-test"
    }
}
