package io.github.fogonapps.social

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val MAX_VIDEO = 15_000_000
internal data class DatosVideo(val duracion: Double, val ancho: Int, val alto: Int)

/** Lector acotado de ISO BMFF: no decodifica ni ejecuta herramientas externas. */
internal fun revisarVideo(bytes: ByteArray): DatosVideo {
    if (bytes.size > MAX_VIDEO) throw Problema(400, "video_grande")
    fun formato(): Nothing = throw Problema(400, "video_formato")
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    data class Caja(val tipo: String, val inicio: Int, val fin: Int)
    var cuantasCajas = 0
    fun cajas(inicio: Int, fin: Int): List<Caja> {
        val lista = mutableListOf<Caja>()
        var p = inicio
        while (p < fin) {
            if (++cuantasCajas > 8192) formato()
            if (fin - p < 8) formato()
            val tam = b.getInt(p).toLong() and 0xffffffffL
            val tipo = String(bytes, p + 4, 4, Charsets.ISO_8859_1)
            val cab = if (tam == 1L) 16 else 8
            if (fin - p < cab) formato()
            val largo = when (tam) { 0L -> (fin - p).toLong(); 1L -> b.getLong(p + 8); else -> tam }
            if (largo < cab || largo > fin - p) formato()
            lista += Caja(tipo, p + cab, p + largo.toInt())
            p += largo.toInt()
        }
        return lista
    }
    fun List<Caja>.una(tipo: String): Caja = singleOrNull { it.tipo == tipo } ?: formato()
    fun Caja.hijos() = cajas(inicio, fin)
    fun Caja.exige(n: Int) { if (fin - inicio < n) formato() }
    fun u32(p: Int) = b.getInt(p).toLong() and 0xffffffffL
    val raiz = cajas(0, bytes.size)
    val ftyp = raiz.una("ftyp"); ftyp.exige(8)
    val marcas = (ftyp.inicio until ftyp.fin step 4).filter { it != ftyp.inicio + 4 }.map {
        if (it + 4 > ftyp.fin) formato()
        String(bytes, it, 4, Charsets.ISO_8859_1)
    }
    if (marcas.none { it in setOf("isom", "iso2", "mp41", "mp42", "avc1") }) formato()
    if (raiz.any { it.tipo == "moof" }) formato()
    val moov = raiz.una("moov").hijos()
    if (moov.any { it.tipo == "mvex" }) formato()
    val mvhd = moov.una("mvhd"); mvhd.exige(20)
    val version = bytes[mvhd.inicio].toInt()
    val escala: Long
    val ticks: Long
    when (version) {
        0 -> { escala = u32(mvhd.inicio + 12); ticks = u32(mvhd.inicio + 16) }
        1 -> { mvhd.exige(32); escala = u32(mvhd.inicio + 20); ticks = b.getLong(mvhd.inicio + 24) }
        else -> formato()
    }
    if (escala == 0L || ticks <= 0) formato()
    val duracion = ticks.toDouble() / escala
    if (duracion > 31) throw Problema(400, "video_duracion")
    var ancho = 0
    var alto = 0
    var videos = 0
    var audios = 0
    for (trak in moov.filter { it.tipo == "trak" }) {
        val hijos = trak.hijos()
        val mdia = hijos.una("mdia").hijos()
        val hdlr = mdia.una("hdlr"); hdlr.exige(12)
        val tipo = String(bytes, hdlr.inicio + 8, 4, Charsets.ISO_8859_1)
        if (tipo !in setOf("vide", "soun")) formato()
        val stsd = mdia.una("minf").hijos().una("stbl").hijos().una("stsd")
        stsd.exige(8)
        val entradas = cajas(stsd.inicio + 8, stsd.fin)
        if (entradas.size != 1 || u32(stsd.inicio + 4) != 1L) formato()
        if (entradas.single().tipo != if (tipo == "vide") "avc1" else "mp4a") formato()
        if (tipo == "vide") {
            videos++
            val tkhd = hijos.una("tkhd"); tkhd.exige(4)
            val desplazamiento = when (bytes[tkhd.inicio].toInt()) { 0 -> 76; 1 -> 88; else -> formato() }
            tkhd.exige(desplazamiento + 8)
            val w = u32(tkhd.inicio + desplazamiento)
            val h = u32(tkhd.inicio + desplazamiento + 4)
            if (w == 0L || h == 0L) formato()
            if (maxOf(w, h) > 1280L * 65536) throw Problema(400, "video_grande")
            ancho = (w / 65536).toInt(); alto = (h / 65536).toInt()
        } else audios++
    }
    if (videos != 1 || audios > 1) formato()
    return DatosVideo(duracion, ancho, alto)
}

/** Un único rango, incluidos sufijos; sin asignaciones del tamaño del archivo. */
internal fun rangoVideo(cabecera: String, largo: Long): LongRange? {
    val m = Regex("bytes=(\\d*)-(\\d*)").matchEntire(cabecera) ?: return null
    val (a, z) = m.destructured
    if (a.isEmpty()) {
        val n = z.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return maxOf(0, largo - n)..(largo - 1)
    }
    val inicio = a.toLongOrNull() ?: return null
    val fin = if (z.isEmpty()) largo - 1 else z.toLongOrNull() ?: return null
    if (inicio >= largo || fin < inicio) return null
    return inicio..minOf(fin, largo - 1)
}
