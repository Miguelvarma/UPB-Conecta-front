package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.network.AuthApiService
import co.edu.upb.conecta.data.network.LoginRequestDto
import co.edu.upb.conecta.data.network.LoginResponseDto
import co.edu.upb.conecta.data.network.NetworkModule
import co.edu.upb.conecta.data.network.PerfilDto
import co.edu.upb.conecta.data.network.RefreshRequestDto
import co.edu.upb.conecta.data.network.SessionStore
import co.edu.upb.conecta.domain.model.MockProgramas
import co.edu.upb.conecta.domain.model.Programa
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.domain.model.Usuario
import retrofit2.Response
import java.io.IOException

/**
 * Implementación real de [AuthRepository] contra `POST /auth/login` del
 * backend (`backend/src/contexts/identity/infrastructure/http/authRoutes.ts`,
 * que a su vez llama a `AuthenticateStudent`). Primer `Http*Repository` de
 * este front — el resto sigue en `Fake*` hasta que su contexto tenga
 * endpoint propio (ver `ARQUITECTURA-INTEGRACION.md`).
 *
 * El rol viaja en `role` de la respuesta de login ("student" |
 * "professor" | "content-admin") y se traduce con [aRolUsuario]: así el
 * flujo profesor↔estudiante se prueba contra el backend real con las
 * cuentas de prueba de profesor (p. ej. `profesor@upb.edu.co`).
 */
class HttpAuthRepository(
    private val api: AuthApiService,
    private val usuarioRepository: UsuarioRepository
) : AuthRepository {

    override suspend fun iniciarSesion(correoInstitucional: String, contrasena: String): ResultadoLogin {
        val correo = correoInstitucional.trim()
        if (correo.isBlank() || contrasena.isBlank()) {
            return ResultadoLogin.Error("Ingresa tu correo institucional y tu contraseña.")
        }

        return try {
            val respuesta = api.iniciarSesion(LoginRequestDto(username = correo, password = contrasena))
            // En 4xx/5xx Retrofit deja el JSON `{ ok:false, error, message }`
            // en errorBody(), no en body(): sin leerlo, una contraseña
            // incorrecta (401) se mostraba como "código 401".
            val cuerpo = respuesta.body() ?: leerCuerpoDeError(respuesta)

            when {
                respuesta.isSuccessful && cuerpo?.ok == true && cuerpo.profile != null -> {
                    cuerpo.session?.let { SessionStore.guardar(it.accessToken.value, it.refreshToken.value) }
                    val usuario = cuerpo.profile.aUsuario(aRolUsuario(cuerpo.role))
                    usuarioRepository.establecerUsuarioActual(usuario)
                    ResultadoLogin.Exito(usuario)
                }

                cuerpo != null -> ResultadoLogin.Error(mensajeDeError(cuerpo))

                else -> ResultadoLogin.Error(
                    "El servidor respondió de forma inesperada (código ${respuesta.code()}). Intenta de nuevo."
                )
            }
        } catch (error: IOException) {
            // Sin conexión, backend caído, o el host de NetworkModule.BASE_URL
            // no es alcanzable (revisar que `npm run start:http` esté corriendo
            // y que el dispositivo pueda alcanzarlo).
            ResultadoLogin.Error("No se pudo conectar con el servidor. Verifica tu conexión e inténtalo de nuevo.")
        } catch (error: Exception) {
            ResultadoLogin.Error("Ocurrió un error inesperado al iniciar sesión.")
        }
    }

    /**
     * Revoca la sesión en el backend (`POST /auth/logout`) y olvida los
     * tokens. Si no hay red, igual se olvidan localmente: el usuario sale.
     */
    override suspend fun cerrarSesion() {
        val refreshToken = SessionStore.refreshToken
        SessionStore.limpiar()
        if (refreshToken != null) {
            try {
                api.cerrarSesion(RefreshRequestDto(refreshToken))
            } catch (error: Exception) {
                // Sin red: la sesión expira sola en el servidor.
            }
        }
    }

    private fun leerCuerpoDeError(respuesta: Response<LoginResponseDto>): LoginResponseDto? =
        try {
            respuesta.errorBody()?.string()?.let {
                NetworkModule.moshi.adapter(LoginResponseDto::class.java).fromJson(it)
            }
        } catch (error: Exception) {
            null
        }

    private fun mensajeDeError(cuerpo: LoginResponseDto): String =
        when (cuerpo.error) {
            "invalid-credentials" -> "Correo o contraseña incorrectos."
            "rate-limited" -> "Demasiados intentos. Espera un momento y vuelve a intentar."
            "provider-unavailable" -> "El directorio institucional no está disponible en este momento."
            else -> cuerpo.message.ifBlank { "No se pudo iniciar sesión." }
        }
}

/**
 * Traduce el perfil del directorio institucional (nombre plano de programa,
 * como lo entrega el backend) al [Programa] tipado de este front. Si el
 * nombre no coincide con el catálogo local de [MockProgramas] (por ahora
 * ilustrativo, no el catálogo real del backend), se construye un
 * [Programa] "de paso" con ese mismo nombre en vez de bloquear el login:
 * un programa no reconocido en la UI es preferible a no poder entrar.
 */
private fun PerfilDto.aUsuario(rol: RolUsuario): Usuario {
    return Usuario(
        // El correo, no el código estudiantil: es el mismo identificador que
        // usa el backend (sujeto de la sesión, autor de cada mensaje), así la
        // mensajería distingue los mensajes propios (ver HttpMensajeriaRepository).
        id = email,
        nombre = name,
        correoInstitucional = email,
        programa = programaDesdeNombre(program),
        // Un profesor no tiene semestre; 0 es la misma convención que ya usa
        // `MockData.usuarioProfesor`, y la UI no lo muestra para ese rol.
        semestre = semester ?: 0,
        identidadVerificada = true,
        rol = rol
    )
}

/** Programa del catálogo local por nombre, o uno "de paso" con ese nombre (ver nota arriba). */
internal fun programaDesdeNombre(nombre: String): Programa =
    MockProgramas.todos.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
        ?: Programa(id = nombre.lowercase().replace(" ", "-"), nombre = nombre, facultad = "")

/**
 * Traduce el rol del backend al [RolUsuario] de este front. El front solo
 * distingue estudiante y profesor: "content-admin" (o un rol que el backend
 * agregue después) entra como [RolUsuario.ESTUDIANTE], que es el que menos
 * permisos tiene en la app — nunca se asciende a profesor por omisión.
 */
private fun aRolUsuario(role: String?): RolUsuario =
    when (role) {
        "professor" -> RolUsuario.PROFESOR
        else -> RolUsuario.ESTUDIANTE
    }
