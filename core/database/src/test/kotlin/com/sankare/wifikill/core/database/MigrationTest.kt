package com.sankare.wifikill.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WiFiKillDatabase::class.java,
    )

    @Test
    fun `migrate 1 to 2 adds port and protocol with defaults`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO networks (id, networkType, isCurrent, firstSeenAt, lastSeenAt) " +
                    "VALUES ('net-1', 'WIFI', 1, 1, 1)",
            )
            execSQL(
                "INSERT INTO routers " +
                    "(id, networkId, name, ipAddress, adapterType, connectionStatus, createdAt) " +
                    "VALUES ('router-1', 'net-1', 'Home', '192.168.1.1', 'GENERIC', 'UNKNOWN', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *WiFiKillDatabase.MIGRATIONS).apply {
            query("SELECT port, protocol FROM routers WHERE id = 'router-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(443, cursor.getInt(0))
                assertEquals("HTTPS", cursor.getString(1))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
