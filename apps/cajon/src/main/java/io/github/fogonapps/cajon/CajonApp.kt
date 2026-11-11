package io.github.fogonapps.cajon

import android.app.Application
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

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class CajonApp : Application(), SingletonImageLoader.Factory, io.github.fogonapps.social.ui.AppConAvisos, io.github.fogonapps.social.ui.AppConTrabajador {
    /* X-10: los avisos con la app cerrada (la revisión común; El Cajón conserva el nombre AvisosRed). */
    override val avisosConfiguracion = io.github.fogonapps.social.ConfiguracionSocial("cajon", "El Cajón", "ElCajon-Fogon")
    override val avisosConMensajes = true
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
        confirmarMigracion = java.io.File(filesDir, "migracion-perfiles.json").let { it.exists() && it.readText() == "terminada" }
        migracion.ejecutar()
        perfiles = io.github.fogonapps.cajon.datos.Perfiles(filesDir)
        perfiles.limpiarPendientes { id ->
            io.github.fogonapps.cajon.datos.borrarClavesPerfil(this, id)
            check(java.io.File(cacheDir, "compartir/$id").let { !it.exists() || it.deleteRecursively() })
        }
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
