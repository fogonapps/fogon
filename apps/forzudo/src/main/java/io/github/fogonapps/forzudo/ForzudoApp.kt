package io.github.fogonapps.forzudo

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.CajaFuerte
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.Logica
import io.github.fogonapps.forzudo.datos.crearAlmacen
import io.github.fogonapps.identidad.Idioma

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class ForzudoApp : Application() {
    lateinit var perfiles: io.github.fogonapps.social.Perfiles
        private set
    val perfilActivo = kotlinx.coroutines.flow.MutableStateFlow("1")
    private val alcances = mutableMapOf<String, kotlinx.coroutines.CoroutineScope>()
    private val almacenes = mutableMapOf<String, DataStore<Estado>>()
    @Synchronized fun almacenPerfil(id: String): DataStore<Estado> = almacenes.getOrPut(id) {
        val alcance = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        alcances[id] = alcance
        crearAlmacen(this, perfiles.directorio(id), alcance)
    }
    suspend fun cerrarAlmacen(id: String) {
        alcances.remove(id)?.coroutineContext?.get(kotlinx.coroutines.Job)?.let { it.cancel(); it.join() }
        almacenes.remove(id)
    }
    val almacen get() = almacenPerfil(perfiles.lista.activo)
    val caja get() = CajaFuerte(this, perfiles.lista.activo)
    fun activarPerfil(id: String) { perfiles.cambiar(id); perfilActivo.value = id }


    /** Datos fijos y lógica en el idioma en uso. Se vuelven a armar al cambiar de idioma ([aplicarIdioma]). */
    @Volatile lateinit var biblioteca: Biblioteca
        private set
    @Volatile lateinit var logica: Logica
        private set

    private var confirmarMigracion = false
    suspend fun confirmarCuaderno() {
        if (!confirmarMigracion || perfiles.lista.activo != "1") return
        val almacen = almacenPerfil("1")
        almacen.updateData { it }
        io.github.fogonapps.forzudo.datos.migracionPerfiles(this).confirmarArranque()
        confirmarMigracion = false
    }
    override fun onCreate() {
        super.onCreate()
        confirmarMigracion = java.io.File(filesDir, "migracion-perfiles.json").takeIf { it.exists() }?.readText() == "terminada"
        io.github.fogonapps.forzudo.datos.migracionPerfiles(this).ejecutar()
        perfiles = io.github.fogonapps.social.Perfiles(filesDir)
        perfiles.limpiarPendientes { id ->
            listOf("sincronizacion-p$id", "sincronizacion-psocial-$id", "social-p$id").forEach {
                check(getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit())
            }
        }
        perfilActivo.value = perfiles.lista.activo
        aplicarIdioma()
    }

    fun aplicarIdioma() {
        val c = Idioma.envolver(this)
        Textos.aplicar(c, Idioma.actual(this))
        biblioteca = Biblioteca.cargar(Idioma.actual(this)) { nombre -> assets.open("datos/$nombre").bufferedReader().use { it.readText() } }
        logica = Logica(biblioteca)
        Avisos.crearCanal(c)
    }
}
