package co.edu.upb.conecta.ui.screens.mensajes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.upb.conecta.data.repository.MensajeriaRepository
import co.edu.upb.conecta.data.repository.UsuarioRepository
import co.edu.upb.conecta.domain.model.ConversacionChat
import co.edu.upb.conecta.domain.model.MensajeChat
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.ui.components.EstadoVacio
import co.edu.upb.conecta.ui.screens.foro.tiempoRelativo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat de una conversación privada profesor↔estudiante. Que quien la abre
 * sea uno de los dos participantes lo valida el backend con el token de
 * sesión: a cualquier otro le responde como si no existiera.
 *
 * Mientras la pantalla está abierta, la conversación se recarga cada
 * [INTERVALO_CHAT_MS] para mostrar lo que escribe el otro participante desde
 * su celular (sin notificaciones push todavía).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversacionScreen(
    id: String,
    mensajeriaRepository: MensajeriaRepository,
    usuarioRepository: UsuarioRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usuario = remember { usuarioRepository.obtenerUsuarioActual() }
    var conversacion by remember(id) { mutableStateOf<ConversacionChat?>(null) }
    var cargando by remember(id) { mutableStateOf(true) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    val estadoScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        while (true) {
            mensajeriaRepository.obtenerConversacion(id)
                .onSuccess {
                    conversacion = it
                    aviso = null
                }
                .onFailure { aviso = it.message ?: "No se pudo actualizar la conversación." }
            cargando = false
            delay(INTERVALO_CHAT_MS)
        }
    }

    fun enviar() {
        val actual = conversacion ?: return
        val contenido = texto.trim()
        if (contenido.isEmpty() || enviando) return
        scope.launch {
            enviando = true
            mensajeriaRepository.enviarMensaje(actual.id, usuario, contenido)
                .onSuccess { mensaje ->
                    texto = ""
                    aviso = null
                    // Se muestra de inmediato, sin esperar la siguiente recarga.
                    val vigente = conversacion
                    if (vigente != null && vigente.mensajes.none { it.id == mensaje.id }) {
                        conversacion = vigente.copy(mensajes = vigente.mensajes + mensaje)
                    }
                }
                .onFailure { aviso = it.message ?: "No se pudo enviar el mensaje." }
            enviando = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val conv = conversacion
                    Column {
                        Text(
                            text = if (usuario.rol == RolUsuario.PROFESOR) {
                                conv?.estudianteNombre ?: "Conversación"
                            } else {
                                conv?.profesorNombre ?: "Conversación"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (conv != null) {
                            Text(text = conv.asunto, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            if (conversacion != null) {
                Column {
                    aviso?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    BarraEnvioMensaje(
                        valor = texto,
                        onValorChange = { texto = it },
                        onEnviar = { enviar() },
                        habilitado = !enviando
                    )
                }
            }
        }
    ) { padding ->
        val conversacionActual = conversacion
        if (conversacionActual == null) {
            if (cargando) {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EstadoVacio(aviso ?: "No se encontró la conversación.", modifier = Modifier.padding(padding))
            }
            return@Scaffold
        }

        val mensajesOrdenados = remember(conversacionActual) {
            conversacionActual.mensajes.sortedBy { it.fecha }
        }

        LaunchedEffect(mensajesOrdenados.size) {
            if (mensajesOrdenados.isNotEmpty()) {
                estadoScroll.animateScrollTo(estadoScroll.maxValue)
            }
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(estadoScroll)
                .padding(16.dp)
        ) {
            mensajesOrdenados.forEach { mensaje ->
                BurbujaMensaje(
                    mensaje = mensaje,
                    esMio = mensaje.autorId == usuario.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BurbujaMensaje(mensaje: MensajeChat, esMio: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (esMio) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (esMio) 14.dp else 2.dp,
                bottomEnd = if (esMio) 2.dp else 14.dp
            ),
            color = if (esMio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (esMio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!esMio) {
                    Text(
                        text = mensaje.autorNombre + if (mensaje.esDeProfesor) " · Profesor" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(text = mensaje.contenido, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tiempoRelativo(mensaje.fecha),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (esMio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BarraEnvioMensaje(
    valor: String,
    onValorChange: (String) -> Unit,
    onEnviar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    Surface(modifier = modifier, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = valor,
                onValueChange = onValorChange,
                placeholder = { Text("Escribe un mensaje...") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onEnviar, enabled = habilitado && valor.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Enviar mensaje")
            }
        }
    }
}

private const val INTERVALO_CHAT_MS = 4_000L
