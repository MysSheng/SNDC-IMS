package icdf.sndc.ims

import icdf.sndc.ims.ui.assets.AssetFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssetFormatTest {

    @Test
    fun purchaseDateMustBe2xxxSlashFormat() {
        assertNull(AssetFormat.purchaseDateError("2024/01/15"))
        assertNotNull(AssetFormat.purchaseDateError("2024-01-15"))
        assertNotNull(AssetFormat.purchaseDateError("2024/02/30"))
        assertNotNull(AssetFormat.purchaseDateError("1999/12/31"))
        assertNotNull(AssetFormat.purchaseDateError(""))
    }

    @Test
    fun convertsBetweenDisplayAndIso() {
        assertEquals("2024-01-15", AssetFormat.toIso("2024/01/15"))
        assertEquals("2024/01/15", AssetFormat.isoToDisplay("2024-01-15"))
        assertEquals("", AssetFormat.isoToDisplay(null))
    }

    @Test
    fun usefulLifeAllowsOneDecimal() {
        assertNull(AssetFormat.usefulLifeError("5"))
        assertNull(AssetFormat.usefulLifeError("2.5"))
        assertNotNull(AssetFormat.usefulLifeError("2.55"))
        assertNotNull(AssetFormat.usefulLifeError("0"))
        assertNotNull(AssetFormat.usefulLifeError("abc"))
    }

    @Test
    fun calendarMillisRoundTrip() {
        val d = java.time.LocalDate.of(2024, 1, 15)
        assertEquals(d, AssetFormat.fromEpochMillis(AssetFormat.toEpochMillis(d)))
        assertEquals("2024/01/15", AssetFormat.toDisplay(d))
    }

    @Test
    fun formatsYears() {
        assertEquals("5", AssetFormat.years(5.0))
        assertEquals("2.5", AssetFormat.years(2.5))
    }
}
