package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** La importación de Steam: la respuesta del servidor, los estados, sin repetir al reimportar y las horas en la ficha. */
class SteamTest {
    private var n = 0
    private val id: (String) -> String = { p -> p + (++n) }
    private val ahora = 1_800_000_000_000L

    private fun ficha(igdb: Long, titulo: String) = Obra(id = "", tipo = Tipos.JUEGO, titulo = titulo, fuente = "igdb:$igdb",
        portada = "https://images.igdb.com/igdb/image/upload/t_cover_big/x$igdb.jpg", traida = 123L)

    @Test fun cadaErrorConSuTexto() {
        assertEquals(R.string.steam_error_igdb, io.github.fogonapps.cajon.ui.errorSteam(Steam.IGDB))
        assertEquals(R.string.steam_error_no_responde, io.github.fogonapps.cajon.ui.errorSteam(Steam.NO_RESPONDE))
        assertEquals(R.string.steam_error_no_responde, io.github.fogonapps.cajon.ui.errorSteam("otro"))
    }

    @Test fun analizaLaRespuesta() {
        val j = Steam.convertir("""{"juegos":[
            {"appid":570,"nombre":" Dota 2 ","minutos":7200,"minutos2semanas":30,"igdb":2963},
            {"appid":4000,"nombre":"Garry's Mod","minutos":0,"minutos2semanas":0,"igdb":null},
            {"appid":"x","nombre":"sin appid"},
            {"nombre":"tampoco"},
            {"appid":10,"nombre":"Counter-Strike","minutos":-5,"extra":true}
        ]}""")!!.juegos
        assertEquals(3, j.size)
        assertEquals(Steam.Juego(570, "Dota 2", 7200, 30, 2963), j[0])
        assertNull(j[1].igdb)
        assertEquals(Steam.Juego(10, "Counter-Strike", 0, 0, null), j[2])
        assertNull(Steam.convertir("[]"))
        assertNull(Steam.convertir("no es json"))
        assertEquals(emptyList<Steam.Juego>(), Steam.convertir("""{"juegos":[]}""")!!.juegos)
        // Una biblioteca grande llega recortada a los más jugados, con cuántos tenía.
        val grande = Steam.convertir("""{"juegos":[{"appid":1,"nombre":"A","minutos":5}],"total":9000,"recortada":true}""")!!
        assertTrue(grande.recortada)
        assertEquals(9000, grande.total)
        assertFalse(Steam.convertir("""{"juegos":[{"appid":1,"nombre":"A"}],"total":1}""")!!.recortada)
        assertEquals("biblioteca_privada", Steam.errorDe("""{"error":"biblioteca_privada"}"""))
        assertNull(Steam.errorDe("<html>"))
        assertNull(Steam.errorDe("""{"error":"<script>"}"""))
    }

    @Test fun perfilesValidos() {
        listOf("https://steamcommunity.com/id/gabelogannewell/", "steamcommunity.com/profiles/76561197960287930", "76561197960287930",
            "mi_nombre-2", "https://steamcommunity.com/id/gabe?l=spanish", "https://steamcommunity.com/id/gabe/games/?tab=all",
            "https://steamcommunity.com/profiles/76561197960287930/", "steamcommunity.com/id/gabe/games").forEach { assertTrue(it, Steam.perfilValido(it)) }
        listOf("", "a", "https://steamcommunity.com/profiles/123", "https://otro.example/id/gabe", "con espacio",
            "https://steamcommunity.com/id/gabe/games con espacio").forEach { assertFalse(it, Steam.perfilValido(it)) }
    }

    @Test fun estados() {
        assertEquals(Estados.EN_CURSO, Steam.estadoDe(600, 30))
        assertEquals(Estados.EN_CURSO, Steam.estadoDe(0, 5))
        assertEquals(Estados.QUIERO, Steam.estadoDe(0, 0))
        assertEquals(Estados.SIN_ESTADO, Steam.estadoDe(600, 0))
        val juegos = listOf(Steam.Juego(1, "A", 600, 30, 11), Steam.Juego(2, "B", 0, 0, 22), Steam.Juego(3, "C", 600, 0, 33))
        val (e, r) = Steam.sumar(Estado(), juegos, mapOf(11L to ficha(11, "A"), 22L to ficha(22, "B"), 33L to ficha(33, "C")), emptyMap(), id, ahora)
        assertEquals(Steam.Resultado(3, 0, 0), r)
        assertEquals(listOf(Estados.EN_CURSO, Estados.QUIERO, Estados.SIN_ESTADO), e.obras.map { it.estado })
        // Lo que viene de la ficha de IGDB: fuente, portada y cuándo se trajo.
        val a = e.obras[0]
        assertEquals("igdb:11", a.fuente)
        assertEquals(listOf("PC"), a.dondeJuego)
        assertTrue(a.portada.isNotEmpty())
        assertEquals(123L, a.traida)
        assertEquals(600, a.minutosSteam)
        assertEquals(ahora, a.agregada)
        // Con renovar (español), los datos se vuelven a traer después.
        assertEquals(0L, Steam.sumar(Estado(), juegos.take(1), mapOf(11L to ficha(11, "A")), emptyMap(), id, ahora, renovar = true).first.obras[0].traida)
    }

