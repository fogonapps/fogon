package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Los videojuegos de Wikidata: respuestas con la forma real de su API, recortadas y con valores de muestra. */
class WikidataJuegosTest {
    private fun json(s: String) = JsonCajon.parseToJsonElement(s).jsonObject

    private fun item(q: String, rank: String = "normal") =
        """{"mainsnak":{"snaktype":"value","property":"P","datavalue":{"value":{"entity-type":"item","numeric-id":${q.drop(1)},"id":"$q"},"type":"wikibase-entityid"},"datatype":"wikibase-item"},"type":"statement","rank":"$rank"}"""
    private fun fecha(t: String, rank: String = "normal") =
        """{"mainsnak":{"snaktype":"value","property":"P577","datavalue":{"value":{"time":"$t","timezone":0,"before":0,"after":0,"precision":11,"calendarmodel":"http://www.wikidata.org/entity/Q1985727"},"type":"time"},"datatype":"time"},"type":"statement","rank":"$rank"}"""
    private fun imagen(nombre: String) =
        """{"mainsnak":{"snaktype":"value","property":"P18","datavalue":{"value":"$nombre","type":"string"},"datatype":"commonsMedia"},"type":"statement","rank":"normal"}"""
    private fun etiquetas(vararg l: Pair<String, String>) = l.joinToString(",", "{", "}") { (k, v) -> """"$k":{"language":"$k","value":"$v"}""" }

    private val busqueda = json("""{"batchcomplete":"","query":{"searchinfo":{"totalhits":5},"search":[
      {"ns":0,"title":"Q29300592","pageid":30954609},{"ns":0,"title":"Q123017178","pageid":1},
      {"ns":0,"title":"Q63294487","pageid":63028763},{"ns":0,"title":"Q29300592","pageid":30954609},{"ns":0,"pageid":2}
    ]}}""")

    // Hollow Knight (solo "mul", varias fechas, varias plataformas, sin P18), su serie (no es un juego), Silksong
    // (con imagen y un desarrollador preferido), uno sin etiquetas y una persona con imagen.
    private val entidades = json("""{"entities":{
      "Q29300592":{"type":"item","id":"Q29300592","labels":${etiquetas("mul" to "Hollow Knight")},"claims":{
        "P31":[${item("Q7889")}],
        "P577":[${fecha("+2017-04-11T00:00:00Z")},${fecha("+2017-02-24T00:00:00Z")},${fecha("+2018-06-12T00:00:00Z")},${fecha("+2009-01-01T00:00:00Z", "deprecated")}],
        "P178":[${item("Q29326164")}],
        "P400":[${item("Q1406")},${item("Q14116")},${item("Q388")},${item("Q1406")},${item("Q19610114")}]}},
      "Q123017178":{"type":"item","id":"Q123017178","labels":${etiquetas("mul" to "Hollow Knight")},"claims":{"P31":[${item("Q7058673")}]}},
      "Q63294487":{"type":"item","id":"Q63294487","labels":${etiquetas("en" to "Hollow Knight: Silksong", "es" to "Hollow Knight: Canción de seda")},"claims":{
        "P31":[${item("Q7889")},${item("Q21125433")}],
        "P577":[${fecha("+2025-09-04T00:00:00Z")}],
        "P178":[${item("Q99")},${item("Q29326164", "preferred")}],
        "P18":[${imagen("Silksong captura.png")}]}},
      "Q8":{"type":"item","id":"Q8","labels":${etiquetas("mul" to "Juego raro")},"claims":{
        "P31":[${item("Q865493")}],
        "P178":[${item("Q7", "preferred")},${item("Q29326164")}],
        "P400":[]}},
      "Q5":{"type":"item","id":"Q5","claims":{"P31":[${item("Q7889")}]}},
      "Q17518":{"type":"item","id":"Q17518","labels":${etiquetas("en" to "Mary Anderson", "es" to "Mary Anderson")},"claims":{
        "P31":[${item("Q5")}],"P18":[${imagen("MaryAnderson(inventora).jpg")}]}}
    },"success":1}""")["entities"] as JsonObject

