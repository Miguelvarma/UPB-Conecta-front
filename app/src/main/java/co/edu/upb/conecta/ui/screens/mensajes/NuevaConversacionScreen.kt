package co.edu.upb.conecta.ui.screens.mensajes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.edu.upb.conecta.data.repository.MensajeriaRepository
import co.edu.upb.conecta.data.repository.UsuarioRepository
import co.edu.upb.conecta.domain.model.RolUsuario
import co.edu.upb.conecta.ui.components.EstadoVacio
import co.edu.upb.conecta.ui.theme.UpbGradienteMarca

/**
 * Formulario con el que un profesor inicia una conversación con un
 * estudiante del directorio ([MensajeriaRepository.obtenerEstudiantesDisponibles]).
 * Solo tiene sentido para [RolUsuario.PROFESOR] — si alguien más llega
 * aquí, se muestra un aviso en vez del formulario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaConversacionScreen(
    mensajeriaRepository: MensajeriaRepository,
    usuarioRepository: UsuarioRepository,
    onConversacionCreada: (String) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usuario = remember { usuarioRepository.obtenerUsuarioActual() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Nueva conversación") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (usuario.rol != RolUsuario.PROFESOR) {
            EstadoVacio("Esta pantalla es solo para profesores.", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val estudiantes = remember { mensajeriaRepository.obtenerEstudiantesDisponibles() }
        var estudianteSeleccionado by remember { mutableStateOf(estudiantes.firstOrNull()) }
        var asunto by remember { mutableStateOf("") }
        var mensaje by remember { mutableStateOf("") }

        val puedeEnviar = estudianteSeleccionado != null && asunto.isNotBlank() && mensaje.isNotBlank()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Estudiante", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            if (estudiantes.isEmpty()) {
                Text(
                    "No hay estudiantes en el directorio de ejemplo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estudiantes, key = { it.id }) { estudiante ->
                        FilterChip(
                            selected = estudianteSeleccionado?.id == estudiante.id,
                            onClick = { estudianteSeleccionado = estudiante },
                            label = {
                                Text(
                                    text = estudiante.nombre,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            estudianteSeleccionado?.let { seleccionado ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${seleccionado.programa.nombre} · ${seleccionado.semestre}.º semestre",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = asunto,
                onValueChange = { asunto = it },
                label = { Text("Asunto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = mensaje,
                onValueChange = { mensaje = it },
                label = { Text("Mensaje") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            val colorBoton = if (puedeEnviar) UpbGradienteMarca else Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorBoton)
                    .clickable(enabled = puedeEnviar) {
                        val destino = estudianteSeleccionado ?: return@clickable
                        val nueva = mensajeriaRepository.crearConversacion(
                            profesor = usuario,
                            estudianteId = destino.id,
                            asunto = asunto.trim(),
                            mensajeInicial = mensaje.trim()
                        )
                        onConversacionCreada(nueva.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Enviar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
