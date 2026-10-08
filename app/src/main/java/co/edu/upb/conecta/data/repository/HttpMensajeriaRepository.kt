package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.network.ConversacionDto
import co.edu.upb.conecta.data.network.ErrorApiDto
import co.edu.upb.conecta.data.network.EstudianteDto
import co.edu.upb.conecta.data.network.MensajeDto
import co.edu.upb.conecta.data.network.MensajeriaApiService
import co.edu.upb.conecta.data.network.NetworkModule
import co.edu.upb.conecta.data.network.NuevaConversacionDto
import co.edu.upb.conecta.data.network.NuevoMensajeDto
import co.edu.upb.conecta.domain.model.ConversacionChat
import co.edu.upb.conecta.domain.model.MensajeChat
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.domain.model.Usuario
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mensajería real contra `/messaging/...` del backend: las conversaciones
 * viven en MongoDB Atlas, así que lo que escribe un profesor en su celular
 * lo lee el estudiante en el suyo. Quién es el usuario lo decide el backend
 * a partir del token de sesión (no de los parámetros [Usuario] que recibe
 * este repositorio, que solo existen por compatibilidad con la interfaz).
 *
 * Los identificadores de usuario son el correo institucional, igual que en
 * el backend: [Usuario.id] del usuario logueado por [HttpAuthRepository] y
 * [MensajeChat.autorId] son correos, así la UI distingue "mis" mensajes.
 */
class HttpMensajeriaRepository(private val api: MensajeriaApiService) : MensajeriaRepository {

    override suspend fun obtenerConversacionesDe(usuario: Usuario): Result<List<ConversacionChat>> =
        llamar({ api.listarConversaciones() }) { cuerpo -> cuerpo.conversations.map { it.aConversacion() } }

    override suspend fun obtenerConversacion(id: String): Result<ConversacionChat?> =
        llamar({ api.obtenerConversacion(id) }, permitirNoEncontrado = true) { cuerpo -> cuerpo.conversation.aConversacion() }

    override suspend fun obtenerEstudiantesDisponibles(): Result<List<Usuario>> =
        llamar({ api.listarEstudiantes() }) { cuerpo -> cuerpo.students.map { it.aUsuario() } }

    override suspend fun crearConversacion(
        profesor: Usuario,
        estudianteId: String,
        asunto: String,
        mensajeInicial: String
    ): Result<ConversacionChat> =
        llamar({ api.crearConversacion(NuevaConversacionDto(estudianteId, asunto, mensajeInicial)) }) { cuerpo ->
            cuerpo.conversation.aConversacion()
        }

    override suspend fun enviarMensaje(conversacionId: String, autor: Usuario, contenido: String): Result<MensajeChat> =
        llamar({ api.enviarMensaje(conversacionId, NuevoMensajeDto(contenido)) }) { cuerpo -> cuerpo.message.aMensaje() }
}

/** Error de mensajería con un texto listo para mostrar en la UI. */
class ErrorMensajeria(mensaje: String) : Exception(mensaje)

/**
 * Ejecuta la petición y traduce el resultado: éxito → [convertir]; error
 * esperado del backend → [ErrorMensajeria] con su mensaje; sin red →
 * mensaje genérico. Con [permitirNoEncontrado], un 404 es éxito con `null`.
 */
private suspend fun <D : Any, T> llamar(
    peticion: suspend () -> Response<D>,
    permitirNoEncontrado: Boolean = false,
    convertir: (D) -> T
): Result<T> {
    val respuesta = try {
        peticion()
    } catch (error: IOException) {
        return Result.failure(ErrorMensajeria("No se pudo conectar con el servidor. Verifica tu conexión."))
    } catch (error: Exception) {
        return Result.failure(ErrorMensajeria("Ocurrió un error inesperado con la mensajería."))
    }

    val cuerpo = respuesta.body()
    if (respuesta.isSuccessful && cuerpo != null) return Result.success(convertir(cuerpo))
    if (permitirNoEncontrado && respuesta.code() == 404) {
        @Suppress("UNCHECKED_CAST")
        return Result.success(null as T)
    }
    if (respuesta.code() == 401) {
        return Result.failure(ErrorMensajeria("Tu sesión expiró. Cierra sesión y vuelve a ingresar."))
    }
    return Result.failure(ErrorMensajeria(mensajeDeError(respuesta)))
}

private fun mensajeDeError(respuesta: Response<*>): String {
    val texto = try {
        respuesta.errorBody()?.string()
    } catch (error: IOException) {
        null
    }
    val cuerpo = texto?.let {
        try {
            NetworkModule.moshi.adapter(ErrorApiDto::class.java).fromJson(it)
        } catch (error: Exception) {
            null
        }
    }
    return cuerpo?.message?.takeIf { it.isNotBlank() }
        ?: "El servidor respondió de forma inesperada (código ${respuesta.code()})."
}

private fun ConversacionDto.aConversacion(): ConversacionChat = ConversacionChat(
    id = id,
    profesorId = professor.email,
    profesorNombre = professor.name,
    estudianteId = student.email,
    estudianteNombre = student.name,
    asunto = subject,
    // El detalle trae el historial completo; la bandeja solo el último mensaje.
    mensajes = messages.ifEmpty { listOfNotNull(lastMessage) }.map { it.aMensaje() }
)

private fun MensajeDto.aMensaje(): MensajeChat = MensajeChat(
    id = id,
    autorId = authorEmail,
    autorNombre = authorName,
    esDeProfesor = fromProfessor,
    contenido = text,
    fecha = fechaLocal(sentAt)
)

private fun EstudianteDto.aUsuario(): Usuario = Usuario(
    id = email,
    nombre = name,
    correoInstitucional = email,
    programa = programaDesdeNombre(program),
    semestre = semester ?: 0,
    identidadVerificada = true,
    rol = RolUsuario.ESTUDIANTE
)

/** ISO-8601 en UTC (como lo serializa Express) → hora local del celular. */
private fun fechaLocal(iso: String): LocalDateTime =
    try {
        LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
    } catch (error: Exception) {
        LocalDateTime.now()
    }
