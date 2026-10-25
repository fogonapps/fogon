package io.github.fogonapps.ahorrador.datos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Todo lo de la persona cabe en un Estado, que se guarda como un solo JSON en el teléfono.
 * La plata va en pesos enteros (Long): en Colombia no se usan centavos.
 * Las fechas van como texto "AAAA-MM-DD" y los meses como "AAAA-MM".
 */

val JsonAhorrador = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Cómo arma la persona su presupuesto. Se elige en el tutorial y se cambia en Perfil. */
@Serializable
enum class Sistema { SOBRES, REGLA_50_30_20, KAKEIBO, SOLO_ANOTAR }

@Serializable
enum class TipoCuenta { EFECTIVO, BILLETERA, BANCO, CREDITO }

/** Grupo de una categoría: sirve para el 50/30/20 (necesidad, gusto, ahorro) y el kakeibo (los cuatro primeros). */
@Serializable
enum class Grupo { NECESIDAD, GUSTO, CULTURA, IMPREVISTO, AHORRO }

@Serializable
enum class TipoMovimiento { GASTO, INGRESO, TRASPASO, AHORRO }

@Serializable
data class Credito(
    val cupo: Long,
    val diaCorte: Int,
    val diaPago: Int,
    /** Tasa de interés mensual en %, la que trae el extracto. */
    val tasaMensual: Double = 2.0,
    /** Qué parte de la deuda es el pago mínimo, en %. Es aproximado: cada banco lo calcula a su modo. */
    val minimoPct: Double = 5.0,
)

@Serializable
data class Cuenta(
    val id: String,
    val nombre: String,
    val tipo: TipoCuenta,
    val saldoInicial: Long = 0,
    val credito: Credito? = null,
    val archivada: Boolean = false,
)

@Serializable
data class Categoria(
    val id: String,
    val nombre: String,
    /** Color ARGB de su tinta. */
    val color: Long,
    /** Nombre de su ícono en ui/Iconos.kt. */
    val icono: String,
    val grupo: Grupo = Grupo.NECESIDAD,
    /** true: categoría de ingresos (sueldo, prima…). */
    val ingreso: Boolean = false,
)

@Serializable
data class Movimiento(
    val id: String,
    val fecha: String,
    val tipo: TipoMovimiento,
    val monto: Long,
    /** De dónde sale (gasto, traspaso, ahorro) o a dónde entra (ingreso). */
    val cuenta: String,
    /** Traspaso: a dónde llega. */
    val aCuenta: String? = null,
    val categoria: String? = null,
    val nota: String = "",
    /** Compra con tarjeta de crédito a cuotas. */
    val cuotas: Int? = null,
    /** Movimiento creado por un pago fijo. */
    val fijo: String? = null,
    /** Ahorro: a qué meta va. */
    val meta: String? = null,
)

/** Un pago que se repite cada mes (arriendo, servicios, suscripciones). */
@Serializable
data class Fijo(
    val id: String,
    val nombre: String,
    val monto: Long,
    val categoria: String,
    val cuenta: String,
    val dia: Int,
    /** true: se anota solo el día que vence. false: solo avisa. */
    val automatico: Boolean = false,
    val activo: Boolean = true,
)

@Serializable
data class Meta(
    val id: String,
    val nombre: String,
    val objetivo: Long,
    val fecha: String? = null,
    val color: Long = 0xFF1FB45A,
    val icono: String = "moneda",
)

/**
 * Reto de ahorro: la semana 1 se guarda [base], la 2 el doble, y así hasta la [semanas]. Lo ahorrado va a la
 * meta [meta], que se crea al empezar el reto.
 */
@Serializable
data class Reto(val inicio: String, val base: Long, val meta: String, val semanas: Int = 52)

@Serializable
data class Abono(val fecha: String, val monto: Long)

/** Un préstamo entre personas, o un crédito (con cuotas) que la persona debe. */
@Serializable
data class Deuda(
    val id: String,
    val nombre: String,
    /** true: me deben. false: debo. */
    val meDeben: Boolean,
    val monto: Long,
    val fecha: String,
    val nota: String = "",
    val cuotas: Int? = null,
    val abonos: List<Abono> = emptyList(),
)

@Serializable
data class Estado(
    val v: Int = 1,
    val updatedAt: Long = 0,
    val nombre: String? = null,
    val sistema: Sistema = Sistema.SOBRES,
    val cuentas: List<Cuenta> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val movimientos: List<Movimiento> = emptyList(),
    /** Sistema Sobres: mes → (categoría → plata asignada). */
    val sobres: Map<String, Map<String, Long>> = emptyMap(),
    /** Regla 50/30/20: % para necesidades, gustos y ahorro. */
    val reparto: List<Int> = listOf(50, 30, 20),
    val fijos: List<Fijo> = emptyList(),
    val metas: List<Meta> = emptyList(),
    val deudas: List<Deuda> = emptyList(),
    /** Kakeibo: cuánto quiere ahorrar cada mes. */
    val metaAhorroMes: Long? = null,
    /** Cierre del mes: mes → qué voy a mejorar. */
    val cierres: Map<String, String> = emptyMap(),
    /** Anotar el 4×1000 al sacar o mover plata de una cuenta de banco. */
    val gmf: Boolean = false,
    /** Ya pasó por el tutorial. */
    val listo: Boolean = false,
    /** El reto de ahorro por semanas, si la persona lo empezó. */
    val reto: Reto? = null,
)
