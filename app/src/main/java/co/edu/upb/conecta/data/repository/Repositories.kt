package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.mock.MockData
import co.edu.upb.conecta.data.network.NetworkModule
import co.edu.upb.conecta.domain.model.*
import java.time.LocalDateTime

/**
 * Repositorios de este front preliminar.
 *
 * Cada interfaz representa el "puerto" que en el futuro implementará un
 * adaptador real contra el backend (ver `src/contexts/` en el repositorio
 * de backend, que ya sigue arquitectura hexagonal). Por ahora solo existe
 * la implementación `Fake*` respaldada por [MockData], para que sustituirla
 * más adelante sea un cambio de una línea en [AppContainer] — el mismo
 * principio que ya aplica el backend con sus adaptadores de `ingestion`.
 */
interface ConvocatoriasRepository {
    fun obtenerTodas(): List<Convocatoria>
    fun obtenerPorId(id: String): Convocatoria?
}

class FakeConvocatoriasRepository : ConvocatoriasRepository {
    override fun obtenerTodas(): List<Convocatoria> =
        MockData.convocatorias.sortedBy { it.fechaCierre }

    override fun obtenerPorId(id: String): Convocatoria? =
        MockData.convocatorias.firstOrNull { it.id == id }
}

interface PracticasRepository {
    fun obtenerTodas(): List<Practica>
    fun obtenerPorId(id: String): Practica?
}

class FakePracticasRepository : PracticasRepository {
    override fun obtenerTodas(): List<Practica> =
        MockData.practicas.sortedBy { it.fechaCierre }

    override fun obtenerPorId(id: String): Practica? =
        MockData.practicas.firstOrNull { it.id == id }
}

interface NoticiasRepository {
    fun obtenerTodas(): List<Noticia>
}

class FakeNoticiasRepository : NoticiasRepository {
    override fun obtenerTodas(): List<Noticia> =
        MockData.noticias.sortedByDescending { it.fechaPublicacion }
}

interface ForoRepository {
    fun obtenerPosts(): List<PostForo>
    fun obtenerPostPorId(id: String): PostForo?

    /**
     * Agrega una respuesta a la publicación [postId] y la devuelve, o `null`
     * si la publicación no existe. La moderación automática solo corre
     * sobre publicaciones nuevas en este front preliminar — un comentario
     * se agrega siempre como aprobado, igual que si lo escribiera un
     * usuario con identidad verificada.
     */
    fun agregarComentario(
        postId: String,
        contenido: String,
        autorNombre: String,
        autorVerificado: Boolean
    ): ComentarioForo?
}

class FakeForoRepository : ForoRepository {
    // Estado mutable en memoria: vive mientras dure la app (este repositorio
    // es un único objeto en AppContainer). Un comentario agregado se
    // mantiene al navegar entre pantallas, pero se pierde si se reinicia el
    // proceso — coherente con que todavía no hay backend que lo persista.
    private var posts: List<PostForo> = MockData.postsForo

    override fun obtenerPosts(): List<PostForo> =
        posts.sortedByDescending { it.fecha }

    override fun obtenerPostPorId(id: String): PostForo? =
        posts.firstOrNull { it.id == id }

    override fun agregarComentario(
        postId: String,
        contenido: String,
        autorNombre: String,
        autorVerificado: Boolean
    ): ComentarioForo? {
        if (posts.none { it.id == postId }) return null

        val nuevoComentario = ComentarioForo(
            id = "com-${System.currentTimeMillis()}",
            autorNombre = autorNombre,
            autorVerificado = autorVerificado,
            contenido = contenido,
            fecha = LocalDateTime.now()
        )
        posts = posts.map { post ->
            if (post.id == postId) post.copy(comentarios = post.comentarios + nuevoComentario) else post
        }
        return nuevoComentario
    }
}

interface NotificacionesRepository {
    fun obtenerTodas(): List<Notificacion>
}

class FakeNotificacionesRepository : NotificacionesRepository {
    override fun obtenerTodas(): List<Notificacion> =
        MockData.notificaciones.sortedByDescending { it.fecha }
}

interface MapaRepository {
    fun obtenerPuntosDeInteres(): List<PuntoInteres>
}

class FakeMapaRepository : MapaRepository {
    override fun obtenerPuntosDeInteres(): List<PuntoInteres> = MockData.puntosInteres
}

interface ChatbotRepository {
    fun obtenerPreguntasFrecuentes(): List<PreguntaFrecuente>
    fun responder(pregunta: String): String
}

class FakeChatbotRepository : ChatbotRepository {
    override fun obtenerPreguntasFrecuentes(): List<PreguntaFrecuente> = MockData.preguntasFrecuentes

