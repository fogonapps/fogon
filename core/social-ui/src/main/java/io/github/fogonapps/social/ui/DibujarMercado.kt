package io.github.fogonapps.social.ui

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import io.github.fogonapps.identidad.puntosHalo
import io.github.fogonapps.identidad.R as RI
import java.io.File
import kotlin.math.*

internal val piezasMercado = linkedMapOf(
    "personaje_ahorrador_decidido" to RI.drawable.personaje_ahorrador_decidido,
    "personaje_ahorrador_dormido" to RI.drawable.personaje_ahorrador_dormido,
    "personaje_ahorrador_enamorado" to RI.drawable.personaje_ahorrador_enamorado,
    "personaje_ahorrador_feliz" to RI.drawable.personaje_ahorrador_feliz,
    "personaje_ahorrador_guino" to RI.drawable.personaje_ahorrador_guino,
    "personaje_ahorrador_miedo" to RI.drawable.personaje_ahorrador_miedo,
    "personaje_ahorrador_normal" to RI.drawable.personaje_ahorrador_normal,
    "personaje_ahorrador_parpadeo" to RI.drawable.personaje_ahorrador_parpadeo,
    "personaje_ahorrador_sorpresa" to RI.drawable.personaje_ahorrador_sorpresa,
    "personaje_ahorrador_triste" to RI.drawable.personaje_ahorrador_triste,
    "personaje_cajon_decidido" to RI.drawable.personaje_cajon_decidido,
    "personaje_cajon_dormido" to RI.drawable.personaje_cajon_dormido,
    "personaje_cajon_enamorado" to RI.drawable.personaje_cajon_enamorado,
    "personaje_cajon_feliz" to RI.drawable.personaje_cajon_feliz,
    "personaje_cajon_guino" to RI.drawable.personaje_cajon_guino,
    "personaje_cajon_miedo" to RI.drawable.personaje_cajon_miedo,
    "personaje_cajon_normal" to RI.drawable.personaje_cajon_normal,
    "personaje_cajon_parpadeo" to RI.drawable.personaje_cajon_parpadeo,
    "personaje_cajon_sorpresa" to RI.drawable.personaje_cajon_sorpresa,
    "personaje_cajon_triste" to RI.drawable.personaje_cajon_triste,
    "personaje_cucha_decidido" to RI.drawable.personaje_cucha_decidido,
    "personaje_cucha_dormido" to RI.drawable.personaje_cucha_dormido,
    "personaje_cucha_enamorado" to RI.drawable.personaje_cucha_enamorado,
    "personaje_cucha_feliz" to RI.drawable.personaje_cucha_feliz,
    "personaje_cucha_guino" to RI.drawable.personaje_cucha_guino,
    "personaje_cucha_miedo" to RI.drawable.personaje_cucha_miedo,
    "personaje_cucha_normal" to RI.drawable.personaje_cucha_normal,
    "personaje_cucha_parpadeo" to RI.drawable.personaje_cucha_parpadeo,
    "personaje_cucha_sorpresa" to RI.drawable.personaje_cucha_sorpresa,
    "personaje_cucha_triste" to RI.drawable.personaje_cucha_triste,
    "personaje_forzudo_decidido" to RI.drawable.personaje_forzudo_decidido,
    "personaje_forzudo_dormido" to RI.drawable.personaje_forzudo_dormido,
    "personaje_forzudo_enamorado" to RI.drawable.personaje_forzudo_enamorado,
    "personaje_forzudo_feliz" to RI.drawable.personaje_forzudo_feliz,
    "personaje_forzudo_guino" to RI.drawable.personaje_forzudo_guino,
    "personaje_forzudo_miedo" to RI.drawable.personaje_forzudo_miedo,
    "personaje_forzudo_normal" to RI.drawable.personaje_forzudo_normal,
    "personaje_forzudo_parpadeo" to RI.drawable.personaje_forzudo_parpadeo,
    "personaje_forzudo_sorpresa" to RI.drawable.personaje_forzudo_sorpresa,
    "personaje_forzudo_triste" to RI.drawable.personaje_forzudo_triste,
    "celebrar_ahorrador" to RI.drawable.celebrar_ahorrador,
    "celebrar_cajon" to RI.drawable.celebrar_cajon,
    "celebrar_cucha" to RI.drawable.celebrar_cucha,
    "celebrar_forzudo" to RI.drawable.celebrar_forzudo,
    "chispa" to RI.drawable.chispa,
)

