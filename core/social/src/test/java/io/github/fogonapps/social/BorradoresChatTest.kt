package io.github.fogonapps.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BorradoresChatTest {
    @get:Rule val carpeta = TemporaryFolder()
    private fun archivo() = carpeta.root.resolve("chats.json.borradores")

    @Test fun loEscritoSobreviveAlCierreDeLaApp() {
        BorradoresChat(archivo()).poner("yo", "ana", "Qq hola")
        // Otro objeto con el mismo archivo: como cuando Android cerró la app y se vuelve a abrir.
        assertEquals("Qq hola", BorradoresChat(archivo()).de("yo", "ana"))
        assertEquals("", BorradoresChat(archivo()).de("yo", "beto"))
    }

    @Test fun alEnviarElCampoVacioLoBorra() {
        val b = BorradoresChat(archivo())
        b.poner("yo", "ana", "hola")
        b.poner("yo", "ana", "")
        assertEquals("", BorradoresChat(archivo()).de("yo", "ana"))
        assertFalse(archivo().exists())
    }

    @Test fun bloquearOBorrarLaConversacionLoOlvidaSoloAEsaPersona() {
        val b = BorradoresChat(archivo())
        b.poner("yo", "ana", "para ana")
        b.poner("yo", "beto", "para beto")
        b.olvidar("ana")
        val otra = BorradoresChat(archivo())
        assertEquals("", otra.de("yo", "ana"))
        assertEquals("para beto", otra.de("yo", "beto"))
    }

    @Test fun cerrarLaSesionLosOlvidaTodos() {
        val b = BorradoresChat(archivo())
        b.poner("yo", "ana", "uno"); b.poner("yo", "beto", "dos")
        b.olvidarTodo()
        assertEquals("", BorradoresChat(archivo()).de("yo", "beto"))
        assertFalse(archivo().exists())
    }

    @Test fun otraCuentaNoVeElBorradorNiSinSesionSeGuarda() {
        val b = BorradoresChat(archivo())
        b.poner("yo", "ana", "privado")
        assertEquals("", b.de("otra", "ana"))
        assertEquals("", b.de(null, "ana"))
        b.poner(null, "beto", "sin sesión")
        assertEquals("", b.de("yo", "beto"))
    }

    @Test fun seRecortaAlLargoDelCampo() {
        val b = BorradoresChat(archivo())
        b.poner("yo", "ana", "a".repeat(5000))
        assertEquals(BorradoresChat.MAX, BorradoresChat(archivo()).de("yo", "ana").length)
    }

    @Test fun unArchivoDanadoNoRompeNada() {
        archivo().writeText("{no es json")
        val b = BorradoresChat(archivo())
        assertEquals("", b.de("yo", "ana"))
        b.poner("yo", "ana", "nuevo")
        assertEquals("nuevo", BorradoresChat(archivo()).de("yo", "ana"))
    }

    @Test fun cerrarLaSesionLosBorraAunqueElChatNoSeHayaAbierto() {
        val chats = carpeta.root.resolve("chats.json")
        BorradoresChat(BorradoresChat.archivoDe(chats)).poner("yo", "ana", "a medias")
        // Lo que hace RedApp.olvidarCuenta: sin objeto de chat, solo el archivo del perfil.
        BorradoresChat.borrarDe(chats)
        assertFalse(BorradoresChat.archivoDe(chats).exists())
        assertEquals("", BorradoresChat(BorradoresChat.archivoDe(chats)).de("yo", "ana"))
    }
}
