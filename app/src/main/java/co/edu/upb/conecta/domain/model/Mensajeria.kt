package co.edu.upb.conecta.domain.model

import java.time.LocalDateTime

/**
 * Un mensaje dentro de una [ConversacionChat]. [autorId] enlaza con
 * [Usuario.id] (del profesor o del estudiante, según [esDeProfesor]).
 */
data class MensajeChat(
    val id: String,
    val autorId: String,
    val autorNombre: String,
    val esDeProfesor: Boolean,
    val contenido: String,
    val fecha: LocalDateTime
)

/**
 * Conversación privada 1 a 1 entre un profesor y un estudiante. Siempre la
 * crea el profesor (RF: "el profesor puede crear conversaciones con
 * estudiantes"); el estudiante solo puede responder dentro de las que ya
 * existen. La visibilidad es por participante — [profesorId] +
 * [estudianteId] — nunca por rol ni por programa, así que ni otros
 * estudiantes ni otros profesores pueden verla (RF: "solo la ve el
 * estudiante al que fue enviada").
 */
data class ConversacionChat(
    val id: String,
    val profesorId: String,
    val profesorNombre: String,
    val estudianteId: String,
    val estudianteNombre: String,
    val asunto: String,
    val mensajes: List<MensajeChat>
) {
    val ultimoMensaje: MensajeChat?
        get() = mensajes.maxByOrNull { it.fecha }
}