/** Archivos internos únicamente; el JSON no puede abrir rutas arbitrarias. */
internal fun carpetaEditor(ctx: Context) = File(ctx.filesDir, "editor-mercado").apply { mkdirs() }
internal fun archivoImagenEditor(ctx: Context, nombre: String): File {
    require(Regex("foto-[a-f0-9-]+\\.jpg").matches(nombre))
    return File(carpetaEditor(ctx), nombre)
}

internal class DibujoMercado(private val ctx: Context) {
    private val imagenes = mutableMapOf<String, Bitmap>()
    private val letra = ResourcesCompat.getFont(ctx, RI.font.bricolage_bold)
    fun imagen(c: CapaMercado): Bitmap? = if (c.clase !in listOf("imagen", "personaje")) null else imagenes.getOrPut(c.contenido) {
        if (c.clase == "personaje") BitmapFactory.decodeResource(ctx.resources, piezasMercado[c.contenido] ?: error("pieza"))
        else BitmapFactory.decodeFile(archivoImagenEditor(ctx, c.contenido).path) ?: error("imagen")
    }
    fun limites(c: CapaMercado, l: LienzoMercado): RectF {
        val lado = min(l.ancho, l.alto) * c.tamano
        val foto = imagen(c)
        val ancho = when { c.clase == "texto" -> Paint().apply { typeface = letra; textSize = lado * .3f }.measureText(c.contenido)
            c.clase == "pildora" -> lado * 2f
            foto != null -> lado * foto.width / foto.height
            else -> lado }
        val alto = if (c.clase == "texto") lado * .4f else lado
        return RectF(c.x*l.ancho-ancho/2, c.y*l.alto-alto/2, c.x*l.ancho+ancho/2, c.y*l.alto+alto/2)
    }
    fun dibujar(l: LienzoMercado, colores: List<Int>): Bitmap {
        val bitmap = Bitmap.createBitmap(l.ancho, l.alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (l.tipo == "marco") {
            val anillo = Path().apply { fillType = Path.FillType.EVEN_ODD
                addCircle(256f,256f,256f,Path.Direction.CW); addCircle(256f,256f,180f,Path.Direction.CW) }
            canvas.clipPath(anillo)
        }
        fun color(i: Int) = colores[i.coerceIn(colores.indices)]
        p.color = color(l.fondo)
        if (l.degradado) p.shader = LinearGradient(0f,0f,l.ancho.toFloat(),l.alto.toFloat(),color(l.fondo),color(l.segundo),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,l.ancho.toFloat(),l.alto.toFloat(),p); p.shader = null
        p.color = color(l.segundo)
        puntosHalo(l.ancho.toFloat(),l.alto.toFloat(),l.paso).forEach { (o,t) ->
            p.alpha = (255 * l.trama * .55f * t).toInt(); canvas.drawCircle(o.x,o.y,l.paso*.15f,p)
        }
        p.alpha = 255
        l.capas.forEach { c ->
            val r = limites(c,l); p.color = color(c.color)
            when (c.clase) {
                "texto" -> { p.typeface = letra; p.textSize = min(l.ancho,l.alto)*c.tamano*.3f
                    canvas.drawText(c.contenido,r.left,r.centerY()-(p.ascent()+p.descent())/2,p) }
                "imagen", "personaje" -> imagen(c)?.let { canvas.drawBitmap(it,null,r,p) }
                "circulo" -> canvas.drawOval(r,p)
                "pildora" -> canvas.drawRoundRect(r,r.height()/2,r.height()/2,p)
                "estrella" -> {
                    val camino = Path()
                    repeat(16) { i -> val angulo = -PI/2+i*PI/8; val radio = r.width()/2 * if (i%2==0) 1f else .38f
                        val x = r.centerX()+cos(angulo).toFloat()*radio; val y = r.centerY()+sin(angulo).toFloat()*radio
                        if (i==0) camino.moveTo(x,y) else camino.lineTo(x,y) }
                    camino.close(); canvas.drawPath(camino,p)
                }
            }
        }
        if (l.tipo == "marco") {
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawCircle(256f,256f,180f,p)
            p.xfermode = null
        }
        return bitmap
    }
    fun exportar(l: LienzoMercado, colores: List<Int>): ByteArray {
        val b = dibujar(l,colores)
        try {
            val formato = if (l.tipo == "marco") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            var calidad = 92
            while (true) {
                val bytes = java.io.ByteArrayOutputStream().also { b.compress(formato,calidad,it) }.toByteArray()
                if (bytes.size <= 900_000) return bytes
                require(formato != Bitmap.CompressFormat.PNG && calidad > 35) { "imagen" }
                calidad -= 12
            }
        } finally { b.recycle() }
    }
    fun cerrar() { imagenes.values.forEach { it.recycle() }; imagenes.clear() }
}
