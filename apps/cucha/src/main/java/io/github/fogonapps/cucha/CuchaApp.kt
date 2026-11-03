package io.github.fogonapps.cucha

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.cucha.datos.Base
import io.github.fogonapps.cucha.datos.Estado
import io.github.fogonapps.cucha.datos.Logica
import io.github.fogonapps.cucha.datos.crearAlmacen

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class CuchaApp : Application() {
    val almacen: DataStore<Estado> by lazy { crearAlmacen(this) }

    /** La base de alimentos y las recetas de ejemplo; se leen una vez, la primera vez que hacen falta. */
    val logica: Logica by lazy {
        fun leer(n: String) = assets.open("datos/$n").bufferedReader().use { it.readText() }
        Logica(Base.leer(leer("alimentos.json"), leer("recetas.json")))
    }
}
