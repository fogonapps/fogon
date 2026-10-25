package io.github.fogonapps.minga.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Lo que La Minga sabe de las apps de Fogón:
 * - el catálogo (qué apps hay), en catalogo.json en la raíz del repo público, con una copia
 *   dentro de la app por si no hay internet la primera vez;
 * - las versiones, de GitHub Releases del repo fogonapps/fogon. Cada app publica con su propia
 *   etiqueta: forzudo-v1.7, ahorrador-v1.1, minga-v1.0…
 */

val JsonMinga = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

@Serializable
data class Catalogo(val v: Int = 1, val apps: List<AppFogon> = emptyList())

@Serializable
data class AppFogon(
    val id: String,
    val nombre: String,
    /** applicationId. Sirve para saber si está instalada y para comprobar el APK. */
    val paquete: String,
    /** Las versiones de esta app son las etiquetas que empiezan así (forzudo-v). */
    val etiqueta: String,
    /** Qué hace, por idioma ("es", "en"). */
    val descripcion: Map<String, String> = emptyMap(),
    /** Su personaje (el-forzudo, el-ahorrador…), para el dibujo. */
    val personaje: String = "la-minga",
)

/** Una versión publicada de una app, sacada de GitHub Releases. */
data class Version(
    val app: String,
    val version: String,
    val titulo: String,
    val notas: String,
    val fecha: String,
    val url: String,
    val tamano: Long,
    /** SHA-256 del APK según GitHub ("digest" del archivo), en hexadecimal. Null si GitHub no lo dio. */
    val sha256: String?,
)

/* ----- Lo que devuelve la API de GitHub (solo lo que usamos) ----- */

@Serializable
data class ReleaseGitHub(
    @SerialName("tag_name") val etiqueta: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publicada: String? = null,
    val assets: List<ArchivoGitHub> = emptyList(),
)

@Serializable
data class ArchivoGitHub(
    val name: String,
    val size: Long = 0,
    val digest: String? = null,
    @SerialName("browser_download_url") val url: String,
)
