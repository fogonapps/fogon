package io.github.fogonapps.ahorrador.datos

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

@Serializable
enum class Banco { BANCOLOMBIA, NU, NEQUI, DAVIVIENDA, DAVIPLATA, BOGOTA, BBVA, OTRO }

const val PAQUETE_NU = "com.nu.production"
val PAQUETES_MENSAJES = setOf("com.samsung.android.messaging", "com.google.android.apps.messaging")

@Serializable
data class AvisoBanco(
    val banco: Banco,
    val tipo: TipoMovimiento,
    val monto: Long,
    val momento: String,
    val cuenta: String,
    val contraparte: String,
    val texto: String,
) {
    val huella: String get() = MessageDigest.getInstance("SHA-256").digest(
        "$banco|$tipo|$monto|$momento|${cuenta.filter(Char::isDigit)}".toByteArray(Charsets.UTF_8)
    ).joinToString("") { "%02x".format(it) }
    val enlace: String get() = "$banco|${cuenta.filter(Char::isDigit)}"
}

@Serializable
data class RegistroBanco(
    val aviso: AvisoBanco,
    val movimiento: String? = null,
    val descartado: Boolean = false,
    val automatico: Boolean = false,
)

@Serializable
data class NotificacionesBanco(
    val elegidos: Set<Banco> = emptySet(),
    val solos: Set<Banco> = emptySet(),
    val enlaces: Map<String, String> = emptyMap(),
    val registros: List<RegistroBanco> = emptyList(),
    val categorias: Map<String, String> = emptyMap(),
    val noEntendidos: Set<Banco> = emptySet(),
)

/**
 * Bancos sin reglas exactas (no tenemos sus alertas reales): se leen con [reconocerGenerico] desde su app o desde un
 * SMS que los nombra, y lo que se entiende siempre se propone para confirmar, nunca se anota solo.
 */
val PAQUETES_GENERICOS = mapOf(
    Banco.NEQUI to "com.nequi.MobileApp",
    Banco.DAVIPLATA to "com.davivienda.daviplataapp",
    Banco.DAVIVIENDA to "com.davivienda.daviviendaapp",
    Banco.BOGOTA to "com.bancodebogota.bancamovil",
    Banco.BBVA to "co.com.bbva.mb",
)
val GENERICOS = PAQUETES_GENERICOS.keys

/** Cómo se nombra cada banco en un SMS (sin tildes, en minúsculas). Daviplata va antes que Davivienda. */
private val NOMBRES_SMS = listOf(
    Banco.NEQUI to Regex("\\bnequi\\b"), Banco.DAVIPLATA to Regex("\\bdaviplata\\b"), Banco.DAVIVIENDA to Regex("\\bdavivienda\\b"),
    Banco.BOGOTA to Regex("\\bbanco de bogota\\b|\\bbancodebogota\\b"), Banco.BBVA to Regex("\\bbbva\\b"),
)

