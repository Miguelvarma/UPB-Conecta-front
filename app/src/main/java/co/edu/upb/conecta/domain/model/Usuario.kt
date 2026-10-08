package co.edu.upb.conecta.domain.model

/**
 * Rol de la cuenta institucional. Define qué puede hacer en la mensajería
 * privada (ver [MensajeriaRepository][co.edu.upb.conecta.data.repository.MensajeriaRepository]):
 * un [PROFESOR] puede iniciar conversaciones con estudiantes; un
 * [ESTUDIANTE] solo puede responder dentro de las que le enviaron a él.
 */
enum class RolUsuario { ESTUDIANTE, PROFESOR }

data class Usuario(
    val id: String,
    val nombre: String,
    val correoInstitucional: String,
    val programa: Programa,
    val semestre: Int,
    val identidadVerificada: Boolean,
    val rol: RolUsuario = RolUsuario.ESTUDIANTE
)
