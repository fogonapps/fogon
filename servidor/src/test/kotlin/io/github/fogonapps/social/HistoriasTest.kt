package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class HistoriasTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100)

    @Test fun historiasPorAppVistasYVencimiento() {
        val dir = Files.createTempDirectory("historias").toFile()
        val b = Base(File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        try {
            val s = Social(b, File(dir, "fotos"), { reloj })
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("roberto")).token)!!
            val otra = s.cuentaDe(s.crearCuenta(NuevaCuenta("tercera")).token)!!
            s.seguir(beto, "anabel")
            // Vacía, con imagen ajena o con adjunto inventado: no.
            assertEquals("vacia", assertFailsWith<Problema> { s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("adjunto", buildJsonObject { put("tipo", "virus"); put("titulo", "x") }) })) }.mensaje)
            val img = s.subirImagen(otra, jpeg).nombre
            assertEquals("imagen", assertFailsWith<Problema> { s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("imagen", img) })) }.mensaje)
            // Con foto propia y una canción: el audio y el enlace solo de Apple; lo demás se descarta.
            val mia = s.subirImagen(ana, jpeg).nombre
            val h = s.subirHistoria(ana, "forzudo", HistoriaNueva(buildJsonObject {
                put("imagen", mia); put("texto", "Pierna hoy"); put("fondo", 99)
                put("adjunto", buildJsonObject {
                    put("tipo", "cancion"); put("titulo", "Canción"); put("audio", "https://audio-ssl.itunes.apple.com/a.m4a")
                    put("enlace", "https://evil.example/x"); put("imagen", "javascript:alert(1)"); put("raro", "x")
                })
            }))
            val adj = h.datos["adjunto"]!!.jsonObject
            assertEquals(setOf("tipo", "titulo", "audio"), adj.keys)
            assertEquals(7, h.datos["fondo"]!!.jsonPrimitive.int)
            // Por app: en El Cajón no sale; en El Forzudo, sí, para quien la sigue y no para quien no.
            assertTrue(s.historias(beto, "cajon").isEmpty())
            assertEquals(listOf(h.id), s.historias(beto, "forzudo").single().historias.map { it.id })
            assertFalse(s.historias(beto, "forzudo").single().historias.single().vista)
            assertTrue(s.historias(otra, "forzudo").isEmpty())
            assertEquals(404, assertFailsWith<Problema> { s.verHistoria(otra, h.id) }.codigo)
            s.verHistoria(beto, h.id)
            assertTrue(s.historias(beto, "forzudo").single().historias.single().vista)
            assertEquals(listOf("roberto"), s.vistasHistoria(ana, h.id).map { it.persona.usuario })
            assertEquals(404, assertFailsWith<Problema> { s.vistasHistoria(beto, h.id) }.codigo)
            // Me gusta: le llega un aviso a quien la subió (una sola vez) y lo ve en quién la vio.
            s.meGustaHistoria(beto, h.id, true); s.meGustaHistoria(beto, h.id, true)
            assertTrue(s.vistasHistoria(ana, h.id).single().meGusta)
            assertTrue(s.historias(beto, "forzudo").single().historias.single().meGusta)
            assertEquals(1, s.avisos(ana, null, "forzudo").count { it.tipo == "historia_megusta" })
            assertEquals(404, assertFailsWith<Problema> { s.meGustaHistoria(otra, h.id, true) }.codigo)
            // Hecha en el editor (con foto): queda marcada como compuesta; sin foto, no.
            val hecha = s.subirHistoria(ana, "forzudo", HistoriaNueva(buildJsonObject { put("imagen", s.subirImagen(ana, jpeg).nombre); put("compuesta", true); put("texto", "Hola") }))
            assertEquals(true, hecha.datos["compuesta"]?.jsonPrimitive?.boolean)
            assertNull(s.subirHistoria(ana, "forzudo", HistoriaNueva(buildJsonObject { put("compuesta", true); put("texto", "Hola") })).datos["compuesta"])
            // Reportada y retirada: deja de verse y su imagen también.
            s.reportar(beto, NuevoReporte("historia", "${h.id}", "spam"))
            // A las 24 horas se va; queda en el archivo de quien la subió y a los 90 días se borra con su imagen.
            reloj += 24 * 3600_000L + 1
            assertTrue(s.historias(beto, "forzudo").isEmpty())
            assertTrue(s.archivoHistorias(ana, "forzudo").any { it.id == hecha.id })
            reloj += 24 * 3600_000L
            s.limpiarHistorias()
            assertTrue(s.archivoHistorias(ana, "forzudo").any { it.id == hecha.id })
            reloj += 90 * 24 * 3600_000L
            s.limpiarHistorias()
            assertTrue(b.usar { leer("SELECT 1 FROM historias") { 1 } }.isEmpty())
            assertEquals(404, assertFailsWith<Problema> { s.imagen(mia, ana) }.codigo)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun notaConAdjuntoLimpio() {
        val dir = Files.createTempDirectory("notas").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"))
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            s.ponerNota(ana, NotaNueva("Escuchando", buildJsonObject {
                put("adjunto", buildJsonObject { put("tipo", "cancion"); put("titulo", "Una"); put("enlace", "https://music.apple.com/co/song/1"); put("escena", "abc"); put("segundo", 99999) })
            }))
            val adj = s.notas(ana).single().datos["adjunto"]!!.jsonObject
            assertEquals(setOf("tipo", "titulo", "enlace"), adj.keys)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun cancionesSoloConAvanceDeApple() {
        val cuerpo = """{"results":[
            {"trackName":"Buena","artistName":"A","artworkUrl100":"https://is1-ssl.mzstatic.com/x/100x100bb.jpg","previewUrl":"https://audio-ssl.itunes.apple.com/p.m4a","trackViewUrl":"https://music.apple.com/co/album/1"},
            {"trackName":"Sin avance","artistName":"B","artworkUrl100":"https://is1-ssl.mzstatic.com/y.jpg","trackViewUrl":"https://music.apple.com/co/album/2"},
            {"trackName":"Ajena","artistName":"C","artworkUrl100":"https://is1-ssl.mzstatic.com/z.jpg","previewUrl":"https://otro.example/p.m4a","trackViewUrl":"https://music.apple.com/co/album/3"}]}"""
        var pedidos = 0
        val c = Canciones(pedir = { pedidos++; cuerpo })
        val l = c.buscar("buena")
        assertEquals(listOf("Buena"), l.map { it.titulo })
        assertTrue(l.single().portada.endsWith("300x300bb.jpg"))
        c.buscar("Buena ")
        assertEquals(1, pedidos)
        assertTrue(c.buscar("a").isEmpty())
    }

    @Test fun amigosMejoresAmigosYSilenciar() {
        val dir = Files.createTempDirectory("amigos").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"))
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("roberto")).token)!!
            val cami = s.cuentaDe(s.crearCuenta(NuevaCuenta("camila")).token)!!
            s.seguir(ana, "roberto"); s.seguir(beto, "anabel"); s.seguir(cami, "anabel")
            // Amigos: solo quien se sigue mutuamente.
            assertEquals(listOf("roberto"), s.amigos(ana).map { it.usuario })
            // Mejores amigos: solo entre los amigos; la historia «solo mejores» no la ve quien no está.
            s.ponerMejoresAmigos(ana, ListaUsuarios(listOf("roberto", "camila")))
            assertEquals(listOf("roberto"), s.mejoresAmigos(ana))
            val h = s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("texto", "Solo ustedes"); put("mejores", true) }))
            assertTrue(h.mejores)
            assertEquals(1, s.historias(beto, "cajon").size)
            assertTrue(s.historias(cami, "cajon").isEmpty())
            // Silenciar: siguen saliendo, marcadas y al final.
            s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("texto", "Para todos") }))
            s.silenciarHistorias(cami, "anabel", true)
            assertTrue(s.historias(cami, "cajon").single().silenciada)
            s.silenciarHistorias(cami, "anabel", false)
            assertFalse(s.historias(cami, "cajon").single().silenciada)
            // Cumpleaños: solo día y mes válidos; lo ven solo los amigos.
            assertEquals(400, assertFailsWith<Problema> { s.ponerCumple(beto, Cumple("02-30")) }.codigo)
            s.ponerCumple(beto, Cumple("03-14")); s.ponerCumple(cami, Cumple("05-01"))
            assertEquals(listOf("roberto" to "03-14"), s.cumplesAmigos(ana).map { it.persona.usuario to it.fecha })
            assertEquals("03-14", s.miCumple(beto))
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun encuestaYDestacadas() {
        val dir = Files.createTempDirectory("encuesta").toFile()
        val b = Base(File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        try {
            val s = Social(b, File(dir, "fotos"), { reloj })
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("roberto")).token)!!
            s.seguir(beto, "anabel")
            val h = s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject {
                put("encuesta", buildJsonObject { put("pregunta", "¿Cuál primero?"); put("opciones", JsonArray(listOf(JsonPrimitive("Chihiro"), JsonPrimitive("Totoro")))) })
            }))
            assertEquals(400, assertFailsWith<Problema> { s.votarHistoria(beto, h.id, Voto(5)) }.codigo)
            val votada = s.votarHistoria(beto, h.id, Voto(1))
            assertEquals(listOf(0, 1), votada.votos); assertEquals(1, votada.miVoto)
            assertEquals(listOf(0, 1), s.historias(ana, "cajon").single().historias.single().votos)
            assertEquals(1, s.avisos(ana, null, "cajon").count { it.tipo == "historia_voto" })
            // Pregunta: responder avisa y solo lo lee quien la subió.
            val p = s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("pregunta", "Recomiéndame un libro"); put("menciones", JsonArray(listOf(JsonPrimitive("roberto")))) }))
            assertEquals(1, s.avisos(beto, null, "cajon").count { it.tipo == "historia_mencion" })
            s.responderPregunta(beto, p.id, TextoRespuesta("Rayuela"))
            assertEquals(listOf("Rayuela"), s.respuestasPregunta(ana, p.id).map { it.texto })
            assertEquals(404, assertFailsWith<Problema> { s.respuestasPregunta(beto, p.id) }.codigo)
            // Solo amigos: roberto sigue a ana, pero ana no a él: no la ve.
            s.subirHistoria(ana, "cajon", HistoriaNueva(buildJsonObject { put("texto", "Amigos"); put("amigos", true) }))
            assertEquals(2, s.historias(beto, "cajon").single().historias.size)
            // Destacada: sale en el perfil y no se borra al vencer.
            s.destacarHistoria(ana, h.id, Destacar("Ghibli"))
            assertEquals(404, assertFailsWith<Problema> { s.destacarHistoria(beto, h.id, Destacar("x")) }.codigo)
            reloj += 3 * 24 * 3600_000L
            s.limpiarHistorias()
            assertEquals(listOf("Ghibli"), s.destacadas(beto, "anabel", "cajon").map { it.titulo })
            assertTrue(s.historias(beto, "cajon").isEmpty())
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun mensajesLleganATodasLasSesiones() {
        val dir = Files.createTempDirectory("chat").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"))
            val ana = s.cuentaDe(s.crearCuenta(NuevaCuenta("anabel")).token)!!
            val beto = s.cuentaDe(s.crearCuenta(NuevaCuenta("roberto")).token)!!
            b.usar { cambiar("INSERT INTO sesiones(huella, cuenta, creada, ultimo_uso, app) VALUES('cajon', ?, 0, 0, 'cajon'), ('forzudo', ?, 0, 0, 'forzudo')", ana, ana) }
            s.mensajesSinLeer(ana, "forzudo")
            s.mandar(beto, "anabel", SobreNuevo("x".repeat(40), "f".repeat(40)))
            // El Cajón lo recibe y confirma; El Forzudo todavía lo encuentra.
            val id = s.recibir(ana, 0, "cajon").single().id
            assertTrue(s.recibir(ana, id, "cajon").isEmpty())
            assertEquals(1, s.mensajesSinLeer(ana, "forzudo"))
            assertEquals(0, s.mensajesSinLeer(ana, "cajon"))
            assertEquals(id, s.recibir(ana, 0, "forzudo").single().id)
            // Cuando las dos lo recibieron, se borra.
            s.recibir(ana, id, "forzudo")
            assertTrue(b.usar { leer("SELECT 1 FROM mensajes") { 1 } }.isEmpty())
            // Las llaves privadas del chat (cifradas en el teléfono) se guardan tal cual.
            s.ponerLlavesChat(ana, CajonCifrado(llave = "sobre-cifrado"))
            assertEquals("sobre-cifrado", s.llavesChat(ana).llave)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }
}