    @Test fun reimportarNoDuplicaNiTocaLoPersonal() {
        val juegos = listOf(Steam.Juego(1, "Hades", 600, 0, 11), Steam.Juego(2, "Juego raro", 30, 0, null))
        val fichas = mapOf(11L to ficha(11, "Hades"))
        val (e1, r1) = Steam.sumar(Estado(), juegos, fichas, emptyMap(), id, ahora)
        assertEquals(Steam.Resultado(1, 0, 1), r1)
        // La persona lo terminó, le puso nota, reseña y notas.
        val hades = e1.obras.first { it.fuente == "igdb:11" }
        val e1b = e1.copy(obras = e1.obras.map { if (it.id == hades.id) it.copy(estado = Estados.TERMINADO, nota = 9, resena = "Muy bueno", notas = "con amigos") else it })
        val mas = listOf(Steam.Juego(1, "Hades", 900, 60, 11), Steam.Juego(2, "Juego raro", 45, 0, null))
        val (e2, r2) = Steam.sumar(e1b, mas, fichas, emptyMap(), id, ahora + 1)
        assertEquals(Steam.Resultado(0, 2, 0), r2)
        assertEquals(2, e2.obras.size)
        val h = e2.obras.first { it.id == hades.id }
        assertEquals(900, h.minutosSteam)
        assertEquals(Estados.TERMINADO, h.estado)
        assertEquals(9, h.nota)
        assertEquals("Muy bueno", h.resena)
        assertEquals("con amigos", h.notas)
        // Las horas llevan su propia hora: el cambio de la obra es el de la persona, no el de la importación.
        assertEquals(hades.cambio, h.cambio)
        assertEquals(ahora + 1, h.minutosSteamCuando)
        assertEquals(45, e2.obras.first { it.fuente.isEmpty() }.minutosSteam)
        // Otra vez igual: nada cambia (ni la hora del cambio, para no sincronizar de más).
        val (e3, r3) = Steam.sumar(e2, mas, fichas, emptyMap(), id, ahora + 2)
        assertEquals(Steam.Resultado(0, 2, 0), r3)
        assertEquals(e2.obras, e3.obras)
    }

    @Test fun juegoDeWikidataConLaMismaFichaSeActualiza() {
        val wd = Obra(id = "o-wd", tipo = Tipos.JUEGO, titulo = "Celeste", fuente = "wd:Q24887416", estado = Estados.TERMINADO, nota = 10)
        val (e, r) = Steam.sumar(Estado(obras = listOf(wd)), listOf(Steam.Juego(504230, "Celeste", 1200, 0, 26226)),
            mapOf(26226L to ficha(26226, "Celeste")), mapOf(26226L to "o-wd"), id, ahora)
        assertEquals(Steam.Resultado(0, 1, 0), r)
        assertEquals(1, e.obras.size)
        assertEquals(1200, e.obras[0].minutosSteam)
        assertEquals("wd:Q24887416", e.obras[0].fuente)
        assertEquals(Estados.TERMINADO, e.obras[0].estado)
        assertEquals(10, e.obras[0].nota)
    }

