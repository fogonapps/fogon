package io.github.fogonapps.cucha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fogonapps.cucha.CuchaViewModel
import io.github.fogonapps.cucha.Pantalla
import io.github.fogonapps.cucha.R
import io.github.fogonapps.cucha.datos.*
import io.github.fogonapps.identidad.*
import io.github.fogonapps.perfil.FotoPerfilLocal
import io.github.fogonapps.perfil.SeccionPerfil
import io.github.fogonapps.social.*
import io.github.fogonapps.social.ui.*
import kotlinx.serialization.json.*

/*
 * La red de La Cucha (CU-01), como El Forzudo: cuenta, «Tu gente», perfiles ajenos y tarjetas de plato,
 * receta y reseña de restaurante. Sin chat (D-030). Las pantallas comunes vienen de core/social-ui.
 */

@Composable
fun interfazCuenta(vm: CuchaViewModel): InterfazSocial {
    val confirmar = LocalConfirmar.current
    val explicacion = stringResource(R.string.cuenta_compartido)
    val local = stringResource(R.string.cuenta_local)
    val borrado = stringResource(R.string.cuenta_borrado)
    val intro = stringResource(R.string.cuenta_intro)
    val queSale = stringResource(R.string.cuenta_que_sale)
    val pausa = stringResource(R.string.cuenta_politica_pausa)
    val resumen = stringResource(R.string.cuenta_politica_resumen)
    val nombreTipos = mapOf(TIPO_PLATO to stringResource(R.string.red_tipo_plato), TIPO_RECETA to stringResource(R.string.red_tipo_receta),
        TIPO_RESENA to stringResource(R.string.red_tipo_resena))
    return remember(vm, confirmar, explicacion, local, borrado, intro, queSale, pausa, resumen, nombreTipos) {
        object : InterfazSocial {
            override val red = vm.red
            override val correoDisponible = red.correoDisponible
            override val textosApp = TextosSocialApp(
                introduccion = intro, contenido = queSale, compartido = explicacion, recuperacion = local,
                borradoExplicacion = borrado, borradoSeguro = borrado, sinClave = local,
                pausaPolitica = pausa, resumenPolitica = resumen, explicacionOrden = "",
            )
            override val confirmar: (String, String, () -> Unit) -> Unit = { t, x, si -> confirmar(t, x, si) }
            override val navegacion = NavegacionSocial(
                perfil = { vm.abrir(Pantalla.Social("perfil", usuario = it)) }, publicacion = { vm.abrir(Pantalla.Social("publicacion", id = it)) },
                etiqueta = { vm.abrir(Pantalla.Social("etiqueta", usuario = it)) },
                cuenta = { vm.abrir(Pantalla.Cuenta) }, ajustes = { vm.abrir(Pantalla.Seguridad(it)) },
                adjunto = {}, volver = vm::cerrar, gente = { u, seguidores -> vm.abrir(Pantalla.Gente(u, seguidores)) },
            )
            override val contenido = object : ContenidoSocialApp {
                override val tiposAdmitidos = TIPOS_CUCHA
                override fun multimediaEnCuerpo(tipo: String) = tipo in TIPOS_CUCHA
                @Composable override fun Compositor(tipo: String, adjunto: AdjuntoSocial?) {}
                @Composable override fun NombreTipo(tipo: String) = nombreTipos[tipo].orEmpty()
                @Composable override fun Cuerpo(publicacion: PublicacionSocial, destapado: Boolean) { CuerpoCucha(vm, publicacion) }
                @Composable override fun SeccionesPerfil(perfil: PerfilSocial, propio: Boolean, contenido: @Composable (Int) -> Unit) =
                    seccionesRed { contenido(it) }
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
            override fun borrarPublicacion(id: Long, clave: String) { red.borrar(id) }
            override fun quitarDeLaRed(clave: String) { red.retirar(clave) }
            override fun cabeceras() = red.cabecerasPublicacion()
            @Composable override fun menu(publicacion: PublicacionSocial, cerrar: () -> Unit) { MenuCucha(vm, this, publicacion, cerrar) }
            @Composable override fun moderacionApp() {}
            @Composable override fun contenidoReporte(contenido: String) { ReporteCucha(vm, contenido) }
        }
    }
}

/** Las pestañas del perfil en la red: Comidas, Recetas, Restaurantes y Reseñas (el índice es el del tipo). */
@Composable
fun seccionesRed(contenido: @Composable (Int) -> Unit) = listOf(
    SeccionPerfil(0, Iconos.Plato, stringResource(R.string.perfil_comidas)) { contenido(0) },
    SeccionPerfil(1, Iconos.Recetas, stringResource(R.string.pestana_recetas)) { contenido(1) },
    SeccionPerfil(2, IconosFogon.Inicio, stringResource(R.string.red_restaurantes)) { contenido(2) },
    SeccionPerfil(3, IconosFogon.Estrella, stringResource(R.string.red_resenas)) { contenido(3) },
)

/** Lo de cada pestaña del perfil en la red. Restaurantes: un lugar por reseña, sin repetir. */
@Composable
fun ContenidoSeccionRed(interfaz: InterfazSocial, publicaciones: List<PublicacionSocial>, seccion: Int, avisoVacio: Boolean = true) {
    val lista = when (seccion) {
        0 -> publicaciones.filter { it.tipo == TIPO_PLATO }
        1 -> publicaciones.filter { it.tipo == TIPO_RECETA }
        else -> publicaciones.filter { it.tipo == TIPO_RESENA }
    }
    if (lista.isEmpty()) { if (avisoVacio) Pista(stringResource(R.string.red_sin_publicaciones), Modifier.padding(16.dp)); return }
    if (seccion == 2) {
        lista.mapNotNull { lugarDe(it.datos) }.distinctBy { it.id }.forEach { lugar ->
            FilaAjuste(IconosFogon.Inicio, lugar.nombre, listOfNotNull(lugar.ciudad, lugar.direccion).joinToString(" · ")) {}
        }
    } else lista.forEach { TarjetaPublicacion(interfaz, it) }
}

@Composable
fun Cuenta(vm: CuchaViewModel, exportar: () -> Unit) {
    val interfaz = interfazCuenta(vm)
    val estado by vm.estado.collectAsStateWithLifecycle()
    CuentaRed(interfaz, "", estado?.nombre.orEmpty())
    CopiaCuenta(vm.red)
    Spacer(Modifier.height(12.dp))
    CambiarDeCuenta(vm, exportar)
}

/** «Cambiar de cuenta»: los perfiles de este teléfono, cada uno con su diario. */
@Composable
fun CambiarDeCuenta(vm: CuchaViewModel, exportar: () -> Unit) {
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
fun SeguridadCuenta(vm: CuchaViewModel, ajuste: AjusteSocial) {
    val interfaz = interfazCuenta(vm)
    when (ajuste) {
        AjusteSocial.SESIONES -> Sesiones(interfaz)
        AjusteSocial.ESTADO_CUENTA -> EstadoCuenta(interfaz)
        AjusteSocial.BLOQUEADOS -> BloqueadosRed(interfaz)
        AjusteSocial.BORRAR_CUENTA -> FormularioBorrado(interfaz, vm::cerrar)
    }
}

/** «Qué sale de tu teléfono» de La Cucha. */
@Composable
fun TransparenciaCucha(vm: CuchaViewModel) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val forzudo by vm.forzudo.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    Transparencia(vm::cerrar, stringResource(R.string.cuenta_local), stringResource(R.string.cuenta_que_sale),
        buildList {
            if (usuario != null) add(stringResource(R.string.cuenta_servicio_social))
            if (forzudo != null) add(stringResource(R.string.cuenta_servicio_forzudo))
            if (estado?.codigoBarras == true) add(stringResource(R.string.lic_off))
        }, stringResource(R.string.cuenta_borrado), emptyList())
}

@Composable
fun PoliticaCuenta(vm: CuchaViewModel) {
    PoliticaPrivacidad(interfazCuenta(vm))
    val gracia by vm.red.gracia.collectAsStateWithLifecycle()
    gracia?.let { GraciaBorrado(it.borrarEl, vm.red::decidirGracia) }
}

/** Las pantallas de la red que se abren encima: avisos, guardados, moderación, una publicación, un perfil o una etiqueta. */
@Composable
fun RedCucha(vm: CuchaViewModel, destino: Pantalla.Social) {
    val interfaz = interfazCuenta(vm)
    when (destino.destino) {
        "publicacion" -> PublicacionRed(interfaz, destino.id)
        "avisos" -> AvisosRed(interfaz)
        "guardados" -> GuardadosRed(interfaz)
        "moderacion" -> Moderacion(interfaz)
        "etiqueta" -> EtiquetaRed(interfaz, destino.usuario)
        "perfil" -> PerfilRed(vm, interfaz, destino.usuario)
        else -> TuGente(vm, interfaz, conAtras = true)
    }
}

@Composable
private fun PerfilRed(vm: CuchaViewModel, interfaz: InterfazSocial, usuario: String) {
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    LaunchedEffect(usuario) { vm.red.verPerfil(usuario) }
    val perfil = perfiles[usuario]
    var elegida by rememberSaveable(usuario) { mutableIntStateOf(0) }
    Cabecera(usuario, vm::cerrar)
    if (perfil == null) return
    val secciones = seccionesRed {
        FavoritosRed(perfil.partes, it)
        ContenidoSeccionRed(interfaz, perfil.publicaciones, it)
    }
    val publicaciones = perfil.publicaciones
    val vista = io.github.fogonapps.perfil.PerfilVista(perfil.persona.nombre, perfil.persona.usuario,
        "", perfil.portada, perfil.bio, perfil.enlaces, perfil.persona.privada, perfil.relacion,
        contadores = cifrasRed(publicaciones), secciones = secciones, insignia = perfil.persona.insignia)
    io.github.fogonapps.perfil.Perfil(vista, elegida, { elegida = it }, portada = {},
        foto = { AvatarRed(perfil.persona, 96.dp) },
        bajoNombre = { Text("@${perfil.persona.usuario}", color = FogonTema.actual.textoSuave) },
        enlace = {}, acciones = {
            if (perfil.persona.usuario != yo) BotonSecundario(stringResource(
                if (perfil.relacion == "sigo") R.string.red_dejar_seguir else R.string.red_seguir), {
                if (perfil.relacion == "sigo") vm.red.dejarDeSeguir(usuario) else vm.red.seguir(usuario)
            }, Modifier.weight(1f))
        })
}

/** Las cifras del perfil en la red: platos, recetas y lugares reseñados; nunca calorías ni peso. */
@Composable
fun cifrasRed(publicaciones: List<PublicacionSocial>) = listOf(
    io.github.fogonapps.perfil.ContadorPerfil(publicaciones.count { it.tipo == TIPO_PLATO }.toString(), stringResource(R.string.red_platos)),
    io.github.fogonapps.perfil.ContadorPerfil(publicaciones.count { it.tipo == TIPO_RECETA }.toString(), stringResource(R.string.pestana_recetas)),
    io.github.fogonapps.perfil.ContadorPerfil(publicaciones.filter { it.tipo == TIPO_RESENA }.mapNotNull { lugarDe(it.datos)?.id }.distinct().size.toString(),
        stringResource(R.string.red_restaurantes)),
)

/** «Tu gente»: Siguiendo y Explorar de La Cucha; es una sección de la barra (X-07) y también se abre encima. */
@Composable
fun TuGente(vm: CuchaViewModel, interfaz: InterfazSocial = interfazCuenta(vm), conAtras: Boolean = false) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    var pestana by rememberSaveable { mutableIntStateOf(1) }
    val feed by vm.red.feed.collectAsStateWithLifecycle()
    val explorar by vm.red.explorar.collectAsStateWithLifecycle()
    val ocultas by vm.red.ocultas.collectAsStateWithLifecycle()
    val sinLeer by vm.red.sinLeer.collectAsStateWithLifecycle()
    val cuenta by vm.red.cuenta.collectAsStateWithLifecycle()
    var eligiendo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.red.cargarFeed(); vm.red.cargarExplorar(); vm.red.revisarSinLeer() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (conAtras) BotonIconoSocial(IconosSociales.Atras, stringResource(R.string.volver)) { vm.cerrar() }
        Text(stringResource(R.string.red_tu_gente), style = MaterialTheme.typography.headlineSmall, color = FogonTema.actual.texto, modifier = Modifier.weight(1f))
        if (usuario != null) ConCuentaSocial(sinLeer) {
            BotonIconoSocial(IconosFogon.Campana, stringResource(R.string.red_avisos), grande = true) { vm.abrir(Pantalla.Social("avisos")) }
        }
        if (!cuenta?.permisos.isNullOrEmpty()) BotonIconoSocial(IconosSociales.Bandera, stringResource(R.string.red_moderacion), grande = true) { vm.abrir(Pantalla.Social("moderacion")) }
    }
    if (usuario == null) BotonRiso(stringResource(R.string.red_entrar), { vm.abrir(Pantalla.Cuenta) }, Modifier.padding(16.dp))
    CopiaCuenta(vm.red, soloSiFalta = true)
    PestanasFeedSocial(pestana, usuario, { pestana = it }, textoPublicar = stringResource(R.string.red_compartir)) { eligiendo = true }
    if (eligiendo) ElegirQueCompartir(vm) { eligiendo = false }
    ListaFeedSocial((if (pestana == 0) (if (usuario == null) emptyList() else feed) else explorar)?.filter { it.id !in ocultas }?.map { it to Unit }, pestana,
        vm.red::masFeed, stringResource(R.string.red_feed_pie)) { p, _ -> TarjetaPublicacion(interfaz, p) }
}

