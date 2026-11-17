package io.github.fogonapps.social.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DibujarMercadoTest {
    private val colores=listOf(Color.RED,Color.BLUE,Color.GREEN,Color.WHITE,Color.BLACK,Color.YELLOW)
    @Test fun bannerExportadoEsJpeg1360Por400ConCapasOrdenadas() {
        val d=DibujoMercado(RuntimeEnvironment.getApplication())
        val l=LienzoMercado().agregar("circulo",color=1).agregar("circulo",color=2)
        val bytes=d.exportar(l,colores)
        assertEquals(0xff,bytes[0].toInt() and 255); assertEquals(0xd8,bytes[1].toInt() and 255)
        val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(1360,b.width); assertEquals(400,b.height)
        assertTrue(Color.green(b.getPixel(680,200))>240)
        b.recycle(); d.cerrar()
    }
    @Test fun marcoPng512ConHuecoInclusoCuandoUnaFotoOTextoLoTaparia() {
        val d=DibujoMercado(RuntimeEnvironment.getApplication())
        val l=LienzoMercado("marco",degradado=true,trama=1f).agregar("circulo",color=2).let { it.cambiar(it.capas.single().copy(tamano=3f)) }
        val bytes=d.exportar(l,colores)
        assertEquals(0x89,bytes[0].toInt() and 255)
        val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(512,b.width); assertEquals(512,b.height)
        for(y in 90..422 step 16) for(x in 90..422 step 16) {
            if(kotlin.math.hypot(x-256.0,y-256.0)<178) assertEquals(0,Color.alpha(b.getPixel(x,y)))
        }
        assertEquals(255,Color.alpha(b.getPixel(256,32)))
        assertEquals(0,Color.alpha(b.getPixel(0,0)))
        b.recycle(); d.cerrar()
    }
    @Test fun fotoPrivadaSigueDisponibleAlReconstruirElBorrador() {
        val ctx=RuntimeEnvironment.getApplication()
        val archivo=archivoImagenEditor(ctx,"foto-123.jpg")
        val foto=android.graphics.Bitmap.createBitmap(100,50,android.graphics.Bitmap.Config.ARGB_8888)
        foto.eraseColor(Color.GREEN)
        archivo.outputStream().use { foto.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it) }; foto.recycle()
        val l=LienzoMercado.leer(LienzoMercado().agregar("imagen",archivo.name).agregar("texto","Riso").serializar())
        val d=DibujoMercado(ctx)
        val b=d.dibujar(l,colores)
        assertEquals(2f,d.limites(l.capas.first(),l).let { it.width()/it.height() },.01f)
        assertTrue(Color.green(b.getPixel(600,200))>200)
        b.recycle(); d.cerrar(); archivo.delete()
        assertThrows(IllegalArgumentException::class.java) { archivoImagenEditor(ctx,"../fuera.jpg") }
    }
    @Test fun todasLasPiezasSonRecursosQueSePuedenDibujar() {
        val d=DibujoMercado(RuntimeEnvironment.getApplication())
        piezasMercado.keys.forEach { nombre -> assertNotNull(d.imagen(CapaMercado(1,"personaje",contenido=nombre))) }
        assertEquals(45,piezasMercado.size)
        d.cerrar()
    }
}
