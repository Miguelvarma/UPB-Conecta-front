package co.edu.upb.conecta.data.network

import retrofit2.Response
import retrofit2.http.GET

/** Contrato Retrofit del mapa del campus (`backend/src/contexts/campus/infrastructure/http/campusRoutes.ts`). */
interface CampusApiService {
    @GET("campus/map")
    suspend fun obtenerMapa(): Response<MapaCampusDto>
}

/**
 * Respuesta de `GET /campus/map`. Con `ok = false` solo llegan `error` y
 * `message`, por eso casi todo es opcional.
 */
data class MapaCampusDto(
    val ok: Boolean,
    val name: String? = null,
    val center: CoordenadasDto? = null,
    val blocks: List<BloqueCampusDto> = emptyList(),
    val pointsOfInterest: List<PuntoInteresDto> = emptyList(),
    val message: String? = null
)

data class CoordenadasDto(val latitude: Double, val longitude: Double)

data class BloqueCampusDto(val id: String, val name: String, val buildings: List<EdificioDto> = emptyList())

data class EdificioDto(
    val id: String,
    val name: String,
    val coordinates: CoordenadasDto,
    val levels: List<NivelDto> = emptyList()
)

data class NivelDto(val id: String, val number: Int, val name: String)

data class PuntoInteresDto(
    val id: String,
    val name: String,
    /** "cafeteria" | "biblioteca" | "bienestar" | "coordinacion" | "punto-de-atencion" | "parqueadero" | "servicio-sanitario" */
    val category: String,
    val coordinates: CoordenadasDto,
    val buildingId: String? = null,
    val levelId: String? = null
)
