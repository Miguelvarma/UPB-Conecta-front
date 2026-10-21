package co.edu.upb.conecta.domain.model

/**
 * Categorías del mapa. Además de los edificios, son las siete categorías de
 * punto de interés del backend (`PoiCategory` en el contexto `campus`).
 */
enum class CategoriaPunto(val etiqueta: String) {
    EDIFICIO("Edificios"),
    CAFETERIA("Cafeterías"),
    BIBLIOTECA("Biblioteca"),
    BIENESTAR("Bienestar"),
    COORDINACION("Coordinaciones"),
    PUNTO_ATENCION("Atención"),
    PARQUEADERO("Parqueaderos"),
    SANITARIO("Baños")
}

/** Punto del mapa del campus, en coordenadas geográficas (WGS84, grados). */
data class PuntoInteres(
    val id: String,
    val nombre: String,
    val categoria: CategoriaPunto,
    val descripcion: String,
    val latitud: Double,
    val longitud: Double
)

/** De dónde salieron los datos del mapa que se está mostrando. */
enum class OrigenMapa {
    /** `GET /campus/map` del backend. */
    SERVIDOR,

    /** Copia guardada en la app (sin sesión real o sin conexión). */
    LOCAL
}

data class MapaCampus(
    val nombre: String,
    val centroLatitud: Double,
    val centroLongitud: Double,
    val puntos: List<PuntoInteres>,
    val origen: OrigenMapa
)