/** «+ Compartir»: un plato (anotado o nuevo), una receta propia o un restaurante. */
@Composable
private fun ElegirQueCompartir(vm: CuchaViewModel, cerrar: () -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var paso by rememberSaveable { mutableIntStateOf(0) }
    val f = FogonTema.actual
    AlertDialog(onDismissRequest = cerrar, title = {
        Text(stringResource(when (paso) { 1 -> R.string.red_elegir_plato; 2 -> R.string.red_elegir_receta; else -> R.string.red_que_compartir }))
    }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            when (paso) {
                0 -> {
                    TextButton({ paso = 1 }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_plato)) }
                    TextButton({ paso = 2 }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_receta)) }
                    TextButton({ cerrar(); vm.abrir(Pantalla.ResenarRestaurante()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_restaurante)) }
                }
                1 -> {
                    TextButton({ cerrar(); vm.abrir(Pantalla.CompartirPlato()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_plato_en_blanco)) }
                    val recientes = estado?.dias.orEmpty().entries.sortedByDescending { it.key }.take(7)
                        .flatMap { (fecha, dia) -> dia.anotados.map { fecha to it } }.take(30)
                    if (recientes.isEmpty()) Text(stringResource(R.string.red_sin_comidas_hoy), color = f.textoSuave)
                    recientes.forEach { (fecha, a) ->
                        TextButton({ cerrar(); vm.abrir(Pantalla.CompartirPlato(fecha, a.id)) }, Modifier.fillMaxWidth()) {
                            Text("${a.nombre} · $fecha")
                        }
                    }
                }
                else -> {
                    val propias = estado?.recetas.orEmpty().filter { it.propia }
                    if (propias.isEmpty()) Text(stringResource(R.string.red_sin_recetas_propias), color = f.textoSuave)
                    propias.forEach { r ->
                        TextButton({ cerrar(); vm.abrir(Pantalla.CompartirReceta(r.id)) }, Modifier.fillMaxWidth()) { Text(r.nombre(vm.idioma())) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton({ if (paso == 0) cerrar() else paso = 0 }) { Text(stringResource(R.string.volver)) } })
}

