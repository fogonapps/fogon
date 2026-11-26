package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Reportes que ocultan, lo preservado para las autoridades, quién puede escribir y la revisión automática de fotos. */
class SeguridadInfantilTest {
    private class Entorno(revisor: RevisorFotos? = null) : AutoCloseable {
        val dir = Files.createTempDirectory("seguridad-infantil").toFile()
        val b = Base(File(dir, "social.db").path)
        var reloj = 2_000_000_000_000L
        val correos = mutableListOf<Pair<String, String>>()
        val s = Social(b, File(dir, "fotos"), { reloj }, Cartero { _, t, c -> correos += t to c }, revisor = revisor, revisarEn = { it.run() })
        data class Cuenta(val id: Long, val token: String, val usuario: String)
        /** Una cuenta creada hace [dias] días (y, si se da, desde la dirección [ip]). */
        fun alta(usuario: String, dias: Int = 60, ip: String? = null): Cuenta {
            val a = s.crearCuenta(NuevaCuenta(usuario))
            val id = s.cuentaDe(a.token)!!
            b.usar { cambiar("UPDATE cuentas SET creada = ?, ip_registro = ? WHERE id = ?", reloj - dias * DIA, ip?.let(s::huellaDireccion), id) }
            return Cuenta(id, a.token, usuario)
        }
        fun equipo(usuario: String, rol: String): Cuenta {
            val c = alta(usuario)
            b.usar { cambiar("INSERT INTO roles VALUES(?,?, 'servidor', ?)", c.id, rol, reloj) }
            val i = s.equipo.activar(c.id, Factor(), c.token)
            val bytes = Totp.decodificar(i.secreto)
            s.equipo.confirmar(c.id, Factor(codigo = Totp.codigo(bytes, reloj / 30_000)), c.token)
            reloj += 30_000
            s.equipo.elevar(c.id, Factor(codigo = Totp.codigo(bytes, reloj / 30_000)), c.token)
            return c
        }
        fun pub(c: Cuenta, clave: String = "a", datos: JsonObject = buildJsonObject { put("texto", "Algo") }): Long {
            s.subirPublicaciones(c.id, listOf(PublicacionSubida(clave, Vis.PUBLICA, reloj, "diario", datos)))
            return b.usar { leer("SELECT id FROM publicaciones WHERE cuenta = ? AND clave = ?", c.id, clave) { it.getLong(1) }.single() }
        }
        fun oculta(pub: Long) = b.usar { leer("SELECT oculta FROM publicaciones WHERE id = ?", pub) { it.getInt(1) == 1 }.single() }
        fun reportar(c: Cuenta, pub: Long, motivo: String = "spam") = s.reportar(c.id, NuevoReporte("publicacion", "$pub", "$motivo: texto"))
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }

    /* ----- Ocultar mientras se revisa ----- */

    @Test fun unaSolaCuentaNuevaNoOcultaNadaNiConMotivoGrave(): Unit = Entorno().use { e ->
        val autora = e.alta("autora"); val nueva = e.alta("nueva", dias = 0)
        val pub = e.pub(autora)
        e.reportar(nueva, pub, Moderacion.MASI)
        assertFalse(e.oculta(pub))
        // Tampoco cambiando de motivo hasta su tope diario.
        repeat(Moderacion.REPORTES_DIA - 1) { e.reportar(nueva, pub, if (it % 2 == 0) Moderacion.MENOR_EN_RIESGO else "spam") }
        assertFalse(e.oculta(pub))
        assertEquals(429, assertFailsWith<Problema> { e.reportar(nueva, pub) }.codigo)
        assertNotNull(e.s.publicacion(null, pub))
    }

    @Test fun tresCuentasQueCuentanOcultanYLasDemasNo() = Entorno().use { e ->
        val autora = e.alta("autora"); val pub = e.pub(autora)
        val sancionada = e.alta("sancionada").also { e.b.usar { cambiar("INSERT INTO faltas(cuenta, puntos, regla, cuando, vence) VALUES(?,1,'spam',?,?)", it.id, e.reloj, e.reloj + Moderacion.VENCE) } }
        val bloqueada = e.alta("bloqueada").also { e.s.bloquear(autora.id, it.usuario, true) }
        val reciente = e.alta("reciente", dias = 6)
        val restringida = e.alta("restringida").also { e.b.usar { cambiar("UPDATE cuentas SET restringida_hasta = ? WHERE id = ?", e.reloj + DIA, it.id) } }
        // Varias creadas desde la misma dirección casi a la vez cuentan como una sola.
        val gemela1 = e.alta("gemela1", dias = 40, ip = "203.0.113.9"); val gemela2 = e.alta("gemela2", dias = 40, ip = "203.0.113.9")
        listOf(sancionada, bloqueada, reciente, restringida, gemela1, gemela2).forEach { e.reportar(it, pub) }
        e.reportar(autora, pub)
        assertFalse(e.oculta(pub))
        e.reportar(e.alta("buena1"), pub)
        assertFalse(e.oculta(pub))                                              // gemelas (1) + buena1 = 2
        e.reportar(e.alta("buena2"), pub)
        assertTrue(e.oculta(pub))                                               // 3
        assertNotNull(e.s.publicacion(autora.id, pub))                         // su autora la sigue viendo
        assertNull(e.s.publicacion(null, pub))
        // A la autora no se le dice quién reportó (ni que reportaron).
        assertTrue(e.s.avisos(autora.id, null).isEmpty())
    }

