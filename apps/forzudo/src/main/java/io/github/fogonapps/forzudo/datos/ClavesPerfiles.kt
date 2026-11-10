package io.github.fogonapps.forzudo.datos

import android.content.Context
import io.github.fogonapps.social.abrirPreferenciasClaves
import io.github.fogonapps.social.copiarPreferenciasClaves

fun migracionPerfiles(contexto: Context): MigracionPerfiles {
    val antiguas = contexto.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
    val destino = contexto.getSharedPreferences("sincronizacion-p1", Context.MODE_PRIVATE)
    return MigracionPerfiles(contexto.filesDir, { copiarPreferenciasClaves(antiguas.all) }, { bytes ->
        val valores = abrirPreferenciasClaves(bytes)
        val editor = destino.edit()
        valores.forEach { (clave, valor) ->
            when (valor) {
                is String -> editor.putString(clave, valor)
                is Boolean -> editor.putBoolean(clave, valor)
                is Int -> editor.putInt(clave, valor)
                is Long -> editor.putLong(clave, valor)
                is Float -> editor.putFloat(clave, valor)
                is Set<*> -> editor.putStringSet(clave, valor.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit())
        check(abrirPreferenciasClaves(copiarPreferenciasClaves(destino.all)) == valores)
        check(antiguas.edit().clear().commit())
    })
}
