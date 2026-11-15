package icdf.sndc.ims

import icdf.sndc.ims.data.remote.AppError
import icdf.sndc.ims.data.remote.toAppError
import icdf.sndc.ims.ui.common.AuthValidation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun pinKeepsLeadingZeroAndRejectsBadInput() {
        assertNull(AuthValidation.pin("0037"))
        assertNotNull(AuthValidation.pin("37"))
        assertNotNull(AuthValidation.pin("12345"))
        assertNotNull(AuthValidation.pin("12a4"))
    }

    @Test
    fun sanitizePinKeepsOnlyFourDigits() {
        assertEquals("0037", AuthValidation.sanitizePin("0a0-37 9"))
    }

    @Test
    fun pinConfirmMustMatch() {
        assertNull(AuthValidation.pinConfirm("1234", "1234"))
        assertNotNull(AuthValidation.pinConfirm("1234", "1235"))
    }

    @Test
    fun mapsBackendErrors() {
        val json = Json { ignoreUnknownKeys = true }
        val auth = toAppError(json, 401, """{"status":401,"code":"INVALID_CREDENTIALS","message":"x","details":[]}""")
        assertTrue(auth is AppError.Auth && auth.code == "INVALID_CREDENTIALS")

        val validation = toAppError(json, 400,
            """{"status":400,"code":"VALIDATION_ERROR","message":"x","details":[{"field":"pinConfirm","message":"m"}],"timestamp":"t"}""")
        assertTrue(validation is AppError.Validation && validation.fieldErrors["pinConfirm"] == "m")

        assertTrue(toAppError(json, 500, null) is AppError.Server)
    }
}