    @Test fun lasMismasDireccionesConMasDeUnDiaDeDiferenciaSonPersonasDistintas() = Entorno().use { e ->
        val pub = e.pub(e.alta("autora"))
        e.reportar(e.alta("dir_a1", dias = 40, ip = "198.51.100.1"), pub)
        e.reportar(e.alta("dir_a2", dias = 38, ip = "198.51.100.1"), pub)
        e.reportar(e.alta("dir_a3", dias = 36, ip = "198.51.100.1"), pub)
        assertTrue(e.oculta(pub))
    }

    @Test fun laDireccionDeRegistroSeGuardaComoHuellaYSeBorraALos90Dias() = Entorno().use { e ->
        e.s.pedirCodigo("ana@correo.co")
        val codigo = Regex("[0-9]{6}").find(e.correos.last { it.first.startsWith("Tu código") }.first)!!.value
        val a = e.s.entrar(Entrada("ana@correo.co", codigo, "ana", "Ana", Social.POLITICA, true), "", "203.0.113.20")
        val guardada = e.b.usar { leer("SELECT ip_registro FROM cuentas WHERE usuario = 'ana'") { it.getString(1) }.single() }
        assertEquals(e.s.huellaDireccion("203.0.113.20"), guardada)
        assertFalse(guardada.contains("203"))
        e.reloj += 91 * DIA; e.s.limpiarSeguridad()
        assertNull(e.b.usar { leer("SELECT ip_registro FROM cuentas WHERE usuario = ?", a.usuario) { it.getString(1) }.single() })
    }

    @Test fun loGraveSeOcultaConDosCuentasOUnaDeConfianzaYAvisaAlEquipo() = Entorno().use { e ->
        val principal = e.equipo("fogon", "principal"); val moderador = e.equipo("mod", "moderador")
        val autora = e.alta("autora")
        val pub = e.pub(autora)
        // Una cuenta que cuenta pero no es de confianza (menos de 30 días): no oculta sola, pero el caso va primero y avisa.
        e.reportar(e.alta("normal", dias = 10), pub, Moderacion.MASI)
        assertFalse(e.oculta(pub))
        e.reportar(e.alta("spam1"), e.pub(autora, "b"))
        assertEquals("$pub", e.s.casos(moderador.id, moderador.token).first().objeto)
        for (c in listOf(principal, moderador)) {
            val aviso = e.s.avisos(c.id, null).single { it.tipo == "reporte_urgente" }
            assertEquals("", aviso.objeto)                                      // sin nada del contenido
        }
        // Un segundo reporte grave de otra cuenta que cuenta la oculta.
        e.reportar(e.alta("otra", dias = 8), pub, Moderacion.MENOR_EN_RIESGO)
        assertTrue(e.oculta(pub))
        // Una sola cuenta de confianza basta.
        val pub2 = e.pub(autora, "c")
        e.reportar(e.alta("confiable", dias = 31), pub2, Moderacion.MASI)
        assertTrue(e.oculta(pub2))
        // Pero no si le descartaron un reporte hace poco.
        val pub3 = e.pub(autora, "d")
        val dudosa = e.alta("dudosa", dias = 200)
        e.b.usar { cambiar("INSERT INTO reportes(de, que, objeto, motivo, cuando, estado) VALUES(?, 'cuenta', 'x', 'spam', ?, 'sin_falta')", dudosa.id, e.reloj - DIA) }
        e.reportar(dudosa, pub3, Moderacion.MASI)
        assertFalse(e.oculta(pub3))
    }

    @Test fun conTresReportesDescartadosEnNoventaDiasSusReportesNoOcultan() = Entorno().use { e ->
        val mod = e.equipo("mod", "administrador")
        val autora = e.alta("autora")
        val falsa = e.alta("falsa"); val b1 = e.alta("buena_b1"); val b2 = e.alta("buena_b2")
        repeat(Moderacion.DESCARTADOS_MAX) { i ->
            val p = e.pub(autora, "x$i")
            e.reportar(falsa, p)
            e.s.decidir(mod.id, Decision("publicacion", "$p", "sin_falta"), mod.token)
        }
        val pub = e.pub(autora)
        listOf(falsa, b1, b2).forEach { e.reportar(it, pub) }
        assertFalse(e.oculta(pub))
        assertTrue(e.s.casos(mod.id, mod.token).any { it.objeto == "$pub" && it.personas == 3 })   // sí llega a la cola
        // Pasados los 90 días vuelve a contar.
        e.reloj += 91 * DIA
        e.reportar(e.alta("buena_b3"), pub)
        assertTrue(e.oculta(pub))
    }

