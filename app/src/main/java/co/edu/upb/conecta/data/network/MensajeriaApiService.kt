package co.edu.upb.conecta.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato Retrofit de `/messaging/...`. Igual que [AuthApiService], devuelve
 * [Response] para poder leer el cuerpo `{ ok:false, error, message }` de los
 * fallos esperados (403, 404, 400) en vez de recibir una excepción.
 */
interface MensajeriaApiService {
    @GET("messaging/conversations")
    suspend fun listarConversaciones(): Response<ListaConversacionesDto>

    @GET("messaging/conversations/{id}")
    suspend fun obtenerConversacion(@Path("id") id: String): Response<ConversacionRespuestaDto>

    @POST("messaging/conversations")
    suspend fun crearConversacion(@Body body: NuevaConversacionDto): Response<ConversacionRespuestaDto>

    @POST("messaging/conversations/{id}/messages")
    suspend fun enviarMensaje(@Path("id") id: String, @Body body: NuevoMensajeDto): Response<MensajeRespuestaDto>

    @GET("messaging/students")
    suspend fun listarEstudiantes(): Response<ListaEstudiantesDto>
}