    private val nombres = json("""{"entities":{
      "Q29326164":{"type":"item","id":"Q29326164","labels":${etiquetas("en" to "Team Cherry", "es" to "Team Cherry", "mul" to "Team Cherry")}},
      "Q1406":{"type":"item","id":"Q1406","labels":${etiquetas("mul" to "Microsoft Windows", "en" to "Microsoft Windows")}},
      "Q14116":{"type":"item","id":"Q14116","labels":${etiquetas("mul" to "macOS")}},
      "Q388":{"type":"item","id":"Q388","labels":${etiquetas("en" to "Linux", "es" to "GNU/Linux")}},
      "Q19610114":{"type":"item","id":"Q19610114"},
      "Q7":{"type":"item","id":"Q7","labels":${etiquetas("es" to "Categoría:Videojuegos de Alguien", "en" to "Category:Video games by Someone")}}
    },"success":1}""")["entities"] as JsonObject

    private val orden = listOf("Q29300592", "Q123017178", "Q63294487", "Q5", "Q17518", "Q8")

    @Test fun idsDeLaBusquedaEnOrdenYSinRepetir() {
        assertEquals(listOf("Q29300592", "Q123017178", "Q63294487"), WikidataJuegos.idsDeBusqueda(busqueda))
        assertEquals(emptyList<String>(), WikidataJuegos.idsDeBusqueda(json("""{"error":{"code":"x"}}""")))
        assertEquals(emptyList<String>(), WikidataJuegos.idsDeBusqueda(json("""{"query":{"search":"raro"}}""")))
    }

    @Test fun laConsultaPideSoloJuegos() {
        val c = WikidataJuegos.consulta("  hollow   knight ")
        assertTrue(c.startsWith("hollow knight haswbstatement:P31=Q7889|"))
        assertEquals(WikidataJuegos.CLASES.size, Regex("P31=Q[0-9]+").findAll(c).count())
    }

    @Test fun losNombresQueFallanNoTumbanLaBusqueda() = kotlinx.coroutines.runBlocking {
        assertEquals(mapOf("Q1" to "Uno"), WikidataJuegos.oVacio { mapOf("Q1" to "Uno") })
        for (fallo in listOf(SinRed(), IllegalStateException("HTTP 502"), IllegalArgumentException("no es un objeto"),
                kotlinx.serialization.SerializationException("JSON roto"))) {
            assertEquals(fallo.toString(), emptyMap<String, String>(), WikidataJuegos.oVacio { throw fallo })
        }
        try {
            WikidataJuegos.oVacio { throw kotlinx.coroutines.CancellationException("cancelada") }
            fail("La cancelación debe seguir de largo")
        } catch (_: kotlinx.coroutines.CancellationException) {}
    }

    @Test fun soloLosJuegosYSinRomperseConLoQueFalta() {
        val j = WikidataJuegos.juegosDe(entidades, orden + "Q404", "es")
        // Fuera: la serie, la persona y el que no tiene ninguna etiqueta; el que no vino en la respuesta tampoco rompe.
        assertEquals(listOf("Q29300592", "Q63294487", "Q8"), j.map { it.qid })
    }

    @Test fun anioMasTempranoSinLosObsoletos() {
        val j = WikidataJuegos.juegosDe(entidades, orden, "es")
        assertEquals(2017, j[0].anio)
        assertEquals(2025, j[1].anio)
    }

    @Test fun tituloEnElIdiomaConRespaldo() {
        val es = WikidataJuegos.juegosDe(entidades, orden, "es")
        val en = WikidataJuegos.juegosDe(entidades, orden, "en")
        assertEquals("Hollow Knight", es[0].titulo) // solo "mul"
        assertEquals("Hollow Knight: Canción de seda", es[1].titulo)
        assertEquals("Hollow Knight: Silksong", en[1].titulo)
        val soloIngles = json("""{"Q1":{"labels":${etiquetas("en" to "Only English")},"claims":{"P31":[${item("Q7889")}]}}}""")
        assertEquals("Only English", WikidataJuegos.juegosDe(soloIngles, listOf("Q1"), "es").single().titulo)
    }