    @Test fun siElEquipoRestauraLaPublicacionVuelveTalCual() = Entorno().use { e ->
        val mod = e.equipo("mod", "administrador")
        val autora = e.alta("autora")
        val datos = buildJsonObject { put("texto", "Mi reseña"); put("titulo", "Una obra") }
        val pub = e.pub(autora, datos = datos)
        listOf("rep1", "rep2", "rep3").forEach { e.reportar(e.alta(it), pub) }
        assertTrue(e.oculta(pub))
        e.s.decidir(mod.id, Decision("publicacion", "$pub", "sin_falta"), mod.token)
        val vuelta = e.s.publicacion(null, pub)!!
        assertEquals("Mi reseña", vuelta.datos["texto"]!!.jsonPrimitive.content)
        assertEquals("Una obra", vuelta.datos["titulo"]!!.jsonPrimitive.content)
    }

    @Test fun loQueEstaEnRevisionNoSeBorraNiSeCambiaDesdeLaApp() = Entorno().use { e ->
        val autora = e.alta("autora"); val pub = e.pub(autora)
        e.reportar(e.alta("confiable"), pub, Moderacion.MASI)
        assertTrue(e.oculta(pub))
        e.s.subirPublicaciones(autora.id, listOf(PublicacionSubida("a", Vis.PUBLICA, e.reloj, "diario", buildJsonObject { put("texto", "Otra cosa") })))
        assertEquals("Algo", e.b.usar { leer("SELECT json_extract(datos, '$.texto') FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
        e.s.borrarPublicacion(autora.id, pub)
        e.s.limpiarSeguridad()
        assertEquals("retirado", e.b.usar { leer("SELECT estado FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
        // Y la cuenta no se puede borrar mientras tenga un reporte grave sin decidir.
        e.s.borrarCuenta(autora.id)
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM cuentas WHERE id = ?", autora.id) { it.getInt(1) }.single() })
    }

    /* ----- Preservado ----- */

    @Test fun loGraveDecididoQuedaPreservadoCifradoYSoloLoExportaLaPrincipal() = Entorno().use { e ->
        val principal = e.equipo("fogon", "principal"); val admin = e.equipo("adminis", "administrador")
        val autora = e.alta("autora")
        val foto = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        val pub = e.pub(autora, datos = buildJsonObject { put("texto", "Evidencia"); put("imagenes", buildJsonArray { add(foto) }) })
        e.reportar(e.alta("confiable"), pub, Moderacion.MASI)
        val original = File(e.dir, "fotos/i").listFiles().orEmpty().single { it.name.startsWith(foto) }.readBytes()
        e.s.decidir(admin.id, Decision("publicacion", "$pub", "cerrar", Moderacion.MASI), admin.token)
        // Preservado: sin contenido legible en la base ni la foto en el disco; cuenta cerrada; nadie lo ve.
        val (estado, datos) = e.b.usar { leer("SELECT estado, datos FROM publicaciones WHERE id = ?", pub) { it.getString(1) to it.getString(2) }.single() }
        assertEquals("preservado", estado); assertEquals("{}", datos)
        val sobre = e.b.usar { leer("SELECT sobre FROM preservados") { it.getString(1) }.single() }
        assertFalse(String(java.util.Base64.getDecoder().decode(sobre), Charsets.ISO_8859_1).contains("Evidencia"))
        assertTrue(File(e.dir, "fotos/i").listFiles().orEmpty().none { it.name.startsWith(foto) })
        assertTrue(e.s.estadoCuenta(autora.id).cerrada)
        assertFailsWith<Problema> { e.s.imagen(foto, admin.id, admin.token) }
        assertNull(e.s.publicacion(admin.id, pub))
        assertTrue(e.s.casos(admin.id, admin.token).none { it.objeto == "$pub" })
        // Solo la principal lo lista y lo exporta, con motivo, y queda en el registro.
        assertEquals("solo_principal", assertFailsWith<Problema> { e.s.preservados(admin.id, admin.token) }.mensaje)
        val caso = e.s.preservados(principal.id, principal.token).single()
        assertEquals(Moderacion.MASI, caso.regla); assertEquals("autora", caso.usuario)
        assertFailsWith<Problema> { e.s.exportarPreservado(admin.id, caso.id, "Requerimiento", admin.token) }
        assertEquals("motivo", assertFailsWith<Problema> { e.s.exportarPreservado(principal.id, caso.id, " ", principal.token) }.mensaje)
        // La foto queda cifrada por partes en la carpeta del caso, no en la base.
        val cifrada = File(e.dir, "preservados/${caso.id}").listFiles().orEmpty().single()
        assertTrue(cifrada.name.startsWith(foto) && cifrada.name.endsWith(".fgp"))
        assertFalse(String(cifrada.readBytes(), Charsets.ISO_8859_1).contains(String(original.copyOfRange(0, 64), Charsets.ISO_8859_1)))
        // El paquete para la autoridad: un archivo age cifrado; la frase se da una sola vez y el servidor no la guarda.
        val paquete = e.s.exportarPreservado(principal.id, caso.id, "Requerimiento 123", principal.token)
        val archivo = e.s.paqueteAutoridad(principal.id, paquete.archivo, principal.token)
        val bytes = archivo.readBytes()
        assertEquals(paquete.bytes, bytes.size.toLong())
        assertEquals(paquete.sha256, java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Evidencia"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(paquete.frase))
        assertTrue(e.b.usar { leer("SELECT detalle FROM registro_equipo") { it.getString(1) } }.none { it.contains(paquete.frase) })
        assertFails { CifradoTest.abrirAge("otra-frase-cualquiera", bytes) }
        val zip = CifradoTest.entradasZip(CifradoTest.abrirAge(paquete.frase, bytes))
        val exportado = JsonSocial.decodeFromString(CasoExportado.serializer(), String(zip.getValue("caso.json")))
        assertTrue(exportado.contenido.contains("Evidencia"))
        assertTrue(exportado.reportes.single().startsWith(Moderacion.MASI))
        assertEquals("Requerimiento 123", exportado.motivo)
        val nombreFoto = exportado.archivos.single()
        assertContentEquals(original, zip.getValue("archivos/$nombreFoto"))
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'preservado_exportar' AND actor = ? AND detalle LIKE 'Requerimiento 123 · paquete cifrado sha256:%'", principal.id.toString()) { it.getInt(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'preservado_descargar'") { it.getInt(1) }.single() })
        // Solo la principal lo descarga.
        assertFailsWith<Problema> { e.s.paqueteAutoridad(admin.id, paquete.archivo, admin.token) }
        // La limpieza diaria no lo toca, ni pasados años; la cuenta tampoco se borra.
        e.b.usar { cambiar("UPDATE cuentas SET borrar_el = ? WHERE id = ?", e.reloj, autora.id) }
        e.reloj += 3 * 365 * DIA; e.s.limpiarSeguridad()
        assertEquals("preservado", e.b.usar { leer("SELECT estado FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM reportes WHERE que = 'publicacion' AND objeto = ?", "$pub") { it.getInt(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM cuentas WHERE id = ?", autora.id) { it.getInt(1) }.single() })
        // (La sesión de la principal venció con los años: vuelve a entrar y a elevarla.)
        e.b.usar { cambiar("INSERT OR REPLACE INTO sesiones(huella, cuenta, creada, ultimo_uso, elevada_hasta) VALUES(?,?,?,?,?)", Base.huella(principal.token), principal.id, e.reloj, e.reloj, Long.MAX_VALUE) }
        // Solo cuando la principal lo libera se borra.
        e.s.liberarPreservado(principal.id, caso.id, "Entregado a la Fiscalía", principal.token)
        e.s.limpiarSeguridad()
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM publicaciones WHERE id = ?", pub) { it.getInt(1) }.single() })
        assertEquals("", e.b.usar { leer("SELECT sobre FROM preservados") { it.getString(1) }.single() })
        assertFalse(File(e.dir, "preservados/${caso.id}").exists())
    }

    @Test fun siLaDecisionFallaLosArchivosOriginalesNoSeBorran() = Entorno().use { e ->
        val admin = e.equipo("adminis", "administrador")
        val autora = e.alta("autora")
        val foto = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        val pub = e.pub(autora, datos = buildJsonObject { put("texto", "Evidencia"); put("imagenes", buildJsonArray { add(foto) }) })
        e.reportar(e.alta("confiable"), pub, Moderacion.MASI)
        // La carpeta de lo preservado no se puede crear (un archivo en su lugar): falla a mitad de la decisión.
        File(e.dir, "preservados").writeText("estorbo")
        assertFails { e.s.decidir(admin.id, Decision("publicacion", "$pub", "cerrar", Moderacion.MASI), admin.token) }
        assertTrue(File(e.dir, "fotos/i").listFiles().orEmpty().any { it.name.startsWith(foto) })
        assertEquals("visible", e.b.usar { leer("SELECT estado FROM publicaciones WHERE id = ?", pub) { it.getString(1) }.single() })
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM imagenes WHERE nombre = ?", foto) { it.getInt(1) }.single() })
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM preservados") { it.getInt(1) }.single() })
        // Con la carpeta disponible, la misma decisión sale bien y recién ahí se borra el original.
        File(e.dir, "preservados").delete()
        e.s.decidir(admin.id, Decision("publicacion", "$pub", "cerrar", Moderacion.MASI), admin.token)
        assertTrue(File(e.dir, "fotos/i").listFiles().orEmpty().none { it.name.startsWith(foto) })
    }

    @Test fun lasTareasDeDiscoCorrenSoloSiLaTransaccionSeConfirma() = Entorno().use { e ->
        val hechas = mutableListOf<String>()
        assertFails { e.b.transaccion { e.b.despuesDeConfirmar { hechas += "borrar" }; e.b.siSeDeshace { hechas += "limpiar" }; error("falla") } }
        assertEquals(listOf("limpiar"), hechas)
        hechas.clear()
        e.b.transaccion { e.b.despuesDeConfirmar { hechas += "borrar" }; e.b.siSeDeshace { hechas += "limpiar" } }
        assertEquals(listOf("borrar"), hechas)
        hechas.clear()
        e.b.despuesDeConfirmar { hechas += "en el acto" }
        assertEquals(listOf("en el acto"), hechas)
    }

    @Test fun unVideoDe15MbSePreservaYSeExportaPorPartes() = Entorno().use { e ->
        val principal = e.equipo("fogon", "principal"); val admin = e.equipo("adminis", "administrador")
        val autora = e.alta("autora")
        val video = "v" + "a".repeat(20)
        val datos = ByteArray(15 * 1024 * 1024).also { java.util.Random(3).nextBytes(it) }
        File(e.dir, "videos").mkdirs(); File(e.dir, "videos/$video.mp4").writeBytes(datos)
        e.b.usar { cambiar("INSERT INTO videos(nombre, cuenta, cuando, duracion, ancho, alto) VALUES(?,?,?,10.0,720,1280)", video, autora.id, e.reloj) }
        val poster = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        e.s.subirPublicaciones(autora.id, listOf(PublicacionSubida("v", Vis.PUBLICA, e.reloj, "foto", buildJsonObject { put("texto", "Video"); put("video", video); put("poster", poster) })))
        val pub = e.b.usar { leer("SELECT id FROM publicaciones WHERE clave = 'v'") { it.getLong(1) }.single() }
        e.reportar(e.alta("confiable"), pub, Moderacion.MASI)
        e.s.decidir(admin.id, Decision("publicacion", "$pub", "cerrar", Moderacion.MASI), admin.token)
        assertFalse(File(e.dir, "videos/$video.mp4").exists())
        // En la base solo quedan los metadatos (el sobre es chico); el video va cifrado en su carpeta.
        assertTrue(e.b.usar { leer("SELECT length(sobre) FROM preservados") { it.getInt(1) }.single() } < 10_000)
        val caso = e.s.preservados(principal.id, principal.token).single()
        val cifrado = File(e.dir, "preservados/${caso.id}/video-$video.mp4.fgp")
        assertTrue(cifrado.length() > datos.size)
        val paquete = e.s.exportarPreservado(principal.id, caso.id, "Requerimiento 9", principal.token)
        val zip = CifradoTest.entradasZip(CifradoTest.abrirAge(paquete.frase, e.s.paqueteAutoridad(principal.id, paquete.archivo, principal.token).readBytes()))
        assertContentEquals(datos, zip.getValue("archivos/video-$video.mp4"))
    }

    /* ----- Transición del chat ----- */

    @Test fun durante30DiasValeLaReglaDeAntesYLosChatsQuedanAnotados() = Entorno().use { e ->
        val ana = e.alta("ana"); val beto = e.alta("beto"); val caro = e.alta("caro"); val dani = e.alta("dani")
        // La regla llegó a una base que ya tenía cuentas: empieza la transición (se mira antes del primer mensaje).
        e.b.usar { cambiar("UPDATE configuracion SET valor = ? WHERE clave = 'chats_transicion_desde'", e.reloj.toString()) }
        val sobre = SobreNuevo("cifrado", "firma")
        // Beto sigue a Ana, Ana no a él: con la regla de antes puede escribirle, y el chat queda anotado.
        e.s.seguir(beto.id, "ana")
        assertTrue(e.s.verPerfil(beto.id, "ana").puedoEscribir)
        e.s.mandar(beto.id, "ana", sobre)
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM conversaciones WHERE a = ? AND b = ?", minOf(ana.id, beto.id), maxOf(ana.id, beto.id)) { it.getInt(1) }.single() })
        // El tope de antes sigue: 20 por hora a quien no te sigue.
        repeat(19) { e.s.mandar(beto.id, "ana", sobre) }
        assertEquals(429, assertFailsWith<Problema> { e.s.mandar(beto.id, "ana", sobre) }.codigo)
        // El bloqueo también.
        e.s.bloquear(ana.id, "caro", true)
        assertFailsWith<Problema> { e.s.mandar(caro.id, "ana", sobre) }
        assertEquals(0, e.b.usar { leer("SELECT COUNT(*) FROM conversaciones WHERE a = ? OR b = ?", caro.id, caro.id) { it.getInt(1) }.single() })
        // Pasados los 30 días: el chat anotado sigue, uno nuevo necesita la regla nueva.
        e.reloj += 31 * DIA
        e.s.mandar(beto.id, "ana", sobre)
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(dani.id, "ana", sobre) }.mensaje)
        assertFalse(e.s.verPerfil(dani.id, "ana").puedoEscribir)
    }

    @Test fun enLaTransicionSeRespetanNadieYSigo() = Entorno().use { e ->
        val ana = e.alta("ana"); val beto = e.alta("beto"); val caro = e.alta("caro")
        e.b.usar { cambiar("UPDATE configuracion SET valor = ? WHERE clave = 'chats_transicion_desde'", e.reloj.toString()) }
        val sobre = SobreNuevo("cifrado", "firma")
        // Ana eligió «nadie»: ni con la regla de antes le escriben.
        e.s.ajustar(ana.id, Ajustes(escribir = "nadie"))
        e.s.seguir(beto.id, "ana")
        assertFalse(e.s.verPerfil(beto.id, "ana").puedoEscribir)
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(beto.id, "ana", sobre) }.mensaje)
        // Con «sigo», solo quien Ana sigue.
        e.s.ajustar(ana.id, Ajustes(escribir = "sigo"))
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(beto.id, "ana", sobre) }.mensaje)
        e.s.seguir(ana.id, "caro")
        e.s.mandar(caro.id, "ana", sobre)
        // Con «mutuos» (lo predeterminado) vuelve la regla de antes mientras dura la transición.
        e.s.ajustar(ana.id, Ajustes(escribir = "mutuos"))
        assertTrue(e.s.mandar(beto.id, "ana", sobre) > 0)
    }

    @Test fun unaCuentaCreadaEnLaTransicionUsaLaReglaNueva() = Entorno().use { e ->
        val ana = e.alta("ana"); val beto = e.alta("beto")
        e.b.usar { cambiar("UPDATE configuracion SET valor = ? WHERE clave = 'chats_transicion_desde'", (e.reloj - 10 * DIA).toString()) }
        val sobre = SobreNuevo("cifrado", "firma")
        // Una cuenta nueva (creada hace 2 días, ya en la transición) no puede escribirle a quien no la sigue de vuelta.
        val nueva = e.alta("nueva", dias = 2)
        e.s.seguir(nueva.id, "ana")
        assertFalse(e.s.verPerfil(nueva.id, "ana").puedoEscribir)
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(nueva.id, "ana", sobre) }.mensaje)
        // Ni a ella le escribe con la regla de antes una cuenta vieja que no se siguen los dos.
        e.s.seguir(beto.id, "nueva")
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(beto.id, "nueva", sobre) }.mensaje)
        // Entre dos cuentas de antes, la regla de antes sigue.
        e.s.seguir(beto.id, "ana")
        e.s.mandar(beto.id, "ana", sobre)
        // Si se siguen los dos, la cuenta nueva sí escribe.
        e.s.seguir(ana.id, "nueva")
        assertTrue(e.s.mandar(nueva.id, "ana", sobre) > 0)
    }

    @Test fun unaBaseNuevaNoTieneTransicionYUnaConCuentasSi() {
        val dir = Files.createTempDirectory("transicion-chat").toFile()
        try {
            val ruta = File(dir, "s.db").path
            Base(ruta).apply {
                assertEquals("0", usar { leer("SELECT valor FROM configuracion WHERE clave = 'chats_transicion_desde'") { it.getString(1) }.single() })
                // Una base de antes de la regla: con cuentas y sin la marca.
                usar { cambiar("INSERT INTO cuentas(id, usuario, token, nombre, creada, cambio) VALUES(1, 'vieja', 't', 'V', 0, 0)"); cambiar("DELETE FROM configuracion") }
                cerrar()
            }
            val antes = System.currentTimeMillis()
            Base(ruta).apply {
                assertTrue(usar { leer("SELECT valor FROM configuracion WHERE clave = 'chats_transicion_desde'") { it.getString(1).toLong() }.single() } >= antes)
                cerrar()
            }
        } finally { dir.deleteRecursively() }
    }

    /* ----- Otros ----- */

    @Test fun puedoEscribirViajaAunqueSeaFalse() = Entorno().use { e ->
        e.alta("ana"); val beto = e.alta("beto")
        val json = JsonSocial.encodeToString(PerfilVisto.serializer(), e.s.verPerfil(beto.id, "ana"))
        assertTrue(json.contains("\"puedoEscribir\":false"), json)
    }

    @Test fun elReporteAutomaticoNoCuentaComoPersona() = Entorno().use { e ->
        val mod = e.equipo("mod", "moderador")
        val pub = e.pub(e.alta("autora"))
        e.b.usar { cambiar("INSERT INTO reportes(de, que, objeto, motivo, cuando) VALUES(NULL, 'publicacion', ?, 'desnudos: automatico', ?)", "$pub", e.reloj) }
        e.reportar(e.alta("reportera"), pub)
        assertEquals(1, e.s.casos(mod.id, mod.token).single { it.objeto == "$pub" }.personas)
    }

    @Test fun laFilaDeRevisionTieneTopeYDescartaLoQueNoCabe() {
        val fila = filaRevision(tope = 50)
        val suelta = java.util.concurrent.CountDownLatch(1)
        val hechas = java.util.concurrent.atomic.AtomicInteger()
        try {
            fila.execute { suelta.await(); hechas.incrementAndGet() }          // ocupa el único hilo
            repeat(60) { fila.execute { hechas.incrementAndGet() } }          // 50 esperan, 10 se descartan
            assertEquals(50, fila.queue.size)
            suelta.countDown()
            fila.shutdown()
            assertTrue(fila.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(51, hechas.get())
        } finally { suelta.countDown(); fila.shutdownNow() }
    }

    @Test fun elEquipoAnotaElRadicadoDelCaso() = Entorno().use { e ->
        val mod = e.equipo("mod", "moderador"); val pub = e.pub(e.alta("autora"))
        e.reportar(e.alta("reportera"), pub, Moderacion.MASI)
        e.s.anotarRadicado(mod.id, Radicado("publicacion", "$pub", "NUNC-2026-0001"), mod.token)
        assertEquals("NUNC-2026-0001", e.s.casos(mod.id, mod.token).single().radicado)
        assertFailsWith<Problema> { e.s.anotarRadicado(e.alta("cualquiera").id, Radicado("publicacion", "$pub", "x")) }
        assertEquals(1, e.b.usar { leer("SELECT COUNT(*) FROM registro_equipo WHERE accion = 'radicado_anotar'") { it.getInt(1) }.single() })
    }

    /* ----- Quién puede escribirme ----- */

    @Test fun porDefectoSoloSeEscribenQuienesSeSiguenYElServidorLoHaceCumplir() = Entorno().use { e ->
        val ana = e.alta("ana"); val beto = e.alta("beto")
        val sobre = SobreNuevo("cifrado", "firma")
        assertEquals("mutuos", e.s.miCuenta(ana.id).escribir)
        assertEquals("no_recibe_mensajes", assertFailsWith<Problema> { e.s.mandar(beto.id, "ana", sobre) }.mensaje)
        e.s.seguir(beto.id, "ana")
        assertFailsWith<Problema> { e.s.mandar(beto.id, "ana", sobre) }        // sigue a Ana, pero Ana no a él
        assertFalse(e.s.verPerfil(beto.id, "ana").puedoEscribir)
        e.s.seguir(ana.id, "beto")
        assertTrue(e.s.verPerfil(beto.id, "ana").puedoEscribir)
        e.s.mandar(beto.id, "ana", sobre)
        // El chat ya existe: aunque dejen de seguirse, no se rompe.
        e.b.usar { cambiar("DELETE FROM seguir") }
        e.s.mandar(beto.id, "ana", sobre); e.s.mandar(ana.id, "beto", sobre)
        // «Personas que sigo»: basta con que Ana siga a quien le escribe.
        val caro = e.alta("caro")
        e.s.ajustar(ana.id, Ajustes(escribir = "sigo"))
        assertFailsWith<Problema> { e.s.mandar(caro.id, "ana", sobre) }
        e.s.seguir(ana.id, "caro")
        e.s.mandar(caro.id, "ana", sobre)
        // «Nadie»: ningún chat nuevo; los de antes siguen.
        val dani = e.alta("dani")
        e.s.ajustar(ana.id, Ajustes(escribir = "nadie"))
        e.s.seguir(ana.id, "dani"); e.s.seguir(dani.id, "ana")
        assertFailsWith<Problema> { e.s.mandar(dani.id, "ana", sobre) }
        e.s.mandar(caro.id, "ana", sobre)
        assertEquals("escribir", assertFailsWith<Problema> { e.s.ajustar(ana.id, Ajustes(escribir = "todos")) }.mensaje)
    }

    @Test fun laReglaDelChatTambienRigePorHttp() = Entorno().use { e ->
        val ana = e.alta("ana"); val beto = e.alta("beto")
        testApplication {
            application { fogonSocial(e.s) }
            val r = client.post("/v1/mensajes/ana") {
                header(HttpHeaders.Authorization, "Bearer ${beto.token}"); contentType(ContentType.Application.Json)
                setBody("""{"sobre":"cifrado","firma":"f"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, r.status)
            val ajuste = client.put("/v1/cuenta") {
                header(HttpHeaders.Authorization, "Bearer ${ana.token}"); contentType(ContentType.Application.Json)
                setBody("""{"escribir":"sigo"}""")
            }
            assertEquals(HttpStatusCode.NoContent, ajuste.status)
            assertEquals("sigo", e.s.miCuenta(ana.id).escribir)
        }
    }

    @Test fun lasCuentasQueYaExistianQuedanEnMutuos() {
        val dir = Files.createTempDirectory("migracion-chat").toFile()
        try {
            val ruta = File(dir, "s.db").path
            Base(ruta).cerrar()
            val b = Base(ruta)
            b.usar { cambiar("INSERT INTO cuentas(id, usuario, token, nombre, creada, cambio) VALUES(1, 'vieja', 't', 'V', 0, 0)") }
            b.cerrar()
            val otra = Base(ruta)
            assertEquals("mutuos", otra.usar { leer("SELECT escribir FROM cuentas WHERE id = 1") { it.getString(1) }.single() })
            otra.cerrar()
        } finally { dir.deleteRecursively() }
    }

    /* ----- Revisión automática de fotos ----- */

    @Test fun unaFotoQueSeVeComoDesnudoDejaLaPublicacionEnRevision() = Entorno(revisor = { img -> if (img.getRGB(0, 0) and 0xFFFFFF == 0xFFFFFF) 0.99 else 0.01 }).use { e ->
        val mod = e.equipo("mod", "moderador")
        val autora = e.alta("autora"); val otra = e.alta("otra")
        val blanca = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { color = java.awt.Color.WHITE; fillRect(0, 0, 8, 8); dispose() } }
        val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(blanca, "jpeg", it) }.toByteArray()
        val marcada = e.s.subirImagen(autora.id, bytes).nombre
        val normal = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        e.s.subirPublicaciones(autora.id, listOf(
            PublicacionSubida("n", Vis.PUBLICA, e.reloj, "diario", buildJsonObject { put("imagenes", buildJsonArray { add(normal) }) }),
            PublicacionSubida("m", Vis.PUBLICA, e.reloj, "diario", buildJsonObject { put("imagenes", buildJsonArray { add(marcada) }) })))
        val (pubNormal, pub) = listOf("n", "m").map { c -> e.b.usar { leer("SELECT id FROM publicaciones WHERE clave = ?", c) { it.getLong(1) }.single() } }
        assertFalse(e.oculta(pubNormal))
        assertTrue(e.oculta(pub))
        assertNull(e.s.publicacion(otra.id, pub)); assertNotNull(e.s.publicacion(autora.id, pub))
        assertFailsWith<Problema> { e.s.imagen(marcada, otra.id) }
        e.s.imagen(marcada, autora.id)
        val caso = e.s.casos(mod.id, mod.token).single()
        assertTrue(caso.automatico); assertEquals(mapOf("desnudos" to 1), caso.motivos)
        e.s.imagen(marcada, mod.id, mod.token)                                   // el equipo sí la ve para decidir
        e.s.decidir(mod.id, Decision("publicacion", "$pub", "sin_falta"), mod.token)
        assertFalse(e.oculta(pub))
        e.s.imagen(marcada, otra.id)
        // La foto del perfil marcada: solo la ven su dueña y el equipo, y llega a la cola como reporte de la cuenta.
        e.s.subirFoto(autora.id, bytes)
        assertFailsWith<Problema> { e.s.foto(otra.id, "autora") }
        e.s.foto(autora.id, "autora")
        assertTrue(e.s.casos(mod.id, mod.token).any { it.que == "cuenta" && it.objeto == "autora" && it.automatico })
    }

    @Test fun siElModeloSeQuedaSinMemoriaLaFotoQuedaSinRevisar() = Entorno(revisor = { throw OutOfMemoryError("prueba") }).use { e ->
        val autora = e.alta("autora"); val otra = e.alta("otra")
        // El error del modelo no sale de su hilo: la subida responde y la foto se sigue viendo.
        val nombre = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        e.s.imagen(nombre, otra.id)
        assertEquals(1 to 0, e.b.usar { leer("SELECT sin_revisar, revision FROM imagenes WHERE nombre = ?", nombre) { it.getInt(1) to it.getInt(2) }.single() })
        e.s.subirFoto(autora.id, jpegDePrueba())
        e.s.foto(otra.id, "autora")
        assertEquals(1 to 0, e.b.usar { leer("SELECT foto_sin_revisar, foto_revision FROM cuentas WHERE id = ?", autora.id) { it.getInt(1) to it.getInt(2) }.single() })
    }

    @Test fun unaFotoRevisadaNoQuedaSinRevisar() = Entorno(revisor = { 0.01 }).use { e ->
        val autora = e.alta("autora")
        val nombre = e.s.subirImagen(autora.id, jpegDePrueba()).nombre
        e.s.subirFoto(autora.id, jpegDePrueba())
        assertEquals(0, e.b.usar { leer("SELECT sin_revisar FROM imagenes WHERE nombre = ?", nombre) { it.getInt(1) }.single() })
        assertEquals(0, e.b.usar { leer("SELECT foto_sin_revisar FROM cuentas WHERE id = ?", autora.id) { it.getInt(1) }.single() })
    }

    private companion object { const val DIA = 24 * 3600_000L }
}
