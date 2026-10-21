package io.github.fogonapps.ahorrador

import android.app.Application
import androidx.datastore.core.DataStore
import io.github.fogonapps.ahorrador.datos.Estado
import io.github.fogonapps.ahorrador.datos.Formato
import io.github.fogonapps.ahorrador.datos.Logica
import io.github.fogonapps.ahorrador.datos.crearAlmacen
import io.github.fogonapps.identidad.Idioma

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class AhorradorApp : Application() {
    val almacen: DataStore<Estado> by lazy { crearAlmacen(this) }
    val logica = Logica()

    override fun onCreate() {
        super.onCreate()
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