    override fun responder(pregunta: String): String {
        val coincidencia = MockData.preguntasFrecuentes.firstOrNull {
            it.pregunta.contains(pregunta, ignoreCase = true) ||
                pregunta.split(" ").any { palabra ->
                    palabra.length > 4 && it.pregunta.contains(palabra, ignoreCase = true)
                }
        }
        return coincidencia?.respuesta
            ?: "No encontré una respuesta exacta en las preguntas frecuentes. " +
                "Puedes revisar la lista de abajo o escribir a tu coordinación de programa. " +
                "(Respuesta simulada — el chatbot real se conectará a una base de conocimiento del backend)."
    }
}

interface UsuarioRepository {
    fun obtenerUsuarioActual(): Usuario

    /** Cambia qué [Usuario] está "logueado" — lo llama [AuthRepository] al iniciar sesión. */
    fun establecerUsuarioActual(usuario: Usuario)
}

class FakeUsuarioRepository : UsuarioRepository {
    private var usuarioActual: Usuario = MockData.usuarioActual

    override fun obtenerUsuarioActual(): Usuario = usuarioActual

    override fun establecerUsuarioActual(usuario: Usuario) {
        usuarioActual = usuario
    }
}

/** Resultado de un intento de inicio de sesión. */
sealed class ResultadoLogin {
    data class Exito(val usuario: Usuario) : ResultadoLogin()
    data class Error(val mensaje: String) : ResultadoLogin()
}

/**
 * Puerto de autenticación. Desde que el backend expone `POST /auth/login`
 * (contexto `identity`, ver `ARQUITECTURA-INTEGRACION.md`) existen dos
 * implementaciones, mismo patrón `Fake*`/`Http*` que el resto de
 * repositorios: [FakeAuthRepository] (credenciales de prueba fijas, sin red,
 * útil si el backend no está corriendo) y [HttpAuthRepository] (la real,
 * ver [AppContainer]). `iniciarSesion` es `suspend` porque la implementación
 * real hace una llamada de red — quien la invoque necesita una corrutina
 * (ver `LoginScreen`).
 */
interface AuthRepository {
    suspend fun iniciarSesion(correoInstitucional: String, contrasena: String): ResultadoLogin
}

class FakeAuthRepository(private val usuarioRepository: UsuarioRepository) : AuthRepository {
    // Credencial de prueba fija para este front preliminar — no depende de
    // que el backend esté corriendo. Se muestra en la propia pantalla de
    // login. El correo decide el rol: el de MockData.usuarioProfesor entra
    // como profesor, cualquier otro @upb.edu.co entra como estudiante.
    //
    // El backend real (HttpAuthRepository) todavía no puede probar el rol de
    // profesor: su contrato no incluye ningún rol en la respuesta de login
    // (ver la nota en HttpAuthRepository) — este Fake sigue siendo la única
    // forma de probar esa parte de la UI hasta que el equipo de backend lo
    // agregue.
    private val contrasenaDePrueba = "upb2026"
    private val dominioInstitucional = "@upb.edu.co"

    override suspend fun iniciarSesion(correoInstitucional: String, contrasena: String): ResultadoLogin {
        val correo = correoInstitucional.trim()
        return when {
            correo.isBlank() || contrasena.isBlank() ->
                ResultadoLogin.Error("Ingresa tu correo institucional y tu contraseña.")

            !correo.endsWith(dominioInstitucional, ignoreCase = true) ->
                ResultadoLogin.Error("Usa tu correo institucional ($dominioInstitucional).")

            contrasena != contrasenaDePrueba ->
                ResultadoLogin.Error("Contraseña incorrecta.")

            correo.equals(MockData.usuarioProfesor.correoInstitucional, ignoreCase = true) -> {
                usuarioRepository.establecerUsuarioActual(MockData.usuarioProfesor)
                ResultadoLogin.Exito(MockData.usuarioProfesor)
            }

            else -> {
                usuarioRepository.establecerUsuarioActual(MockData.usuarioActual)
                ResultadoLogin.Exito(MockData.usuarioActual)
            }
        }
    }
}

/**
 * Puerto de mensajería privada profesor↔estudiante. Una [ConversacionChat]
 * solo la crea un profesor y solo la ven sus dos participantes — ver la
 * nota de visibilidad en el modelo de dominio. Igual que el resto: hoy
 * [FakeMensajeriaRepository] guarda todo en memoria; el día que el backend
 * tenga un contexto de mensajería, se agrega un `HttpMensajeriaRepository`.
 */
