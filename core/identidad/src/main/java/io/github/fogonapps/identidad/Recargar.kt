package io.github.fogonapps.identidad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * X-09: deslizar hacia abajo para recargar (el feed, las recomendaciones). Igual en las cuatro apps. Sin [alRecargar],
 * es una caja normal. La ruedita se ve al menos medio segundo, para que se note que recargó aunque sea rápido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeslizarParaRecargar(alRecargar: (suspend () -> Unit)?, modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    if (alRecargar == null) { Box(modifier, content = contenido); return }
    val f = FogonTema.actual
    val scope = rememberCoroutineScope()
    var recargando by remember { mutableStateOf(false) }
    val estado = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = recargando,
        onRefresh = {
            if (!recargando) scope.launch {
                recargando = true
                try { val minimo = launch { delay(500) }; alRecargar(); minimo.join() } finally { recargando = false }
            }
        },
        modifier = modifier,
        state = estado,
        indicator = {
            PullToRefreshDefaults.Indicator(state = estado, isRefreshing = recargando, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = f.fondo, color = f.tinta1)
        },
        content = contenido,
    )
}