    @Test fun juegoSinFicha() {
        val aMano = Obra(id = "o-mano", tipo = Tipos.JUEGO, titulo = "Mi Juego Indie", estado = Estados.EN_CURSO)
        val juegos = listOf(Steam.Juego(7, "Mi juego indie", 120, 0, null), Steam.Juego(8, "Otro sin ficha", 0, 0, null), Steam.Juego(9, "  ", 5, 0, null))
        val (e, r) = Steam.sumar(Estado(obras = listOf(aMano)), juegos, emptyMap(), emptyMap(), id, ahora)
        // El que ya estaba a mano (mismo nombre) solo recibe las horas; el otro entra con su nombre; el sin nombre no entra.
        assertEquals(Steam.Resultado(0, 1, 1), r)
        assertEquals(2, e.obras.size)
        assertEquals(120, e.obras[0].minutosSteam)
        assertEquals(Estados.EN_CURSO, e.obras[0].estado)
        val otro = e.obras[1]
        assertEquals("Otro sin ficha", otro.titulo)
        assertEquals("", otro.fuente)
        assertEquals(Tipos.JUEGO, otro.tipo)
        assertEquals(Estados.QUIERO, otro.estado)
        // Con id de IGDB pero sin ficha traída: entra con su nombre y la fuente, y los datos se traen después.
        val (e2, r2) = Steam.sumar(Estado(), listOf(Steam.Juego(10, "Portal", 60, 0, 71)), emptyMap(), emptyMap(), id, ahora)
        assertEquals(Steam.Resultado(1, 0, 0), r2)
        assertEquals("igdb:71", e2.obras[0].fuente)
        assertEquals("Portal", e2.obras[0].titulo)
        assertEquals(0L, e2.obras[0].traida)
    }

    @Test fun edicionesConLaMismaFichaSumanSusHoras() {
        val juegos = listOf(Steam.Juego(1, "Skyrim", 600, 0, 9), Steam.Juego(2, "Skyrim Special Edition", 300, 0, 9))
        val (e, r) = Steam.sumar(Estado(), juegos, mapOf(9L to ficha(9, "Skyrim")), emptyMap(), id, ahora)
        assertEquals(Steam.Resultado(1, 0, 0), r)
        assertEquals(900, e.obras.single().minutosSteam)
    }

    @Test fun horasEnLaFicha() {
        assertNull(Steam.horas(null))
        assertNull(Steam.horas(0))
        assertNull(Steam.horas(59))
        assertEquals(1, Steam.horas(60))
        assertEquals(120, Steam.horas(7230))
        fun texto(carpeta: String): String {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$carpeta/strings.xml"))
            val l = doc.getElementsByTagName("string")
            return (0 until l.length).map { l.item(it) as org.w3c.dom.Element }.first { it.getAttribute("name") == "steam_horas" }.textContent
        }
        assertEquals("120 h en Steam", String.format(texto("values-es"), 120))
        assertEquals("120 h on Steam", String.format(texto("values"), 120))
    }

