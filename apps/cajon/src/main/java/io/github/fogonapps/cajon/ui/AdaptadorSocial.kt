package io.github.fogonapps.cajon.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.Pantalla
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.PerfilSocial
import io.github.fogonapps.cajon.datos.PublicacionSocial
import io.github.fogonapps.social.ui.*

@Composable
internal fun interfazSocial(vm: CajonViewModel, motivo: io.github.fogonapps.cajon.RedSocial.Companion.Motivo? = null): InterfazSocial {
    val confirmar = LocalConfirmar.current
    val textos = TextosSocialApp(
        introduccion = androidx.compose.ui.res.stringResource(R.string.red_que_es),
        contenido = androidx.compose.ui.res.stringResource(R.string.red_que_sube),
        compartido = androidx.compose.ui.res.stringResource(R.string.red_que_se_comparte),
        recuperacion = androidx.compose.ui.res.stringResource(R.string.red_olvido_aviso),
        borradoExplicacion = androidx.compose.ui.res.stringResource(R.string.red_borrado_explicacion),
        borradoSeguro = androidx.compose.ui.res.stringResource(R.string.red_borrar_cuenta_seguro),
        sinClave = androidx.compose.ui.res.stringResource(R.string.red_sin_clave_crea),
        pausaPolitica = androidx.compose.ui.res.stringResource(R.string.politica_pausa),
        resumenPolitica = androidx.compose.ui.res.stringResource(R.string.politica_resumen),
        explicacionOrden = androidx.compose.ui.res.stringResource(R.string.menu_por_que_explica),
    )
    return object : InterfazSocial {
        override fun adjuntosApp() = vm.adjuntosHistoria()
        override suspend fun completarAdjunto(a: kotlinx.serialization.json.JsonObject) = vm.completarAdjunto(a)
        override val textosApp = textos
        override val red = vm.red
        override val navegacion = NavegacionSocial(
            perfil = { vm.abrir(Pantalla.PerfilRed(it)) },
            publicacion = { vm.abrir(Pantalla.PublicacionRed(it)) },
            etiqueta = { vm.abrir(Pantalla.EtiquetaRed(it)) },
            cuenta = { vm.abrir(Pantalla.CuentaRed) },
            ajustes = { vm.abrir(when (it) {
                AjusteSocial.SESIONES -> Pantalla.Sesiones
                AjusteSocial.ESTADO_CUENTA -> Pantalla.EstadoCuenta
                AjusteSocial.BLOQUEADOS -> Pantalla.BloqueadosRed
                AjusteSocial.BORRAR_CUENTA -> Pantalla.BorrarCuenta
            }) },
            adjunto = { vm.abrir(Pantalla.Publicar(it.id)) },
            volver = vm::cerrar,
            gente = { usuario, seguidores -> vm.abrir(Pantalla.GenteRed(usuario, seguidores)) },
        )
        override val contenido = object : ContenidoSocialApp {
            override fun multimediaEnCuerpo(tipo: String) = tipo == "resena"
            override val tiposAdmitidos = setOf("diario", "resena", "lista", "foto")
            @Composable override fun Compositor(tipo: String, adjunto: AdjuntoSocial?) {
                vm.estado.collectAsStateWithLifecycle().value?.let { Publicar(vm, it, adjunto?.id) }
            }
            @Composable override fun NombreTipo(tipo: String): String = androidx.compose.ui.res.stringResource(when (tipo) {
                "resena" -> R.string.red_tipo_resena
                "diario" -> R.string.red_tipo_diario
                "lista" -> R.string.red_tipo_lista
                else -> R.string.red_tipo_publico
            })
            @Composable override fun Cuerpo(publicacion: PublicacionSocial, destapado: Boolean) = CuerpoPublicacionCajon(vm, publicacion, destapado)
            @Composable override fun SeccionesPerfil(perfil: PerfilSocial, propio: Boolean, contenido: @Composable (Int) -> Unit) = seccionesCajon(propio, contenido)
            @Composable override fun CifrasPerfil(perfil: PerfilSocial) = CifrasDeLaRed(perfil)
        }
        override val confirmar = confirmar
        override fun tx(recurso: Int) = vm.tx(recurso)
        override val correoDisponible = vm.red.correoDisponible
        @Composable override fun palabrasOcultas() = vm.estado.collectAsStateWithLifecycle().value?.ajustes?.palabrasOcultas.orEmpty()
        @Composable override fun cantidadModeracionApp() = vm.red.frasesPendientes.collectAsStateWithLifecycle().value.size
        @Composable override fun tituloModeracionApp() = androidx.compose.ui.res.stringResource(R.string.mod_frases_saludo)
        override fun crearConCorreo(usuario: String, nombre: String, clave: String, foto: String?) { vm.red.crearConCorreo(usuario, nombre, clave, foto) }
        override fun ponerClave(nueva: String, actual: String, listo: () -> Unit) { vm.red.ponerClave(nueva, actual, listo) }
        override fun cambiarUsuario(nuevo: String, listo: () -> Unit) { vm.red.cambiarUsuario(nuevo, listo) }
        override fun vincularCorreo(codigo: String) { vm.red.vincularCorreo(codigo) }
        override fun vista(id: Long) { vm.red.vista(id) }
        override fun borrarPublicacion(id: Long, clave: String) { vm.red.borrarPublicacion(id, clave) }
        override fun quitarDeLaRed(clave: String) { vm.red.quitarDeLaRed(clave) }
        override fun mostrarComentario(palabras: List<String>, texto: String) = !lleva(palabras, texto)
        override fun interesa(publicacion: PublicacionSocial, si: Boolean) { vm.red.interesa(publicacion, si) }
        override fun silenciar(usuario: String) { vm.red.silenciar(usuario) }
        override fun salir() { vm.red.salir() }
        override fun cabeceras() = vm.red.cabecerasVideo()
        @Composable override fun menu(publicacion: PublicacionSocial, cerrar: () -> Unit) = MenuPublicacion(vm, publicacion, motivo, cerrar)
        @Composable override fun moderacionApp() { FrasesPropuestas(vm); FrasesDelEquipo(vm) }
        @Composable override fun contenidoReporte(contenido: String) = VistaContenidoReporte(vm, contenido)
    }
}
