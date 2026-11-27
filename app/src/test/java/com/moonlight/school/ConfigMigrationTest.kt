package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class ConfigMigrationTest {
    @Test fun legacyAdoption() { assertEquals(1, ConfigMigration.next(0, 1)) }
    @Test fun repeatedStartup() { assertEquals(1, ConfigMigration.next(1, 1)) }
    @Test fun newerSchemaNotReset() { assertNull(ConfigMigration.next(2, 1)) }
    @Test fun unknownMigrationNotSkipped() { assertNull(ConfigMigration.next(0, 3)) }
    @Test fun stableStoreNames() {
        assertEquals("moonlight_local", MoonlightConfig.PERSONAL_STORE)
        assertEquals("moonlight_snapshot", MoonlightConfig.SNAPSHOT_STORE)
    }
}
