package io.github.fogonapps.ahorrador

import io.github.fogonapps.ahorrador.datos.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NotificacionesTest {
    private val recibido = LocalDateTime.of(2026, 11, 9, 0, 2, 32)
    private val salida = "Bancolombia: Transferiste \$80,000.00 desde tu cuenta *1234 a la cuenta *9999999999 el 04/11/26 a las 15:49. Más información."
    private val entrada = "Bancolombia: LUCIA, recibiste una transferencia de CAMILO PRUEBA por \$2,000.00 en tu cuenta *1234 conectada a la llave 3000000000 el 09/11/26 a las 00:02. Más información."
    private fun leer(texto: String) = reconocerAviso(Banco.BANCOLOMBIA, "85540", texto, recibido)
    private fun nu() = reconocerAviso(Banco.NU, "Enviaste \$2.000,00", "Le enviaste a Luc**** Pru**** … en su cuenta de Bancolombia.", recibido)!!
    private fun estado(): Estado = Estado(cuentas = listOf(Cuenta("bc", "Cuenta de prueba", TipoCuenta.BANCO, banco = Banco.BANCOLOMBIA), Cuenta("nu", "Otra cuenta", TipoCuenta.BANCO, banco = Banco.NU)),
        notificaciones = NotificacionesBanco(elegidos = setOf(Banco.BANCOLOMBIA, Banco.NU), enlaces = mapOf(leer(entrada)!!.enlace to "bc", nu().enlace to "nu")))

    @Test fun normalizaExtrasYReconoceIngresoNu() {
        val titulo = "\u2066Recibiste\u00a01.200,00\u202fen t…\u2069"
        val texto = "Te llegó\u2007dinero de\u2009CAMILO\u200b PRUEBA\ncon tu llave."
        val a = reconocerExtrasBanco(Banco.NU, listOf("", titulo), listOf("", texto), recibido)!!
        assertEquals(TipoMovimiento.INGRESO, a.tipo)
        assertEquals(1200L, a.monto)
        assertEquals("CAMILO PRUEBA", a.contraparte)
        assertEquals("a b c d e...", normalizarAlerta("\ufeffa\u00a0b\u202fc\u2007d\u2009e…\u200c\u200d\u200e\u200f\u2066\u2067\u2068\u2069"))
        assertEquals(a, reconocerAviso(Banco.NU, "", "Recibiste 1.200,00 en tu cuenta\n$texto", recibido))
    }
    @Test fun extrasAlternativosYSalidaRealConDatosInventados() {
        val textos = listOf("Texto corto", "Le enviaste a LUC***** PRU****** en su cuenta de Nequi S.A. CF.")
        val a = reconocerExtrasBanco(Banco.NU, listOf("Enviaste", "Enviaste \$100,00"), textos, recibido)!!
        assertEquals(100L, a.monto)
        assertEquals("LUC***** PRU******", a.contraparte)
        assertEquals(a, reconocerExtrasBanco(Banco.NU, emptyList(), listOf("Enviaste 100,00 " + textos.last()), recibido))
        assertEquals(a, reconocerExtrasBanco(Banco.NU, listOf(""), listOf("Enviaste 100,00", textos.last()), recibido))
        assertNotNull(reconocerExtrasBanco(Banco.BANCOLOMBIA, emptyList(), listOf("Resumen", salida.replace(" ", "\u00a0")), recibido))
    }
    @Test fun activacionYHoraNoPierdenAlertasNuevas() {
        assertNull(momentoAlerta(0, 200, 200))
        assertNull(momentoAlerta(100, 99, 200))
        assertEquals(100L, momentoAlerta(100, 100, 100))
        assertEquals(200L, momentoAlerta(100, 200, 0))
        assertEquals(200L, momentoAlerta(100, 200, 99))
        assertEquals(200L, momentoAlerta(100, 200, 300))
        assertEquals(150L, momentoAlerta(100, 200, 150))
    }
    @Test fun primeraNuAutomaticaSinCuentaQuedaPendienteHastaEnlazar() {
        val a = nu()
        val inicial = Estado(notificaciones = NotificacionesBanco(elegidos = setOf(Banco.NU), solos = setOf(Banco.NU)))
        val recibido = recibirBanco(inicial, a)
        val automatico = confirmarBanco(recibido, a.huella, automatico = true)
        assertEquals(1, automatico.notificaciones.registros.size)
        assertFalse(automatico.notificaciones.registros.single().descartado)
        assertNull(automatico.notificaciones.registros.single().movimiento)
        assertTrue(automatico.movimientos.isEmpty())
        val conCuenta = automatico.copy(cuentas = listOf(Cuenta("nu", "Nu de prueba", TipoCuenta.BANCO)))
        val enlazado = enlazarBanco(conCuenta, a, "nu")
        val anotado = confirmarBanco(enlazado, a.huella, automatico = true)
        assertEquals("nu", anotado.movimientos.single().cuenta)
        assertTrue(anotado.notificaciones.registros.single().automatico)
        assertEquals(anotado, recibirBanco(anotado, a))
    }

    @Test fun cuatroReglasBancolombia() {
        val transferencia = leer(salida)!!
        assertEquals(TipoMovimiento.GASTO, transferencia.tipo)
        assertEquals(80000L, transferencia.monto)
        assertEquals("2026-11-04T15:49", transferencia.momento)
        assertEquals("*1234", transferencia.cuenta)
        assertEquals("*9999999999", transferencia.contraparte)
        val llave = leer("Bancolombia: LUCIA, transferiste \$4,600.00 a la llave 3000000000 desde tu cuenta *1234 a CAMILO PRUEBA el 04/11/26 a las 22:24. Más información.")!!
        assertEquals(4600L, llave.monto)
        assertEquals("CAMILO PRUEBA", llave.contraparte)
        assertEquals(TipoMovimiento.GASTO, llave.tipo)
        val pago = leer("Bancolombia: Pagaste \$11,230.00 a FONDO INVENTADO desde tu producto 1234 el 04/11/2026 13:12:58. Más información.")!!
        assertEquals("producto 1234", pago.cuenta)
        assertEquals("2026-11-04T13:12:58", pago.momento)
        assertEquals("FONDO INVENTADO", pago.contraparte)
        assertEquals(11230L, pago.monto)
        val ingreso = leer(entrada)!!
        assertEquals(TipoMovimiento.INGRESO, ingreso.tipo)
        assertEquals("CAMILO PRUEBA", ingreso.contraparte)
        assertEquals(2000L, ingreso.monto)
    }
    @Test fun nuSoloGastoConFormatoColombiano() {
        assertEquals(2000L, nu().monto)
        assertEquals(TipoMovimiento.GASTO, nu().tipo)
        assertEquals("Luc**** Pru****", nu().contraparte)
        assertEquals("2026-11-09T00:02", nu().momento)
        assertNull(reconocerAviso(Banco.NU, "Recibiste \$2.000,00", "Ingreso de prueba", recibido))
        assertNull(reconocerAviso(Banco.NU, "Enviaste \$2,000.00", "Le enviaste a Luc**** Pru**** …", recibido))
        assertNull(reconocerAviso(Banco.NU, "Enviaste \$2.000,00", "Promoción", recibido))
    }
    @Test fun montosFechasYLimites() {
        assertEquals(80001L, leer(salida.replace("80,000.00", "80,000.50"))!!.monto)
        for (monto in listOf("80.000,00", "8,00.00", "0.00", "999999999999999999999999.00")) assertNull(leer(salida.replace("80,000.00", monto)))
        for (fecha in listOf("31/02/26", "00/11/26", "04/13/26")) assertNull(leer(salida.replace("04/11/26", fecha)))
        assertNull(leer(salida.replace("15:49", "24:00")))
        assertNull(leer(salida.replace("15:49", "15:49:99")))
    }
    @Test fun filtraPorAppBancoElegidoYPrefijo() {
        val bancos = setOf(Banco.BANCOLOMBIA, Banco.NU)
        for (paquete in PAQUETES_MENSAJES + "mensajes.predeterminados") {
            assertEquals(Banco.BANCOLOMBIA, bancoDelTexto(paquete, "mensajes.predeterminados", bancos, salida))
            assertNull(bancoDelTexto(paquete, "mensajes.predeterminados", bancos, "Hola, Bancolombia: Transferiste"))
        }
        assertNull(bancoDelTexto("otra.app", null, bancos, salida))
        assertNull(bancoDelTexto(PAQUETE_NU, null, emptySet(), ""))
        assertFalse(paqueteAdmitido("otra.app", null, bancos))
        assertNull(leer("Bancolombia: Tu clave es 123456"))
        assertNull(leer("Hola, $salida"))
        assertNull(reconocerAviso(Banco.OTRO, "", salida, recibido))
        // Los bancos sin reglas exactas la leen con el lector general, sin tomar «a las 15:49» por contraparte.
        reconocerAviso(Banco.NEQUI, "", salida, recibido)?.let { assertFalse(it.contraparte.startsWith("las")) }
    }
    @Test fun enlacePersistenteSoloConCuentaCompatible() {
        val a = leer(entrada)!!
        val e = Estado(cuentas = estado().cuentas + Cuenta("efectivo", "Efectivo", TipoCuenta.EFECTIVO) + Cuenta("archivada", "Vieja", TipoCuenta.BANCO, archivada = true))
        assertEquals(listOf("bc"), cuentasPosibles(e, a).map { it.id })
        assertEquals(e, enlazarBanco(e, a, "efectivo"))
        assertNull(e.cuentaEnlazada(a))
        val enlazado = enlazarBanco(e, a, "bc")
        assertEquals("bc", enlazado.cuentaEnlazada(a))
        val restaurado = JsonAhorrador.decodeFromString(Estado.serializer(), JsonAhorrador.encodeToString(Estado.serializer(), enlazado))
        assertEquals("bc", restaurado.cuentaEnlazada(a))
        assertNull(restaurado.copy(cuentas = restaurado.cuentas.map { it.copy(archivada = true) }).cuentaEnlazada(a))
    }
    @Test fun confirmacionPorDefectoYDuplicados() {
        val a = leer(entrada)!!
        val pendiente = recibirBanco(estado(), a)
        assertTrue(pendiente.movimientos.isEmpty())
        assertEquals(pendiente, recibirBanco(pendiente, a))
        val anotado = confirmarBanco(pendiente, a.huella)
        assertEquals(1, anotado.movimientos.size)
        assertNull(anotado.movimientos.single().categoria)
        assertEquals("00:02", anotado.movimientos.single().hora)
        assertEquals(anotado, confirmarBanco(anotado, a.huella))
        assertEquals(anotado, recibirBanco(anotado, a))
        val descartado = descartarBanco(pendiente, a.huella)
        assertTrue(confirmarBanco(descartado, a.huella).movimientos.isEmpty())
        assertEquals(estado().copy(notificaciones = NotificacionesBanco()), recibirBanco(estado().copy(notificaciones = NotificacionesBanco()), a))
    }
    @Test fun traspasoEnCualquierOrdenInclusoPrimeraMitadYaAnotada() {
        val ingreso = leer(entrada)!!
        val gasto = nu()
        for (orden in listOf(listOf(ingreso, gasto), listOf(gasto, ingreso))) {
            for (primeraAnotada in listOf(false, true)) {
                var e = recibirBanco(estado(), orden[0])
                if (primeraAnotada) e = confirmarBanco(e, orden[0].huella, automatico = true)
                e = recibirBanco(e, orden[1])
                val propuesta = e.propuestaBanco(orden[1].huella)!!
                assertEquals(TipoMovimiento.TRASPASO, propuesta.tipo)
                assertEquals("nu", propuesta.cuenta)
                assertEquals("bc", propuesta.aCuenta)
                e = confirmarBanco(e, orden[1].huella, automatico = true)
                assertEquals(1, e.movimientos.size)
                assertEquals(TipoMovimiento.TRASPASO, e.movimientos.single().tipo)
                assertTrue(e.notificaciones.registros.all { it.automatico })
                assertEquals(e, confirmarBanco(e, orden[0].huella))
                // Una tercera alerta no puede absorber el traspaso existente.
                val tercero = ingreso.copy(momento = "2026-11-09T00:03")
                assertEquals(TipoMovimiento.INGRESO, recibirBanco(e, tercero).propuestaBanco(tercero.huella)!!.tipo)
            }
        }
    }
    @Test fun limitesDelTraspaso() {
        val ingreso = leer(entrada)!!
        val gasto = nu()
        for (otro in listOf(gasto.copy(momento = "2026-11-09T00:07"), gasto.copy(monto = 2001), gasto.copy(tipo = TipoMovimiento.INGRESO))) {
            val e = recibirBanco(recibirBanco(estado(), ingreso), otro)
            assertNull(e.parejaBanco(e.notificaciones.registros.last()))
        }
        val misma = estado().copy(notificaciones = estado().notificaciones.copy(enlaces = estado().notificaciones.enlaces.mapValues { "bc" }))
        val e = recibirBanco(recibirBanco(misma, ingreso), gasto)
        assertNull(e.parejaBanco(e.notificaciones.registros.last()))
        val cerca = gasto.copy(momento = "2026-11-08T23:58")
        val cruzado = recibirBanco(recibirBanco(estado(), ingreso), cerca)
        assertNotNull(cruzado.parejaBanco(cruzado.notificaciones.registros.last()))
    }
    @Test fun aprendeLaCorreccionPorContraparte() {
        val a = leer(entrada)!!
        var e = recibirBanco(estado().copy(categorias = listOf(Categoria("regalo", "Regalo", 0, "moneda", ingreso = true))), a)
        e = confirmarBanco(e, a.huella, e.propuestaBanco(a.huella)!!.copy(categoria = "regalo", nota = "Nota corregida"))
        val siguiente = a.copy(momento = "2026-11-10T00:02")
        e = recibirBanco(e, siguiente)
        assertEquals("regalo", e.propuestaBanco(siguiente.huella)!!.categoria)
    }
    @Test fun duplicadoCSVYDireccionDelTraspaso() {
        val a = leer(entrada)!!
        val importado = Movimiento("csv", "2026-11-09", TipoMovimiento.INGRESO, 2000, "bc", importado = true)
        val e = recibirBanco(estado().copy(movimientos = listOf(importado)), a)
        assertEquals("csv", e.notificaciones.registros.single().movimiento)
        assertEquals(1, confirmarBanco(e, a.huella).movimientos.size)
        val m = Movimiento("t", "2026-11-09", TipoMovimiento.TRASPASO, 2000, "nu", aCuenta = "bc")
        assertTrue(coincideExtracto(m, "2026-11-09", -2000, "nu"))
        assertTrue(coincideExtracto(m, "2026-11-09", 2000, "bc"))
        assertFalse(coincideExtracto(m, "2026-11-09", -2000, "bc"))
        assertFalse(coincideExtracto(m, "2026-11-09", 2000, "nu"))
    }
    @Test fun csvAntesODespuesYDosMontosIguales() {
        val a = leer(entrada)!!
        val fila = FilaExtracto("2026-11-09", "Transferencia inventada", 2000)
        var siguiente = 0
        fun importar(e: Estado, cantidad: Int = 1) = importarFilasExtracto(e, List(cantidad) { fila to "extra" }, "bc") { "csv${siguiente++}" }
        val pendiente = recibirBanco(estado(), a)
        val despues = importar(pendiente)
        assertEquals(1, despues.movimientos.size)
        assertEquals(despues, confirmarBanco(despues, a.huella))
        val anotado = confirmarBanco(pendiente, a.huella)
        assertEquals(anotado, importar(anotado))
        val dos = importar(estado(), 2)
        assertEquals(2, dos.movimientos.size)
        assertEquals(dos, importar(dos, 2))
        var e = recibirBanco(dos, a)
        val otro = a.copy(momento = "2026-11-09T01:02")
        e = recibirBanco(e, otro)
        assertEquals(2, e.notificaciones.registros.mapNotNull { it.movimiento }.toSet().size)
        val tercero = a.copy(momento = "2026-11-09T02:02")
        e = confirmarBanco(recibirBanco(e, tercero), tercero.huella)
        assertEquals(3, e.movimientos.size)
        val sinEnlace = estado().copy(notificaciones = estado().notificaciones.copy(enlaces = emptyMap()))
        var tardio = importar(recibirBanco(sinEnlace, a))
        tardio = enlazarBanco(tardio, a, "bc")
        assertEquals(1, confirmarBanco(tardio, a.huella).movimientos.size)
    }
    @Test fun conservaDatosYLeeEstadosAnteriores() {
        val viejo = JsonAhorrador.decodeFromString(Estado.serializer(), "{}")
        assertEquals(NotificacionesBanco(), viejo.notificaciones)
        val a = leer(entrada)!!
        val e = confirmarBanco(recibirBanco(estado(), a), a.huella, automatico = true)
        val texto = JsonAhorrador.encodeToString(Estado.serializer(), e)
        assertFalse(texto.contains("conectada a la llave"))
        assertEquals(e, JsonAhorrador.decodeFromString(Estado.serializer(), texto))
    }

    @Test fun traspasoConUnaMitadImportada() {
        val ingreso = leer(entrada)!!
        val gasto = nu()
        val fila = FilaExtracto("2026-11-09", "Transferencia inventada", 2000)
        for (csvPrimero in listOf(false, true)) {
            var e = confirmarBanco(recibirBanco(estado(), gasto), gasto.huella, automatico = true)
            if (csvPrimero) e = importarFilasExtracto(e, listOf(fila to "extra"), "bc") { "csv" }
            e = recibirBanco(e, ingreso)
            if (!csvPrimero) e = importarFilasExtracto(e, listOf(fila to "extra"), "bc") { "csv" }
            assertEquals(1, e.movimientos.size)
            assertEquals(TipoMovimiento.TRASPASO, e.movimientos.single().tipo)
            assertEquals("nu", e.movimientos.single().cuenta)
            assertEquals("bc", e.movimientos.single().aCuenta)
        }
    }

    @Test fun noSustituyeUnaCorreccionNiUnFormularioQueCambio() {
        val ingreso = leer(entrada)!!
        val gasto = nu()
        val pendiente = recibirBanco(estado(), gasto)
        val formulario = pendiente.propuestaBanco(gasto.huella)!!
        val conIngreso = recibirBanco(pendiente, ingreso)
        assertEquals(conIngreso, confirmarBanco(conIngreso, gasto.huella, formulario))
        var corregido = confirmarBanco(pendiente, gasto.huella, formulario.copy(monto = 1000))
        corregido = recibirBanco(corregido, ingreso)
        assertEquals(TipoMovimiento.INGRESO, corregido.propuestaBanco(ingreso.huella)!!.tipo)
    }

}
