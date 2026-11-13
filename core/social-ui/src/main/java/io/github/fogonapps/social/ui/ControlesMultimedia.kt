package io.github.fogonapps.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.*

internal object IconosMultimedia {
    val Reproducir = dibujo("reproducir", relleno("M8.5 5.6v12.8c0 .8.9 1.3 1.6.8l9.4-6.4c.6-.4.6-1.2 0-1.6l-9.4-6.4c-.7-.5-1.6 0-1.6.8z"))
    val Microfono = dibujo("microfono", relleno("M12 3.2a3 3 0 0 1 3 3v5.6a3 3 0 0 1-6 0V6.2a3 3 0 0 1 3-3z"), trazo("M6.4 11.4a5.6 5.6 0 0 0 11.2 0M12 17v3.6M8.8 20.6h6.4", 2.2f))
    val Sonido = dibujo("sonido", relleno("M3.6 9.2h3.6l4.6-3.8v13.2l-4.6-3.8H3.6z"), trazo("M15.2 9a4.2 4.2 0 0 1 0 6M17.8 6.4a7.8 7.8 0 0 1 0 11.2", 2.2f))
    val Silencio = dibujo("silencio", relleno("M3.6 9.2h3.6l4.6-3.8v13.2l-4.6-3.8H3.6z"), trazo("M15.4 9.4l5 5.2M20.4 9.4l-5 5.2", 2.2f))
    val Pausa = dibujo("pausa", relleno("M7.5 5h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm6 0h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"))
}