    @Test fun encontradoConDesarrolladorPlataformasYPortada() {
        val j = WikidataJuegos.juegosDe(entidades, orden, "es")
        val n = WikidataJuegos.etiquetasDe(nombres, "es")
        assertFalse("Q19610114" in n) // sin etiquetas: se deja fuera
        assertFalse("Q7" in n) // una categoría puesta como desarrollador
        assertFalse("Q7" in WikidataJuegos.etiquetasDe(nombres, "en"))
        val hk = WikidataJuegos.encontrado(j[0], n)
        assertEquals("juego", hk.tipo)
        assertEquals("wd:Q29300592", hk.fuente)
        assertEquals("Team Cherry", hk.creador)
        // En su orden, sin repetir y sin la que no tiene nombre.
        assertEquals("Microsoft Windows · macOS · GNU/Linux", hk.detalle)
        assertEquals("", hk.portada) // sin P18
        val silksong = WikidataJuegos.encontrado(j[1], n)
        assertEquals("Team Cherry", silksong.creador) // el preferido antes que el primero
        // Tiene P18 (se lee en [WikidataJuegos.Juego.imagen]), pero sin autor ni licencia no se pone como portada.
        assertEquals("Silksong captura.png", j[1].imagen)
        assertEquals("", silksong.portada)
        assertEquals("", silksong.detalle)
        // Sin los nombres (falló esa consulta) sigue sirviendo.
        val sinNombres = WikidataJuegos.encontrado(j[0], emptyMap())
        assertEquals("", sinNombres.creador)
        assertEquals("", sinNombres.detalle)
        assertEquals("Linux", WikidataJuegos.etiquetasDe(nombres, "en")["Q388"])
    }

    @Test fun siElDesarrolladorEsUnaCategoriaPasaAlSiguiente() {
        val raro = WikidataJuegos.juegosDe(entidades, orden, "es").single { it.qid == "Q8" }
        assertEquals(listOf("Q7", "Q29326164"), raro.desarrolladores)
        assertEquals("Team Cherry", WikidataJuegos.encontrado(raro, WikidataJuegos.etiquetasDe(nombres, "es")).creador)
    }

    /* ----- Paso 3: detalle, portada con crédito, estudios, géneros y enlaces ----- */

    /** Los nombres cortos de los géneros de juegos de los recursos (`generos_juegos`), como los pasa la app. Corre desde apps/cajon. */
    private fun generosJuegos(carpeta: String): List<String> = arreglo(carpeta, "generos_juegos")

    /** Un `string-array` de los recursos. */
    private fun arreglo(carpeta: String, nombre: String): List<String> {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/res/$carpeta/strings.xml"))
        val lista = doc.getElementsByTagName("string-array")
        for (i in 0 until lista.length) {
            val n = lista.item(i) as org.w3c.dom.Element
            if (n.getAttribute("name") == nombre) return (0 until n.getElementsByTagName("item").length).map { n.getElementsByTagName("item").item(it).textContent }
        }
        error("no está $nombre en $carpeta")
    }
    private val es by lazy { generosJuegos("values-es") }
    private val en by lazy { generosJuegos("values") }
    private val nEs by lazy { GenerosJuego.Nombres(es, arreglo("values-es", "generos_igdb")) }
    private val nEn by lazy { GenerosJuego.Nombres(en, arreglo("values", "generos_igdb")) }

    private val hollow = json("""{
      "Q29300592":{"type":"item","id":"Q29300592","labels":${etiquetas("mul" to "Hollow Knight")},"claims":{
        "P31":[${item("Q7058673")}],
        "P577":[${fecha("+2017-02-24T00:00:00Z")}],
        "P178":[${item("Q29326164")},${item("Q2")},${item("Q3")},${item("Q4")}],
        "P400":[${item("Q1406")},${item("Q388")}],
        "P136":[${item("Q828322")},${item("Q19643088")},${item("Q999")},${item("Q828322")}],
        "P18":[${imagen("Hollow Knight captura.png")}]}}
    }""")

