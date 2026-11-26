package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Estados
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PerfilSocial
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.forma
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Una obra de la otra persona con lo que se sabe de su opinión: la nota (1 a 10, si la dio) y si es de sus favoritas. */
internal data class SuyaConNota(val obra: ObraParte, val nota: Int?, val favorita: Boolean)

/** Una obra que tienen los dos: la tuya, la suya y qué tanto coinciden (0 a 1). */
internal data class EnComun(val mia: Obra, val suya: SuyaConNota, val parecido: Double)

/**
 * Lo que tienen en común: el porcentaje (null si hay muy poco para decir algo), lo que tienen los dos, los artistas
 * que escuchan los dos y lo que la otra persona quiere mucho y tú no tienes.
 */
internal data class Compatible(val porcentaje: Int?, val enComun: List<EnComun>, val artistas: List<String>, val teGustarian: List<SuyaConNota>) {
    val cuantas: Int get() = enComun.size + artistas.size
}

/**
 * La compatibilidad con otra persona, calculada en el teléfono con lo que esa persona hizo público (su diario, sus
 * reseñas, sus favoritas, lo que está viendo y sus artistas) contra tu colección. No sube nada tuyo ni inventa: con
 * menos de [MINIMO] cosas en común no da porcentaje.
 */
internal object Compatibilidad {
    const val MINIMO = 3

    /** Una nota de 1 a 10 es «le encanta» desde 8 (cuatro estrellas). */
    private const val ENCANTA = 8

