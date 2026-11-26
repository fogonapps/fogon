package io.github.fogonapps.cajon

import android.app.Application
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import androidx.datastore.core.DataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.crearAlmacen
import io.github.fogonapps.cajon.datos.limpiarClavesRetiradas

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class CajonApp : Application(), SingletonImageLoader.Factory, io.github.fogonapps.social.ui.AppConAvisos, io.github.fogonapps.social.ui.AppConTrabajador {
    /* X-10: los avisos con la app cerrada (la revisión común; El Cajón conserva el nombre AvisosRed). */
    override val avisosConfiguracion = io.github.fogonapps.social.ConfiguracionSocial("cajon", "Fabs", "ElCajon-Fogon")
    override val avisosConMensajes = true
    // Fabs no usa historias de 24 horas: sus avisos no suenan.
    override val avisosConHistorias = false
    override val avisosIcono = R.drawable.ic_notificacion
    override val trabajadorAvisos: Class<out io.github.fogonapps.social.ui.AvisosFondo> = AvisosRed::class.java
    override fun avisosCuentas() = perfiles.lista.perfiles.mapNotNull { perfil ->
        val caja = runCatching { recursos(perfil.id).caja }.getOrNull() ?: return@mapNotNull null
        val token = caja.tokenSocial ?: return@mapNotNull null
        io.github.fogonapps.social.ui.CuentaAvisos(perfil.id, token, caja.usuarioSocial.orEmpty()) {
            caja.tokenSocial == token && perfiles.lista.perfiles.any { it.id == perfil.id }
        }
    }
    override val avisosFirebase = "1:252547803157:android:a6ffdfe3d07510916dc61c"
    override fun avisosPerfilActivo() = perfiles.lista.activo
    override fun avisosAbrir(perfil: String) = android.content.Intent(this, MainActivity::class.java).putExtra("perfil", perfil)

    private val porPerfil = mutableMapOf<String, RecursosPerfil>()
    private val retirados = mutableSetOf<String>()
    val migracion by lazy { io.github.fogonapps.cajon.datos.migracionPerfiles(this) }
    lateinit var perfiles: io.github.fogonapps.cajon.datos.Perfiles
        private set
    private var confirmarMigracion = false
    override fun onCreate() {
        super.onCreate()
        // Fabs arranca en su propio tema (amarillo sobre casi negro violeta) para quien nunca eligió otro.
        io.github.fogonapps.identidad.AlmacenApariencia.estiloInicial = io.github.fogonapps.identidad.Estilo.MARCA
        // Fabs ya no tiene personaje: la cajita con ojos era del diseño anterior.
        io.github.fogonapps.identidad.Personajes.apagados += io.github.fogonapps.identidad.Quien.CAJON
        io.github.fogonapps.identidad.Trama.apagada = true
        io.github.fogonapps.identidad.Iconografia.usarLucide()
        nombresGenerosJuego()
        confirmarMigracion = java.io.File(filesDir, "migracion-perfiles.json").let { it.exists() && it.readText() == "terminada" }
        limpiarClavesRetiradas(this)
        migracion.ejecutar()
        limpiarClavesRetiradas(this)
        perfiles = io.github.fogonapps.cajon.datos.Perfiles(filesDir)
        perfiles.limpiarPendientes { id ->
            io.github.fogonapps.cajon.datos.borrarClavesPerfil(this, id)
            check(java.io.File(cacheDir, "compartir/$id").let { !it.exists() || it.deleteRecursively() })
        }
    }
    /**
     * Los nombres de los géneros de juego en los dos idiomas (`generos_juegos` y `generos_igdb`), para mostrar y migrar
     * los guardados como claves ([io.github.fogonapps.cajon.datos.GenerosJuego]); antes de leer ningún cajón.
     */
    @android.annotation.SuppressLint("AppBundleLocaleChanges")
    private fun nombresGenerosJuego() {
        fun de(lengua: String): io.github.fogonapps.cajon.datos.GenerosJuego.Nombres {
            val config = android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lengua)) }
            val r = createConfigurationContext(config).resources
            return io.github.fogonapps.cajon.datos.GenerosJuego.Nombres(r.getStringArray(R.array.generos_juegos).toList(), r.getStringArray(R.array.generos_igdb).toList())
        }
        io.github.fogonapps.cajon.datos.GenerosJuego.configurar(de("es"), de("en")) { io.github.fogonapps.identidad.Idioma.actual(this) }
    }

    @Synchronized fun recursos(id: String = perfiles.lista.activo): RecursosPerfil {
        require(id !in retirados && perfiles.lista.perfiles.any { it.id == id })
        return porPerfil.getOrPut(id) { RecursosPerfil(this, id, perfiles.directorio(id)) }
    }
    suspend fun cerrarRecursos(id: String) {
        val recursos = synchronized(this) { retirados.add(id); porPerfil.remove(id) } ?: return
        recursos.trabajo.cancel()
        recursos.trabajo.join()
    }
    val almacen get() = recursos().almacen
    val caja get() = recursos().caja
    val escuchas get() = recursos().escuchas
    fun arranqueCorrecto(id: String) {
        if (id == "1" && confirmarMigracion) { migracion.confirmarArranque(); confirmarMigracion = false }
    }

    /** Las portadas se guardan en la caché del teléfono (hasta 150 MB) para no bajarlas cada vez. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("portadas")).maxSizeBytes(150L * 1024 * 1024).build() }
        // Wikimedia (fotos de artistas) y MusicBrainz piden que cada app se identifique; sin esto, rechazan la descarga.
        .components {
            add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = {
                // Cover Art Archive tarda 5-6 s por carátula (dos redirecciones a archive.org): con los 10 s de
                // siempre, en una discografía larga varias se quedaban vacías. Más tiempo y más descargas a la vez.
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 12 })
                    .addInterceptor { c ->
                    c.proceed(c.request().newBuilder().header("User-Agent", "ElCajon-Fogon/${BuildConfig.VERSION_NAME} (fogonapps@gmail.com)").build())
                }.addInterceptor { c ->
                    // Open Library y Cover Art Archive tardan ~2 s (redirigen a archive.org): pasan por la caché del
                    // servidor de Fogón, que las entrega al instante después de la primera vez. Si falla, directo.
                    val pedido = c.request()
                    val host = pedido.url.host
                    if (host != "covers.openlibrary.org" && host != "coverartarchive.org") return@addInterceptor c.proceed(pedido)
                    // La imagen vacía de Open Library (un GIF de 1×1) se trata como «no hay»: así queda la portada
                    // inventada con su ícono y no una tapa en blanco.
                    fun sinVacia(r: okhttp3.Response): okhttp3.Response {
                        if (!r.isSuccessful || !io.github.fogonapps.cajon.datos.Imagenes.vaciaDeOpenLibrary(host, r.header("Content-Type"))) return r
                        r.close()
                        return r.newBuilder().code(404).message("Sin portada").removeHeader("Content-Length").body("".toResponseBody(null)).build()
                    }
                    val porServidor = pedido.newBuilder().url(io.github.fogonapps.social.ClienteSocial.SERVIDOR.toHttpUrl().newBuilder()
                        .addPathSegments("v1/portada").addQueryParameter("u", pedido.url.toString()).build()).build()
                    val r = runCatching { c.proceed(porServidor) }.getOrNull()
                    if (r != null && r.isSuccessful) return@addInterceptor sinVacia(r)
                    r?.close()
                    // Directo, con un segundo intento si Internet Archive no respondió a tiempo o estaba ocupado: si no,
                    // la portada quedaba vacía hasta salir y volver a la pantalla.
                    val primera = try { c.proceed(pedido) } catch (x: java.io.IOException) {
                        if (c.call().isCanceled()) throw x
                        null
                    }
                    if (primera != null && !io.github.fogonapps.cajon.datos.Imagenes.reintentable(primera.code)) return@addInterceptor sinVacia(primera)
                    if (c.call().isCanceled()) return@addInterceptor primera ?: throw java.io.IOException("Canceled")
                    primera?.close()
                    Thread.sleep(1_000)
                    sinVacia(c.proceed(pedido))
                }.build()
            }))
        }
        .crossfade(true)
        .build()
}

/** Referencias inmutables: una respuesta tardía conserva siempre su directorio y sus claves. */
class RecursosPerfil(app: CajonApp, val id: String, val directorio: java.io.File) {
    val trabajo = kotlinx.coroutines.SupervisorJob()
    val almacen = crearAlmacen(app, directorio, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + trabajo))
    val caja = CajaFuerte(app, id)
    val escuchas = io.github.fogonapps.cajon.datos.RegistroEscuchas(app, directorio)
}