interface MensajeriaRepository {
    /** Conversaciones visibles para [usuario]: las que creó, si es profesor; las que le enviaron, si es estudiante. */
    fun obtenerConversacionesDe(usuario: Usuario): List<ConversacionChat>
    fun obtenerConversacion(id: String): ConversacionChat?
    fun obtenerEstudiantesDisponibles(): List<Usuario>

    /** Solo puede llamarla un [Usuario] con [RolUsuario.PROFESOR]. */
    fun crearConversacion(
        profesor: Usuario,
        estudianteId: String,
        asunto: String,
        mensajeInicial: String
    ): ConversacionChat

    fun enviarMensaje(conversacionId: String, autor: Usuario, contenido: String): MensajeChat?
}

class FakeMensajeriaRepository : MensajeriaRepository {
    private var conversaciones: List<ConversacionChat> = MockData.conversacionesChat

    override fun obtenerConversacionesDe(usuario: Usuario): List<ConversacionChat> =
        conversaciones
            .filter {
                if (usuario.rol == RolUsuario.PROFESOR) it.profesorId == usuario.id else it.estudianteId == usuario.id
            }
            .sortedByDescending { it.ultimoMensaje?.fecha }

    override fun obtenerConversacion(id: String): ConversacionChat? =
        conversaciones.firstOrNull { it.id == id }

    override fun obtenerEstudiantesDisponibles(): List<Usuario> = MockData.estudiantesDirectorio

    override fun crearConversacion(
        profesor: Usuario,
        estudianteId: String,
        asunto: String,
        mensajeInicial: String
    ): ConversacionChat {
        val estudiante = MockData.estudiantesDirectorio.firstOrNull { it.id == estudianteId }
        val nueva = ConversacionChat(
            id = "chat-${System.currentTimeMillis()}",
            profesorId = profesor.id,
            profesorNombre = profesor.nombre,
            estudianteId = estudianteId,
            estudianteNombre = estudiante?.nombre ?: "Estudiante",
            asunto = asunto,
            mensajes = listOf(
                MensajeChat(
                    id = "msg-${System.currentTimeMillis()}",
                    autorId = profesor.id,
                    autorNombre = profesor.nombre,
                    esDeProfesor = true,
                    contenido = mensajeInicial,
                    fecha = LocalDateTime.now()
                )
            )
        )
        conversaciones = conversaciones + nueva
        return nueva
    }

    override fun enviarMensaje(conversacionId: String, autor: Usuario, contenido: String): MensajeChat? {
        if (conversaciones.none { it.id == conversacionId }) return null

        val mensaje = MensajeChat(
            id = "msg-${System.currentTimeMillis()}",
            autorId = autor.id,
            autorNombre = autor.nombre,
            esDeProfesor = autor.rol == RolUsuario.PROFESOR,
            contenido = contenido,
            fecha = LocalDateTime.now()
        )
        conversaciones = conversaciones.map { conversacion ->
            if (conversacion.id == conversacionId) conversacion.copy(mensajes = conversacion.mensajes + mensaje) else conversacion
        }
        return mensaje
    }
}

/**
 * Contenedor simple de dependencias. Sin Hilt/Koin a propósito: para un
 * front preliminar de un solo módulo, instanciar aquí es suficiente y
 * evita una dependencia adicional que Android Studio tendría que resolver
 * antes de poder previsualizar nada. Si el proyecto crece, este es el
 * punto natural para introducir inyección de dependencias.
 */
object AppContainer {
    val convocatoriasRepository: ConvocatoriasRepository = FakeConvocatoriasRepository()
    val practicasRepository: PracticasRepository = FakePracticasRepository()
    val noticiasRepository: NoticiasRepository = FakeNoticiasRepository()
    val foroRepository: ForoRepository = FakeForoRepository()
    val notificacionesRepository: NotificacionesRepository = FakeNotificacionesRepository()
    val mapaRepository: MapaRepository = FakeMapaRepository()
    val chatbotRepository: ChatbotRepository = FakeChatbotRepository()
    val usuarioRepository: UsuarioRepository = FakeUsuarioRepository()

    // Primer repositorio contra el backend real (POST /auth/login — ver
    // HttpAuthRepository y NetworkModule.BASE_URL). Requiere tener
    // `npm run start:http` corriendo en el backend; si no está disponible,
    // comenta esta línea y descomenta la de abajo para volver a probar solo
    // con datos simulados (p. ej. para probar el rol de profesor, que el
    // backend todavía no distingue — ver la nota en HttpAuthRepository).
    val authRepository: AuthRepository = HttpAuthRepository(NetworkModule.authApi, usuarioRepository)
    // val authRepository: AuthRepository = FakeAuthRepository(usuarioRepository)

    val mensajeriaRepository: MensajeriaRepository = FakeMensajeriaRepository()
}
