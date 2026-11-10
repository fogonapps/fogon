package io.github.fogonapps.social

import kotlinx.serialization.json.Json

/** Cajón conserva el protocolo heredado; otras apps declaran su origen. */
data class ConfiguracionSocial(val idApp: String, val nombreVisible: String, val userAgent: String) {
    init { require(idApp in setOf("cajon", "forzudo", "cucha")) }
}

val JsonSocial = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
