package icdf.sndc.ims.data.remote

import kotlinx.serialization.Serializable

/** 與後端 docs/API.md 對應的 DTO；PIN 一律為字串以保留前導零。 */

@Serializable
data class RegisterRequest(
    val username: String,
    val pin: String,
    val pinConfirm: String,
    val registrationCode: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val pin: String,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val createdAt: String? = null,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
    val user: UserDto,
)

@Serializable
data class ApiErrorDto(
    val status: Int = 0,
    val code: String = "",
    val message: String = "",
    val details: List<FieldErrorDto> = emptyList(),
)

@Serializable
data class FieldErrorDto(
    val field: String = "",
    val message: String = "",
)
