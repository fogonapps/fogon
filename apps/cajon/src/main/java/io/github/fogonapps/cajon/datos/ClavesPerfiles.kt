package io.github.fogonapps.cajon.datos

import android.content.Context

/** Copia los valores cifrados y sus IV sin abrirlos ni crear otra llave del Keystore. */
fun migracionPerfiles(contexto: Context): MigracionPerfiles {
    val prefs = contexto.getSharedPreferences("claves", Context.MODE_PRIVATE)
    return MigracionPerfiles(contexto.filesDir, { copiarPreferenciasClaves(prefs.all) }, { bytes ->
        val editor = prefs.edit()
        abrirPreferenciasClaves(bytes).forEach { (clave, valor) ->
            val nueva = "p1.$clave"
            if (!prefs.contains(nueva)) {
                when (valor) {
                    is String -> editor.putString(nueva, valor)
                    is Boolean -> editor.putBoolean(nueva, valor)
                    is Int -> editor.putInt(nueva, valor)
                    is Long -> editor.putLong(nueva, valor)
                    is Float -> editor.putFloat(nueva, valor)
                    is Set<*> -> editor.putStringSet(nueva, valor.filterIsInstance<String>().toSet())
                    else -> error("Tipo de preferencia desconocido")
                }
            }
            editor.remove(clave)
        }
        check(editor.commit()) { "No se pudieron migrar las claves" }
    })
}

/** Quitar un perfil nunca borra la llave compartida del Keystore. */
fun borrarClavesPerfil(contexto: Context, id: String) {
    for (nombre in listOf("claves", "avisos_red")) {
        val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("p$id.") }.forEach { editor.remove(it) }
        check(editor.commit())
    }
}
