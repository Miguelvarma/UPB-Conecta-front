package co.edu.upb.conecta.ui.screens.mapa

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import co.edu.upb.conecta.data.repository.MapaRepository
import co.edu.upb.conecta.domain.model.CategoriaPunto
import co.edu.upb.conecta.domain.model.MapaCampus
import co.edu.upb.conecta.domain.model.OrigenMapa
import co.edu.upb.conecta.domain.model.PuntoInteres
import co.edu.upb.conecta.ui.components.EncabezadoSeccion
import co.edu.upb.conecta.ui.theme.AcentoAzul
import co.edu.upb.conecta.ui.theme.AcentoMorado
import co.edu.upb.conecta.ui.theme.AcentoVerde
import co.edu.upb.conecta.ui.theme.UpbAzulMarino
import co.edu.upb.conecta.ui.theme.UpbDorado
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Mapa del campus sobre OpenStreetMap (osmdroid). Los contornos de los
 * edificios los dibuja la propia cartografía de OSM; encima se pintan los
 * marcadores del catálogo del backend (`GET /campus/map`) o, si no hay
 * conexión o sesión real, la copia local de la app.
 */
@Composable
fun MapaScreen(mapaRepository: MapaRepository, modifier: Modifier = Modifier) {
    var mapa by remember { mutableStateOf<MapaCampus?>(null) }
    LaunchedEffect(mapaRepository) { mapa = mapaRepository.obtenerMapa() }

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoSeccion(
            titulo = "Mapa del campus",
            subtitulo = "Toca un marcador o un elemento de la lista para ver el detalle"
        )
        val actual = mapa
        if (actual == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ContenidoMapa(actual)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContenidoMapa(mapa: MapaCampus) {
    var categoriasVisibles by remember { mutableStateOf(CategoriaPunto.entries.toSet()) }
    var seleccionado by remember { mutableStateOf<PuntoInteres?>(null) }
    val categoriasPresentes = remember(mapa) { CategoriaPunto.entries.filter { c -> mapa.puntos.any { it.categoria == c } } }
    val visibles = mapa.puntos.filter { it.categoria in categoriasVisibles }

    Column(modifier = Modifier.fillMaxSize()) {
        if (mapa.origen == OrigenMapa.LOCAL) {
            Text(
                text = "Sin conexión con el servidor: se muestran los datos guardados en la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categoriasPresentes) { categoria ->
                FilterChip(
                    selected = categoria in categoriasVisibles,
                    onClick = {
                        categoriasVisibles = if (categoria in categoriasVisibles) {
                            categoriasVisibles - categoria
                        } else {
                            categoriasVisibles + categoria
                        }
                        if (seleccionado?.categoria == categoria) seleccionado = null
                    },
                    label = { Text(categoria.etiqueta) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colorCategoria(categoria))
                        )
                    }
                )
            }
        }

        MapaOsm(
            mapa = mapa,
            puntos = visibles,
            seleccionado = seleccionado,
            onSeleccionar = { seleccionado = it },
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        LazyColumn(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            seleccionado?.let { punto ->
                item(key = "seleccionado") {
                    TarjetaPuntoSeleccionado(punto, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            items(visibles, key = { it.id }) { punto ->
                FilaPunto(
                    punto = punto,
                    seleccionado = punto.id == seleccionado?.id,
                    onClick = { seleccionado = punto },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** El `MapView` de osmdroid dentro de Compose, atado al ciclo de vida de la pantalla. */
@Composable
private fun MapaOsm(
    mapa: MapaCampus,
    puntos: List<PuntoInteres>,
    seleccionado: PuntoInteres?,
    onSeleccionar: (PuntoInteres) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { crearMapView(context, GeoPoint(mapa.centroLatitud, mapa.centroLongitud)) }

    DisposableEffect(lifecycle, mapView) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observador)
        onDispose {
            lifecycle.removeObserver(observador)
            mapView.onDetach()
        }
    }

    // Centrar el mapa solo cuando cambia la selección, no al filtrar.
    LaunchedEffect(seleccionado?.id) {
        seleccionado?.let { mapView.controller.animateTo(GeoPoint(it.latitud, it.longitud)) }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { vista ->
            vista.overlays.removeAll { it is Marker }
            puntos.forEach { punto ->
                val marcador = Marker(vista).apply {
                    position = GeoPoint(punto.latitud, punto.longitud)
                    title = punto.nombre
                    snippet = punto.descripcion
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = iconoMarcador(vista.context, punto.categoria, punto.id == seleccionado?.id)
                    setOnMarkerClickListener { _, _ ->
                        onSeleccionar(punto)
                        true
                    }
                }
                vista.overlays.add(marcador)
            }
            vista.invalidate()
        }
    )
}

private fun crearMapView(context: Context, centro: GeoPoint): MapView {
    // La política de uso de los mosaicos de OSM exige identificar la app.
    // La caché va en el directorio privado de la app: no pide permisos.
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        minZoomLevel = 15.0
        maxZoomLevel = 20.0
        controller.setZoom(18.0)
        controller.setCenter(centro)
        // Atribución obligatoria de OpenStreetMap.
        overlays.add(CopyrightOverlay(context))
    }
}

/** Círculo del color de la categoría, más grande y con borde si está seleccionado. */
private fun iconoMarcador(context: Context, categoria: CategoriaPunto, seleccionado: Boolean): Drawable {
    val densidad = context.resources.displayMetrics.density
    val tamano = ((if (seleccionado) 26 else 18) * densidad).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colorCategoria(categoria).toArgb())
        setStroke(((if (seleccionado) 3 else 2) * densidad).toInt(), Color.White.toArgb())
        setSize(tamano, tamano)
    }
}

private fun colorCategoria(categoria: CategoriaPunto): Color = when (categoria) {
    CategoriaPunto.EDIFICIO -> UpbAzulMarino
    CategoriaPunto.CAFETERIA -> UpbDorado
    CategoriaPunto.BIBLIOTECA -> AcentoAzul
    CategoriaPunto.BIENESTAR -> AcentoMorado
    CategoriaPunto.COORDINACION -> Color(0xFF00838F)
    CategoriaPunto.PUNTO_ATENCION -> Color(0xFFC62828)
    CategoriaPunto.PARQUEADERO -> Color(0xFF616161)
    CategoriaPunto.SANITARIO -> AcentoVerde
}

@Composable
private fun TarjetaPuntoSeleccionado(punto: PuntoInteres, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = punto.categoria.etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(text = punto.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text = punto.descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FilaPunto(
    punto: PuntoInteres,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (seleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colorCategoria(punto.categoria))
            )
            Column {
                Text(text = punto.nombre, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = punto.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
