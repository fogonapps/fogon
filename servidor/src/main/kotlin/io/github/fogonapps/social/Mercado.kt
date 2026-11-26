package io.github.fogonapps.social

import kotlinx.serialization.Serializable
import java.sql.ResultSet

@Serializable data class PublicarMercado(val clave: String, val imagen: String, val nombre: String, val tipo: String, val precio: Int)
@Serializable data class DecisionMercado(val accion: String, val motivo: String = "")
@Serializable data class CompraMercado(val clave: String)
@Serializable data class ArticuloMercado(val id: Long, val creador: String, val nombre: String, val tipo: String, val precio: Int,
    val oficial: Boolean, val estado: String, val motivo: String, val ventas: Int, val cuando: Long, val propio: Boolean, val adquirido: Boolean)
@Serializable data class PaginaMercado(val articulos: List<ArticuloMercado>, val siguiente: Int?)

/** Inventario común y movimientos enteros dentro de la misma transacción SQLite. */
class Mercado(private val b: Base, private val s: Social, private val ahora: () -> Long) {
    init { b.usar {
        cambiar("""CREATE TABLE IF NOT EXISTS mercado(id INTEGER PRIMARY KEY AUTOINCREMENT, cuenta INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
            clave TEXT NOT NULL, imagen TEXT NOT NULL, nombre TEXT NOT NULL, tipo TEXT NOT NULL, precio INTEGER NOT NULL,
            oficial INTEGER NOT NULL, estado TEXT NOT NULL, motivo TEXT NOT NULL, cuando INTEGER NOT NULL, UNIQUE(cuenta,clave))""")
        cambiar("""CREATE TABLE IF NOT EXISTS mercado_compras(id INTEGER PRIMARY KEY, articulo INTEGER NOT NULL REFERENCES mercado(id) ON DELETE CASCADE,
            comprador INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE, destino INTEGER NOT NULL REFERENCES cuentas(id) ON DELETE CASCADE,
            clave TEXT NOT NULL, precio INTEGER NOT NULL, cuando INTEGER NOT NULL, UNIQUE(comprador,clave), UNIQUE(articulo,destino))""")
        cambiar("CREATE INDEX IF NOT EXISTS mercado_listado ON mercado(estado,tipo,id)")
    } }
    private fun clave(c: String) { if (!Regex("[A-Za-z0-9_-]{8,80}").matches(c)) throw Problema(400, "clave") }
    private fun fila(r: ResultSet, yo: Long?): ArticuloMercado {
        val id = r.getLong("id"); val cuenta = r.getLong("cuenta"); val tipo = r.getString("tipo")
        return ArticuloMercado(id, r.getString("usuario"), r.getString("nombre"), tipo, r.getInt("precio"), r.getInt("oficial") == 1,
            r.getString("estado"), r.getString("motivo"), r.getInt("ventas"), r.getLong("cuando"), cuenta == yo,
            yo != null && permitido(yo, "$tipo:m$id"))
    }
    private val consulta = """SELECT m.*, c.usuario, (SELECT COUNT(*) FROM mercado_compras v WHERE v.articulo=m.id) AS ventas
        FROM mercado m JOIN cuentas c ON c.id=m.cuenta"""
    fun detalle(yo: Long?, id: Long, equipo: Boolean = false): ArticuloMercado = b.usar {
        val encontrado = leer("$consulta WHERE m.id=?", id) { fila(it, yo) to it.getLong("cuenta") }.singleOrNull()
        encontrado?.first?.takeIf {
            equipo || it.propio || it.estado in setOf("aprobado", "retirado") && (yo == null || it.adquirido || !s.hayBloqueo(yo, encontrado.second))
        } ?: throw Problema(404, "no_existe")
    }
    fun listar(yo: Long?, tipo: String = "", orden: String = "nuevo", oficial: Boolean = false, pagina: Int = 0, mios: Boolean = false, revision: Boolean = false, adquiridos: Boolean = false): PaginaMercado = b.transaccion {
        if (tipo !in setOf("", "banner", "marco") || orden !in setOf("nuevo", "vendidos") || pagina !in 0..100000) throw Problema(400, "filtro")
        // En la vitrina no sale lo de quien bloqueó o fue bloqueado por quien mira (lo propio y lo ya adquirido, sí).
        val vitrina = !revision && !mios && !adquiridos && yo != null
        val condicion = when { revision -> "m.estado='revision'"; mios -> "m.cuenta=?"; adquiridos -> "m.estado!='derechos' AND EXISTS(SELECT 1 FROM temas t WHERE t.cuenta=? AND t.tema=m.tipo || ':m' || m.id)"
            vitrina -> "m.estado='aprobado' AND m.cuenta NOT IN (SELECT a FROM bloqueos WHERE de=?) AND m.cuenta NOT IN (SELECT de FROM bloqueos WHERE a=?)"
            else -> "m.estado='aprobado'" }
        val argumentos = mutableListOf<Any?>().apply { if ((mios || adquiridos) && !revision) add(yo); if (vitrina) { add(yo); add(yo) }; add(tipo); add(tipo); add(if (oficial) 1 else 0); add(pagina * 20) }
        val filas = leer("$consulta WHERE $condicion AND (?='' OR m.tipo=?) AND (?=0 OR m.oficial=1) ORDER BY ${if (orden == "vendidos") "ventas DESC," else ""} m.id DESC LIMIT 21 OFFSET ?", *argumentos.toTypedArray()) { fila(it, yo) }
        PaginaMercado(filas.take(20), if (filas.size > 20) pagina + 1 else null)
    }
    fun publicar(yo: Long, d: PublicarMercado): ArticuloMercado = b.transaccion {
        clave(d.clave)
        val anterior = leer("SELECT id,imagen,nombre,tipo,precio FROM mercado WHERE cuenta=? AND clave=?", yo, d.clave) {
            listOf(it.getLong(1).toString(), it.getString(2), it.getString(3), it.getString(4), it.getInt(5).toString())
        }.singleOrNull()
        if (anterior != null) {
            if (anterior.drop(1) != listOf(d.imagen, d.nombre.trim(), d.tipo, d.precio.toString())) throw Problema(409, "clave_reutilizada")
            return@transaccion detalle(yo, anterior[0].toLong())
        }
        if (d.nombre.trim().length !in 1..40 || d.tipo !in setOf("banner", "marco") || d.precio !in PRECIO_MINIMO..PRECIO_MAXIMO) throw Problema(400, "articulo")
        if (leer("SELECT 1 FROM imagenes WHERE nombre=? AND cuenta=?", d.imagen, yo) { 1 }.isEmpty()) throw Problema(400, "imagen")
        s.validarImagenMercado(d.imagen, d.tipo)
        if (leer("SELECT COUNT(*) FROM mercado WHERE cuenta=? AND cuando>=?", yo, ahora() / 86400000L * 86400000L) { it.getInt(1) }.single() >= 5) throw Problema(429, "tope_publicaciones")
        val oficial = s.equipo.rol(yo) == "principal"
        if (!oficial) s.sinDeuda(yo)
        if (!oficial && s.chispas(yo) < TARIFA_PUBLICAR) throw Problema(402, "sin_chispas")
        val id = insertar("INSERT INTO mercado(cuenta,clave,imagen,nombre,tipo,precio,oficial,estado,motivo,cuando) VALUES(?,?,?,?,?,?,?,?,?,?)",
            yo, d.clave, d.imagen, d.nombre.trim(), d.tipo, d.precio, if (oficial) 1 else 0, if (oficial) "aprobado" else "revision", "", ahora())
        if (!oficial) cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)", yo, -TARIFA_PUBLICAR, "mercado:publicar:$id", ahora())
        // El creador recibe su ejemplar al aprobarse (lo oficial, que no pasa revisión, de una vez); lo conserva aunque se retire.
        if (oficial) cambiar("INSERT INTO temas(cuenta,tema,cuando) VALUES(?,?,?)", yo, "${d.tipo}:m$id", ahora())
        detalle(yo, id)
    }
    fun permitido(yo: Long, item: String): Boolean = b.usar {
        val id = idItem(item) ?: return@usar false
        leer("SELECT 1 FROM mercado WHERE id=? AND tipo=? AND estado!='derechos' AND (cuenta=? OR EXISTS(SELECT 1 FROM temas WHERE cuenta=? AND tema=?))",
            id, item.substringBefore(':'), yo, yo, item) { 1 }.isNotEmpty()
    }
    fun idItem(item: String): Long? = if (Regex("(banner|marco):m[1-9][0-9]*").matches(item)) item.substringAfter(":m").toLongOrNull() else null
    /** La clave identifica la intención de compra; el inventario protege también reintentos con otra clave. */
    fun comprar(yo: Long, id: Long, d: CompraMercado, destino: Long = yo, bloqueado: Boolean = false): Boolean = b.transaccion {
        clave(d.clave)
        val previa = leer("SELECT articulo,destino FROM mercado_compras WHERE comprador=? AND clave=?", yo, d.clave) { it.getLong(1) to it.getLong(2) }.singleOrNull()
        if (previa != null) {
            if (previa != id to destino) throw Problema(409, "clave_reutilizada")
            return@transaccion false
        }
        val a = detalle(yo, id)
        val autor = leer("SELECT cuenta FROM mercado WHERE id=?", id) { it.getLong(1) }.single()
        if (yo == autor || destino == autor) throw Problema(400, "a_si_mismo")
        if (bloqueado) throw Problema(403, "bloqueado")
        if (a.estado != "aprobado") throw Problema(409, "no_disponible")
        val item = "${a.tipo}:m$id"
        if (permitido(destino, item)) {
            if (destino == yo) return@transaccion false
            throw Problema(409, "ya_lo_tiene")
        }
        if (destino != yo && leer("SELECT COUNT(*) FROM mercado_compras WHERE comprador=? AND destino!=comprador AND cuando>=?", yo, ahora()/86400000L*86400000L) { it.getInt(1) }.single() >= 10) throw Problema(429, "tope_regalos")
        s.sinDeuda(yo)
        if (s.chispas(yo) < a.precio) throw Problema(402, "sin_chispas")
        insertar("INSERT INTO mercado_compras(articulo,comprador,destino,clave,precio,cuando) VALUES(?,?,?,?,?,?)", id, yo, destino, d.clave, a.precio, ahora())
        cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)", yo, -a.precio, "mercado:compra:$id", ahora())
        val ganancia = a.precio * PARTE_CREADOR / 100
        cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)", autor, ganancia, "mercado:venta:$id", ahora())
        cambiar("INSERT INTO temas(cuenta,tema,cuando) VALUES(?,?,?)", destino, item, ahora())
        s.avisoMercado(autor, yo, "mercado_venta", "$item|${a.nombre}|$ganancia")
        true
    }
    fun decidir(yo: Long, id: Long, d: DecisionMercado, token: String?): Unit = b.transaccion {
        s.equipo.requiere(yo, "revisar_mercado", token)
        val a = detalle(yo, id, true)
        if (a.propio) throw Problema(403, "equipo_propio")
        val estado = when (d.accion) { "aprobar" -> "aprobado"; "rechazar" -> "rechazado"; "retirar" -> "retirado"; "derechos" -> "derechos"; else -> throw Problema(400, "accion") }
        if (d.motivo.length > 2000 || (estado != "aprobado" && d.motivo.isBlank())) throw Problema(400, "motivo")
        if (a.estado == estado) return@transaccion
        if ((estado in setOf("aprobado", "rechazado") && a.estado != "revision") || (estado in setOf("retirado", "derechos") && a.estado !in setOf("aprobado", "retirado"))) throw Problema(409, "estado")
        cambiar("UPDATE mercado SET estado=?,motivo=? WHERE id=?", estado, d.motivo.trim(), id)
        val autor = leer("SELECT cuenta FROM mercado WHERE id=?", id) { it.getLong(1) }.single()
        val item = "${a.tipo}:m$id"
        if (estado == "aprobado") {
            cambiar("INSERT OR IGNORE INTO temas(cuenta,tema,cuando) VALUES(?,?,?)", autor, item, ahora())
            s.avisoMercado(autor, yo, "mercado_aprobado", "$item|${a.nombre}|")
        }
        if (estado == "rechazado") {
            // Se devuelve lo que se pagó por publicar (una sola vez).
            val pagado = -(leer("SELECT SUM(cantidad) FROM chispas WHERE cuenta=? AND motivo=?", autor, "mercado:publicar:$id") { it.getInt(1) }.firstOrNull() ?: 0)
            val devuelto = leer("SELECT 1 FROM chispas WHERE cuenta=? AND motivo=?", autor, "mercado:devolucion:$id") { 1 }.isNotEmpty()
            if (pagado > 0 && !devuelto) cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,?,?)", autor, pagado, "mercado:devolucion:$id", ahora())
            s.avisoMercado(autor, yo, "mercado_rechazado", "$item|${a.nombre}|${d.motivo.trim().replace("|", " ").take(300)}")
        }
        if (estado == "derechos") {
            val item = "${a.tipo}:m$id"
            cambiar("DELETE FROM temas WHERE tema=?", item)
            if (a.tipo == "marco") cambiar("UPDATE cuentas SET marco='',cambio=MAX(cambio+1,?) WHERE marco=?", ahora(), "m$id")
            else leer("SELECT id,perfil FROM cuentas") { it.getLong(1) to JsonSocial.decodeFromString<PerfilSubido>(it.getString(2)) }
                .filter { it.second.portada == item }.forEach { (cuenta, p) -> cambiar("UPDATE cuentas SET perfil=?,cambio=MAX(cambio+1,?) WHERE id=?", JsonSocial.encodeToString(PerfilSubido.serializer(), p.copy(portada="")), ahora(), cuenta) }
        }
        cambiar("UPDATE reportes SET estado='resuelto' WHERE que='mercado' AND objeto=?", id.toString())
        s.equipo.registrar(yo.toString(), "mercado_${d.accion}", id.toString(), d.motivo, token)
    }
    fun imagen(id: Long, lector: Long?, token: String?): ByteArray = b.usar {
        val estado = leer("SELECT estado,cuenta,imagen FROM mercado WHERE id=?", id) { Triple(it.getString(1), it.getLong(2), it.getString(3)) }.singleOrNull() ?: throw Problema(404, "no_existe")
        if (estado.first == "derechos") throw Problema(404, "no_existe")
        if (estado.first !in setOf("aprobado", "retirado") && lector != estado.second) {
            if (lector == null) throw Problema(404, "no_existe")
            s.equipo.requiere(lector, "revisar_mercado", token)
        }
        s.imagen(estado.third, lector, token)
    }
}

/** Reglas de precio del mercado: el creador gana el 30 % de cada venta. */
const val PRECIO_MINIMO = 300
const val PRECIO_MAXIMO = 2000
const val TARIFA_PUBLICAR = 1000
const val PARTE_CREADOR = 30
