package io.github.fogonapps.ahorrador

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Logica
import io.github.fogonapps.ahorrador.datos.crearAlmacen
import io.github.fogonapps.ahorrador.datos.migracionPerfiles
import io.github.fogonapps.identidad.Idioma
import io.github.fogonapps.social.Perfiles
import io.github.fogonapps.social.SesionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Objetos únicos por proceso: DataStore exige una sola instancia por archivo. Cada perfil del teléfono tiene su
 * cuaderno; el widget, los avisos y la lectura del banco usan el del perfil activo.
 */
class AhorradorApp : Application(), io.github.fogonapps.social.ui.AppConAvisos {
    /* X-10: los avisos de la red con la app cerrada (revisión común en social-ui). */
    override val avisosConfiguracion get() = CONFIGURACION_RED
    override val avisosIcono = R.drawable.ic_notificacion
    override fun avisosCuentas() = perfiles.lista.perfiles.mapNotNull { perfil ->
        val sesion = runCatching { io.github.fogonapps.social.SesionApp(this, perfiles, perfil.id, "alcancia") }.getOrNull() ?: return@mapNotNull null
        val token = sesion.token ?: return@mapNotNull null
        io.github.fogonapps.social.ui.CuentaAvisos(perfil.id, token, sesion.usuario.orEmpty()) { sesion.token == token }
    }
    override val avisosFirebase = "1:252547803157:android:50c9c9e49d3b998e6dc61c"
    override fun avisosPerfilActivo() = perfiles.lista.activo
    override fun avisosAbrir(perfil: String) = android.content.Intent(this, MainActivity::class.java).putExtra("perfil", perfil)

    lateinit var perfiles: Perfiles
        private set
    val perfilActivo = MutableStateFlow("1")
    private val alcances = mutableMapOf<String, CoroutineScope>()
    private val almacenes = mutableMapOf<String, DataStore<Estado>>()
    @Synchronized fun almacenPerfil(id: String): DataStore<Estado> = almacenes.getOrPut(id) {
        val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
        alcances[id] = alcance
        crearAlmacen(perfiles.directorio(id), alcance)
    }
    suspend fun cerrarAlmacen(id: String) {
        alcances.remove(id)?.coroutineContext?.get(Job)?.let { it.cancel(); it.join() }
        almacenes.remove(id)
    }
    /** El cuaderno del perfil activo. */
    val almacen get() = almacenPerfil(perfiles.lista.activo)
    fun activarPerfil(id: String) { perfiles.cambiar(id); perfilActivo.value = id }
    val logica = Logica()

    private var confirmarMigracion = false
    /** Tras cargar y guardar bien el perfil 1 en un arranque posterior, el respaldo de la migración se borra. */
    suspend fun confirmarCuaderno() {
        if (!confirmarMigracion || perfiles.lista.activo != "1") return
        almacenPerfil("1").updateData { it }
        migracionPerfiles(this).confirmarArranque()
        confirmarMigracion = false
    }

    override fun onCreate() {
        super.onCreate()
        confirmarMigracion = java.io.File(filesDir, "migracion-perfiles.json").takeIf { it.exists() }?.readText() == "terminada"
        migracionPerfiles(this).ejecutar()
        perfiles = Perfiles(filesDir)
        perfiles.limpiarPendientes { id ->
            SesionApp.preferenciasDe("alcancia", id).forEach { check(getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit()) }
        }
        perfilActivo.value = perfiles.lista.activo
        aplicarIdioma()
        Avisos.programar(this)
    }

    /** Formatos y canal de avisos en el idioma en uso. Se repite al cambiar de idioma. */
    fun aplicarIdioma() {
        val c = Idioma.envolver(this)
        Formato.idioma = Idioma.actual(this)
        Formato.mesesCortos = c.resources.getStringArray(R.array.meses).toList()
        Formato.mesesLargos = c.resources.getStringArray(R.array.meses_largos).toList()
        Avisos.crearCanal(c)
    }
}
