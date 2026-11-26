package io.github.fogonapps.social

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Qué tan probable es que una foto subida sea un desnudo o contenido sexual (de 0 a 1). Corre en el propio servidor:
 * ninguna foto sale a terceros. Si supera [Desnudos.UMBRAL], lo publicado con ella queda «en revisión» hasta que el
 * equipo decida. No detecta material sexual con menores ni dice nada sobre edades: eso solo lo reporta una persona.
 */
fun interface RevisorFotos {
    fun probabilidad(imagen: BufferedImage): Double
}

/**
 * El modelo libre `Marqo/nsfw-image-detection-384` (Apache-2.0): un ViT diminuto (vit_tiny_patch16_384, 5,7 millones
 * de parámetros, 22 MB) que aquí se ejecuta en Kotlin puro, sin bibliotecas ni código nativo. Los pesos no van en el
 * repositorio: el instalador los baja de su origen y se comprueban con [HUELLA].
 */
class Desnudos private constructor(private val p: Map<String, FloatArray>) : RevisorFotos {

    companion object {
        /** Desde qué probabilidad una foto pasa a revisión (hipótesis inicial; se ajusta con lo que confirme el equipo). */
        const val UMBRAL = 0.85
        const val ARCHIVO = "nsfw-image-detection-384.safetensors"
        /** SHA-256 del archivo de pesos (revisión 0c26ec22 del repositorio del modelo). */
        const val HUELLA = "6bf2e0f64a1d20169736c2836e3a787b12379fdc08ba87f7d94a7a3d58eeefce"
        private const val LADO = 384
        private const val PARCHE = 16
        private const val D = 192
        private const val CABEZAS = 3
        private const val CAPAS = 12
        private const val OCULTA = 768
        private const val N = (LADO / PARCHE) * (LADO / PARCHE) + 1

        /** Carga los pesos. Sin el archivo, null; si su huella no coincide, lanza [IllegalStateException] (la revisión queda apagada). */
        fun cargar(archivo: File): Desnudos? {
            if (!archivo.isFile) return null
            val bytes = archivo.readBytes()
            val huella = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(huella == HUELLA) { "no coincide con su huella" }
            return Desnudos(leerSafetensors(bytes))
        }

        /** Lee un archivo .safetensors de pesos F32 (cabecera JSON y los datos en little-endian). */
        internal fun leerSafetensors(bytes: ByteArray): Map<String, FloatArray> {
            val largo = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
            val cabecera = JsonSocial.parseToJsonElement(String(bytes, 8, largo, Charsets.UTF_8)) as kotlinx.serialization.json.JsonObject
            val base = 8 + largo
            return cabecera.filterKeys { it != "__metadata__" }.mapValues { (_, v) ->
                val o = v as kotlinx.serialization.json.JsonObject
                require((o["dtype"] as kotlinx.serialization.json.JsonPrimitive).content == "F32")
                val (desde, hasta) = (o["data_offsets"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() }
                val f = FloatArray((hasta - desde) / 4)
                ByteBuffer.wrap(bytes, base + desde, hasta - desde).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(f)
                f
            }
        }

        /** Como en el entrenamiento (timm): lado corto a 384 (bicúbico), recorte central de 384 × 384 y valores de -1 a 1. */
        internal fun entrada(img: BufferedImage): FloatArray {
            val escala = LADO.toDouble() / minOf(img.width, img.height)
            val ancho = maxOf(LADO, Math.round(img.width * escala).toInt())
            val alto = maxOf(LADO, Math.round(img.height * escala).toInt())
            val lienzo = BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB)
            lienzo.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(img, -(ancho - LADO) / 2, -(alto - LADO) / 2, ancho, alto, java.awt.Color.BLACK, null)
                dispose()
            }
            val x = FloatArray(3 * LADO * LADO)
            for (y in 0 until LADO) for (c in 0 until LADO) {
                val rgb = lienzo.getRGB(c, y)
                val i = y * LADO + c
                x[i] = ((rgb shr 16 and 255) / 255f - 0.5f) / 0.5f
                x[LADO * LADO + i] = ((rgb shr 8 and 255) / 255f - 0.5f) / 0.5f
                x[2 * LADO * LADO + i] = ((rgb and 255) / 255f - 0.5f) / 0.5f
            }
            return x
        }

        /** Traspone una matriz [filas × columnas] para recorrerla por filas en la multiplicación. */
        private fun traspuesta(w: FloatArray, filas: Int, columnas: Int) = FloatArray(w.size).also { t ->
            for (f in 0 until filas) for (c in 0 until columnas) t[c * filas + f] = w[f * columnas + c]
        }

