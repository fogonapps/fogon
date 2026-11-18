package io.github.fogonapps.perfil

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.Tintas
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal val tintasBanner = mapOf(
    "rojo" to (Tintas.Rojo to R.string.banner_color_rojo),
    "amarillo" to (Tintas.Amarillo to R.string.banner_color_amarillo),
    "rosa" to (Tintas.Rosa to R.string.banner_color_rosa),
    "azul" to (Tintas.Azul to R.string.banner_color_azul),
    "verde" to (Tintas.Verde to R.string.banner_color_verde),
    "violeta" to (Tintas.Violeta to R.string.banner_color_violeta),
    "naranja" to (Tintas.Naranja to R.string.banner_color_naranja),
    "cian" to (Tintas.Cian to R.string.banner_color_cian),
)

@Composable
fun menosMovimientoBanner(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val ajuste = Settings.Global.ANIMATOR_DURATION_SCALE
    fun leer() = Settings.Global.getFloat(resolver, ajuste, 1f) == 0f
    var quieto by remember(resolver) { mutableStateOf(leer()) }
    DisposableEffect(resolver) {
        val observador = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { quieto = leer() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(ajuste), false, observador)
        onDispose { resolver.unregisterContentObserver(observador) }
    }
    return FogonTema.actual.menosMovimiento || quieto
}

fun Modifier.visibilidadBanner(cambio: (Boolean) -> Unit): Modifier = onGloballyPositioned {
    val limites = it.boundsInWindow()
    cambio(limites.width > 0f && limites.height > 0f)
}

@Composable
internal fun BannerAnimado(id: String, tinta: Color?, modifier: Modifier) {
    val f = FogonTema.actual
    var visible by remember { mutableStateOf(false) }
    val quieto = menosMovimientoBanner()
    val fase = if (visible && !quieto) {
        rememberInfiniteTransition(label = "banner").animateFloat(
            0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "fase",
        )
    } else null
    val puntos = remember(id) {
        val azar = Random(id.hashCode())
        List(88) { Triple(azar.nextFloat(), azar.nextFloat(), azar.nextFloat()) }
    }
    val color = tinta ?: when (id) {
        "deriva" -> Tintas.Rosa
        "ascenso" -> Tintas.Naranja
        "estelar" -> Tintas.Amarillo
        "olas" -> Tintas.Cian
        else -> Tintas.Violeta
    }
    Canvas(modifier.clipToBounds().visibilidadBanner { visible = it }) {
        drawRect(f.fondo)
        val t = fase?.value ?: 0f
        val vuelta = (2 * PI).toFloat()
        puntos.forEachIndexed { i, (x, y, semilla) ->
            val pulso = sin(vuelta * t + semilla * vuelta)
            val radio = (1.1f + semilla * 2.4f).dp.toPx()
            val centro = when (id) {
                "deriva" -> Offset(((x + t) % 1f) * size.width, (y + pulso * 0.06f) * size.height)
                "ascenso" -> Offset((x + pulso * 0.023f) * size.width, ((1 + y - t) % 1f) * size.height)
                "olas" -> Offset((i % 22 + 0.5f) / 22f * size.width,
                    ((i / 22 + 0.65f) / 5f + sin(vuelta * (t + i % 22 / 22f)) * 0.11f) * size.height)
                "lluvia" -> Offset(x * size.width, ((y + t * 2) % 1f) * size.height)
                else -> Offset(x * size.width, y * size.height)
            }
            val opacidad = if (id == "estelar") 0.55f + 0.32f * pulso else 0.45f + 0.45f * semilla
            val marca = color.copy(alpha = opacidad)
            if (id == "lluvia") drawLine(marca, centro, centro + Offset(0f, radio * 3), radio)
            else drawCircle(marca, radio, centro)
        }
    }
}
