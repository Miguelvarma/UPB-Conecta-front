package co.edu.upb.conecta.data.network

/**
 * DTOs de `POST /auth/login`, calcados del contrato JSON que expone
 * `backend/src/contexts/identity/infrastructure/http/authRoutes.ts`
 * (`AuthenticationResult` serializado tal cual). Ver
 * `ARQUITECTURA-INTEGRACION.md` para el resto del contrato REST.
 */
data class LoginRequestDto(
    val username: String,
    val password: String,
    /** Alimenta el rate limiting por origen del backend (HU-43). */
    val origin: String = "android-app"
)

/**
 * La respuesta es una unión discriminada por `ok` (igual que
 * `AuthenticationResult` en el backend): con `ok = true` llegan `profile`,
 * `role`, `session` y `consent`; con `ok = false` llega `error` (uno de
 * "invalid-credentials" | "provider-unavailable" | "rate-limited") y
 * `message` explica cualquiera de los dos casos. Todos los campos que no
 * aplican a la rama recibida quedan en `null`.
 */
data class LoginResponseDto(
    val ok: Boolean,
    val message: String,
    val profile: PerfilDto? = null,
    /**
     * Rol vigente de la cuenta: "student" | "professor" | "content-admin".
     * Opcional para tolerar un backend anterior que no lo enviaba.
     */
    val role: String? = null,
    val session: SesionDto? = null,
    val consent: ConsentimientoDto? = null,
    val error: String? = null
)

data class PerfilDto(
    val name: String,
    val email: String,
    val program: String,
    /** Solo estudiantes: el backend no lo envía para un profesor. */
    val semester: Int? = null,
    val studentId: String? = null
)

data class TokenDto(
    val value: String,
    /** ISO-8601, tal como lo serializa `res.json()` de Express a partir de un `Date`. */
    val expiresAt: String
)

data class SesionDto(
    val sessionId: String,
    val accessToken: TokenDto,
    val refreshToken: TokenDto
)

data class PendingConsentDto(
    val documentType: String,
    val explanation: String
)

data class ConsentimientoDto(
    val mustConsent: Boolean,
    val pending: List<PendingConsentDto> = emptyList()
)
