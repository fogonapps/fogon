package io.github.fogonapps.cajon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fogonapps.cajon.CajonViewModel
import io.github.fogonapps.cajon.R
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.Catalogo
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.Registro
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.fogonapps.identidad.AlmacenApariencia
import io.github.fogonapps.identidad.BotonRiso
import io.github.fogonapps.identidad.BotonSecundario
import io.github.fogonapps.identidad.FogonTema
import io.github.fogonapps.identidad.IconoRiso
import io.github.fogonapps.identidad.Paleta
import io.github.fogonapps.identidad.PasoDaleVida
import io.github.fogonapps.identidad.Personaje
import io.github.fogonapps.identidad.aparecer
import io.github.fogonapps.identidad.tarjetaFogon
import io.github.fogonapps.identidad.R as RId

/** Primera vez: bienvenida, el nombre, cómo se usa (los gestos que no se ven) y, al final, "¡Dale vida!" para elegir el estilo. */
@Composable
fun Tutorial(vm: CajonViewModel, acciones: Acciones) {
    val f = FogonTema.actual
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var nombre by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (paso) {
            0 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Personaje(io.github.fogonapps.identidad.Quien.CAJON, null, Modifier.align(Alignment.CenterHorizontally), cara = io.github.fogonapps.identidad.Cara.FELIZ, tamano = 116.dp)
                Text(stringResource(R.string.tut_hola), style = MaterialTheme.typography.headlineLarge, color = f.texto,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tut_hola_texto), style = MaterialTheme.typography.bodyLarge, color = f.textoSuave,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // Lo que cabe en el cajón.
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Tipos.TODOS.forEachIndexed { i, t ->
                        Column(Modifier.aparecer(i), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconoRiso(Iconos.de(t), null, tamano = 26.dp, tinta = f.acentoTexto)
                            Text(nombreTipo(t), style = MaterialTheme.typography.labelSmall, color = f.textoSuave)
                        }
                    }
                }
                BotonRiso(stringResource(R.string.siguiente), { paso = 1 }, Modifier.fillMaxWidth())
                BotonSecundario(stringResource(R.string.tut_tengo_respaldo), { paso = 5 }, Modifier.fillMaxWidth())
            }
            1 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.tut_nombre), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                CampoTexto(nombre, { nombre = it }, stringResource(R.string.tu_nombre))
                Text(stringResource(R.string.tut_privado), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave)
                BotonRiso(stringResource(R.string.siguiente), { vm.cambiarNombre(nombre); paso = 2 }, Modifier.fillMaxWidth())
            }
            // Lo que te gusta: obras y artistas concretos (con eso el cajón no arranca vacío). Al menos 3.
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val e by vm.estado.collectAsStateWithLifecycle()
                Text(stringResource(R.string.cuentame_gustos), style = MaterialTheme.typography.headlineSmall, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp))
                Text(stringResource(R.string.gustos_elige_minimo), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp))
                e?.let { estado ->
                    FilasDeGustos(vm, estado)
                    val n = elegidasIniciales(estado)
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        BotonRiso(if (Catalogo.minimoCumplido(n)) stringResource(R.string.siguiente) else stringResource(R.string.gustos_faltan, 3 - n), { vm.gustosListos(); vm.agregarIniciales(); paso = 3 }, Modifier.fillMaxWidth(), habilitado = Catalogo.minimoCumplido(n))
                    }
                }
            }
            // Lo que se puede hacer: lo más interesante de la app, en cinco líneas.
            3 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.tut_funciones), style = MaterialTheme.typography.headlineSmall, color = f.texto)
                listOf(
                    Iconos.Cajon to R.string.tut_funcion_cajon, Iconos.Gente to R.string.tut_funcion_red, Iconos.Racha to R.string.tut_funcion_momento,
                    Iconos.Cifras to R.string.tut_funcion_cifras, Iconos.Compartir to R.string.tut_funcion_compartir,
                ).forEachIndexed { i, (icono, texto) ->
                    Row(Modifier.aparecer(i).fillMaxWidth().tarjetaFogon().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Ficha(f.tinta1, icono)
                        Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge, color = f.texto, modifier = Modifier.weight(1f))
                    }
                }
                // Cómo se ve, con obras de verdad: sus portadas y estrellas, una entrada del diario y una reseña.
                val peli = Obra("muestra-peli", Tipos.PELICULA, stringResource(R.string.tut_muestra_peli), anio = 2014, nota = 9, meGusta = true,
                    portada = "https://image.tmdb.org/t/p/w342/d1QKiYtceF3GDtxvTFXFAqwwah9.jpg")
                val anime = Obra("muestra-anime", Tipos.ANIME, stringResource(R.string.tut_muestra_anime), anio = 2001, nota = 10, meGusta = true,
                    portada = "https://image.tmdb.org/t/p/w342/2RcxjDykOssx4SfqshewyI9vfSl.jpg")
                val libro = Obra("muestra-libro", Tipos.LIBRO, stringResource(R.string.tut_muestra_libro), anio = 1967, nota = 8,
                    portada = "https://covers.openlibrary.org/b/id/13009946-M.jpg")
                val descripcion = stringResource(R.string.tut_muestra_descripcion)
                Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = descripcion }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(peli, anime, libro).forEach { o ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                coil3.compose.AsyncImage(o.portada, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(io.github.fogonapps.identidad.forma(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                Text(o.titulo, style = MaterialTheme.typography.labelMedium, color = f.texto, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Estrellas(o.nota ?: 0)
                            }
                        }
                    }
                    EntradaDiario(peli, Registro("muestra", vm.hoy().toString(), nota = 9))
                    TarjetaResena(stringResource(R.string.tut_muestra_resena), 10)
                }
                BotonRiso(stringResource(R.string.siguiente), { paso = 4 }, Modifier.fillMaxWidth())
            }
            4 -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PasoDaleVida(Paleta.Cajon, AlmacenApariencia.de(LocalContext.current))
                BotonRiso(stringResource(R.string.siguiente), { paso = 5 }, Modifier.fillMaxWidth())
            }
            // Al final, la cuenta con tu correo (para guardar tu cajón); se puede dejar para después.
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val e by vm.estado.collectAsStateWithLifecycle()
                val usuario by vm.red.usuario.collectAsStateWithLifecycle()
                LaunchedEffect(usuario) { if (usuario != null) vm.terminarTutorial(nombre) }
                Text(stringResource(R.string.tut_cuenta_titulo), style = MaterialTheme.typography.headlineSmall, color = f.texto, modifier = Modifier.padding(horizontal = 24.dp))
                Text(stringResource(R.string.tut_cuenta_texto), style = MaterialTheme.typography.bodyMedium, color = f.textoSuave, modifier = Modifier.padding(horizontal = 24.dp))
                LaunchedEffect(Unit) { vm.red.revisarServidor() }
                e?.let { EntrarRed(vm, it, conIntro = false) }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    BotonSecundario(stringResource(R.string.ahora_no), { vm.terminarTutorial(nombre) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

