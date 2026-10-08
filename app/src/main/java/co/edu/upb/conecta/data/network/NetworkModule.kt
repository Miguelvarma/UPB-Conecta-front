package co.edu.upb.conecta.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Punto único de configuración de red (mismo espíritu que [AppContainer]
 * para los repositorios: un solo lugar para cambiar la URL base o el
 * cliente HTTP sin tocar cada `Http*Repository`).
 *
 * `BASE_URL` apunta por defecto al alias que el emulador de Android usa
 * para la máquina donde corre el emulador (`10.0.2.2`), donde normalmente
 * corre `npm run start:http` del backend en el puerto que fija `HTTP_PORT`
 * (por defecto 3000 — ver `backend/.env.example`). Para probar desde un
 * celular físico en la misma red que tu PC, cambia esto por la IP de tu PC
 * (ej. "http://192.168.1.50:3000/") y agrega esa IP a
 * `res/xml/network_security_config.xml`.
 */
object NetworkModule {
    private const val BASE_URL = "https://upb-conecta-railway-production.up.railway.app/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BODY es útil mientras se depura la integración; bajarlo a NONE antes
        // de un build de release para no imprimir credenciales en Logcat.
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)
}

/**
 * Sesión en memoria del par de tokens emitido por `/auth/login` (HU-45).
 * Deliberadamente simple: hoy ningún otro endpoint exige `Authorization`
 * todavía, así que no hay para qué instalar un interceptor que adjunte el
 * token ni persistirlo en `EncryptedSharedPreferences`/`DataStore` — eso
 * es trabajo para cuando exista el siguiente endpoint autenticado (ver
 * `ARQUITECTURA-INTEGRACION.md`). Se pierde si el proceso se reinicia,
 * igual que el resto del estado "logueado" de este front preliminar.
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
