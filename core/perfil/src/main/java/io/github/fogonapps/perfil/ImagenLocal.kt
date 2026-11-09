package io.github.fogonapps.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.fogonapps.identidad.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imágenes pequeñas incorporadas al respaldo, sin permisos persistentes ni rutas externas. */
@Composable
fun ImagenLocal(imagen: String, modifier: Modifier) {
    val mapa by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, imagen) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching {
                if (imagen.isBlank()) null else {
                    val bytes = Base64.decode(imagen, Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    mapa?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
}

@Composable
fun FotoPerfilLocal(imagen: String, modifier: Modifier = Modifier, tamano: Dp = 92.dp,
    vacio: @Composable () -> Unit = { IconoRiso(IconosFogon.Perfil, null, tamano = tamano * 0.42f, tinta = FogonTema.actual.acentoTexto) },
) {
    Box(modifier.size(tamano).clip(CircleShape).background(FogonTema.actual.superficie).border(FogonTema.actual.borde, FogonTema.actual.linea, CircleShape), contentAlignment = Alignment.Center) {
        vacio()
        ImagenLocal(imagen, Modifier.fillMaxSize())
    }
}

/** El «+» sobre la foto, igual que en El Cajón. */
@Composable
fun BotonMasFoto(descripcion: String, modifier: Modifier, onClick: () -> Unit) {
    val f = FogonTema.actual
    Box(modifier.offset(x = 10.dp, y = 10.dp).size(48.dp).clip(CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = descripcion; role = Role.Button }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(f.tinta1).border(2.dp, f.fondo, CircleShape), contentAlignment = Alignment.Center) {
            IconoRiso(IconosFogon.Mas, null, tamano = 16.dp, tinta = f.sobreTinta1)
        }
    }
}