    /** Lo suyo, sin repetir: la misma obra en el diario, una reseña y sus favoritas se junta (la nota más alta). */
    fun suyas(p: PerfilSocial): List<SuyaConNota> {
        fun t(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
        fun parte(o: JsonObject) = ObraParte(t(o, "titulo"), t(o, "tipoObra").ifBlank { Tipos.PELICULA }, t(o, "portada"), t(o, "fuente"),
            t(o, "anio").toIntOrNull(), t(o, "creador"), o)
        val todas = buildList {
            p.publicaciones.filter { it.tipo == "diario" || it.tipo == "resena" }.forEach { pub ->
                val d = pub.datos
                if (t(d, "titulo").isBlank()) return@forEach
                val nota = t(d, "nota").toIntOrNull()?.takeIf { it in 1..10 } ?: t(d, "estrellas").toDoubleOrNull()?.let { (it * 2).toInt() }?.takeIf { it in 1..10 }
                add(SuyaConNota(parte(d), nota, false))
            }
            ((p.partes["destacados"] as? JsonObject)?.get("vitrina") as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.filter { t(it, "artista") != "true" && t(it, "titulo").isNotBlank() }?.forEach { add(SuyaConNota(parte(it), null, true)) }
            obrasDe(p.partes["viendo"]).forEach { add(SuyaConNota(it, null, false)) }
        }
        return todas.groupBy { Juntos.clave(it.obra.tipo, it.obra.titulo, it.obra.fuente) }.values.map { l ->
            SuyaConNota(l.firstOrNull { it.obra.fuente.isNotBlank() }?.obra ?: l.first().obra, l.mapNotNull { it.nota }.maxOrNull(), l.any { it.favorita })
        }
    }

    /** Qué tanto coinciden en una obra: con las dos notas, por su distancia; si no, tenerla los dos ya dice algo. */
    fun parecido(mia: Obra, suya: SuyaConNota, misFavoritas: Set<String>): Double {
        val yoEncanta = mia.nota >= ENCANTA || mia.meGusta || mia.id in misFavoritas
        val ellaEncanta = (suya.nota ?: 0) >= ENCANTA || suya.favorita
        return when {
            mia.nota > 0 && suya.nota != null -> 1.0 - kotlin.math.abs(mia.nota - suya.nota) / 9.0
            mia.estado == Estados.ABANDONADO && ellaEncanta -> 0.3
            yoEncanta && ellaEncanta -> 1.0
            yoEncanta || ellaEncanta -> 0.8
            else -> 0.7
        }
    }

    fun calcular(e: Estado, p: PerfilSocial): Compatible {
        val mias = e.vivas
        val misFavoritas = (e.ajustes.favoritas + Logica.vitrina(e)).toSet()
        val suyas = suyas(p)
        val enComun = suyas.mapNotNull { s ->
            val mia = mias.firstOrNull { it.esLaMisma(s.obra) } ?: return@mapNotNull null
            EnComun(mia, s, parecido(mia, s, misFavoritas))
        }.distinctBy { it.mia.id }.sortedWith(compareByDescending<EnComun> { it.parecido }.thenByDescending { maxOf(it.mia.nota, it.suya.nota ?: 0) })
        val misArtistas = e.artistasVivos
        val artistas = ((p.partes["musica"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()).mapNotNull { a ->
            val nombre = (a["nombre"] as? JsonPrimitive)?.content.orEmpty()
            val fuente = (a["fuente"] as? JsonPrimitive)?.content.orEmpty()
            nombre.takeIf { n -> n.isNotBlank() && misArtistas.any { (fuente.isNotBlank() && it.fuente == fuente) || Logica.tituloLimpio(it.nombre) == Logica.tituloLimpio(n) } }
        }.distinct()
        // Lo que le encanta y tú no tienes (ni borraste: lo que quitaste no se vuelve a ofrecer).
        val teGustarian = suyas.filter { s -> ((s.nota ?: 0) >= ENCANTA || s.favorita) && e.obras.none { it.esLaMisma(s.obra) } }
            .sortedWith(compareByDescending<SuyaConNota> { it.favorita }.thenByDescending { it.nota ?: 0 }).take(10)
        val cuantas = enComun.size + artistas.size
        val porcentaje = if (cuantas < MINIMO) null else {
            val promedio = (enComun.map { it.parecido } + artistas.map { 0.85 }).average()
            val cobertura = minOf(1.0, cuantas / 10.0)
            (100 * (0.35 + 0.65 * promedio) * (0.55 + 0.45 * cobertura)).toInt().coerceIn(1, 99)
        }
        return Compatible(porcentaje, enComun, artistas, teGustarian)
    }
}

/**
 * En el perfil de otra persona: «En común contigo» con el porcentaje (o por qué todavía no hay), lo que tienen los dos
 * y lo que esa persona quiere mucho y tú no tienes. Solo lo ve quien mira; no se sube ni se guarda nada.
 */
@Composable
internal fun CompatibilidadConmigo(vm: CajonViewModel, e: Estado, p: PerfilSocial) {
    val f = FogonTema.actual
    val c = remember(e, p) { Compatibilidad.calcular(e, p) }
    if (c.cuantas == 0 && c.teGustarian.isEmpty()) return
    val usuario = p.persona.usuario
    var abierta by rememberSaveable(usuario) { mutableStateOf(false) }
    TituloPerfil(stringResource(R.string.compat_titulo))
    Column(Modifier.fillMaxWidth().clip(forma(12.dp)).clickable(role = Role.Button, onClickLabel = stringResource(if (abierta) R.string.compat_menos else R.string.compat_mas)) { abierta = !abierta }
        .semantics(mergeDescendants = true) {}.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (c.porcentaje != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${c.porcentaje} %", fontFamily = f.titulos, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp, color = f.texto)
                Text(stringResource(R.string.compat_en_comun_con, usuario), style = MaterialTheme.typography.titleSmall, color = f.textoSuave,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(textoCuantas(c), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        } else {
            Text(stringResource(R.string.compat_poco), style = MaterialTheme.typography.bodyMedium, color = f.texto)
            Text(stringResource(R.string.compat_poco_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        }
    }
    if (c.enComun.isNotEmpty()) {
        Text(stringResource(R.string.compat_los_dos), style = MaterialTheme.typography.labelLarge, color = f.textoSuave,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val lista = if (abierta) c.enComun else c.enComun.take(8)
            items(lista.size) { i ->
                val x = lista[i]
                // Su portada o, si no la mandó, la tuya (la obra es la misma).
                PortadaChica(vm, x.suya.obra.copy(portada = x.suya.obra.portada.ifBlank { x.mia.portada })) {
                    x.mia.nota.takeIf { it > 0 }?.let { Text(stringResource(R.string.compat_tu_nota, estrellasTexto(it)), style = MaterialTheme.typography.labelSmall,
                        color = f.textoSuave, maxLines = 1) }
                    x.suya.nota?.let { Text(stringResource(R.string.compat_su_nota, estrellasTexto(it)), style = MaterialTheme.typography.labelSmall, color = f.textoSuave,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (abierta && c.artistas.isNotEmpty()) Text(stringResource(R.string.compat_artistas, c.artistas.take(6).joinToString(", ")),
        style = MaterialTheme.typography.bodySmall, color = f.textoSuave, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    if (c.teGustarian.isNotEmpty()) {
        Text(stringResource(R.string.compat_te_gustarian, usuario), style = MaterialTheme.typography.labelLarge, color = f.textoSuave,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(c.teGustarian.size) { i ->
                val s = c.teGustarian[i]
                PortadaChica(vm, s.obra) {
                    Text(s.nota?.let { estrellasTexto(it) } ?: stringResource(R.string.compat_favorita), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                }
            }
        }
    }
}

@Composable
private fun textoCuantas(c: Compatible): String = when {
    c.artistas.isEmpty() -> androidx.compose.ui.res.pluralStringResource(R.plurals.compat_obras, c.enComun.size, c.enComun.size)
    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.compat_obras, c.enComun.size, c.enComun.size) + " · " +
        androidx.compose.ui.res.pluralStringResource(R.plurals.compat_artistas_n, c.artistas.size, c.artistas.size)
}

/** Una nota de 1 a 10 como estrellas cortas: «★ 4½». */
private fun estrellasTexto(nota: Int): String = "★ " + (if (nota / 2 > 0) "${nota / 2}" else "") + (if (nota % 2 == 1) "½" else "")
