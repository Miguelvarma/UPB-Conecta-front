package co.edu.upb.conecta.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Punto único de configuración de red (mismo espíritu que [AppContainer]
 * para los repositorios: un solo lugar para cambiar la URL base o el
 * cliente HTTP sin tocar cada `Http*Repository`).
 *
 * `BASE_URL` apunta al backend desplegado en Railway (HTTPS, alcanzable
 * desde el emulador o un celular en cualquier red). Para probar contra un
 * backend local, cámbialo por "http://10.0.2.2:3000/" (emulador) o por la IP
 * de tu PC, y agrega ese host a `res/xml/network_security_config.xml`.
 *
 * Sesión (HU-45): todas las peticiones, salvo las de `/auth/`, llevan
 * `Authorization: Bearer <accessToken>` ([SessionStore]). Si el backend
 * responde 401 (el access token dura pocos minutos), [RenovadorDeSesion]
 * pide un par nuevo con el refresh token y reintenta la petición una vez.
 */
object NetworkModule {
    private const val BASE_URL = "https://upb-conecta-railway-production.up.railway.app/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BODY es útil mientras se depura la integración; bajarlo a NONE antes
        // de un build de release para no imprimir credenciales en Logcat.
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /** Cliente sin sesión: login, refresh y logout no llevan access token. */
    private val clienteBase: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val authApi: AuthApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(clienteBase)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AuthApiService::class.java)

    /** Cliente con sesión: adjunta el access token y lo renueva ante un 401. */
    private val clienteConSesion: OkHttpClient = clienteBase.newBuilder()
        .addInterceptor(AdjuntarToken)
        .authenticator(RenovadorDeSesion(authApi))
        .build()

    private val retrofitConSesion: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(clienteConSesion)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val mensajeriaApi: MensajeriaApiService = retrofitConSesion.create(MensajeriaApiService::class.java)
}

/** Agrega `Authorization: Bearer <accessToken>` si hay sesión. */
private object AdjuntarToken : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        val token = SessionStore.accessToken ?: return chain.proceed(peticion)
        return chain.proceed(peticion.conToken(token))
    }
}

/**
 * OkHttp llama a este [Authenticator] cuando una respuesta es 401. Renueva
 * la sesión con `POST /auth/refresh` y devuelve la misma petición con el
 * token nuevo; devolver `null` entrega el 401 a quien llamó.
 *
 * `synchronized`: si dos peticiones reciben 401 a la vez, solo la primera
 * renueva. La segunda ve que el token ya cambió y reintenta con el nuevo —
 * renovar dos veces con el mismo refresh token haría que el backend lo
 * detecte como reuso y cierre la sesión (HU-45).
 */
private class RenovadorDeSesion(private val authApi: AuthApiService) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Solo un reintento por petición: si con el token nuevo vuelve a dar
        // 401, la sesión ya no sirve.
        if (response.priorResponse != null) return null

        val tokenUsado = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

        synchronized(this) {
            val tokenActual = SessionStore.accessToken ?: return null
            if (tokenActual != tokenUsado) return response.request.conToken(tokenActual)

            val refreshToken = SessionStore.refreshToken ?: return null
            val respuesta = try {
                authApi.renovarSesion(RefreshRequestDto(refreshToken)).execute()
            } catch (error: IOException) {
                // Sin red: no se cierra la sesión, solo falla esta petición.
                return null
            }

            val sesion = respuesta.body()?.session
            if (!respuesta.isSuccessful || sesion == null) {
                SessionStore.limpiar()
                return null
            }
            SessionStore.guardar(sesion.accessToken.value, sesion.refreshToken.value)
            return response.request.conToken(sesion.accessToken.value)
        }
    }
}

private fun Request.conToken(token: String): Request =
    newBuilder().header("Authorization", "Bearer $token").build()

/**
 * Sesión en memoria del par de tokens emitido por `/auth/login` (HU-45) y
 * renovado por [RenovadorDeSesion]. Se pierde si el proceso se reinicia,
 * igual que el resto del estado "logueado" de este front preliminar;
 * persistirlo (`EncryptedSharedPreferences`/`DataStore`) queda pendiente.
 */
object SessionStore {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    fun guardar(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    fun limpiar() {
        accessToken = null
        refreshToken = null
    }
}
