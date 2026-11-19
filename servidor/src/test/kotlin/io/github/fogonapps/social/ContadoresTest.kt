package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class ContadoresTest {
    @Test
    fun sumaAnonimaSinDuplicarYConsultaProtegida() {
        val ruta = Files.createTempDirectory("contadores").toFile()
        val base = Base(File(ruta, "social.db").path)
        try {
            val social = Social(base, File(ruta, "fotos"))
            val token = social.crearCuenta(NuevaCuenta("ana")).token
            val id = requireNotNull(social.cuentaDe(token))
            val hoy = LocalDate.now(ZoneOffset.UTC)
            val dia = hoy.minusDays(1).toString()
            val lote = LoteContadores(UUID.randomUUID().toString(), "cajon", "3.0",
                listOf(DiaContadores(dia, mapOf("tienda.abrir" to 2, "nombre.inventado" to 3))))
            testApplication {
                application { fogonSocial(social) }
                suspend fun subir(datos: LoteContadores) = client.post("/v1/contadores") {
                    contentType(ContentType.Application.Json)
                    setBody(JsonSocial.encodeToString(LoteContadores.serializer(), datos))
                }.status
                assertEquals(HttpStatusCode.OK, subir(lote))
                assertEquals(HttpStatusCode.OK, subir(lote))
                assertEquals(HttpStatusCode.OK, subir(lote.copy(lote = UUID.randomUUID().toString(), version = "3.1")))
                val invalido = lote.copy(lote = UUID.randomUUID().toString(), dias = listOf(DiaContadores(hoy.minusDays(31).toString(), mapOf("tienda.abrir" to 5))))
                assertEquals(HttpStatusCode.BadRequest, subir(invalido))
                assertEquals(HttpStatusCode.BadRequest, subir(invalido.copy(dias = listOf(DiaContadores(hoy.plusDays(1).toString(), mapOf("tienda.abrir" to 5))))))
                assertEquals(HttpStatusCode.BadRequest, subir(invalido.copy(dias = listOf(DiaContadores(dia, mapOf("tienda.abrir" to 10_001))))))
                val informe = "/v1/equipo/contadores?desde=$dia&hasta=$dia&app=cajon"
                assertEquals(HttpStatusCode.Unauthorized, client.get(informe).status)
                assertEquals(HttpStatusCode.Forbidden, client.get(informe) { bearerAuth(token) }.status)

                val ahora = System.currentTimeMillis()
                base.usar {
                    cambiar("INSERT INTO permisos(cuenta, permiso, dado_por, cuando) VALUES (?, 'ver_registro', 'prueba', ?)", id, ahora)
                    cambiar("INSERT INTO mfa(cuenta, secreto_cifrado, activa, creada) VALUES (?, '', 1, ?)", id, ahora)
                    cambiar("UPDATE sesiones SET elevada_hasta = ? WHERE huella = ?", ahora + 60_000, Base.huella(token))
                }
                val respuesta = client.get(informe) { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, respuesta.status)
                val datos = JsonSocial.decodeFromString<InformeContadores>(respuesta.bodyAsText())
                assertEquals(listOf(TotalContador(dia, "cajon", "tienda.abrir", 4)), datos.totales)
                assertEquals(4L, datos.embudo["tienda.abrir"])
                assertEquals(0L, datos.embudo["tienda.pago_iniciado"])
            }
        } finally {
            base.cerrar()
            ruta.deleteRecursively()
        }
    }

    @Test
    fun listaPermitidaCoincideConAndroid() {
        val core = File("../core/social/src/main/java/io/github/fogonapps/social/NombresContadores.kt").readText()
        val nombres = Regex("\"([a-z_.]+)\"").findAll(core).map { it.groupValues[1] }.toSet()
        assertEquals(nombresContadores, nombres)
    }

    @Test
    fun limpiaLotesAntiguosSinTocarSuma() {
        val base = Base(":memory:")
        try {
            var hoy = LocalDate.of(2026, 11, 18)
            val contadores = Contadores(base) { hoy }
            val dia = hoy.minusDays(1)
            contadores.sumar(LoteContadores(UUID.randomUUID().toString(), "cajon", "3.0", listOf(DiaContadores(dia.toString(), mapOf("tienda.abrir" to 2)))))
            hoy = hoy.plusDays(36)
            contadores.limpiarLotes()
            assertEquals(0, base.usar { leer("SELECT COUNT(*) FROM lotes_contadores") { it.getInt(1) }.single() })
            assertEquals(2L, contadores.consultar(dia.toString(), dia.toString(), null).totales.single().total)
        } finally {
            base.cerrar()
        }
    }
}
