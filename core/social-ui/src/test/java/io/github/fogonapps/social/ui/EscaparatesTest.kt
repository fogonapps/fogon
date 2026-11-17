package io.github.fogonapps.social.ui

import io.github.fogonapps.perfil.ElementoEscaparate
import io.github.fogonapps.social.EscaparateSocial
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class EscaparatesTest {
    @Test fun adaptadorPreservaOrdenPortadasYFotoSinInventarUnaSubida() {
        val e = escaparateElementos("cajon:albumes", listOf(
            ElementoEscaparate("Segundo", "Artista", "https://ejemplo.invalid/portada.jpg"),
            ElementoEscaparate("Primero", portada = "file:///privado.jpg"),
        )).paraVista()
        assertEquals(listOf("Segundo", "Primero"), e.elementos.map { it.titulo })
        assertEquals("https://ejemplo.invalid/portada.jpg", e.elementos[0].portada)
        assertEquals("", e.elementos[1].portada)
        val galeria = EscaparateSocial("galeria", buildJsonObject {
            put("fotos", JsonArray(listOf(buildJsonObject { put("publicacion", 42); put("nombre", "foto-servidor") })))
        }).paraVista()
        assertEquals(listOf("foto-servidor"), galeria.fotos)
    }
    @Test fun portadasPrivadasNuncaSalenEnLaInstantanea() {
        val portadas = listOf("file:///data/user/0/cajon/oidos/disco.jpg", "/data/portada.jpg", "content://album/1", "id_de_imagen", "https://ejemplo.invalid/disco.jpg", "")
        val elementos = escaparateElementos("cajon:albumes", portadas.map { ElementoEscaparate("Disco", portada = it) }).paraVista().elementos
        assertEquals(listOf("", "", "", "", portadas[4], ""), elementos.map { it.portada })
    }

    @Test fun metaSoloContienePorcentajeYEtiqueta() {
        val d = escaparateNumero("alcancia:meta", 75, "%").datos
        assertEquals(setOf("numero", "etiqueta"), d.keys)
        assertEquals(75, d["numero"]!!.jsonPrimitive.int)
        assertEquals("%", d["etiqueta"]!!.jsonPrimitive.content)
    }
}
