package io.github.fogonapps.forzudo.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.forzudo.*
import io.github.fogonapps.forzudo.R
import io.github.fogonapps.perfil.SeccionPerfil
import io.github.fogonapps.perfil.FotoPerfilLocal
import io.github.fogonapps.social.*
import io.github.fogonapps.social.ui.*

@Composable
fun interfazCuenta(vm: GymViewModel): InterfazSocial {
    val confirmar = LocalConfirmar.current
    val explicacion = stringResource(R.string.cuenta_compartido)
    val local = stringResource(R.string.cuenta_local)
    val borrado = stringResource(R.string.cuenta_borrado)
    val intro = stringResource(R.string.cuenta_intro)
    val queSale = stringResource(R.string.cuenta_que_sale)
    val pausa = stringResource(R.string.cuenta_politica_pausa)
    val resumen = stringResource(R.string.cuenta_politica_resumen)
    val interfaz = remember(vm, confirmar, explicacion, local, borrado, intro, queSale, pausa, resumen) {
        object : InterfazSocial {
            override val red = vm.red
            override val correoDisponible = red.correoDisponible
            override val textosApp = TextosSocialApp(
                introduccion = intro, contenido = queSale, compartido = explicacion, recuperacion = local,
                borradoExplicacion = borrado, borradoSeguro = borrado, sinClave = local,
                pausaPolitica = pausa, resumenPolitica = resumen, explicacionOrden = "",
            )
            override val confirmar = confirmar
            override val navegacion = NavegacionSocial(
                perfil = { vm.abrir(Pantalla.Social("perfil", usuario = it)) }, publicacion = { vm.abrir(Pantalla.Social("publicacion", id = it)) }, etiqueta = { vm.abrir(Pantalla.Social("etiqueta", usuario = it)) },
                cuenta = { vm.abrir(Pantalla.Cuenta) }, ajustes = { vm.abrir(Pantalla.Seguridad(it)) },
                adjunto = {}, volver = vm::cerrar, gente = { u, seguidores -> vm.abrir(Pantalla.Gente(u, seguidores)) },
            )
            override val contenido = object : ContenidoSocialApp {
                override val tiposAdmitidos = setOf("entreno")
                override fun multimediaEnCuerpo(tipo: String) = tipo == "entreno"
                @Composable override fun Compositor(tipo: String, adjunto: AdjuntoSocial?) {}
                @Composable override fun NombreTipo(tipo: String) = stringResource(R.string.titulo_entrenos)
                @Composable override fun Cuerpo(publicacion: PublicacionSocial, destapado: Boolean) { CuerpoEntreno(publicacion.datos); MultimediaEntreno(vm, publicacion.datos) }
                @Composable override fun SeccionesPerfil(perfil: PerfilSocial, propio: Boolean, contenido: @Composable (Int) -> Unit) =
                    listOf(SeccionPerfil(0, Iconos.Pesa, stringResource(R.string.titulo_entrenos)) { contenido(0) })
                @Composable override fun CifrasPerfil(perfil: PerfilSocial) {}
            }
            override fun tx(recurso: Int) = vm.tx(recurso)
            @Composable override fun palabrasOcultas() = emptyList<String>()
            @Composable override fun cantidadModeracionApp() = 0
            @Composable override fun tituloModeracionApp() = ""
            override fun salir() { red.salir() }
            override fun interesa(publicacion: PublicacionSocial, si: Boolean) { if (!si) red.ocultar(publicacion.id) }
            override fun silenciar(usuario: String) {
                (red.feed.value.orEmpty() + red.explorar.value.orEmpty()).filter { it.autor.usuario == usuario }.forEach { red.ocultar(it.id) }
            }
            override fun mostrarComentario(palabras: List<String>, texto: String) = true
            override fun crearConCorreo(usuario: String, nombre: String, clave: String, foto: String?) {
                val bytes = foto?.let { android.util.Base64.decode(it.substringAfter(','), android.util.Base64.DEFAULT) }
                red.crear(usuario, nombre, clave, bytes)
            }
            override fun ponerClave(nueva: String, actual: String, listo: () -> Unit) { red.ponerClave(nueva, actual, listo) }
            override fun cambiarUsuario(nuevo: String, listo: () -> Unit) { red.cambiarUsuario(nuevo, listo) }
            override fun vincularCorreo(codigo: String) { red.vincular(codigo) }
            override fun vista(id: Long) { red.vistaPublicacion(id) }
            override fun borrarPublicacion(id: Long, clave: String) { red.borrarEntreno(id) }
            override fun quitarDeLaRed(clave: String) { red.retirarEntreno(clave) }
            override fun cabeceras() = red.cabecerasPublicacion()
            @Composable override fun menu(publicacion: PublicacionSocial, cerrar: () -> Unit) { MenuEntreno(vm, this, publicacion, cerrar) }
            @Composable override fun moderacionApp() {}
            @Composable override fun contenidoReporte(contenido: String) { ReporteEntreno(vm, contenido) }
        }
    }
    return interfaz
}

@Composable
fun Cuenta(vm: GymViewModel, exportar: () -> Unit) {
    val interfaz = interfazCuenta(vm)
    val estado by vm.estado.collectAsState()
    CuentaRed(interfaz, "", estado?.nombre.orEmpty())
    CopiaCuenta(vm.red)
    // El mismo aire que entre los demás botones de la cuenta (antes quedaba pegado a «Usar otro correo»).
    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
    CambiarDeCuenta(vm, exportar)
}

/** «Cambiar de cuenta»: los perfiles de este teléfono, cada uno con su cuaderno. */
@Composable
fun CambiarDeCuenta(vm: GymViewModel, exportar: () -> Unit) {
    SelectorPerfiles(
        perfilesTelefono = {
            val lista = vm.perfiles.lista
            PerfilesLocales(lista.perfiles.map { PerfilLocal(it.id, it.apodo, it.usuario, it.miniatura) }, lista.activo)
        }, cambiarPerfil = vm::cambiarPerfil, agregarPerfil = vm::agregarPerfil,
        quitarPerfil = vm::retirarPerfil, exportar = exportar,
        foto = { FotoPerfilLocal(it) },
    )
}

@Composable
fun SeguridadCuenta(vm: GymViewModel, ajuste: AjusteSocial) {
    val interfaz = interfazCuenta(vm)
    when (ajuste) {
        AjusteSocial.SESIONES -> Sesiones(interfaz)
        AjusteSocial.ESTADO_CUENTA -> EstadoCuenta(interfaz)
        AjusteSocial.BLOQUEADOS -> BloqueadosRed(interfaz)
        AjusteSocial.BORRAR_CUENTA -> FormularioBorrado(interfaz, vm::cerrar)
    }
}

@Composable
fun TransparenciaForzudo(vm: GymViewModel) {
    val sync by vm.sync.collectAsState()
    val usuario by vm.red.usuario.collectAsState()
    Transparencia(vm::cerrar, stringResource(R.string.cuenta_local), stringResource(R.string.cuenta_compartido),
        buildList {
            if (usuario != null) add(stringResource(R.string.cuenta_servicio_social))
            if (sync.estado != EstadoSync.APAGADA) add(stringResource(R.string.cuenta_servicio_gist))
        }, stringResource(R.string.cuenta_borrado), emptyList())
}

@Composable
fun PoliticaCuenta(vm: GymViewModel) {
    PoliticaPrivacidad(interfazCuenta(vm))
    val gracia by vm.red.gracia.collectAsState()
    gracia?.let { GraciaBorrado(it.borrarEl, vm.red::decidirGracia) }
}
