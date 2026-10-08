package co.edu.upb.conecta.data.network

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Contrato Retrofit de `POST /auth/login`. Devuelve [Response] (no la
 * lanza como excepción ante 4xx/5xx) porque el backend responde un cuerpo
 * JSON `{ ok:false, error, message }` con información útil también en los
 * fallos esperados (credenciales inválidas, rate limiting, proveedor no
 * disponible) — [HttpAuthRepository][co.edu.upb.conecta.data.repository.HttpAuthRepository]
 * necesita leer ese cuerpo, no solo el código de estado.
 */
interface AuthApiService {
    @POST("auth/login")
    suspend fun iniciarSesion(@Body body: LoginRequestDto): Response<LoginResponseDto>

    /**
     * Renueva la sesión con el refresh token (rotación, HU-45). Es `Call`
     * síncrono, no `suspend`, porque lo invoca el `Authenticator` de OkHttp
     * desde su propio hilo cuando una petición recibe 401 (ver [NetworkModule]).
     */
    @POST("auth/refresh")
    fun renovarSesion(@Body body: RefreshRequestDto): Call<RefreshResponseDto>

    /** Cierra la sesión en el servidor: revoca la cadena de refresh tokens. */
    @POST("auth/logout")
    suspend fun cerrarSesion(@Body body: RefreshRequestDto): Response<ErrorApiDto>
}
