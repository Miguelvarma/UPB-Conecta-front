package co.edu.upb.conecta.data.network

/**
 * DTOs de la mensajería profesor↔estudiante del backend
 * (`src/contexts/messaging/infrastructure/http/messagingRoutes.ts`). Todas
 * las rutas exigen `Authorization: Bearer <accessToken>`, que agrega
 * [NetworkModule] automáticamente. Las fechas llegan como ISO-8601 (UTC).
 */
data class ParticipanteDto(
    val email: String,
    val name: String
)

data class MensajeDto(
    val id: String,
    val authorEmail: String,
    val authorName: String,
    val fromProfessor: Boolean,
    val text: String,
    val sentAt: String
)

/**
 * Misma forma para el detalle y para la bandeja: el detalle trae
 * [messages] completo; la bandeja trae solo [lastMessage].
 */
data class ConversacionDto(
    val id: String,
    val professor: ParticipanteDto,
    val student: ParticipanteDto,
    val subject: String,
    val messages: List<MensajeDto> = emptyList(),
    val lastMessage: MensajeDto? = null,
    val createdAt: String,
    val updatedAt: String
)

data class ListaConversacionesDto(
    val ok: Boolean,
    val conversations: List<ConversacionDto> = emptyList()
)

data class ConversacionRespuestaDto(
    val ok: Boolean,
    val conversation: ConversacionDto
)

data class MensajeRespuestaDto(
    val ok: Boolean,
    val message: MensajeDto
)

data class EstudianteDto(
    val email: String,
    val name: String,
    val program: String,
    val semester: Int? = null
)

data class ListaEstudiantesDto(
    val ok: Boolean,
    val students: List<EstudianteDto> = emptyList()
)

data class NuevaConversacionDto(
    val studentEmail: String,
    val subject: String,
    val text: String
)

data class NuevoMensajeDto(
    val text: String
)

/** Cuerpo de cualquier respuesta de error del backend: `{ ok: false, error, message }`. */
data class ErrorApiDto(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
