package io.github.fogonapps.cajon

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.fogonapps.cajon.datos.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PortadasOidasTest {
    @Test fun claveToleranteConservaElNombreSinEdicionesNiInvitados() {
        for (titulo in listOf("Álbum (Deluxe Edition)", "Álbum - Single", "Álbum [Remastered 2011]", "Álbum (Live)", "Álbum - EP")) {
            assertEquals("artista|album", RegistroEscuchas.claveTolerante("Artista feat. Otro", titulo))
        }
        assertEquals("artista|album", RegistroEscuchas.claveTolerante("Artista ft. Otro", "Álbum"))
        assertEquals("artista|album", RegistroEscuchas.claveTolerante("Artista con Otro", "Álbum"))
        assertNotEquals(RegistroEscuchas.claveTolerante("Artista", "Álbum (Otra historia)"), "artista|album")
    }

    @Test fun reintentaJustoALas24HorasYConservaElIntentoEnDisco() {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "intentos").apply { mkdirs() }
        try {
            val registro = RegistroEscuchas(RuntimeEnvironment.getApplication(), dir)
            registro.cambiar { it.copy(intentosPortada = mapOf("clave" to 1000L)) }
            val o = RegistroEscuchas(RuntimeEnvironment.getApplication(), dir).oidos.value
            assertFalse(RegistroEscuchas.debeIntentar(o, "clave", 86_400_999L))
            assertTrue(RegistroEscuchas.debeIntentar(o, "clave", 86_401_000L))
            assertTrue(RegistroEscuchas.debeIntentar(o, "otra", 1000L))
        } finally { dir.deleteRecursively() }
    }

    @Test fun guardaBitmapLocalPequenoYPrefiereLaCopiaExistente() {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "caratula").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        try {
            val registro = RegistroEscuchas(RuntimeEnvironment.getApplication(), dir)
            val uri = registro.guardarPortada("artista|album", bitmap)
            val archivo = File(java.net.URI(uri))
            val dimensiones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(archivo.path, dimensiones)
            assertEquals(300, dimensiones.outWidth)
            assertEquals(200, dimensiones.outHeight)
            val escucha = Escucha("Artista", "Álbum", "Canción", 1L, "App", portada = uri)
            registro.sumar(listOf(escucha))
            registro.sumar(listOf(escucha.copy(cuando = 60001L, portada = "https://ejemplo.org/a.jpg")))
            assertEquals(uri, registro.oidos.value.albumes.single().portada)
            assertEquals("", RegistroEscuchas.paraExportar(registro.oidos.value).albumes.single().portada)
            archivo.delete()
            assertEquals("https://ejemplo.org/a.jpg", RegistroEscuchas.preferirPortada(uri, "https://ejemplo.org/a.jpg"))
        } finally { bitmap.recycle(); dir.deleteRecursively() }
    }
}
