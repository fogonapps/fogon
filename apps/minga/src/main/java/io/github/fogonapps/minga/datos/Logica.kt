package io.github.fogonapps.minga.datos

/*
 * Cálculos de La Minga, sin Android: se prueban en la JVM (src/test).
 */

/** El certificado con que se firman todas las apps de Fogón (SHA-256 del certificado de ~/.fogon/firma). */
const val CERTIFICADO_FOGON = "ce5406882e361cf18e3ece18fa348769e34d8fa6cc8dd53f74a77289a993d8e5"

/** Compara "1.10" con "1.9" como números, parte por parte. Lo que no es número cuenta como 0. */
fun compararVersiones(a: String, b: String): Int {
    val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** Las versiones publicadas de cada app, de la más nueva a la más vieja. Sin borradores ni preliminares, y solo las que traen un APK. */
fun versionesPorApp(catalogo: Catalogo, releases: List<ReleaseGitHub>): Map<String, List<Version>> =
    catalogo.apps.associate { app ->
        app.id to releases
            .filter { !it.draft && !it.prerelease && it.etiqueta.startsWith(app.etiqueta) }
            .mapNotNull { r ->
                val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@mapNotNull null
                Version(
                    app = app.id,
                    version = r.etiqueta.removePrefix(app.etiqueta),
                    titulo = r.name ?: r.etiqueta,
                    notas = limpiarNotas(r.body.orEmpty()),
                    fecha = r.publicada?.take(10).orEmpty(),
                    url = apk.url,
                    tamano = apk.size,
                    sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                )
            }
            .sortedWith { a, b -> compararVersiones(b.version, a.version) }
    }

/** Cómo está una app en este teléfono frente a su última versión. */
sealed interface EstadoApp {
    /** Todavía no hay ninguna versión publicada. */
    data object SinVersiones : EstadoApp
    data class NoInstalada(val ultima: Version) : EstadoApp
    data class AlDia(val instalada: String) : EstadoApp
    data class PorActualizar(val instalada: String, val ultima: Version) : EstadoApp
    /** Instalada con otra firma (una versión de prueba, por ejemplo): Android no deja actualizarla encima. */
    data class OtraFirma(val instalada: String) : EstadoApp
}

/**
 * [instalada] es la versión en el teléfono (null si no está) y [firmaFogon] si está firmada con la llave de Fogón.
 */
fun estadoDe(versiones: List<Version>, instalada: String?, firmaFogon: Boolean): EstadoApp {
    val ultima = versiones.firstOrNull()
    return when {
        instalada != null && !firmaFogon -> EstadoApp.OtraFirma(instalada)
        ultima == null -> if (instalada != null) EstadoApp.AlDia(instalada) else EstadoApp.SinVersiones
        instalada == null -> EstadoApp.NoInstalada(ultima)
        compararVersiones(ultima.version, instalada) > 0 -> EstadoApp.PorActualizar(instalada, ultima)
        else -> EstadoApp.AlDia(instalada)
    }
}

/**
 * Las notas de cada versión vienen en Markdown y traen cosas para GitHub (la línea de Obtainium,
 * las sumas SHA-256). Para leerlas en la app: sin esas líneas y sin marcas de formato.
 */
fun limpiarNotas(md: String): String = md.lines()
    .filterNot { l -> l.contains("Obtainium", ignoreCase = true) || l.contains("SHA-256") }
    .joinToString("\n") { l ->
        l.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace("`", "").replace(Regex("^\\s*[-*] "), "• ")
    }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/** "7,6 MB" o "7.6 MB", según el separador decimal del idioma. */
fun megas(bytes: Long, decimal: Char = ','): String {
    val mb = bytes / 1_048_576.0
    val s = String.format(java.util.Locale.ROOT, "%.1f", mb)
    return s.replace('.', decimal) + " MB"
}

/** El catálogo que viene dentro de la app: sirve sin internet y si el del repo no se pudo leer. */
val CATALOGO_BASE = Catalogo(
    v = 1,
    apps = listOf(
        AppFogon(
            "forzudo", "El Forzudo", "io.github.fogonapps.forzudo", "forzudo-v",
            mapOf("es" to "Registro de entrenos de gimnasio: rutinas, récords, rangos de fuerza y constancia.",
                "en" to "Gym workout log: routines, records, strength ranks and consistency."),
            "el-forzudo",
        ),
        AppFogon(
            "ahorrador", "El Ahorrador", "io.github.fogonapps.ahorrador", "ahorrador-v",
            mapOf("es" to "Gastos, presupuesto y ahorro, con tarjeta de crédito, metas y pagos fijos. Sin internet.",
                "en" to "Spending, budget and savings, with credit card, goals and fixed payments. No internet."),
            "el-ahorrador",
        ),
        AppFogon(
            "minga", "La Minga", "io.github.fogonapps.minga", "minga-v",
            mapOf("es" to "La casa de las apps de Fogón: instálalas y mantenlas al día.",
                "en" to "The home of Fogón apps: install them and keep them up to date."),
            "la-minga",
        ),
    ),
)
