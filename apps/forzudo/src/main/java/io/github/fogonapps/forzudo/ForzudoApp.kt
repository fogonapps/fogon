package io.github.fogonapps.forzudo

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.forzudo.datos.Biblioteca
import io.github.fogonapps.forzudo.datos.CajaFuerte
import io.github.fogonapps.forzudo.datos.Estado
import io.github.fogonapps.forzudo.datos.crearAlmacen

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class ForzudoApp : Application() {
    val almacen: DataStore<Estado> by lazy { crearAlmacen(this) }
    val caja: CajaFuerte by lazy { CajaFuerte(this) }
    val biblioteca: Biblioteca by lazy {
        Biblioteca.cargar { nombre -> assets.open("datos/$nombre").bufferedReader().use { it.readText() } }
    }

    override fun onCreate() {
        super.onCreate()
        Avisos.crearCanal(this)
    }
}
