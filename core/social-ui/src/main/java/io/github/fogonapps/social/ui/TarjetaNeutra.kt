package io.github.fogonapps.social.ui
import io.github.fogonapps.identidad.IconosSociales as Iconos
import io.github.fogonapps.identidad.PildoraSocial as Pildora

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.social.ClienteSocial
import io.github.fogonapps.social.PublicacionSocial
import kotlinx.serialization.json.JsonPrimitive

/** Un tipo ajeno nunca se interpreta como una obra del catálogo de la app. */
@Composable
internal fun TarjetaNeutra(publicacion: PublicacionSocial) {
    val uri = LocalUriHandler.current
    val origen = (publicacion.datos["origen"] as? JsonPrimitive)?.content.orEmpty().ifBlank { ClienteSocial.SERVIDOR }
    Column {
        if (origen.isNotBlank()) Text(origen, color = FogonTema.actual.textoSuave)
        Pildora(stringResource(R.string.social_red_publicacion), Iconos.Enlace) {
            uri.openUri(ClienteSocial.enlacePublicacion(publicacion.id))
        }
    }
}
