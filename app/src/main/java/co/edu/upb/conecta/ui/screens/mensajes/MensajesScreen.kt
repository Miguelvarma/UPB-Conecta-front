package co.edu.upb.conecta.ui.screens.mensajes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.edu.upb.conecta.data.repository.MensajeriaRepository
import co.edu.upb.conecta.data.repository.UsuarioRepository
import co.edu.upb.conecta.domain.model.ConversacionChat
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.ui.components.EncabezadoSeccion
import co.edu.upb.conecta.ui.components.EstadoVacio
import co.edu.upb.conecta.ui.screens.foro.tiempoRelativo
import co.edu.upb.conecta.ui.theme.UpbGradienteMarca

/**
 * Bandeja de mensajes privados profesor↔estudiante. Lo que se ve depende
 * del rol del usuario logueado ([RolUsuario]):
 * - Profesor: las conversaciones que él creó, y un botón para iniciar una
 *   nueva con cualquier estudiante del directorio.
 * - Estudiante: las conversaciones que le enviaron a él — no puede iniciar
 *   ninguna, solo responder (ver [ConversacionScreen]).
 */
@Composable
fun MensajesScreen(
    mensajeriaRepository: MensajeriaRepository,
    usuarioRepository: UsuarioRepository,
    onAbrirConversacion: (String) -> Unit,
    onNuevaConversacion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usuario = remember { usuarioRepository.obtenerUsuarioActual() }
    val esProfesor = usuario.rol == RolUsuario.PROFESOR
    val conversaciones = remember { mensajeriaRepository.obtenerConversacionesDe(usuario) }

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoSeccion(
            titulo = "Mensajes",
            subtitulo = if (esProfesor) {
                "Conversaciones privadas con tus estudiantes"
            } else {
                "Conversaciones privadas con tus profesores — solo tú las ves"
            }
        )

        if (esProfesor) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UpbGradienteMarca)
                    .clickable(onClick = onNuevaConversacion),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nueva conversación", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (conversaciones.isEmpty()) {
            EstadoVacio(
                mensaje = if (esProfesor) {
                    "Aún no has iniciado ninguna conversación."
                } else {
                    "Aún no tienes mensajes de tus profesores."
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(conversaciones, key = { it.id }) { conversacion ->
                    TarjetaConversacion(
                        conversacion = conversacion,
                        esProfesor = esProfesor,
                        onClick = { onAbrirConversacion(conversacion.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaConversacion(
    conversacion: ConversacionChat,
    esProfesor: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (esProfesor) Icons.Filled.Person else Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (esProfesor) conversacion.estudianteNombre else conversacion.profesorNombre,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversacion.asunto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                conversacion.ultimoMensaje?.let { ultimo ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ultimo.contenido,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            conversacion.ultimoMensaje?.let { ultimo ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tiempoRelativo(ultimo.fecha),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
