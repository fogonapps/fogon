package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import io.github.fogonapps.cajon.datos.Acceso as AccesoServidor
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PerfilesTest {
    @get:Rule val temporal = TemporaryFolder()
    private val originales = mapOf(
        "datastore/estado.json" to "{\"listo\":true,\"ajustes\":{\"nombre\":\"Perfil anterior\"}}".encodeToByteArray(),
        "chats.json" to "{\"mensajes\":[1,2]}".encodeToByteArray(),
        "escuchas.json" to "{\"albumes\":[1]}".encodeToByteArray(),
        "para_ti.json" to "{\"vistos\":{}}".encodeToByteArray(),
        "portadas/foto.jpg" to byteArrayOf(0, 1, -1, 120),
        "portadas/foto.jpg.nuevo" to byteArrayOf(7, 8, 9),
        "desconocido/subcarpeta/otro.bin" to byteArrayOf(30, 20, 10),
        "estado-dañado.json" to byteArrayOf(1, 2, 3),
    )
    private class Corte : RuntimeException()
    private class Claves {
        var valores = mapOf("social" to "cifrado", "social.iv" to "iv", "chat_llaves" to "chat", "tmdb" to "cine", "llave_cajon" to "cajon", "ultima" to "123")
        val originales = valores.toMap()
        fun copiar() = valores.entries.joinToString("\n") { "${it.key}=${it.value}" }.encodeToByteArray()
        fun migrar(bytes: ByteArray) {
            val nuevas = bytes.decodeToString().lines().associate { it.substringBefore('=') to it.substringAfter('=') }
            valores = nuevas.mapKeys { "p1.${it.key}" }
        }
    }
    private fun preparar(): File = temporal.newFolder().also { raiz ->
        originales.forEach { (ruta, bytes) -> File(raiz, ruta).apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
    }
    private fun comprobar(raiz: File, claves: Claves) {
        originales.forEach { (ruta, bytes) ->
            assertArrayEquals(ruta, bytes, File(raiz, "perfiles/1/$ruta").readBytes())
            assertArrayEquals(ruta, bytes, File(raiz, "migracion-perfiles-respaldo/archivos/$ruta").readBytes())
        }
        assertEquals(claves.originales.mapKeys { "p1.${it.key}" }, claves.valores)
        assertEquals(originales.keys, File(raiz, "perfiles/1").walkTopDown().filter { it.isFile }.map { it.relativeTo(File(raiz, "perfiles/1")).path }.toSet())
        val lista = Perfiles(raiz).lista
        assertEquals("1", lista.activo)
        assertEquals(1, lista.perfiles.size)
    }
    @Test fun migracionCompletaYRepetidaNoPierdeArchivosNiClaves() {
        val raiz = preparar(); val claves = Claves()
        val migracion = MigracionPerfiles(raiz, claves::copiar, claves::migrar)
        migracion.ejecutar(); comprobar(raiz, claves)
        // Una ejecución posterior no pisa datos creados tras migrar.
        File(raiz, "perfiles/1/chats.json").writeText("nuevo chat")
        migracion.ejecutar()
        assertEquals("nuevo chat", File(raiz, "perfiles/1/chats.json").readText())
        assertTrue(File(raiz, "migracion-perfiles-respaldo").isDirectory)
        migracion.confirmarArranque()
        assertFalse(File(raiz, "migracion-perfiles-respaldo").exists())
        migracion.ejecutar()
        assertEquals("nuevo chat", File(raiz, "perfiles/1/chats.json").readText())
    }
    @Test fun corteEnCadaArchivoYFaseSeReanudaSinDuplicar() {
        val eventos = mutableListOf<String>()
        val inicial = preparar(); val clavesIniciales = Claves()
        MigracionPerfiles(inicial, clavesIniciales::copiar, clavesIniciales::migrar, eventos::add).ejecutar()
        eventos.indices.forEach { corte ->
            val raiz = preparar(); val claves = Claves(); var paso = 0
            try {
                MigracionPerfiles(raiz, claves::copiar, claves::migrar) { if (paso++ == corte) throw Corte() }.ejecutar()
                fail("Faltó el corte $corte")
            } catch (_: Corte) { }
            // Antes de completar el respaldo todavía están TODOS los originales.
            if (!File(raiz, "migracion-perfiles-respaldo").exists()) {
                originales.forEach { (ruta, bytes) -> assertArrayEquals(bytes, File(raiz, ruta).readBytes()) }
            } else {
                originales.forEach { (ruta, bytes) -> assertArrayEquals(bytes, File(raiz, "migracion-perfiles-respaldo/archivos/$ruta").readBytes()) }
            }
            val migracion = MigracionPerfiles(raiz, claves::copiar, claves::migrar)
            migracion.ejecutar(); migracion.ejecutar(); comprobar(raiz, claves)
            migracion.confirmarArranque()
            assertFalse(File(raiz, "migracion-perfiles-respaldo").exists())
        }
    }
    @Test fun noBorraRespaldoSiNoTerminoNiCambiaImagenesRemotas() {
        val raiz = preparar(); val claves = Claves()
        val ruta = File(raiz, "portadas/foto.jpg").absolutePath
        File(raiz, "datastore/estado.json").writeText("{\"foto\":\"file://$ruta\",\"remota\":\"https://ejemplo.invalid/a.jpg\"}")
        val migracion = MigracionPerfiles(raiz, claves::copiar, claves::migrar) { if (it == "copiada") throw Corte() }
        try { migracion.ejecutar() } catch (_: Corte) { }
        migracion.confirmarArranque()
        assertTrue(File(raiz, "migracion-perfiles-respaldo").exists())
        MigracionPerfiles(raiz, claves::copiar, claves::migrar).ejecutar()
        val texto = File(raiz, "perfiles/1/datastore/estado.json").readText()
        assertTrue(texto.contains(File(raiz, "perfiles/1/portadas/foto.jpg").absolutePath))
        assertTrue(texto.contains("https://ejemplo.invalid/a.jpg"))
        assertTrue(File(raiz, "migracion-perfiles-respaldo/archivos/datastore/estado.json").readText().contains(ruta))
    }
    @Test fun falloDeEscrituraNoBorraOriginalesNiModificaClaves() {
        val raiz = preparar(); val claves = Claves()
        // Un directorio donde debería publicarse un archivo simula un fallo real de rename.
        File(raiz, "migracion-perfiles-preparando/archivos/datastore/estado.json").mkdirs()
        try { MigracionPerfiles(raiz, claves::copiar, claves::migrar).ejecutar(); fail() }
        catch (_: java.io.IOException) { }
        originales.forEach { (ruta, bytes) -> assertArrayEquals(bytes, File(raiz, ruta).readBytes()) }
        assertEquals(claves.originales, claves.valores)
    }
    @Test fun corteAlConfirmarRetiraRespaldoEnElSiguienteArranque() {
        val raiz = preparar(); val claves = Claves()
        val migracion = MigracionPerfiles(raiz, claves::copiar, claves::migrar) { if (it == "confirmada") throw Corte() }
        migracion.ejecutar()
        try { migracion.confirmarArranque(); fail() } catch (_: Corte) { }
        assertTrue(File(raiz, "migracion-perfiles-respaldo").exists())
        MigracionPerfiles(raiz, claves::copiar, claves::migrar).ejecutar()
        assertFalse(File(raiz, "migracion-perfiles-respaldo").exists())
        originales.forEach { (ruta, bytes) -> assertArrayEquals(bytes, File(raiz, "perfiles/1/$ruta").readBytes()) }
    }
    @Test fun instantaneaDeClavesConservaTodosLosTiposYValoresCifrados() {
        val valores = mapOf<String, Any>(
            "social" to "texto-cifrado", "social.iv" to "iv-cifrado", "llave_cajon" to "llave-cifrada",
            "chat_llaves" to "chat-cifrado", "ultima" to 1234567890123L, "version_cine" to 2,
            "perfil_red_pendiente" to true, "desconocida" to 1.5f,
            "portadas_revisadas" to setOf("uno", "dos"), "vacio" to emptySet<String>(),
        )
        assertEquals(valores, abrirPreferenciasClaves(copiarPreferenciasClaves(valores)))
    }
    @Test fun estadoIlegibleNoImpideArrancarYSeConservaParaElAlmacen() {
        val raiz = preparar(); val claves = Claves()
        File(raiz, "datastore/estado.json").writeText("JSON interrumpido")
        MigracionPerfiles(raiz, claves::copiar, claves::migrar).ejecutar()
        // Se movió tal cual al perfil 1 (el almacén guarda su copia «estado-dañado» al abrirlo) y queda una copia aparte.
        assertEquals("JSON interrumpido", File(raiz, "perfiles/1/datastore/estado.json").readText())
        assertEquals("JSON interrumpido", File(raiz, "migracion-perfiles-respaldo/estado-ilegible-al-migrar.json").readText())
        assertEquals("terminada", File(raiz, "migracion-perfiles.json").readText())
        assertTrue(File(raiz, "perfiles.json").exists())
        assertEquals(claves.originales.mapKeys { "p1.${it.key}" }, claves.valores)
    }

    @Test fun duplicadoSoloDescartaUnPerfilSinDatosPersonales() {
        assertTrue(perfilProvisionalVacio(Estado(listo = true, apariencia = AparienciaCuenta(cambio = 99)), Oidos()))
        assertFalse(perfilProvisionalVacio(Estado(ajustes = Ajustes(nombre = "Mi nombre")), Oidos()))
        assertFalse(perfilProvisionalVacio(Estado(perfil = Perfil(bio = "Mi biografía")), Oidos()))
        assertFalse(perfilProvisionalVacio(Estado(apariencia = AparienciaCuenta(estilo = "CLARO")), Oidos()))
        assertFalse(perfilProvisionalVacio(Estado(), Oidos(sinAnotar = listOf("mi.app"))))
    }
    @Test fun unificaPorIdentidadAunqueCambieElUsuarioYLaSesionCaduque() {
        val raiz = temporal.newFolder(); val perfiles = Perfiles(raiz)
        perfiles.actualizar("1", "Ana", "usuario_anterior", "foto")
        assertTrue(perfiles.completarIdCuenta("1", "cuenta-estable-ANA"))
        val nueva = perfiles.agregar()!!
        perfiles.actualizar("1", "Ana", "usuario_nuevo", "foto")
        // La resolución no pide una sesión antigua: basta la identidad persistida.
        assertEquals("1", Perfiles(raiz).duplicado(nueva.id, "cuenta-estable-ANA")!!.id)
        assertNull(perfiles.duplicado(nueva.id, "cuenta-estable-ana"))
    }
    @Test fun reutilizarElUsuarioNoUneCuentasDistintas() {
        val perfiles = Perfiles(temporal.newFolder())
        perfiles.actualizar("1", "Ana", "usuario_viejo", "")
        perfiles.completarIdCuenta("1", "identidad-ana")
        perfiles.actualizar("1", "Ana", "usuario_nuevo", "")
        val otra = perfiles.agregar()!!
        perfiles.actualizar(otra.id, "Beto", "usuario_viejo", "")
        perfiles.completarIdCuenta(otra.id, "identidad-beto")
        assertNull(perfiles.duplicado(otra.id, "identidad-beto"))
        assertNull(perfiles.duplicado("1", "identidad-ana"))
        assertEquals("identidad-ana", perfiles.lista.perfiles.first { it.id == "1" }.idCuenta)
    }
    @Test fun perfilViejoCompletaIdentidadYRespuestasSinIdNoLaBorran() {
        val raiz = temporal.newFolder()
        File(raiz, "perfiles.json").writeText("""{"perfiles":[{"id":"1","usuario":"ana"}],"activo":"1"}""")
        val perfiles = Perfiles(raiz)
        val otra = perfiles.agregar()!!
        assertEquals("", perfiles.lista.perfiles.first().idCuenta)
        assertNull(perfiles.duplicado(otra.id, ""))
        assertNull(perfiles.duplicado(otra.id, "ana")) // El @ nunca sirve para unificar.
        val cuenta = JsonCajon.decodeFromString(MiCuenta.serializer(), """{"usuario":"ana_nueva","idCuenta":"identidad-ana"}""")
        assertTrue(perfiles.completarIdCuenta("1", cuenta.idCuenta))
        assertEquals("1", Perfiles(raiz).duplicado(otra.id, cuenta.idCuenta)!!.id)
        assertFalse(perfiles.completarIdCuenta("1", ""))
        assertFalse(perfiles.completarIdCuenta("1", "otra-identidad"))
        assertEquals("identidad-ana", Perfiles(raiz).lista.perfiles.first().idCuenta)
        val accesoViejo = JsonCajon.decodeFromString(AccesoServidor.serializer(), """{"usuario":"ana","token":"ficticio"}""")
        assertEquals("", accesoViejo.idCuenta)
        assertEquals("", JsonCajon.decodeFromString(MiCuenta.serializer(), """{"usuario":"ana"}""").idCuenta)
        assertEquals("identidad-ana", JsonCajon.decodeFromString(AccesoServidor.serializer(), """{"usuario":"ana","token":"ficticio","idCuenta":"identidad-ana"}""").idCuenta)
    }
    @Test fun registroLimiteDuplicadoYQuitarSinReutilizarIdentidades() {
        val raiz = temporal.newFolder(); val perfiles = Perfiles(raiz)
        perfiles.actualizar("1", "Primera", "uno", "foto")
        perfiles.completarIdCuenta("1", "identidad-uno")
        val segunda = perfiles.agregar()!!
        assertEquals("1", perfiles.duplicado(segunda.id, "identidad-uno")!!.id)
        assertNull(perfiles.duplicado(segunda.id, "otra"))
        repeat(3) { assertNotNull(perfiles.agregar()) }
        assertNull(perfiles.agregar())
        try { perfiles.quitar("1"); fail() } catch (_: IllegalArgumentException) { }
        perfiles.cambiar(segunda.id)
        File(perfiles.directorio("1"), "propio").writeText("solo uno")
        perfiles.quitar("1")
        assertFalse(File(raiz, "perfiles/1").exists())
        assertEquals(segunda.id, Perfiles(raiz).lista.activo)
        assertEquals("6", perfiles.agregar()!!.id)
    }
    @Test fun quitarInterrumpidoRetomaBorradoSinTocarElOtroPerfil() {
        val raiz = temporal.newFolder(); val perfiles = Perfiles(raiz)
        val segunda = perfiles.agregar()!!
        File(perfiles.directorio("1"), "privado").writeText("uno")
        File(perfiles.directorio(segunda.id), "privado").writeText("dos")
        perfiles.cambiar(segunda.id)
        try { perfiles.quitar("1") { throw Corte() }; fail() } catch (_: Corte) { }
        val reabiertos = Perfiles(raiz)
        assertEquals(listOf("1"), reabiertos.lista.pendientesDeBorrar)
        var clavesBorradas = false
        reabiertos.limpiarPendientes { assertEquals("1", it); clavesBorradas = true }
        assertTrue(clavesBorradas)
        assertFalse(File(raiz, "perfiles/1").exists())
        assertEquals("dos", File(raiz, "perfiles/${segunda.id}/privado").readText())
        assertTrue(Perfiles(raiz).lista.pendientesDeBorrar.isEmpty())
    }
    @Test fun almacenesRealesPorPerfilYRespuestaTardiaQuedanAislados() = runTest {
        val perfiles = Perfiles(temporal.newFolder())
        val segunda = perfiles.agregar()!!
        val uno = crearAlmacenPerfil(perfiles.directorio("1"), backgroundScope)
        val dos = crearAlmacenPerfil(perfiles.directorio(segunda.id), backgroundScope)
        uno.updateData { it.copy(listo = true, ajustes = it.ajustes.copy(nombre = "Primera"), apariencia = AparienciaCuenta(estilo = "CLARO")) }
        dos.updateData { it.copy(listo = true, ajustes = it.ajustes.copy(nombre = "Segunda"), apariencia = AparienciaCuenta(estilo = "GRAFITO")) }
        val vigente = VigenciaPerfil()
        val esperando = CompletableDeferred<Unit>(); val respuesta = CompletableDeferred<Unit>()
        var unido = false; var subido = false; var confirmado = false
        val trabajo = launch {
            val resultado = sincronizarCajonCifrado(
                llave = "no se usa una llave si la generación caducó",
                autorizacion = { null }, vigente = vigente::vigente,
                descargar = { esperando.complete(Unit); respuesta.await(); CajonCifrado() },
                unir = { unido = true; uno.updateData { it.copy(ajustes = it.ajustes.copy(nombre = "Tardía")) } },
                local = { Estado() }, guardar = { _, _ -> subido = true }, confirmar = { confirmado = true },
            )
            assertFalse(resultado)
        }
        esperando.await()
        vigente.invalidar(); perfiles.cambiar(segunda.id)
        respuesta.complete(Unit); trabajo.join()
        assertFalse(unido); assertFalse(subido); assertFalse(confirmado)
        assertEquals("Primera", uno.data.first().ajustes.nombre)
        assertEquals("Segunda", dos.data.first().ajustes.nombre)
        assertEquals("CLARO", uno.data.first().apariencia!!.estilo)
        assertEquals("GRAFITO", dos.data.first().apariencia!!.estilo)
    }
    @Test fun sincronizacionTardiaNoEscribeEnPerfilNuevoNiEnGeneracionRetirada() = runTest {
        val raiz = temporal.newFolder(); val perfiles = Perfiles(raiz)
        val segunda = perfiles.agregar()!!
        val primeraDir = perfiles.directorio("1")
        val segundaDir = perfiles.directorio(segunda.id)
        val vigente = VigenciaPerfil()
        val esperando = CompletableDeferred<Unit>(); val respuesta = CompletableDeferred<Unit>()
        File(primeraDir, "estado").writeText("uno")
        File(segundaDir, "estado").writeText("dos")
        val trabajo = launch {
            esperando.complete(Unit); respuesta.await()
            if (vigente.vigente()) File(primeraDir, "estado").writeText("respuesta tardía")
        }
        esperando.await(); vigente.invalidar(); perfiles.cambiar(segunda.id)
        respuesta.complete(Unit); trabajo.join()
        assertEquals("uno", File(primeraDir, "estado").readText())
        assertEquals("dos", File(segundaDir, "estado").readText())
        perfiles.cambiar("1")
        assertFalse(vigente.vigente())
    }
}