/** El lugar que trae una publicación: la ficha actual que añade el servidor (`lugarDetalle`). */
fun lugarDe(datos: JsonObject): Lugar? = (datos["lugarDetalle"] as? JsonObject)?.let {
    runCatching { JsonSocial.decodeFromJsonElement(Lugar.serializer(), it) }.getOrNull()
}

/** La nota en estrellas (1 a 5), con su texto accesible. */
@Composable
fun NotaEstrellas(nota: Int) {
    if (nota !in 1..5) return
    val descripcion = stringResource(R.string.red_nota_n, nota)
    Text("★".repeat(nota) + "☆".repeat(5 - nota), color = FogonTema.actual.acentoTexto,
        modifier = Modifier.semantics { contentDescription = descripcion })
}

/** Una línea con las calorías y los macros, solo si la publicación los incluye. */
@Composable
private fun LineaNutricion(datos: JsonObject, porPorcion: Boolean) {
    val n = datos["nutricion"] as? JsonObject ?: return
    fun v(k: String) = n[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val idioma = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val linea = stringResource(R.string.red_macros, v("kcal").toInt(), cifra(v("proteina"), idioma), cifra(v("carbohidratos"), idioma), cifra(v("grasa"), idioma))
    Text(if (porPorcion) stringResource(R.string.red_por_porcion) + ": " + linea else linea,
        style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave)
}

/** El cuerpo de las tarjetas: plato, receta o reseña. El lugar muestra nombre, ciudad y dirección escrita, nunca coordenadas. */
@Composable
fun CuerpoCucha(vm: CuchaViewModel, p: PublicacionSocial) {
    val f = FogonTema.actual
    val d = p.datos
    fun texto(k: String) = d[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (p.tipo) {
            TIPO_PLATO, TIPO_RECETA -> Text(texto("nombre"), style = MaterialTheme.typography.titleMedium, color = f.texto)
        }
        if (p.tipo == TIPO_RESENA || d["lugar"] != null || d["lugarDetalle"] != null) {
            val lugar = lugarDe(d)
            if (lugar == null) Text(stringResource(R.string.red_lugar_no_disponible), color = f.textoSuave)
            else {
                Text(lugar.nombre, style = if (p.tipo == TIPO_RESENA) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = f.texto)
                Text(listOfNotNull(lugar.ciudad, lugar.direccion).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            }
        }
        d["nota"]?.jsonPrimitive?.intOrNull?.let { NotaEstrellas(it) }
        if (p.tipo == TIPO_RESENA && texto("plato").isNotBlank()) Text(texto("plato"), color = f.texto)
        if (p.tipo == TIPO_RECETA) {
            val porciones = d["porciones"]?.jsonPrimitive?.intOrNull ?: 1
            val tiempo = d["tiempo"]?.jsonPrimitive?.intOrNull
            Text(listOfNotNull(pluralPorciones(porciones), tiempo?.let { stringResource(R.string.red_minutos, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
            Text(stringResource(R.string.red_ingredientes), style = MaterialTheme.typography.labelLarge, color = f.texto)
            val idioma = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
            d["ingredientes"]?.jsonArray.orEmpty().forEach { e ->
                val i = e.jsonObject
                Text("· ${i["nombre"]?.jsonPrimitive?.content.orEmpty()} — ${cifra(i["cantidad"]?.jsonPrimitive?.doubleOrNull ?: 0.0, idioma)} ${i["unidad"]?.jsonPrimitive?.content.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall, color = f.texto)
            }
            Text(stringResource(R.string.red_pasos), style = MaterialTheme.typography.labelLarge, color = f.texto)
            d["pasos"]?.jsonArray.orEmpty().forEachIndexed { i, e ->
                Text("${i + 1}. ${e.jsonPrimitive.content}", style = MaterialTheme.typography.bodySmall, color = f.texto)
            }
        }
        LineaNutricion(d, porPorcion = p.tipo == TIPO_RECETA)
        MultimediaCucha(vm, d)
        if (p.tipo == TIPO_RESENA) Util(vm, p)
    }
}

@Composable
private fun pluralPorciones(n: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.porciones_n, n, n)

/** «Útil» en la reseña de otra persona (no en la propia). */
@Composable
private fun Util(vm: CuchaViewModel, p: PublicacionSocial) {
    val yo by vm.red.usuario.collectAsStateWithLifecycle()
    if (p.id == 0L) return
    if (yo == null || yo == p.autor.usuario) {
        if (p.utiles > 0) Text(androidx.compose.ui.res.pluralStringResource(R.plurals.red_utiles, p.utiles, p.utiles),
            style = MaterialTheme.typography.bodySmall, color = FogonTema.actual.textoSuave)
        return
    }
    var mio by remember(p.id, p.yoUtil) { mutableStateOf(p.yoUtil) }
    val total = p.utiles - (if (p.yoUtil) 1 else 0) + (if (mio) 1 else 0)
    PildoraSocial(androidx.compose.ui.res.pluralStringResource(R.plurals.red_utiles, total, total), IconosFogon.Chulo, acento = mio) {
        mio = !mio; vm.red.util(p.id, mio)
    }
}

@Composable
fun MultimediaCucha(vm: CuchaViewModel, datos: JsonObject) {
    val video = datos["video"]?.jsonPrimitive?.content.orEmpty()
    if (video.isNotBlank()) VideoPublicacion(vm.red::cabecerasPublicacion, video,
        datos["poster"]?.jsonPrimitive?.content.orEmpty(), 0, stringResource(R.string.red_reproducir))
    else CarruselFotos(imagenesPublicacion(datos))
}

/** El menú de una publicación propia (editar, volver privada, borrar) o ajena (el común, más reportar el lugar). */
@Composable
fun MenuCucha(vm: CuchaViewModel, interfaz: InterfazSocial, p: PublicacionSocial, cerrar: () -> Unit) {
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val confirmar = LocalConfirmar.current
    var corrigiendo by remember { mutableStateOf<Lugar?>(null) }
    corrigiendo?.let { l -> CorregirLugar(vm, l) { corrigiendo = null; cerrar() }; return }
    if (usuario == p.autor.usuario) {
        val borrar = stringResource(R.string.red_borrar)
        val privado = stringResource(R.string.red_privado)
        AlertDialog(onDismissRequest = cerrar, title = { Text(stringResource(R.string.red_publicado)) }, text = {
            Column {
                val clave = vm.red.origen(p)
                val editar = clave?.let { destinoEdicion(vm, it) }
                if (editar != null) TextButton({ cerrar(); vm.abrir(editar) }) { Text(stringResource(R.string.red_actualizar)) }
                lugarDe(p.datos)?.let { l -> TextButton({ corrigiendo = l }) { Text(stringResource(R.string.red_corregir_lugar)) } }
                TextButton({ cerrar(); confirmar(privado, null) { if (clave != null) vm.red.retirar(clave, p.id) else vm.red.borrar(p.id) } }) { Text(privado) }
                TextButton({ cerrar(); confirmar(borrar, null) { vm.red.borrar(p.id) } }) { Text(borrar) }
            }
        }, confirmButton = { TextButton(cerrar) { Text(stringResource(R.string.volver)) } })
        return
    }
    var reportandoLugar by remember { mutableStateOf(false) }
    val lugar = lugarDe(p.datos)
    if (reportandoLugar && lugar != null) {
        HojaReporte({ reportandoLugar = false; cerrar() }) { codigo, texto ->
            reportandoLugar = false; cerrar()
            vm.red.reportar("lugar", lugar.id.toString(), if (codigo == "otro") "otro: $texto" else codigo)
        }
        return
    }
    if (lugar != null) {
        AlertDialog(onDismissRequest = cerrar, text = {
            Column {
                MenuPublicacion(interfaz, p, "", cerrar)
                TextButton({ reportandoLugar = true }) { Text(stringResource(R.string.red_reportar_lugar)) }
            }
        }, confirmButton = { TextButton(cerrar) { Text(stringResource(R.string.volver)) } })
    } else MenuPublicacion(interfaz, p, "", cerrar)
}

/** De qué compositor salió una publicación propia, para «Actualizar publicación». */
private fun destinoEdicion(vm: CuchaViewModel, clave: String): Pantalla? {
    val e = vm.estado.value ?: return null
    if (clave.startsWith(prefijoClave(TIPO_RECETA))) e.recetas.firstOrNull { clavePublicacion(TIPO_RECETA, it.id) == clave }?.let { return Pantalla.CompartirReceta(it.id) }
    if (clave.startsWith(prefijoClave(TIPO_RESENA))) return Pantalla.ResenarRestaurante(clave)
    if (clave.startsWith(prefijoClave(TIPO_PLATO))) {
        e.dias.forEach { (fecha, dia) -> dia.anotados.firstOrNull { clavePublicacion(TIPO_PLATO, it.id) == clave }?.let { return Pantalla.CompartirPlato(fecha, it.id) } }
    }
    return null
}

@Composable
fun ReporteCucha(vm: CuchaViewModel, contenido: String) {
    val datos = remember(contenido) { runCatching {
        val raiz = JsonSocial.parseToJsonElement(contenido).jsonObject
        (raiz["datos"] as? JsonObject) ?: raiz
    }.getOrNull() }
    if (datos != null && ("nombre" in datos || "lugar" in datos)) {
        val tipo = when { "porciones" in datos -> TIPO_RECETA; "nombre" in datos -> TIPO_PLATO; else -> TIPO_RESENA }
        CuerpoCucha(vm, PublicacionSocial(0, PersonaSocial(""), tipo, 0, "publica", datos))
        datos["texto"]?.jsonPrimitive?.content?.let { Text(it, color = FogonTema.actual.texto) }
    } else Text(contenido.take(2000), color = FogonTema.actual.texto)
}

/** Los favoritos que la persona decidió mostrar: platos en Comidas y restaurantes en Restaurantes. */
@Composable
fun FavoritosRed(partes: Map<String, JsonElement>, seccion: Int) {
    val (clave, titulo) = when (seccion) {
        0 -> PARTE_PLATOS to R.string.red_platos_favoritos
        2 -> PARTE_RESTAURANTES to R.string.red_restaurantes_favoritos
        else -> return
    }
    val nombres = nombresFavoritos(partes[clave])
    if (nombres.isEmpty()) return
    Seccion(stringResource(titulo))
    nombres.forEach { Text("· $it", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = FogonTema.actual.texto) }
}

/** «Quién lo ve» de los favoritos, como las partes del perfil de El Cajón y El Forzudo. */
@Composable
fun PartesCucha(vm: CuchaViewModel) {
    val f = FogonTema.actual
    val estado by vm.estado.collectAsStateWithLifecycle()
    val usuario by vm.red.usuario.collectAsStateWithLifecycle()
    val perfiles by vm.red.perfiles.collectAsStateWithLifecycle()
    val remoto by vm.red.identidad.collectAsStateWithLifecycle()
    val e = estado ?: return
    LaunchedEffect(usuario) { usuario?.let { vm.red.verPerfil(it) } }
    val idioma = vm.idioma()
    val platos = e.favoritos.mapNotNull { vm.L.comible(e, it)?.nombre(idioma) }
    val restaurantes = usuario?.let { perfiles[it] }?.publicaciones.orEmpty()
        .filter { it.tipo == TIPO_RESENA && (it.datos["nota"]?.jsonPrimitive?.intOrNull ?: 0) >= 4 }
        .mapNotNull { lugarDe(it.datos)?.let { l -> "${l.nombre} · ${l.ciudad}" } }.distinct()
    var visPlatos by remember(remoto) { mutableStateOf(vm.red.visibilidad(PARTE_PLATOS)) }
    var visRestaurantes by remember(remoto) { mutableStateOf(vm.red.visibilidad(PARTE_RESTAURANTES)) }
    val opciones = listOf("privado" to stringResource(R.string.red_vis_privado), "publica" to stringResource(R.string.red_vis_publica),
        "amigos" to stringResource(R.string.red_vis_seguidores))
    Seccion(stringResource(R.string.red_quien_lo_ve))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.red_partes_pista), style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
        listOf(Triple(R.string.red_platos_favoritos, platos, visPlatos) to { v: String -> visPlatos = v },
            Triple(R.string.red_restaurantes_favoritos, restaurantes, visRestaurantes) to { v: String -> visRestaurantes = v }).forEach { (t, cambiar) ->
            val (titulo, lista, valor) = t
            Column(Modifier.fillMaxWidth().tarjetaFogon().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(titulo), style = MaterialTheme.typography.bodyLarge, color = f.texto)
                Text(lista.take(5).joinToString(", ").ifBlank { stringResource(R.string.red_sin_favoritos) },
                    style = MaterialTheme.typography.bodySmall, color = f.textoSuave)
                Selector(opciones, valor, cambiar)
            }
        }
        BotonRiso(stringResource(io.github.fogonapps.social.ui.R.string.social_guardar), {
            vm.red.publicarPartes(partesCucha(platos, visPlatos, restaurantes, visRestaurantes))
        }, Modifier.fillMaxWidth())
    }
}

/** Corregir la dirección escrita de un lugar (solo quien lo creó; el servidor lo revisa). */
@Composable
private fun CorregirLugar(vm: CuchaViewModel, l: Lugar, cerrar: () -> Unit) {
    var direccion by remember { mutableStateOf(l.direccion.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = cerrar, title = { Text(stringResource(R.string.red_corregir_lugar)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${l.nombre} · ${l.ciudad}", color = FogonTema.actual.textoSuave)
            androidx.compose.material3.OutlinedTextField(direccion, { direccion = it.take(200); error = false }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.red_lugar_direccion)) })
            if (error) Text(stringResource(R.string.red_corregir_no), color = FogonTema.actual.texto)
        }
    }, confirmButton = {
        TextButton({ ocupado = true; vm.red.corregirLugar(l.id, direccion) { bien -> ocupado = false; if (bien) cerrar() else error = true } },
            enabled = !ocupado && direccion.isNotBlank()) { Text(stringResource(R.string.red_guardar_lugar)) }
    }, dismissButton = { TextButton(cerrar) { Text(stringResource(R.string.volver)) } })
}
