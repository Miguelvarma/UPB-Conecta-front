package co.edu.upb.conecta.ui.screens.foro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.upb.conecta.data.repository.ForoRepository
import co.edu.upb.conecta.data.repository.UsuarioRepository
import co.edu.upb.conecta.domain.model.ComentarioForo
import co.edu.upb.conecta.domain.model.EstadoModeracion
import co.edu.upb.conecta.ui.components.EstadoVacio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForoDetalleScreen(
    id: String,
    foroRepository: ForoRepository,
    usuarioRepository: UsuarioRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usuario = remember { usuarioRepository.obtenerUsuarioActual() }
    var post by remember(id) { mutableStateOf(foroRepository.obtenerPostPorId(id)) }
    var textoComentario by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Publicación") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            if (post != null) {
                BarraNuevoComentario(
                    valor = textoComentario,
                    onValorChange = { textoComentario = it },
                    onEnviar = {
                        val idPost = post?.id ?: return@BarraNuevoComentario
                        if (textoComentario.isNotBlank()) {
                            foroRepository.agregarComentario(
                                postId = idPost,
                                contenido = textoComentario.trim(),
                                autorNombre = usuario.nombre,
                                autorVerificado = usuario.identidadVerificada
                            )
                            post = foroRepository.obtenerPostPorId(idPost)
                            textoComentario = ""
                        }
                    }
                )
            }
        }
    ) { padding ->
        val postActual = post
        if (postActual == null) {
            EstadoVacio("No se encontró la publicación.", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (postActual.estadoModeracion == EstadoModeracion.EN_REVISION) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = "Esta publicación está pendiente de revisión por el moderador automático y solo la ves tú.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = postActual.autorNombre, style = MaterialTheme.typography.titleSmall)
                if (postActual.autorVerificado) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Identidad verificada", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = postActual.programa.nombre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = postActual.titulo, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = postActual.contenido, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Respuestas (${postActual.comentarios.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (postActual.comentarios.isEmpty()) {
                Text(
                    text = "Aún no hay respuestas. Sé el primero en comentar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                postActual.comentarios.forEach { comentario ->
                    TarjetaComentario(comentario, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BarraNuevoComentario(
    valor: String,
    onValorChange: (String) -> Unit,
    onEnviar: () -> Unit,
    modifier: Modifier = Modifier
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
                placeholder = { Text("Escribe una respuesta...") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onEnviar, enabled = valor.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Enviar respuesta")
            }
        }
    }
}

@Composable
private fun TarjetaComentario(comentario: ComentarioForo, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = comentario.autorNombre, style = MaterialTheme.typography.labelLarge)
                if (comentario.autorVerificado) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Identidad verificada",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f, fill = true))
                Text(
                    text = tiempoRelativo(comentario.fecha),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = comentario.contenido, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