    @Test fun lasHorasSeConservanAlJuntarRepetidas() {
        val a = Obra(id = "a", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", minutosSteam = 600, estado = Estados.SIN_ESTADO, cambio = 2)
        val b = Obra(id = "b", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", estado = Estados.TERMINADO, cambio = 1)
        val juntas = Logica.sinRepetidas(Estado(obras = listOf(a, b))).vivas.single()
        assertEquals(600, juntas.minutosSteam)
        assertEquals(Estados.TERMINADO, juntas.estado)
    }

    @Test fun lasHorasViajanEnElRespaldo() {
        val e = Estado(obras = listOf(Obra(id = "a", tipo = Tipos.JUEGO, titulo = "Hades", minutosSteam = 600)))
        val texto = JsonCajon.encodeToString(Estado.serializer(), e)
        assertEquals(600, JsonCajon.decodeFromString(Estado.serializer(), texto).obras.single().minutosSteam)
        // Un respaldo viejo, sin el campo, se sigue leyendo.
        assertNull(JsonCajon.decodeFromString(Estado.serializer(), """{"obras":[{"id":"a","tipo":"juego","titulo":"Hades"}]}""").obras.single().minutosSteam)
    }

    @Test fun lasHorasNoSubenEnCursoNiCambian() {
        val viejo = Obra(id = "v", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", estado = Estados.EN_CURSO, cambio = 5, minutosSteam = 60)
        val (e, _) = Steam.sumar(Estado(obras = listOf(viejo)), listOf(Steam.Juego(1, "Hades", 600, 30, 11)), emptyMap(), emptyMap(), id, ahora)
        val o = e.obras.single()
        assertEquals(5L, o.cambio)
        assertEquals(600, o.minutosSteam)
        assertEquals(ahora, o.minutosSteamCuando)
    }

    @Test fun unirConDosTelefonos() {
        // El teléfono A reimporta (horas nuevas); el B, después, terminó el juego y le puso nota.
        val base = Obra(id = "h", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", estado = Estados.SIN_ESTADO, minutosSteam = 600,
            minutosSteamCuando = 100, cambio = 100)
        val a = Steam.sumar(Estado(obras = listOf(base)), listOf(Steam.Juego(1, "Hades", 900, 0, 11)), emptyMap(), emptyMap(), id, 300).first
        val b = Estado(obras = listOf(base.copy(estado = Estados.TERMINADO, nota = 9, cambio = 200)))
        for (unido in listOf(Logica.unir(a, b), Logica.unir(b, a))) {
            val o = unido.obras.single()
            // Lo de B no se pierde por reimportar en A, y las horas son las más recientes (las de A).
            assertEquals(Estados.TERMINADO, o.estado)
            assertEquals(9, o.nota)
            assertEquals(900, o.minutosSteam)
            assertEquals(300L, o.minutosSteamCuando)
        }
        // Al revés: B importó después que A; ganan las horas de B aunque la obra de A sea la más reciente.
        val a2 = Estado(obras = listOf(base.copy(nota = 7, cambio = 500, minutosSteam = 700, minutosSteamCuando = 150)))
        val b2 = Estado(obras = listOf(base.copy(minutosSteam = 800, minutosSteamCuando = 400)))
        val o2 = Logica.unir(a2, b2).obras.single()
        assertEquals(7, o2.nota)
        assertEquals(800, o2.minutosSteam)
        assertEquals(400L, o2.minutosSteamCuando)
    }

    @Test fun juegoSinFuenteOSinFichaPasaALaFicha() {
        val aMano = Obra(id = "m", tipo = Tipos.JUEGO, titulo = "Celeste", estado = Estados.TERMINADO, nota = 10, notas = "fresas", cambio = 7)
        // Uno que entró sin ficha en una importación anterior (IGDB no lo tenía).
        val (e1, _) = Steam.sumar(Estado(obras = listOf(aMano)), listOf(Steam.Juego(2, "Hollow Knight", 30, 0, null)), emptyMap(), emptyMap(), id, ahora)
        val sinFicha = e1.obras.first { it.titulo == "Hollow Knight" }
        assertEquals("", sinFicha.fuente)
        // Ahora los dos tienen ficha: se enlazan a la misma obra, no se crea otra.
        val juegos = listOf(Steam.Juego(1, "Celeste", 600, 0, 26226), Steam.Juego(2, "Hollow Knight", 90, 0, 14593))
        val (e2, r2) = Steam.sumar(e1, juegos, mapOf(26226L to ficha(26226, "Celeste")), emptyMap(), id, ahora + 10)
        assertEquals(Steam.Resultado(0, 2, 0), r2)
        assertEquals(2, e2.obras.size)
        val c = e2.obras.first { it.id == "m" }
        assertEquals("igdb:26226", c.fuente)
        assertEquals(0L, c.traida)
        assertEquals(Estados.TERMINADO, c.estado)
        assertEquals(10, c.nota)
        assertEquals("fresas", c.notas)
        assertEquals(600, c.minutosSteam)
        // Cambió la fuente: eso sí se sincroniza.
        assertEquals(ahora + 10, c.cambio)
        val h = e2.obras.first { it.id == sinFicha.id }
        assertEquals("igdb:14593", h.fuente)
        assertEquals(90, h.minutosSteam)
        // Y la próxima vez se encuentra por su ficha.
        assertEquals(Steam.Resultado(0, 2, 0), Steam.sumar(e2, juegos, emptyMap(), emptyMap(), id, ahora + 20).second)
    }

    @Test fun loBorradoNoVuelve() {
        val borradas = listOf(
            Obra(id = "b1", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", borrada = true, cambio = 9),
            Obra(id = "b2", tipo = Tipos.JUEGO, titulo = "Juego raro", borrada = true, cambio = 9),
            Obra(id = "b3", tipo = Tipos.JUEGO, titulo = "Celeste", borrada = true, cambio = 9),
        )
        val juegos = listOf(Steam.Juego(1, "Hades", 600, 0, 11), Steam.Juego(2, "Juego raro", 30, 0, null),
            Steam.Juego(3, "Celeste", 10, 0, 26226), Steam.Juego(4, "Nuevo", 0, 0, 77))
        val (e, r) = Steam.sumar(Estado(obras = borradas), juegos, mapOf(11L to ficha(11, "Hades")), emptyMap(), id, ahora)
        assertEquals(Steam.Resultado(1, 0, 0), r)
        assertEquals(listOf("Nuevo"), e.vivas.map { it.titulo })
        assertEquals(borradas, e.obras.filter { it.borrada })
    }

    @Test fun loImportadoNoLlenaElPerfil() {
        val juegos = listOf(Steam.Juego(1, "A", 0, 0, 11), Steam.Juego(2, "B", 60, 30, 22))
        val (e, _) = Steam.sumar(Estado(), juegos, emptyMap(), emptyMap(), id, ahora)
        assertTrue(e.obras.all { it.silenciosa })
        val publico = e.copy(perfil = e.perfil.copy(visibilidad = mapOf(PartesPerfil.POR_VER to Visibilidad.PUBLICA, PartesPerfil.VIENDO to Visibilidad.PUBLICA)))
        val partes = RedSocial.partesDeLoTuyo(publico)
        assertNull(partes[PartesPerfil.POR_VER])
        assertNull(partes[PartesPerfil.VIENDO])
        // Recibir horas al reimportar no es tocarla.
        val (e2, _) = Steam.sumar(publico, listOf(Steam.Juego(1, "A", 0, 0, 11), Steam.Juego(2, "B", 120, 30, 22)), emptyMap(), emptyMap(), id, ahora + 1)
        assertTrue(Logica.alTocar(publico, e2).obras.all { it.silenciosa })
        // La persona le pone nota a una: esa ya cuenta (y la otra sigue en silencio).
        val a = e2.obras.first { it.titulo == "A" }
        val tocada = Logica.alTocar(e2, e2.copy(obras = e2.obras.map { if (it.id == a.id) it.copy(nota = 8) else it }))
        assertFalse(tocada.obras.first { it.id == a.id }.silenciosa)
        assertTrue(tocada.obras.first { it.titulo == "B" }.silenciosa)
        assertEquals(listOf("A"), (RedSocial.partesDeLoTuyo(tocada)[PartesPerfil.POR_VER]!!.datos as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonObject)["titulo"].toString().trim('"') })
        // También al sumarla a una lista.
        val conLista = Logica.alTocar(e2, e2.copy(listas = listOf(Lista(id = "l", nombre = "Mis juegos", obras = listOf(a.id)))))
        assertFalse(conLista.obras.first { it.id == a.id }.silenciosa)
        // Una que agregó la persona nunca está en silencio.
        assertFalse(Obra(id = "x", tipo = Tipos.JUEGO, titulo = "X").silenciosa)
    }

    @Test fun jugadaEnSteamNoQuedaEnQuieroJugar() {
        val steam = Obra(id = "a", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", minutosSteam = 600, minutosSteamCuando = 5,
            estado = Estados.SIN_ESTADO, cambio = 1)
        val quiero = Obra(id = "b", tipo = Tipos.JUEGO, titulo = "Hades", fuente = "igdb:11", estado = Estados.QUIERO, cambio = 2)
        assertEquals(Estados.SIN_ESTADO, Logica.sinRepetidas(Estado(obras = listOf(steam, quiero))).vivas.single().estado)
        assertEquals(Estados.SIN_ESTADO, Logica.sinRepetidas(Estado(obras = listOf(quiero, steam))).vivas.single().estado)
        // Con horas, aunque la copia de Steam diga «Quiero jugar» (llegó con 0 y después se jugó).
        val ambas = Logica.juntarPersonal(steam.copy(estado = Estados.QUIERO), listOf(quiero))
        assertEquals(Estados.SIN_ESTADO, ambas.estado)
        // Si la otra tiene un estado de verdad, queda ese.
        assertEquals(Estados.ABANDONADO, Logica.juntarPersonal(steam, listOf(quiero.copy(estado = Estados.ABANDONADO))).estado)
        // Sin horas, sigue en «Quiero jugar».
        assertEquals(Estados.QUIERO, Logica.juntarPersonal(steam.copy(minutosSteam = 0), listOf(quiero)).estado)
        // Las horas, las de la hora más reciente; en silencio solo si las dos lo estaban.
        val j = Logica.juntarPersonal(steam.copy(silenciosa = true), listOf(quiero.copy(minutosSteam = 900, minutosSteamCuando = 9)))
        assertEquals(900, j.minutosSteam)
        assertEquals(9L, j.minutosSteamCuando)
        assertFalse(j.silenciosa)
    }
}
