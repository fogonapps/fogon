package io.github.fogonapps.social

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PerfilesTest {
    @Test fun persistePerfilesSinUnirPorNombreMutable() {
        val raiz = Files.createTempDirectory("perfiles-sociales").toFile()
        try {
            val registro = Perfiles(raiz)
            assertTrue(registro.completarIdCuenta("1", "estable-1"))
            registro.actualizar("1", "Ana", "ana", "")
            val otro = registro.agregar()!!
            registro.actualizar(otro.id, "Ana", "ana", "")
            assertNull(registro.duplicado(otro.id, "estable-2"))
            assertEquals("1", registro.duplicado(otro.id, "estable-1")?.id)
            assertFalse(registro.completarIdCuenta("1", "estable-2"))
            registro.cambiar(otro.id)
            val restaurado = Perfiles(raiz)
            assertEquals(otro.id, restaurado.lista.activo)
            assertEquals("estable-1", restaurado.lista.perfiles.first().idCuenta)
            assertFalse(registro.completarIdCuenta("1", ""))
        } finally { raiz.deleteRecursively() }
    }
    @Test fun retiradaInterrumpidaConservaIntencionYNoReutilizaId() {
        val raiz = Files.createTempDirectory("perfiles-retirada").toFile()
        try {
            val registro = Perfiles(raiz)
            val otro = registro.agregar()!!
            try { registro.quitar(otro.id) { throw java.io.IOException("cierre") }; fail() } catch (_: java.io.IOException) {}
            val restaurado = Perfiles(raiz)
            assertEquals(listOf(otro.id), restaurado.lista.pendientesDeBorrar)
            restaurado.limpiarPendientes()
            assertTrue(restaurado.lista.pendientesDeBorrar.isEmpty())
            assertNotEquals(otro.id, restaurado.agregar()!!.id)
        } finally { raiz.deleteRecursively() }
    }
    @Test fun soloUnPerfilLocalAlAnadirCuentas() {
        val raiz = Files.createTempDirectory("perfiles-local").toFile()
        try {
            val registro = Perfiles(raiz)
            // El perfil 1 está sin cuenta: «añadir» lo reutiliza.
            assertEquals("1", registro.paraOtraCuenta()!!.id)
            assertEquals(1, registro.lista.perfiles.size)
            registro.completarIdCuenta("1", "estable-1"); registro.actualizar("1", "Ana", "ana", "")
            val local = registro.paraOtraCuenta()!!
            assertNotEquals("1", local.id)
            // Mientras ese siga sin cuenta, añadir otra vez no suma perfiles.
            repeat(3) { assertEquals(local.id, registro.paraOtraCuenta()!!.id) }
            assertEquals(2, registro.lista.perfiles.size)
        } finally { raiz.deleteRecursively() }
    }
}