    @Test fun elDetalleLeeAunqueYaNoSeaDeUnaClaseDeJuego() {
        // Un juego ya guardado cuya clase cambió en Wikidata (aquí, "serie") igual se actualiza.
        assertTrue(WikidataJuegos.juegosDe(hollow, listOf("Q29300592"), "es").isEmpty())
        val j = WikidataJuegos.leer("Q29300592", hollow["Q29300592"] as JsonObject, "es")!!
        assertEquals("Hollow Knight", j.titulo)
        assertEquals(listOf("Q828322", "Q19643088", "Q999"), j.generos)
        assertEquals("Hollow Knight captura.png", j.imagen)
        assertNull(WikidataJuegos.leer("Q5", json("""{"claims":{}}"""), "es"))
    }

    private val nombresHollow = mapOf("Q29326164" to "Team Cherry", "Q2" to "Otro", "Q3" to "Tercero", "Q4" to "Cuarto",
        "Q1406" to "Microsoft Windows", "Q388" to "GNU/Linux", "Q999" to "videojuego independiente", "Q828322" to "videojuego de plataformas")

    @Test fun deResultadoAObraSinImagen() {
        val j = WikidataJuegos.leer("Q29300592", hollow["Q29300592"] as JsonObject, "es")!!
        val o = WikidataJuegos.obra(j, nombresHollow, null)
        assertEquals(Tipos.JUEGO, o.tipo)
        assertEquals("wd:Q29300592", o.fuente)
        assertEquals("Hollow Knight", o.titulo)
        assertEquals(2017, o.anio)
        assertEquals("Team Cherry, Otro, Tercero", o.creador) // hasta tres estudios
        assertEquals(listOf("Microsoft Windows", "GNU/Linux"), o.plataformas)
        // Los conocidos por su elemento (sin repetir) y el que no, como lo nombra Wikidata, con mayúscula.
        assertEquals(listOf("Q828322", "Q19643088", "Videojuego independiente"), o.generos)
        // Se muestran con su nombre corto en el idioma de la app.
        assertEquals(listOf("Plataformas", "Metroidvania", "Videojuego independiente"), o.generos.map { GenerosJuego.nombre(it, nEs, nEs, nEn) })
        // Tiene P18, pero sin crédito no hay portada: queda la de respaldo (el ícono del tipo).
        assertEquals("", o.portada)
        assertNull(o.imagen)
        assertTrue(o.traida > 0) // ya tiene sus datos: no se vuelven a pedir al agregarlo
        // En inglés, los nombres cortos en inglés.
        assertEquals(listOf("Platformer", "Metroidvania", "Videojuego independiente"), o.generos.map { GenerosJuego.nombre(it, nEn, nEs, nEn) })
        // Sin los nombres (falló esa consulta) sale igual, sin estudio ni plataformas.
        val sinNombres = WikidataJuegos.obra(j, emptyMap(), null)
        assertEquals("", sinNombres.creador)
        assertEquals(listOf("Q828322", "Q19643088"), sinNombres.generos)
    }