private fun sinTildes(t: String) = java.text.Normalizer.normalize(t.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private fun esMensajes(paquete: String, predeterminado: String?) = paquete in PAQUETES_MENSAJES || paquete == predeterminado

/** El paquete se comprueba antes de leer los extras de la notificación. */
fun paqueteAdmitido(paquete: String, predeterminado: String?, bancos: Set<Banco>): Boolean =
    (Banco.NU in bancos && paquete == PAQUETE_NU) ||
        (Banco.BANCOLOMBIA in bancos && esMensajes(paquete, predeterminado)) ||
        bancos.any { it in GENERICOS && (paquete == PAQUETES_GENERICOS[it] || esMensajes(paquete, predeterminado)) }

/** El prefijo se exige incluso si la app de mensajes es la predeterminada. */
fun bancoDelTexto(paquete: String, predeterminado: String?, bancos: Set<Banco>, texto: String): Banco? = when {
    !paqueteAdmitido(paquete, predeterminado, bancos) -> null
    paquete == PAQUETE_NU -> Banco.NU
    PAQUETES_GENERICOS.entries.any { it.value == paquete } -> PAQUETES_GENERICOS.entries.first { it.value == paquete }.key.takeIf { it in bancos }
    Banco.BANCOLOMBIA in bancos && texto.startsWith("Bancolombia:") -> Banco.BANCOLOMBIA
    esMensajes(paquete, predeterminado) -> sinTildes(texto).let { t -> NOMBRES_SMS.firstOrNull { (b, r) -> b in bancos && r.containsMatchIn(t) }?.first }
    else -> null
}

/* ----- Lectura general (bancos sin reglas exactas) ----- */

/** Avisos que no son movimientos: códigos, accesos, publicidad. */
private val NO_ES_MOVIMIENTO = Regex("\\b(codigo|clave dinamica|clave temporal|otp|verificacion|inicio de sesion|iniciaste sesion|ingresaste a|nuevo dispositivo|gana|ganate|oferta|promo|promocion|descuento|preaprobado|preaprobada|cupo disponible|solicita|simula)\\b")
private val INGRESO = Regex("\\b(recibiste|te (enviaron|llego|llegaron|consignaron|pagaron|abonaron|transfirieron|devolvimos)|abono|abonamos|consignacion|consignaron|reembolso|devolucion|deposito|recibio|entrada de dinero)\\b")
private val GASTO_TUYO = Regex("\\b(compraste|pagaste|enviaste|transferiste|retiraste|realizaste|hiciste)\\b")
private val GASTO = Regex("\\b(compra|compraste|pagaste|pago|enviaste|transferiste|transferencia a|retiro|retiraste|debito|debitamos|cobro|cobramos|avance|pse)\\b")
private val MONTO = Regex("(?:\\$|\\bcop\\b|\\bcol\\$)\\s?([0-9][0-9.,]*[0-9])")

/** Un monto en pesos escrito al estilo colombiano (1.234.567,89) o al de EE. UU. (1,234,567.89). */
internal fun montoFlexible(s: String): Long? = runCatching {
    val limpio = when {
        Regex("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]{1,2})?").matches(s) -> s.replace(".", "").replace(',', '.')
        Regex("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?").matches(s) -> s.replace(",", "")
        Regex("[0-9]+([.,][0-9]{1,2})?").matches(s) -> s.replace(',', '.')
        else -> return null
    }
    BigDecimal(limpio).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { it > 0 }
}.getOrNull()

/**
 * Lee cualquier alerta de banco: el monto (con $ o COP), si es ingreso o gasto por sus palabras, la contraparte
 * («en …», «a …», «de …») y la cuenta (*1234). Si algo no está claro, no la entiende (null) y la app lo dice.
 */
fun reconocerGenerico(banco: Banco, titulo: String, texto: String, recibido: LocalDateTime): AvisoBanco? {
    val original = normalizarAlerta(listOf(titulo, texto).filter(String::isNotBlank).joinToString(". "))
    val t = sinTildes(original)
    if (NO_ES_MOVIMIENTO.containsMatchIn(t)) return null
    val m = MONTO.find(t) ?: return null
    if (MONTO.findAll(t).map { it.groupValues[1] }.distinct().count() > 2) return null
    val monto = montoFlexible(m.groupValues[1]) ?: return null
    val tipo = when {
        INGRESO.containsMatchIn(t) && !GASTO_TUYO.containsMatchIn(t) -> TipoMovimiento.INGRESO
        GASTO.containsMatchIn(t) -> TipoMovimiento.GASTO
        else -> return null
    }
    val cuenta = Regex("\\*\\s?([0-9]{3,6})\\b|terminada en ([0-9]{3,6})\\b|cuenta (?:no\\.? ?)?([0-9]{3,6})\\b").find(t)
        ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let { "*$it" }.orEmpty()
    // La contraparte, en el texto original (con mayúsculas), después del monto si se puede.
    val desde = original.length.coerceAtMost(m.range.last + 1)
    val conector = Regex("\\b(?:en|a|de|desde|para)\\s+([\\p{L}0-9&'*][\\p{L}0-9&'*. -]{1,40}?)(?=\\s+(?:el|por|con|desde|hoy|ref|referencia|a las|en tu|a tu|de tu|tu|su)\\b|[.,;:!]|$)")
    val contraparte = (conector.findAll(original, desde).toList() + conector.findAll(original).toList())
        .map { it.groupValues[1].trim() }
        .firstOrNull { c -> val l = sinTildes(c); !l.startsWith("tu ") && !l.startsWith("su ") && !l.startsWith("las ") && !l.startsWith("los ") && !l.startsWith("la llave") && !l.startsWith("la cuenta") && !l.startsWith("cuenta") && l.any(Char::isLetter) && !MONTO.containsMatchIn(l) }
        .orEmpty()
    return AvisoBanco(banco, tipo, monto, recibido.withSecond(0).withNano(0).toString(), cuenta, contraparte.take(80), contraparte.take(60))
}

private fun montoBanco(s: String, colombiano: Boolean): Long? = runCatching {
    val patron = if (colombiano) Regex("(?:[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+),[0-9]{2}")
        else Regex("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]{2}")
    if (!patron.matches(s)) return null
    BigDecimal(if (colombiano) s.replace(".", "").replace(',', '.') else s.replace(",", ""))
        .setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { it > 0 }
}.getOrNull()

/** Normaliza solo en memoria; los extras originales nunca se persisten. */
fun normalizarAlerta(texto: String): String = texto
    .replace(Regex("[\u00a0\u202f\u2007\u2009]"), " ")
    .replace(Regex("[\u200b-\u200f\ufeff\u2066-\u2069]"), "")
    .replace("…", "...")
    .replace(Regex("\\s+"), " ").trim()

/** postTime decide si es nueva; when solo aporta la hora si pertenece a esta activación. */
fun momentoAlerta(desde: Long, publicada: Long, cuando: Long): Long? =
    if (desde <= 0 || publicada < desde) null else cuando.takeIf { it in desde..publicada } ?: publicada

/** Prueba todas las variantes de extras, sin unir mensajes distintos de EXTRA_TEXT_LINES. */
fun reconocerExtrasBanco(banco: Banco, titulos: List<String>, textos: List<String>, recibido: LocalDateTime): AvisoBanco? {
    val cuerpos = textos.map(::normalizarAlerta).filter(String::isNotBlank).distinct()
    val encabezados = titulos.map(::normalizarAlerta).filter(String::isNotBlank).distinct().ifEmpty {
        cuerpos.mapNotNull { Regex("(?:Enviaste|Recibiste) .+").find(it)?.value }.ifEmpty { listOf("") }
    }
    for (texto in cuerpos) for (titulo in encabezados) reconocerAviso(banco, titulo, texto, recibido)?.let { return it }
    return null
}

/** Reglas cerradas por banco. Nu usa la hora de la alerta, redondeada al minuto. */
fun reconocerAviso(banco: Banco, tituloOriginal: String, textoOriginal: String, recibido: LocalDateTime): AvisoBanco? {
    if (banco in GENERICOS) return reconocerGenerico(banco, tituloOriginal, textoOriginal, recibido)
    val titulo = normalizarAlerta(tituloOriginal)
    val texto = normalizarAlerta(textoOriginal)
    if (banco == Banco.NU) {
        val encabezado = titulo.ifBlank { Regex("(?:Enviaste|Recibiste) .+").find(texto)?.value.orEmpty() }
        val m = Regex("^(Enviaste|Recibiste) \\$?([0-9.,]+)(?= |$)(?: .*)?$").matchEntire(encabezado) ?: return null
        val ingreso = m.groupValues[1] == "Recibiste"
        val patron = if (ingreso) "Te llegó dinero de " else "Le enviaste a "
        val contraparte = Regex(Regex.escape(patron) + "(.+?)(?= en su cuenta| con tu llave| en tu cuenta|\\.\\.\\.|[.]?$)")
            .find(texto)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return AvisoBanco(banco, if (ingreso) TipoMovimiento.INGRESO else TipoMovimiento.GASTO,
            montoBanco(m.groupValues[2], true) ?: return null,
            recibido.withSecond(0).withNano(0).toString(), "", contraparte.take(80), contraparte.take(60))
    }
    if (banco != Banco.BANCOLOMBIA || !texto.startsWith("Bancolombia:")) return null
    val fecha = Regex(" el ([0-9]{2}/[0-9]{2}/(?:[0-9]{4}|[0-9]{2}))(?: a las | )([0-9]{2}:[0-9]{2}(?::[0-9]{2})?)(?:\\.|$)").find(texto) ?: return null
    val f = fecha.groupValues[1].split('/')
    val hora = fecha.groupValues[2]
    val instante = runCatching {
        val ano = if (f[2].length == 2) "20${f[2]}" else f[2]
        LocalDateTime.parse("$ano-${f[1]}-${f[0]}T$hora", DateTimeFormatter.ofPattern(if (hora.length == 8) "uuuu-MM-dd'T'HH:mm:ss" else "uuuu-MM-dd'T'HH:mm").withResolverStyle(ResolverStyle.STRICT))
    }.getOrNull() ?: return null
    val cuerpo = texto.substring("Bancolombia: ".length, fecha.range.first)
    val reglas = listOf(
        Regex("Transferiste \\$([0-9.,]+) desde tu cuenta \\*([0-9]+) a la cuenta (\\*[0-9]+)", RegexOption.IGNORE_CASE) to TipoMovimiento.GASTO,
        Regex(".+?, transferiste \\$([0-9.,]+) a la llave [^ ]+ desde tu cuenta \\*([0-9]+) a (.+)", RegexOption.IGNORE_CASE) to TipoMovimiento.GASTO,
        Regex("Pagaste \\$([0-9.,]+) a (.+) desde tu producto ([0-9]+)", RegexOption.IGNORE_CASE) to TipoMovimiento.GASTO,
        Regex(".+?, recibiste una transferencia de (.+) por \\$([0-9.,]+) en tu cuenta \\*([0-9]+)(?: conectada a la llave [^ ]+)?", RegexOption.IGNORE_CASE) to TipoMovimiento.INGRESO,
    )
    reglas.forEachIndexed { i, (regla, tipo) ->
        val m = regla.matchEntire(cuerpo) ?: return@forEachIndexed
        val g = m.groupValues
        val monto = montoBanco(g[if (i == 3) 2 else 1], false) ?: return null
        val cuenta = g[when (i) { 2, 3 -> 3; else -> 2 }]
        val otro = g[when (i) { 2 -> 2; 3 -> 1; else -> 3 }].take(80)
        return AvisoBanco(banco, tipo, monto, instante.toString(), if (i == 2) "producto $cuenta" else "*$cuenta", otro, otro.take(60))
    }
    return null
}

fun cuentasPosibles(e: Estado, aviso: AvisoBanco): List<Cuenta> = e.cuentas.filter {
    !it.archivada && it.tipo in setOf(TipoCuenta.BANCO, TipoCuenta.BILLETERA) && (it.banco == null || it.banco == aviso.banco)
}

fun Estado.cuentaEnlazada(a: AvisoBanco): String? = notificaciones.enlaces[a.enlace]?.takeIf { id ->
    cuentas.any { it.id == id && !it.archivada && it.banco == a.banco && it.tipo in setOf(TipoCuenta.BANCO, TipoCuenta.BILLETERA) }
}

fun Estado.parejaBanco(r: RegistroBanco): RegistroBanco? {
    val cuenta = cuentaEnlazada(r.aviso) ?: return null
    return notificaciones.registros.firstOrNull { otro ->
        !otro.descartado && otro.aviso.huella != r.aviso.huella && otro.aviso.tipo != r.aviso.tipo &&
            otro.aviso.monto == r.aviso.monto && cuentaEnlazada(otro.aviso)?.let { it != cuenta } == true &&
            runCatching { Duration.between(LocalDateTime.parse(otro.aviso.momento), LocalDateTime.parse(r.aviso.momento)).abs() < Duration.ofMinutes(5) }.getOrDefault(false) &&
            // Una alerta ya agrupada no se vuelve a usar para otro movimiento.
            (otro.movimiento == null || (notificaciones.registros.count { it.movimiento == otro.movimiento } == 1 &&
                movimientos.any { it.id == otro.movimiento && it.tipo == otro.aviso.tipo && it.monto == otro.aviso.monto &&
                    it.cuenta == cuentaEnlazada(otro.aviso) && it.fecha == otro.aviso.momento.substringBefore('T') }))
    }
}

fun claveContraparte(a: AvisoBanco) = "${a.banco}|${a.tipo}|${a.contraparte.trim().lowercase(Locale.ROOT)}"

fun Estado.propuestaBanco(huella: String): Movimiento? {
    val r = notificaciones.registros.firstOrNull { it.aviso.huella == huella && !it.descartado && it.movimiento == null } ?: return null
    val a = r.aviso
    if (a.monto <= 0 || a.tipo !in setOf(TipoMovimiento.GASTO, TipoMovimiento.INGRESO) || runCatching { LocalDateTime.parse(a.momento) }.isFailure) return null
    val cuenta = cuentaEnlazada(a) ?: return null
    val pareja = parejaBanco(r)
    val gasto = if (a.tipo == TipoMovimiento.GASTO) a else pareja?.aviso
    val ingreso = if (a.tipo == TipoMovimiento.INGRESO) a else pareja?.aviso
    return Movimiento("b$huella", a.momento.substringBefore('T'), if (pareja != null) TipoMovimiento.TRASPASO else a.tipo,
        a.monto, if (pareja != null) cuentaEnlazada(gasto!!)!! else cuenta,
        aCuenta = if (pareja != null) cuentaEnlazada(ingreso!!) else null,
        categoria = if (pareja == null) notificaciones.categorias[claveContraparte(a)]?.takeIf { id -> categorias.any { it.id == id && it.ingreso == (a.tipo == TipoMovimiento.INGRESO) } } else null,
        nota = a.texto, hora = a.momento.substringAfter('T'))
}

/** Atómico e idempotente: al llegar la segunda mitad sustituye la primera por un traspaso. */
fun confirmarBanco(e: Estado, huella: String, editado: Movimiento? = null, automatico: Boolean = false): Estado {
    val base = e.propuestaBanco(huella) ?: return e
    if (editado != null && editado.tipo != base.tipo) return e
    val r = e.notificaciones.registros.first { it.aviso.huella == huella }
    val pareja = e.parejaBanco(r)
    if (pareja == null) {
        val usados = e.notificaciones.registros.mapNotNull { it.movimiento }.toSet()
        val csv = e.movimientos.firstOrNull { it.importado && it.id !in usados && coincideExtracto(it, base.fecha, if (base.tipo == TipoMovimiento.GASTO) -base.monto else base.monto, base.cuenta) }
        if (csv != null) return agruparAnotadosBanco(e.copy(notificaciones = e.notificaciones.copy(registros = e.notificaciones.registros.map {
            if (it.aviso.huella == huella) it.copy(movimiento = csv.id) else it
        })))
    }
    val m = (editado ?: base).copy(id = base.id, hora = base.hora)
    if (m.monto <= 0 || e.cuentas.none { it.id == m.cuenta && !it.archivada } ||
        (m.tipo == TipoMovimiento.TRASPASO && (m.aCuenta == m.cuenta || e.cuentas.none { it.id == m.aCuenta && !it.archivada }))) return e
    val huellas = setOfNotNull(huella, pareja?.aviso?.huella)
    val anteriores = e.notificaciones.registros.filter { it.aviso.huella in huellas }.mapNotNull { it.movimiento }.toSet()
    return e.copy(movimientos = e.movimientos.filter { it.id !in anteriores } + m,
        notificaciones = e.notificaciones.copy(
            registros = e.notificaciones.registros.map { if (it.aviso.huella in huellas) it.copy(movimiento = m.id, automatico = automatico) else it },
            categorias = if (m.categoria != null) e.notificaciones.categorias + (claveContraparte(r.aviso) to m.categoria) else e.notificaciones.categorias))
}

fun enlazarBanco(e: Estado, aviso: AvisoBanco, cuenta: String): Estado {
    if (cuentasPosibles(e, aviso).none { it.id == cuenta }) return e
    return e.copy(cuentas = e.cuentas.map { if (it.id == cuenta) it.copy(banco = aviso.banco) else it },
        notificaciones = e.notificaciones.copy(enlaces = e.notificaciones.enlaces + (aviso.enlace to cuenta)))
}

fun descartarBanco(e: Estado, huella: String): Estado = e.copy(notificaciones = e.notificaciones.copy(
    registros = e.notificaciones.registros.map { if (it.aviso.huella == huella && it.movimiento == null) it.copy(descartado = true) else it }))

/** CSV sin hora: solo permite comparar día, dirección, monto y cuenta; conserva el sentido del traspaso. */
fun coincideExtracto(m: Movimiento, fecha: String, monto: Long, cuenta: String): Boolean =
    m.fecha == fecha && m.monto == kotlin.math.abs(monto) && when {
        monto < 0 -> m.cuenta == cuenta && m.tipo in setOf(TipoMovimiento.GASTO, TipoMovimiento.TRASPASO)
        else -> (m.cuenta == cuenta && m.tipo == TipoMovimiento.INGRESO) || (m.aCuenta == cuenta && m.tipo == TipoMovimiento.TRASPASO)
    }

fun recibirBanco(e: Estado, a: AvisoBanco): Estado {
    if (a.banco !in e.notificaciones.elegidos || e.notificaciones.registros.any { it.aviso.huella == a.huella }) return e
    val cuenta = e.cuentaEnlazada(a)
    val usados = e.notificaciones.registros.mapNotNull { it.movimiento }.toSet()
    val duplicado = cuenta?.let { c -> e.movimientos.firstOrNull { it.importado && it.id !in usados && coincideExtracto(it, a.momento.substringBefore('T'), if (a.tipo == TipoMovimiento.GASTO) -a.monto else a.monto, c) } }
    return agruparAnotadosBanco(e.copy(notificaciones = e.notificaciones.copy(registros = e.notificaciones.registros + RegistroBanco(a, movimiento = duplicado?.id))))
}

/** También agrupa si una mitad ya se había anotado mediante CSV y luego llegó su alerta. */
fun agruparAnotadosBanco(inicial: Estado): Estado {
    var e = inicial
    inicial.notificaciones.registros.forEach { original ->
        val r = e.notificaciones.registros.first { it.aviso.huella == original.aviso.huella }
        if (r.descartado || r.movimiento == null) return@forEach
        val pareja = e.parejaBanco(r) ?: return@forEach
        if (pareja.movimiento == null) return@forEach
        val gasto = if (r.aviso.tipo == TipoMovimiento.GASTO) r else pareja
        val ingreso = if (r.aviso.tipo == TipoMovimiento.INGRESO) r else pareja
        val salida = e.movimientos.firstOrNull { it.id == gasto.movimiento && it.tipo == TipoMovimiento.GASTO && it.monto == gasto.aviso.monto && it.cuenta == e.cuentaEnlazada(gasto.aviso) } ?: return@forEach
        val entrada = e.movimientos.firstOrNull { it.id == ingreso.movimiento && it.tipo == TipoMovimiento.INGRESO && it.monto == ingreso.aviso.monto && it.cuenta == e.cuentaEnlazada(ingreso.aviso) } ?: return@forEach
        val m = salida.copy(id = "t${gasto.aviso.huella}", tipo = TipoMovimiento.TRASPASO, aCuenta = entrada.cuenta,
            categoria = null, importado = salida.importado || entrada.importado)
        val huellas = setOf(gasto.aviso.huella, ingreso.aviso.huella)
        e = e.copy(movimientos = e.movimientos.filter { it.id != salida.id && it.id != entrada.id } + m,
            notificaciones = e.notificaciones.copy(registros = e.notificaciones.registros.map {
                if (it.aviso.huella in huellas) it.copy(movimiento = m.id, automatico = gasto.automatico || ingreso.automatico) else it
            }))
    }
    return e
}
