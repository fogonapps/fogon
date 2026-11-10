package io.github.fogonapps.cajon.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.social.ui.PerfilLocal
import io.github.fogonapps.social.ui.PerfilesLocales

@Composable
fun SelectorPerfiles(vm: CajonViewModel) {
    val acciones = LocalAcciones.current
    io.github.fogonapps.social.ui.SelectorPerfiles(
        perfilesTelefono = { vm.perfilesTelefono().let { lista -> PerfilesLocales(lista.perfiles.map {
            PerfilLocal(it.id, it.apodo, it.usuario, it.miniatura)
        }, lista.activo) } },
        cambiarPerfil = { vm.cambiarPerfil(it) },
        agregarPerfil = { vm.agregarPerfil() },
        quitarPerfil = { vm.quitarPerfil() },
        exportar = { acciones?.exportar?.invoke() },
        foto = { FotoPerfil(it, 40.dp) },
    )
}
