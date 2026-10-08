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
            val cuerpo = respuesta.body()

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
    val programa = MockProgramas.todos.firstOrNull { it.nombre.equals(program, ignoreCase = true) }
        ?: Programa(id = program.lowercase().replace(" ", "-"), nombre = program, facultad = "")

    return Usuario(
        id = studentId ?: email,
        nombre = name,
        correoInstitucional = email,
        programa = programa,
        // Un profesor no tiene semestre; 0 es la misma convención que ya usa
        // `MockData.usuarioProfesor`, y la UI no lo muestra para ese rol.
        semestre = semester ?: 0,
        identidadVerificada = true,
        rol = rol
    )
}

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
