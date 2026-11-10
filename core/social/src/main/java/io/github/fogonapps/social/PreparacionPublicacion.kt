package io.github.fogonapps.social

/** Barrera de la app antes de leer los datos que se van a compartir. */
fun interface PreparacionPublicacion {
    suspend fun preparar(vigente: () -> Boolean): Boolean
}

/** Recupera identidad antes del contenido privado, sin consumir el reintento de subida. */
suspend fun prepararSubidaPerfil(
    pendiente: () -> Boolean,
    recuperarPerfil: suspend () -> Boolean,
    recuperarContenido: suspend () -> Boolean,
    vigente: () -> Boolean,
): Boolean {
    if (!vigente()) return false
    if (pendiente() && !recuperarPerfil()) return false
    if (!vigente() || pendiente()) return false
    return recuperarContenido() && vigente()
}

/** Instantánea explícitamente compartida; la app conserva su modelo privado y sus conversiones. */
interface AporteSocialApp {
    suspend fun perfil(): PerfilSubido
    fun publicaciones(): List<PublicacionSubida>
}

/** Solo una subida confirmada establece la referencia para omitir repeticiones. */
class SubidasPerfil {
    private var ultimo: Pair<PerfilSubido, List<PublicacionSubida>>? = null
    private var generacion = 0L

    fun reiniciar() { generacion++; ultimo = null }

    suspend fun subir(contenido: Pair<PerfilSubido, List<PublicacionSubida>>, enviar: suspend () -> Boolean): Boolean {
        if (contenido == ultimo) return true
        val actual = generacion
        if (!enviar() || actual != generacion) return false
        ultimo = contenido
        return true
    }
}

/** Conserva el orden perfil, cambios y confirmación local de las claves conocidas. */
suspend fun subirAporteSocial(
    aporte: AporteSocialApp,
    subidas: SubidasPerfil,
    vigente: () -> Boolean,
    clavesAnteriores: () -> Set<String>,
    cliente: () -> ClienteSocial,
    confirmar: (Set<String>) -> Unit,
): Boolean {
    val ahora = aporte.perfil() to aporte.publicaciones()
    val transporte = cliente()
    if (!vigente()) return false
    return subidas.subir(ahora) {
        transporte.subirPerfil(ahora.first)
        if (!vigente()) return@subir false
        val claves = ahora.second.map { it.clave }.toSet()
        transporte.subirCambios(ahora.second, (clavesAnteriores() - claves).toList())
        if (!vigente()) return@subir false
        confirmar(claves)
        true
    }
}