        /** erf con error menor que 1,5e-7 (Abramowitz y Stegun, 7.1.26), para la GELU exacta. */
        private fun erf(x: Float): Float {
            val t = 1.0 / (1.0 + 0.3275911 * abs(x))
            val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-(x.toDouble() * x))
            return (if (x >= 0) y else -y).toFloat()
        }
    }

    /** Los pesos lineales traspuestos ([entrada × salida]) para que el bucle interno sea contiguo. */
    private val t = p.filterKeys { it.endsWith(".weight") && (it.contains("attn.") || it.contains("mlp.") || it == "head.weight") }.mapValues { (k, w) ->
        val salida = p.getValue(k.removeSuffix(".weight") + ".bias").size
        traspuesta(w, salida, w.size / salida)
    } + ("patch_embed.proj.weight" to traspuesta(p.getValue("patch_embed.proj.weight"), D, 3 * PARCHE * PARCHE))

    /** x [filas × entrada] · W + b → [filas × salida]. */
    private fun lineal(x: FloatArray, filas: Int, entrada: Int, nombre: String): FloatArray {
        val w = t.getValue("$nombre.weight"); val bias = p.getValue("$nombre.bias"); val salida = bias.size
        val y = FloatArray(filas * salida)
        for (i in 0 until filas) {
            val o = i * salida
            System.arraycopy(bias, 0, y, o, salida)
            for (k in 0 until entrada) {
                val a = x[i * entrada + k]
                if (a == 0f) continue
                val wk = k * salida
                for (j in 0 until salida) y[o + j] += a * w[wk + j]
            }
        }
        return y
    }

    private fun normalizar(x: FloatArray, filas: Int, nombre: String): FloatArray {
        val g = p.getValue("$nombre.weight"); val b = p.getValue("$nombre.bias")
        val y = FloatArray(x.size)
        for (i in 0 until filas) {
            val o = i * D
            var media = 0f; for (j in 0 until D) media += x[o + j]; media /= D
            var varianza = 0f; for (j in 0 until D) { val d = x[o + j] - media; varianza += d * d }; varianza /= D
            val inv = 1f / sqrt(varianza + 1e-6f)
            for (j in 0 until D) y[o + j] = (x[o + j] - media) * inv * g[j] + b[j]
        }
        return y
    }

    private fun atencion(x: FloatArray, capa: Int): FloatArray {
        val qkv = lineal(x, N, D, "blocks.$capa.attn.qkv")
        val dim = D / CABEZAS
        val escala = 1f / sqrt(dim.toFloat())
        val salida = FloatArray(N * D)
        val kt = FloatArray(dim * N)
        val fila = FloatArray(N)
        for (h in 0 until CABEZAS) {
            for (j in 0 until N) for (d in 0 until dim) kt[d * N + j] = qkv[j * 3 * D + D + h * dim + d]
            for (i in 0 until N) {
                java.util.Arrays.fill(fila, 0f)
                for (d in 0 until dim) {
                    val q = qkv[i * 3 * D + h * dim + d] * escala
                    val o = d * N
                    for (j in 0 until N) fila[j] += q * kt[o + j]
                }
                var max = Float.NEGATIVE_INFINITY; for (j in 0 until N) if (fila[j] > max) max = fila[j]
                var suma = 0f; for (j in 0 until N) { fila[j] = exp(fila[j] - max); suma += fila[j] }
                val o = i * D + h * dim
                for (j in 0 until N) {
                    val a = fila[j] / suma
                    val v = j * 3 * D + 2 * D + h * dim
                    for (d in 0 until dim) salida[o + d] += a * qkv[v + d]
                }
            }
        }
        return lineal(salida, N, D, "blocks.$capa.attn.proj")
    }

    /** Los dos valores de salida del modelo: [NSFW, SFW]. */
    internal fun logits(entrada: FloatArray): FloatArray {
        val ladoParches = LADO / PARCHE
        val parches = FloatArray((N - 1) * 3 * PARCHE * PARCHE)
        for (py in 0 until ladoParches) for (px in 0 until ladoParches) {
            val o = (py * ladoParches + px) * 3 * PARCHE * PARCHE
            for (c in 0 until 3) for (ky in 0 until PARCHE) for (kx in 0 until PARCHE)
                parches[o + (c * PARCHE + ky) * PARCHE + kx] = entrada[c * LADO * LADO + (py * PARCHE + ky) * LADO + px * PARCHE + kx]
        }
        val incrustado = lineal(parches, N - 1, 3 * PARCHE * PARCHE, "patch_embed.proj")
        val pos = p.getValue("pos_embed")
        var x = FloatArray(N * D)
        System.arraycopy(p.getValue("cls_token"), 0, x, 0, D)
        System.arraycopy(incrustado, 0, x, D, (N - 1) * D)
        for (i in x.indices) x[i] += pos[i]
        for (capa in 0 until CAPAS) {
            val a = atencion(normalizar(x, N, "blocks.$capa.norm1"), capa)
            for (i in x.indices) x[i] += a[i]
            val m = lineal(normalizar(x, N, "blocks.$capa.norm2"), N, D, "blocks.$capa.mlp.fc1")
            for (i in m.indices) m[i] = 0.5f * m[i] * (1f + erf(m[i] / 1.4142135f))
            val y = lineal(m, N, OCULTA, "blocks.$capa.mlp.fc2")
            for (i in x.indices) x[i] += y[i]
        }
        val cls = normalizar(x.copyOf(D), 1, "norm")
        return lineal(cls, 1, D, "head")
    }

    /** Una foto a la vez: así el pico de memoria y de CPU queda acotado aunque lleguen varias juntas. */
    @Synchronized
    override fun probabilidad(imagen: BufferedImage): Double {
        val l = logits(entrada(imagen))
        val m = maxOf(l[0], l[1])
        val a = exp((l[0] - m).toDouble()); val b = exp((l[1] - m).toDouble())
        return a / (a + b)
    }
}
