package io.github.fogonapps.cajon.datos

typealias FactorMfa = io.github.fogonapps.social.FactorMfa
typealias InscripcionMfa = io.github.fogonapps.social.InscripcionMfa
typealias CodigosMfa = io.github.fogonapps.social.CodigosMfa
typealias ElevacionMfa = io.github.fogonapps.social.ElevacionMfa
typealias MiembroEquipo = io.github.fogonapps.social.MiembroEquipo
typealias ContenidoEquipo = io.github.fogonapps.social.ContenidoEquipo
typealias SancionEquipo = io.github.fogonapps.social.SancionEquipo
typealias CambioEquipo = io.github.fogonapps.social.CambioEquipo
typealias EntradaRegistro = io.github.fogonapps.social.EntradaRegistro
typealias RegistroEquipo = io.github.fogonapps.social.RegistroEquipo
fun convertirRegistro(texto: String) = io.github.fogonapps.social.convertirRegistro(texto)
fun puedeRestaurar(caso: CasoModeracion, permisos: Set<String>) = io.github.fogonapps.social.puedeRestaurar(caso.estado, permisos)
fun necesitaElevar(cuenta: MiCuenta, ahora: Long) = io.github.fogonapps.social.necesitaElevar(cuenta.mfa, cuenta.elevadaHasta, ahora)
fun accionesModeracion(permisos: Set<String>, regla: String = "", faltas: Int = 0) = io.github.fogonapps.social.accionesModeracion(permisos, regla, faltas)
