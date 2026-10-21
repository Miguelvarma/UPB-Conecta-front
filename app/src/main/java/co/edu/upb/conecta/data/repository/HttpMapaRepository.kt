package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.network.CampusApiService
import co.edu.upb.conecta.data.network.EdificioDto
import co.edu.upb.conecta.data.network.MapaCampusDto
import co.edu.upb.conecta.data.network.PuntoInteresDto
import co.edu.upb.conecta.domain.model.CategoriaPunto
import co.edu.upb.conecta.domain.model.MapaCampus
import co.edu.upb.conecta.domain.model.OrigenMapa
import co.edu.upb.conecta.domain.model.PuntoInteres
import kotlinx.coroutines.CancellationException

/**
 * Mapa del campus desde `GET /campus/map`. Si la petición falla (sin
 * sesión real, sin red o backend sin desplegar) devuelve el [respaldo]
 * local, para que la pantalla siempre muestre el campus.
 */
class HttpMapaRepository(
    private val api: CampusApiService,
    private val respaldo: MapaRepository
) : MapaRepository {

    override suspend fun obtenerMapa(): MapaCampus {
        val cuerpo = try {
            api.obtenerMapa().takeIf { it.isSuccessful }?.body()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        val mapa = cuerpo?.aMapa()
        return mapa ?: respaldo.obtenerMapa().copy(origen = OrigenMapa.LOCAL)
    }
}

private fun MapaCampusDto.aMapa(): MapaCampus? {
    val centro = center ?: return null
    if (!ok) return null
    val edificios = blocks.flatMap { bloque -> bloque.buildings.map { it.aPunto(bloque.name) } }
    val nombresEdificio = blocks.flatMap { it.buildings }.associateBy { it.id }
    val puntos = pointsOfInterest.mapNotNull { it.aPunto(nombresEdificio) }
    return MapaCampus(
        nombre = name ?: "Campus UPB",
        centroLatitud = centro.latitude,
        centroLongitud = centro.longitude,
        puntos = edificios + puntos,
        origen = OrigenMapa.SERVIDOR
    )
}

private fun EdificioDto.aPunto(zona: String): PuntoInteres {
    val niveles = levels.size
    return PuntoInteres(
        id = id,
        nombre = name,
        categoria = CategoriaPunto.EDIFICIO,
        descripcion = "$zona · $niveles ${if (niveles == 1) "nivel" else "niveles"}",
        latitud = coordinates.latitude,
        longitud = coordinates.longitude
    )
}

/** `null` si la categoría es nueva y la app todavía no la conoce. */
private fun PuntoInteresDto.aPunto(edificios: Map<String, EdificioDto>): PuntoInteres? {
    val categoria = when (category) {
        "cafeteria" -> CategoriaPunto.CAFETERIA
        "biblioteca" -> CategoriaPunto.BIBLIOTECA
        "bienestar" -> CategoriaPunto.BIENESTAR
        "coordinacion" -> CategoriaPunto.COORDINACION
        "punto-de-atencion" -> CategoriaPunto.PUNTO_ATENCION
        "parqueadero" -> CategoriaPunto.PARQUEADERO
        "servicio-sanitario" -> CategoriaPunto.SANITARIO
        else -> return null
    }
    val edificio = buildingId?.let { edificios[it] }
    val nivel = edificio?.levels?.firstOrNull { it.id == levelId }?.name
    val descripcion = when {
        edificio == null -> "Al aire libre"
        nivel == null -> edificio.name
        else -> "${edificio.name}, ${nivel.lowercase()}"
    }
    return PuntoInteres(id, name, categoria, descripcion, coordinates.latitude, coordinates.longitude)
}
