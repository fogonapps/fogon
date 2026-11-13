package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.ui.resenasDelPerfil
import io.github.fogonapps.cajon.ui.aPerfilVista
import io.github.fogonapps.cajon.ui.catalogoSeccionesPerfil
import io.github.fogonapps.perfil.ContadorPerfil
import io.github.fogonapps.perfil.SeccionPerfil
import org.junit.Assert.*
import org.junit.Test

class PerfilVistaTest {
    private fun secciones(propio: Boolean = true) = catalogoSeccionesPerfil(propio).map { (id, icono, nombre) ->
        SeccionPerfil(id, icono, nombre.toString()) {}
    }

    @Test fun `el dueno conserva todas sus secciones cualquiera que sea su visibilidad`() {
        for (visibilidad in Visibilidad.TODAS) {
            val estado = Estado(ajustes = Ajustes(nombre = "Nombre local"), perfil = Perfil(
                usuario = "local", foto = "foto-local", portada = "portada-local", bio = "Bio", enlaces = listOf("https://example.org"),
                visibilidad = PartesPerfil.TODAS.associateWith { visibilidad },
            ))
            val vista = estado.aPerfilVista(secciones())
            assertEquals(setOf(0, 1, 5, 6), vista.secciones.map { it.id }.toSet())
            assertEquals("Nombre local", vista.nombre)
            assertEquals("local", vista.usuario)
            assertEquals("foto-local", vista.foto)
            assertEquals("portada-local", vista.portada)
            assertEquals("Bio", vista.bio)
            assertEquals(estado.perfil.enlaces, vista.enlaces)
            assertEquals("yo", vista.relacion)
        }
    }

    @Test fun `con cuenta usa la identidad de cuenta y conserva las acciones de contadores`() {
        val contador = ContadorPerfil("3", "Seguidores") {}
        val vista = Estado().aPerfilVista(secciones(), listOf(contador), MiCuenta(usuario = "cuenta", privada = true))
        assertEquals("cuenta", vista.usuario)
        assertTrue(vista.privada)
        assertSame(contador, vista.contadores.single())
        assertEquals(4, vista.secciones.size)
    }

    @Test fun `las partes filtradas por la red no ocultan pestanas ni se reconstruyen desde el estado local`() {
        // Las partes privadas no llegan en PerfilSocial. Las pestañas permanecen y su contenido muestra el vacío.
        for (relacion in listOf("nada", "pendiente", "sigo", "yo")) {
            for (conPartes in listOf(false, true)) {
                val partes = if (conPartes) mapOf("musica" to kotlinx.serialization.json.JsonArray(emptyList())) else emptyMap()
                val perfil = PerfilSocial(PersonaSocial("persona", "Nombre", foto = true), bio = "Bio", portada = "Portada",
                    enlaces = listOf("https://example.org"), relacion = relacion, partes = partes)
                val secciones = secciones(false)
                val vista = perfil.aPerfilVista(secciones)
                assertEquals(setOf(0, 1, 5, 6), vista.secciones.map { it.id }.toSet())
                assertSame(secciones[2].contenido, vista.secciones[2].contenido)
                assertEquals(relacion, vista.relacion)
                assertEquals("Nombre", vista.nombre)
                assertEquals(ClienteSocial.foto("persona"), vista.foto)
                assertEquals(perfil.bio, vista.bio)
                assertEquals(perfil.enlaces, vista.enlaces)
            }
        }
    }

    @Test fun `cuenta privada sin seguir conserva su presentacion pero no tiene secciones`() {
        for (relacion in listOf("nada", "pendiente")) {
            val vista = PerfilSocial(PersonaSocial("privada", privada = true), cerrada = true, relacion = relacion,
                bio = "Presentación", portada = "portada", enlaces = listOf("https://ejemplo.org"))
                .aPerfilVista(secciones(false))
            assertTrue(vista.privada)
            assertTrue(vista.secciones.isEmpty())
            assertEquals("Presentación", vista.bio)
            assertEquals("portada", vista.portada)
            assertEquals(listOf("https://ejemplo.org"), vista.enlaces)
        }
    }

    @Test fun `cuenta privada seguida muestra las mismas pestanas que la publica`() {
        val vista = PerfilSocial(PersonaSocial("privada", privada = true), relacion = "sigo", cerrada = false)
            .aPerfilVista(secciones(false))
        assertEquals(setOf(0, 1, 5, 6), vista.secciones.map { it.id }.toSet())
    }

    @Test fun `bloquear elimina contenido e imagenes incluso de un perfil publico almacenado`() {
        val vista = PerfilSocial(PersonaSocial("persona", foto = true), bio = "Bio", portada = "Portada",
            enlaces = listOf("https://example.org"), relacion = "sigo")
            .aPerfilVista(secciones(false), listOf(ContadorPerfil("3", "Seguidores")), bloqueado = true)
        assertTrue(vista.secciones.isEmpty())
        assertTrue(vista.contadores.isEmpty())
        assertEquals("", vista.foto)
        assertEquals("", vista.portada)
        assertEquals("", vista.bio)
        assertTrue(vista.enlaces.isEmpty())
    }

    @Test fun `el catalogo distingue segunda y tercera persona para evitar H CJ 07`() {
        val propio = catalogoSeccionesPerfil(true).associate { it.first to it.third }
        assertEquals(4, propio.size)
        val ajeno = catalogoSeccionesPerfil(false).associate { it.first to it.third }
        assertEquals(R.string.parte_destacados, propio[1])
        assertEquals(R.string.red_lo_suyo, ajeno[1])
        assertEquals(R.string.tus_cifras, propio[5])
        assertEquals(R.string.red_cifras, ajeno[5])
        assertEquals(R.string.resena_tuyas, propio[6])
        assertEquals(R.string.resenas_suyas, ajeno[6])
        assertEquals(propio[0], ajeno[0])
    }

    @Test fun `resenas propias excluyen borradas conservan privadas y ordenan por fecha`() {
        val antigua = Resena("a", "obra", fecha = "2025-01-01", visibilidad = Visibilidad.PUBLICA)
        val privada = Resena("p", "obra", fecha = "2026-11-07", visibilidad = Visibilidad.PRIVADA)
        val borrada = Resena("b", "obra", fecha = "2026-11-08", borrada = true)
        assertEquals(listOf(privada, antigua), Estado(resenas = listOf(antigua, borrada, privada)).resenasDelPerfil())
        assertTrue(Estado().resenasDelPerfil().isEmpty())
    }

    @Test fun `perfil ajeno selecciona solo publicaciones de tipo resena`() {
        fun publicacion(id: Long, tipo: String) = PublicacionSocial(id, PersonaSocial("persona"), tipo, id,
            Visibilidad.PUBLICA, kotlinx.serialization.json.JsonObject(emptyMap()))
        val resena = publicacion(2, "resena")
        val perfil = PerfilSocial(PersonaSocial("persona"), publicaciones = listOf(
            publicacion(1, "diario"), resena, publicacion(3, "lista"), publicacion(4, "publicacion")))
        assertEquals(listOf(resena), perfil.resenasDelPerfil())
        assertTrue(perfil.copy(publicaciones = emptyList()).resenasDelPerfil().isEmpty())
    }
}
