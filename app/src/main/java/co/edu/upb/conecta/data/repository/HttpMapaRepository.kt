package co.edu.upb.conecta.data.repository

import co.edu.upb.conecta.data.network.CampusApiService
import co.edu.upb.conecta.data.network.EdificioDto
import co.edu.upb.conecta.data.network.MapaCampusDto
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
    // Solo los edificios: los puntos de interés internos (cafeterías, baños...)
    // no se muestran mientras no estén confirmados.
    val edificios = blocks.flatMap { bloque -> bloque.buildings.map { it.aPunto() } }
    return MapaCampus(
        nombre = name ?: "Campus UPB",
        centroLatitud = centro.latitude,
        centroLongitud = centro.longitude,
        puntos = edificios,
        origen = OrigenMapa.SERVIDOR
    )
}

private fun EdificioDto.aPunto(): PuntoInteres = PuntoInteres(
    id = id,
    nombre = name,
    categoria = CategoriaPunto.EDIFICIO,
    descripcion = "Campus Floridablanca",
    latitud = coordinates.latitude,
    longitud = coordinates.longitude
)
