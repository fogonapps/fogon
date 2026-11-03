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
    val almacen: DataStore<Estado> by lazy { crearAlmacen(this) }
    val caja: CajaFuerte by lazy { CajaFuerte(this) }

    /** Datos fijos y lógica en el idioma en uso. Se vuelven a armar al cambiar de idioma ([aplicarIdioma]). */
    @Volatile lateinit var biblioteca: Biblioteca
        private set
    @Volatile lateinit var logica: Logica
        private set

    override fun onCreate() {
        super.onCreate()
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
