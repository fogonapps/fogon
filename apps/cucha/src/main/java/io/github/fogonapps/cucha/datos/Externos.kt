package io.github.fogonapps.cucha.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/*
 * Lo que La Cucha trae de afuera: los datos de El Forzudo (en el mismo teléfono, por el proveedor de Fogón)
 * y los productos de Open Food Facts (por internet, solo si la persona lo activa).
 */

/** Lo que La Cucha lee de El Forzudo. */
data class DeForzudo(
    /** El último peso anotado en El Forzudo y su fecha. */
    val peso: Peso?,
    /** Todos los pesos, del más viejo al más nuevo. */
    val pesos: List<Peso>,
    /** El nombre del entreno de hoy, si ya entrenó. */
    val entrenoHoy: String?,
    /** Entrenos de los últimos 7 días. */
    val entrenosSemana: Int,
)

/** Lee el respaldo de El Forzudo (su JSON) sin depender de su modelo: solo el peso y los entrenos. */
fun leerForzudo(texto: String, hoy: LocalDate): DeForzudo? = runCatching {
    val o = JsonCucha.parseToJsonElement(texto).jsonObject
    val pesos = o["body"]?.jsonArray.orEmpty().mapNotNull { p ->
        val x = p.jsonObject
        val f = x["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val kg = x["kg"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        Peso(f.take(10), kg)
    }.sortedBy { it.fecha }
    val sesiones = o["sessions"]?.jsonArray.orEmpty().map { it.jsonObject }
    fun fecha(s: JsonObject) = s["date"]?.jsonPrimitive?.contentOrNull?.take(10)
    val hoyTxt = hoy.toString()
    val deHoy = sesiones.firstOrNull { fecha(it) == hoyTxt }
    val nombre = deHoy?.let { (it["title"] ?: it["day"])?.jsonPrimitive?.contentOrNull ?: "" }
    val desde = hoy.minusDays(6).toString()
    DeForzudo(pesos.lastOrNull(), pesos, nombre, sesiones.count { s -> fecha(s)?.let { it in desde..hoyTxt } == true })
}.getOrNull()

/** Un producto de Open Food Facts (por 100 g), o null si no está o no trae las calorías. */
class OpenFoodFacts(private val cliente: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()) {

    class SinRed : Exception()

    /** Busca un código de barras. Lanza [SinRed] si no hay conexión. */
    suspend fun buscar(codigo: String): Producto? = withContext(Dispatchers.IO) {
        val limpio = codigo.filter(Char::isDigit)
        if (limpio.length !in 8..14) return@withContext null
        val pedido = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$limpio.json?fields=product_name,product_name_es,product_name_en,nutriments,serving_quantity")
            // Open Food Facts pide identificarse con el nombre de la app y un contacto.
            .header("User-Agent", "LaCucha-Fogon/1.0 (fogonapps@gmail.com)")
            .build()
        val texto = try {
            cliente.newCall(pedido).execute().use { r -> if (r.code == 404) return@withContext null else r.body.string() }
        } catch (e: java.io.IOException) { throw SinRed() }
        leerProductoOff(texto, limpio)
    }
}

/** Convierte la respuesta de Open Food Facts en un producto (separado para probarlo sin red). */
fun leerProductoOff(texto: String, codigo: String): Producto? = runCatching {
    val o = JsonCucha.parseToJsonElement(texto).jsonObject
    if (o["status"]?.jsonPrimitive?.intOrNull != 1) return null
    val p = o["product"]!!.jsonObject
    val n = p["nutriments"]?.jsonObject ?: return null
    fun v(k: String) = n[k]?.jsonPrimitive?.doubleOrNull
    val kcal = v("energy-kcal_100g") ?: v("energy_100g")?.let { it / 4.184 } ?: return null
    val nombre = listOf("product_name_es", "product_name", "product_name_en")
        .firstNotNullOfOrNull { k -> p[k]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } } ?: codigo
    Producto(
        id = "off$codigo", nombre = nombre.take(60),
        por100 = Nutrientes(kcal, v("proteins_100g") ?: 0.0, v("carbohydrates_100g") ?: 0.0, v("fat_100g") ?: 0.0),
        codigo = codigo, porcionGramos = p["serving_quantity"]?.jsonPrimitive?.doubleOrNull, fuente = "off",
    )
}.getOrNull()
