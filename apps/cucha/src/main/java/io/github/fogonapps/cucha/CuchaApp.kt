package io.github.fogonapps.cucha

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.cucha.datos.Base
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Logica
import io.github.fogonapps.cucha.datos.crearAlmacen
import io.github.fogonapps.cucha.datos.migracionPerfiles
import io.github.fogonapps.social.Perfiles
import io.github.fogonapps.social.SesionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. Cada perfil del teléfono tiene su cuaderno. */
class CuchaApp : Application() {
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
    /** El cuaderno del perfil activo (widget, respaldo de todo Fogón). */
    val almacen get() = almacenPerfil(perfiles.lista.activo)
    fun activarPerfil(id: String) { perfiles.cambiar(id); perfilActivo.value = id }

    /** La base de alimentos y las recetas de ejemplo; se leen una vez, la primera vez que hacen falta. */
    val logica: Logica by lazy {
        fun leer(n: String) = assets.open("datos/$n").bufferedReader().use { it.readText() }
        Logica(Base.leer(leer("alimentos.json"), leer("recetas.json")))
    }

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
            SesionApp.preferenciasDe("cucha", id).forEach { check(getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit()) }
        }
        perfilActivo.value = perfiles.lista.activo
    }
}
