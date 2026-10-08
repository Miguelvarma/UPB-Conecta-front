package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.network.AuthApiService
import co.edu.upb.conecta.data.network.LoginRequestDto
import co.edu.upb.conecta.data.network.LoginResponseDto
import co.edu.upb.conecta.data.network.PerfilDto
import co.edu.upb.conecta.data.network.SessionStore
import co.edu.upb.conecta.domain.model.MockProgramas
import co.edu.upb.conecta.domain.model.Programa
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.domain.model.Usuario
import java.io.IOException

/**
 * Implementación real de [AuthRepository] contra `POST /auth/login` del
 * backend (`backend/src/contexts/identity/infrastructure/http/authRoutes.ts`,
 * que a su vez llama a `AuthenticateStudent`). Primer `Http*Repository` de
 * este front — el resto sigue en `Fake*` hasta que su contexto tenga
 * endpoint propio (ver `ARQUITECTURA-INTEGRACION.md`).
 *
 * Vacío importante que todavía no resuelve este repositorio: el backend NO
 * devuelve ningún rol en la respuesta de login (su `Role` solo distingue
 * `student`/`content-admin`, sin `PROFESOR`, y `AuthenticationResult` ni
 * siquiera expone ese campo). Por eso todo login por esta vía entra como
 * [RolUsuario.ESTUDIANTE] — el flujo profesor↔estudiante sigue probándose
 * solo con [FakeAuthRepository] hasta que el equipo de backend agregue el
 * rol al contrato.
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
            val cuerpo = respuesta.body()

            when {
                respuesta.isSuccessful && cuerpo?.ok == true && cuerpo.profile != null -> {
                    cuerpo.session?.let { SessionStore.guardar(it.accessToken.value, it.refreshToken.value) }
                    val usuario = cuerpo.profile.aUsuario()
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
private fun PerfilDto.aUsuario(): Usuario {
    val programa = MockProgramas.todos.firstOrNull { it.nombre.equals(program, ignoreCase = true) }
        ?: Programa(id = program.lowercase().replace(" ", "-"), nombre = program, facultad = "")

    return Usuario(
        id = studentId ?: email,
        nombre = name,
        correoInstitucional = email,
        programa = programa,
        semestre = semester,
        identidadVerificada = true,
        // Ver nota de clase: el backend todavía no distingue profesor/estudiante.
        rol = RolUsuario.ESTUDIANTE
    )
}
