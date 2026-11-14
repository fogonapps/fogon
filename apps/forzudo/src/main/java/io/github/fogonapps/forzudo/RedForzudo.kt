package io.github.fogonapps.forzudo

import io.github.fogonapps.social.*
import kotlinx.coroutines.CoroutineScope

/** Cuenta y publicaciones explícitas de entrenos, sin transporte de chat: la red común de `core/social` con lo de El Forzudo. */
class RedForzudo(
    app: ForzudoApp,
    almacen: SesionApp,
    scope: CoroutineScope,
    mensaje: (Int) -> Unit,
    cambiarPerfil: (String) -> Unit,
) : RedApp(app.perfiles, almacen, scope, ConfiguracionSocial("forzudo", "El Forzudo", "ElForzudo-Fogon"),
    TextosRed(R.string.cuenta_error, R.string.cuenta_perfil_cambio, R.string.cuenta_operacion_lista, R.string.cuenta_otro_perfil, R.string.cuenta_muchas_cuentas),
    mensaje, cambiarPerfil, { io.github.fogonapps.forzudo.datos.cabeInstantaneaEntreno(it) }, "entrenos-publicados.json",
    listOf("rangos", "medallas")) {
    fun publicarEntreno(publicacion: PublicacionSubida, listo: () -> Unit) = publicar(publicacion, listo)
    fun borrarEntreno(id: Long) = borrar(id)
    fun retirarEntreno(clave: String, id: Long? = null) = retirar(clave, id)
}

/** La sesión de un perfil de El Forzudo: el token sigue en su caja de antes (`sincronizacion-psocial-<perfil>`, llave `forzudo-token`). */
fun sesionForzudo(contexto: android.content.Context, perfiles: Perfiles, perfil: String) =
    SesionApp(contexto, perfiles, perfil, "forzudo", "sincronizacion-psocial-$perfil", "forzudo-token")