    @Test fun laPortadaSoloConAutorYLicencia() {
        val url = WikidataJuegos.urlImagen("Hollow Knight captura.png")
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Hollow%20Knight%20captura.png?width=500", url)
        val buena = ImagenElegida("wikimedia", url, "Team Cherry", "CC BY-SA 4.0")
        assertEquals(buena, WikidataJuegos.conCredito(buena))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = null)))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "  ")))
        assertNull(WikidataJuegos.conCredito(buena.copy(licencia = "")))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "Unknown author")))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "Desconocido")))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "Author unknown")))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "Autor desconocido")))
        assertNull(WikidataJuegos.conCredito(buena.copy(autor = "Anónimo")))
        assertNull(WikidataJuegos.conCredito(ImagenElegida("wikimedia", url))) // Commons no respondió con sus datos
        // La frase entera cuenta: un estudio que empieza por "Unknown" o un autor en otra escritura sí son autores.
        assertNotNull(WikidataJuegos.conCredito(buena.copy(autor = "Unknown Worlds Entertainment")))
        assertNotNull(WikidataJuegos.conCredito(buena.copy(autor = "山田太郎")))
        val j = WikidataJuegos.leer("Q29300592", hollow["Q29300592"] as JsonObject, "es")!!
        val o = WikidataJuegos.obra(j, nombresHollow, buena)
        assertEquals(url, o.portada)
        assertEquals("Team Cherry", o.imagen?.autor)
        assertEquals("CC BY-SA 4.0", o.imagen?.licencia)
    }

    @Test fun estudiosYGenerosSeBuscanPorSuPropiedad() {
        val c = WikidataJuegos.conPropiedad("P178", listOf("Q1", "Q2"))
        assertTrue(c.startsWith("haswbstatement:P178=Q1|P178=Q2 haswbstatement:P31=Q7889|"))
        assertEquals(WikidataJuegos.CLASES.size, Regex("P31=Q[0-9]+").findAll(c).count())
        // En cualquiera de los dos idiomas.
        assertEquals("Q828322", WikidataJuegos.generoConocido("plataformas", es, en))
        assertEquals("Q828322", WikidataJuegos.generoConocido("Platformer", es, en))
        assertEquals("Q343568", WikidataJuegos.generoConocido("Acción-aventura", es, en))
        assertNull(WikidataJuegos.generoConocido("Hollow Knight", es, en))
        assertNull(WikidataJuegos.generoConocido("", es, en))
        // Cada idioma tiene un nombre por id, en el mismo orden, y ninguno se repite (si no, un género buscaría otro).
        assertEquals(WikidataJuegos.GENEROS.size, WikidataJuegos.GENEROS.distinct().size)
        for (l in listOf(es, en)) {
            assertEquals(WikidataJuegos.GENEROS.size, l.size)
            assertEquals(l.size, l.map { Logica.tituloLimpio(it) }.distinct().size)
        }
        assertEquals("Plataformas", es[WikidataJuegos.GENEROS.indexOf("Q828322")])
        assertEquals("Platformer", en[WikidataJuegos.GENEROS.indexOf("Q828322")])
    }

    @Test fun elEstudioPorSuNombreExacto() {
        val r = json("""{"search":[
          {"id":"Q29326164","label":"Team Cherry","match":{"type":"label","language":"es","text":"Team Cherry"}},
          {"id":"Q134269865","label":"Team Cherry","match":{"type":"label","language":"en","text":"Team Cherry"}},
          {"id":"Q7","label":"Team Cherry Jam","match":{"type":"label","language":"en","text":"Team Cherry Jam"}},
          {"id":"Q8","label":"Cherry","match":{"type":"alias","language":"en","text":"team cherry"}},
          {"id":"raro","label":"Team Cherry"},
          {"id":"Q29326164","label":"Team Cherry"}
        ]}""")
        assertEquals(listOf("Q29326164", "Q134269865", "Q8"), WikidataJuegos.idsConNombre(r, "team  cherry"))
        assertEquals(emptyList<String>(), WikidataJuegos.idsConNombre(r, "Nintendo"))
        assertEquals(emptyList<String>(), WikidataJuegos.idsConNombre(json("""{"error":{}}"""), "Team Cherry"))
    }

    @Test fun elArticuloDeWikipediaEnElIdiomaOEnIngles() {
        val e = json("""{"Q1":{"sitelinks":{"enwiki":{"site":"enwiki","title":"Hollow Knight","url":"https://en.wikipedia.org/wiki/Hollow_Knight"},
          "eswiki":{"site":"eswiki","title":"Hollow Knight","url":"https://es.wikipedia.org/wiki/Hollow_Knight"}}},
          "Q2":{"sitelinks":{"enwiki":{"url":"https://en.wikipedia.org/wiki/Solo"}}},"Q3":{"sitelinks":{}}}""")
        assertEquals("https://es.wikipedia.org/wiki/Hollow_Knight", WikidataJuegos.wikipediaDe(e, "Q1", "es"))
        assertEquals("https://en.wikipedia.org/wiki/Hollow_Knight", WikidataJuegos.wikipediaDe(e, "Q1", "en"))
        assertEquals("https://en.wikipedia.org/wiki/Solo", WikidataJuegos.wikipediaDe(e, "Q2", "es"))
        assertNull(WikidataJuegos.wikipediaDe(e, "Q3", "es"))
        assertNull(WikidataJuegos.wikipediaDe(e, "Q404", "es"))
    }

    @Test fun losEnlacesDeUnaFuenteQueNoEsDeWikidataNoSePiden() = kotlinx.coroutines.runBlocking {
        // Sin red: una fuente rara no consulta nada (y no hay nada que volver a pedir).
        assertEquals(emptyList<Enlace>() to true, WikidataJuegos.enlaces("wd:raro", "es"))
        assertEquals(emptyList<Enlace>() to true, WikidataJuegos.enlaces("tmdb:movie:603", "es"))
    }

    @Test fun unEstudioConComaEsUnSoloEstudio() {
        val j = WikidataJuegos.leer("Q29300592", hollow["Q29300592"] as JsonObject, "es")!!
        val o = WikidataJuegos.obra(j, nombresHollow + ("Q29326164" to "Foo Co., Ltd."), null)
        assertEquals("Foo Co. Ltd., Otro, Tercero", o.creador)
        // La ficha parte por comas: quedan tres, no cuatro.
        assertEquals(listOf("Foo Co. Ltd.", "Otro", "Tercero"), o.creador.split(",").map { it.trim() })
        assertEquals("A B", WikidataJuegos.sinComas(" A ,  B, "))
    }

    @Test fun unElementoBorradoNoEsFaltaDeInternet() {
        val r = json("""{"Q1":{"id":"Q1","missing":""},"Q2":{"id":"Q2","labels":{}}}""")
        assertThrows(WikidataJuegos.SinDatos::class.java) { WikidataJuegos.entidadDe(r, "Q1") }
        assertThrows(WikidataJuegos.SinDatos::class.java) { WikidataJuegos.entidadDe(r, "Q404") }
        assertEquals("Q2", (WikidataJuegos.entidadDe(r, "Q2")["id"] as kotlinx.serialization.json.JsonPrimitive).content)
        // No es un IOException: la app no dice «sin internet».
        assertFalse(java.io.IOException::class.java.isAssignableFrom(WikidataJuegos.SinDatos::class.java))
    }

    /* ----- Paso 5: géneros de videojuego y en el idioma de la app ----- */

    /** Géneros de Undertale como los da wbgetentities (claims y labels): dos son género de videojuego y uno es un tema. */
    private val generos = json("""{"entities":{
      "Q744038":{"type":"item","id":"Q744038","labels":${etiquetas("es" to "videojuego de rol", "en" to "role-playing video game")},"claims":{"P31":[${item("Q659563")}]}},
      "Q821157":{"type":"item","id":"Q821157","labels":${etiquetas("es" to "lluvia de balas", "en" to "bullet hell")},"claims":{"P31":[${item("Q659563")}]}},
      "Q85422949":{"type":"item","id":"Q85422949","labels":${etiquetas("es" to "videojuego con personajes LGBT", "en" to "video game with LGBT character")},"claims":{"P31":[${item("Q42907216")}]}},
      "Q999":{"type":"item","id":"Q999","labels":${etiquetas("es" to "Categoría:Juegos")},"claims":{"P31":[${item("Q659563")}]}},
      "Q998":{"type":"item","id":"Q998","labels":${etiquetas("es" to "sin clase")}}
    }}""")["entities"]!!.jsonObject

    @Test fun soloEntranLosGenerosDeVideojuego() {
        val n = WikidataJuegos.generosDeJuego(generos, "es")
        assertEquals(mapOf("Q744038" to "videojuego de rol", "Q821157" to "lluvia de balas"), n)
        assertEquals("bullet hell", WikidataJuegos.generosDeJuego(generos, "en")["Q821157"])
        // En la obra: el conocido con su nombre corto, el otro género con mayúscula y sin el tema.
        val j = WikidataJuegos.Juego("Q21039924", "Undertale", 2015, emptyList(), emptyList(), "", listOf("Q744038", "Q85422949", "Q821157"))
        assertEquals(listOf("Q744038", "Q821157"), WikidataJuegos.obra(j, n, null).generos)
        assertEquals(listOf("Rol", "Lluvia de balas"), WikidataJuegos.obra(j, n, null).generos.map { GenerosJuego.nombre(it, nEs, nEs, nEn) })
    }

    @Test fun losGenerosGuardadosSeMuestranEnElIdiomaDeLaApp() {
        // Guardado en español, visto en inglés, y al revés; sin importar mayúsculas ni tildes.
        assertEquals("Platformer", GenerosJuego.nombre("Plataformas", nEn, nEs, nEn))
        assertEquals("Lógica", GenerosJuego.nombre("puzzle", nEs, nEs, nEn))
        assertEquals("Supervivencia", GenerosJuego.nombre("Survival", nEs, nEs, nEn))
        // Los sumados tras la prueba en el emulador (Undertale) también cambian de idioma.
        assertEquals("Bullet hell", GenerosJuego.nombre("Lluvia de balas", nEn, nEs, nEn))
        assertEquals("Comedia", GenerosJuego.nombre("Comedy", nEs, nEs, nEn))
        // Uno que no está en la tabla queda como se guardó.
        assertEquals("Juego de cartas coleccionables", GenerosJuego.nombre("Juego de cartas coleccionables", nEn, nEs, nEn))
    }

    /** Los géneros de juego se guardan como claves y se traducen al mostrarlos; los ya guardados con su nombre se migran. */
    @Test fun generosDeJuegoComoClaves() {
        // Las claves de IGDB fuera de Wikidata, en el mismo orden que sus nombres.
        assertEquals(Igdb.OTROS_GENEROS.size, GenerosJuego.CLAVES_IGDB.size)
        assertEquals(GenerosJuego.CLAVES_IGDB.size, nEs.igdb.size)
        assertEquals(GenerosJuego.CLAVES_IGDB.size, nEn.igdb.size)
        assertEquals(WikidataJuegos.GENEROS.size, nEs.wikidata.size)
        // Lo que vio el tester: Celeste refrescado en español y vuelto a inglés mezclaba «Independiente».
        val celeste = Obra("1", Tipos.JUEGO, "Celeste", fuente = "igdb:26226", generos = listOf("Action", "Plataformas", "Adventure", "Independiente"))
        val migrada = GenerosJuego.migrar(celeste, nEs, nEn)
        assertEquals(listOf("Q270948", "Q828322", "Q23916", "igdb-genero:indie"), migrada.generos)
        assertEquals(listOf("Action", "Platformer", "Adventure", "Indie"), migrada.generos.map { GenerosJuego.nombre(it, nEn, nEs, nEn) })
        assertEquals(listOf("Acción", "Plataformas", "Aventura", "Independiente"), migrada.generos.map { GenerosJuego.nombre(it, nEs, nEs, nEn) })
        // Otra vez no cambia nada (es la misma obra), y un nombre de IGDB en inglés también se reconoce.
        assertSame(migrada, GenerosJuego.migrar(migrada, nEs, nEn))
        assertEquals("igdb-genero:card-board", GenerosJuego.clave("Card & Board Game", nEs, nEn))
        assertEquals("igdb-genero:point-and-click", GenerosJuego.clave("Apuntar y hacer clic", nEs, nEn))
        assertEquals("Q1610017", GenerosJuego.clave("Simulator"))
        // El mismo género con dos nombres queda una sola vez; uno desconocido queda como estaba.
        assertEquals(listOf("Q828322", "Juego de cartas coleccionables"),
            GenerosJuego.migrar(celeste.copy(generos = listOf("Platformer", "Plataformas", "Juego de cartas coleccionables")), nEs, nEn).generos)
        // A mano (sin fuente) o de otro tipo, no se toca: lo escribió la persona.
        assertEquals(celeste.generos, GenerosJuego.migrar(celeste.copy(fuente = ""), nEs, nEn).generos)
        assertEquals(listOf("Acción"), GenerosJuego.migrar(Obra("2", Tipos.PELICULA, "X", fuente = "tmdb:movie:1", generos = listOf("Acción")), nEs, nEn).generos)
    }
}
