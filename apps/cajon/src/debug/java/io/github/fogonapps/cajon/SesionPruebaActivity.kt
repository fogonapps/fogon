package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Entrada local del tester; este archivo no forma parte de release. */
class SesionPruebaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enlace = intent.data
        val token = enlace?.getQueryParameter("token")
        if (io.github.fogonapps.social.ClienteSocial.SERVIDOR != "http://localhost:8080" ||
            enlace?.scheme != "fogon-debug" || enlace.host != "sesion" || token.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val cliente = io.github.fogonapps.cajon.datos.ClienteSocial(token)
                val cuenta = cliente.miCuenta()
                val app = application as CajonApp
                // Conserva las cuentas del emulador y reutiliza un perfil exclusivo de pruebas.
                val perfil = app.perfiles.lista.perfiles.firstOrNull { it.usuario == cuenta.usuario &&
                    app.recursos(it.id).caja.tokenSocial == token }
                    ?: app.perfiles.agregar(provisional = false)
                    ?: error("No hay espacio para el perfil de pruebas")
                app.perfiles.cambiar(perfil.id)
                app.perfiles.actualizar(perfil.id, cuenta.nombre, cuenta.usuario, "")
                app.perfiles.completarIdCuenta(perfil.id, cuenta.idCuenta)
                // Las insignias se recalculan al sincronizar: el cuaderno ficticio también
                // debe contener los logros, para conservar el nivel de la semilla del servidor.
                val muestras = listOf(Tipos.SERIE to 10, Tipos.ANIME to 10, Tipos.LIBRO to 12).flatMap { (tipo, cantidad) ->
                    List(cantidad) { indice ->
                        Obra(id = "social-prueba-$tipo-$indice", tipo = tipo,
                            titulo = "Muestra ficticia $tipo ${indice + 1}", estado = Estados.TERMINADO,
                            terminada = java.time.LocalDate.now().minusDays((indice + 1).toLong()).toString())
                    }
                }
                app.recursos(perfil.id).almacen.updateData { anterior ->
                    val ids = anterior.obras.map { it.id }.toSet()
                    anterior.copy(listo = true, obras = anterior.obras + muestras.filter { it.id !in ids },
                        ajustes = anterior.ajustes.copy(nombre = cuenta.nombre),
                        perfil = anterior.perfil.copy(usuario = cuenta.usuario,
                            visibilidad = PartesPerfil.AL_CREAR_CUENTA + anterior.perfil.visibilidad))
                }
                app.caja.tokenSocial = token
                app.caja.usuarioSocial = cuenta.usuario
                app.caja.politicaSocial = cliente.info().politica
                app.caja.redPermitida = false
                app.caja.perfilRedPendiente = true
                // El controlador normal valida la cuenta al arrancar. Espera esa validación
                // antes de abrir una pantalla que consulta datos protegidos.
                startActivity(Intent(this@SesionPruebaActivity, MainActivity::class.java))
                kotlinx.coroutines.withTimeout(15_000) {
                    while (!app.caja.redPermitida) kotlinx.coroutines.delay(50)
                }
                val repisas = app.caja.clienteSocial().escaparates(cuenta.usuario)
                android.util.Log.i("SesionPrueba", "Sesión local validada: ${repisas.size} repisas")
                startActivity(Intent(this@SesionPruebaActivity, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(android.net.Uri.parse("https://fogonapps.duckdns.org/@${cuenta.usuario}"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (e: Exception) {
                android.util.Log.e("SesionPrueba", "No se pudo abrir la sesión local", e)
            } finally { finish() }
        }
    }
}
