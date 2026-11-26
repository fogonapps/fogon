package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class LenaTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("lena").toFile()
        val base = Base(dir.resolve("social.db").path)
        val reloj = 2_000_000_000_000L
        val social = Social(base, dir.resolve("fotos"), { reloj })
        val persona = social.crearCuenta(NuevaCuenta("persona"))
        val principal = social.crearCuenta(NuevaCuenta("principal"))
        val id = social.cuentaDe(persona.token)!!
        val jefe = social.cuentaDe(principal.token)!!

        init {
            rol(jefe, "principal")
        }

        fun rol(cuenta: Long, rol: String) = base.usar {
            cambiar("INSERT INTO roles VALUES(?, ?, 'servidor', 0)", cuenta, rol)
        }

        fun activarMfa(cuenta: Long, token: String): String {
            val secreto = social.equipo.activar(cuenta, Factor(), token).secreto
            val codigo = Totp.codigo(Totp.decodificar(secreto), reloj / 30_000)
            return social.equipo.confirmar(cuenta, Factor(codigo = codigo), token).codigos.first()
        }

        fun elevar(cuenta: Long, token: String, recuperacion: String = activarMfa(cuenta, token)) {
            social.equipo.elevar(cuenta, Factor(recuperacion = recuperacion), token)
        }

        override fun close() {
            base.cerrar()
            dir.deleteRecursively()
        }
    }

    @Test fun umbrales() {
        for ((pesos, nivel) in listOf(0L to "", 1L to "chispa", 24_999L to "chispa", 25_000L to "brasa",
            99_999L to "brasa", 100_000L to "fogata", 299_999L to "fogata", 300_000L to "fogon")) {
            assertEquals(nivel, nivelLena(pesos), "$pesos COP")
        }
    }

    @Test fun wompiSumaSoloLoPagadoEnOrdenesAprobadas() = Entorno().use { e ->
        val config = ConfiguracionWompi("pub_test_ficticia", "integridad_ficticia", "eventos_ficticios")
        val transacciones = mutableMapOf<String, TransaccionWompi>()
        val pagos = PagosWompi(e.base, config, consultar = { listOfNotNull(transacciones[it]) })
        for (estado in listOf("DECLINED", "ERROR", "VOIDED", "pendiente", "APPROVED")) {
            val ref = pagos.orden(e.id, 5000).referencia
            if (estado != "pendiente") transacciones[ref] = TransaccionWompi(ref, "tx_$estado", estado, 5_000_000, "COP")
        }
        pagos.conciliar(e.id)
        assertEquals(5750 + TemasPerfil.BIENVENIDA, e.social.chispas(e.id))
        assertEquals("brasa", e.social.miCuenta(e.id).nivelLena)

        e.elevar(e.jefe, e.principal.token)
        e.social.lena.registrar(e.jefe, e.principal.token, AporteNuevo("persona", 49_999, "externo"))
        assertEquals("brasa", e.social.miCuenta(e.id).nivelLena)
        e.social.lena.registrar(e.jefe, e.principal.token, AporteNuevo("persona", 1, "umbral"))
        assertEquals("fogata", e.social.miCuenta(e.id).nivelLena)
    }

    @Test fun registroAnulacionVisibilidadYNivelDePerfilPorApi() = Entorno().use { e ->
        e.elevar(e.jefe, e.principal.token)
        val saldo = e.social.chispas(e.id)
        val cupo = e.social.escaparates.cupo(e.id)
        testApplication {
            application { fogonSocial(e.social) }
            suspend fun aporte(referencia: String, monto: Int) = client.post("/v1/admin/aportes") {
                bearerAuth(e.principal.token)
                contentType(ContentType.Application.Json)
                setBody("""{"usuario":"persona","monto":$monto,"referencia":"$referencia","nota":"Aporte externo"}""")
            }
            suspend fun anular(referencia: String) = client.post("/v1/admin/aportes/$referencia/anular") { bearerAuth(e.principal.token) }
            suspend fun propia() = Json.parseToJsonElement(client.get("/v1/cuenta") { bearerAuth(e.persona.token) }.bodyAsText()).jsonObject
            suspend fun vista() = Json.parseToJsonElement(client.get("/v1/perfiles/persona").bodyAsText()).jsonObject
            suspend fun visibilidad(visible: Boolean) = client.put("/v1/cuenta/lena") {
                bearerAuth(e.persona.token)
                contentType(ContentType.Application.Json)
                setBody("""{"visible":$visible}""")
            }
            assertEquals(JsonPrimitive(""), propia()["nivelLena"])
            assertEquals(HttpStatusCode.BadRequest, aporte("invalido", 0).status)
            assertEquals(HttpStatusCode.Created, aporte("prueba", 15_009).status)
            assertEquals(HttpStatusCode.Conflict, aporte(" prueba ", 15_009).status)
            assertEquals(saldo + 1500, e.social.chispas(e.id))
            assertEquals(JsonPrimitive("chispa"), propia()["nivelLena"])
            assertEquals(JsonPrimitive(false), propia()["lena_visible"])
            assertEquals(JsonPrimitive(false), propia()["lena_preguntada"])
            assertFalse("nivelLena" in vista())
            assertEquals(HttpStatusCode.NoContent, visibilidad(true).status)
            assertEquals(JsonPrimitive(true), propia()["lena_visible"])
            assertEquals(JsonPrimitive(true), propia()["lena_preguntada"])
            val visible = vista()
            assertEquals(JsonPrimitive("chispa"), visible["nivelLena"])
            assertEquals(JsonPrimitive(cupo.nivel), visible["nivel"])
            assertEquals(cupo, e.social.escaparates.cupo(e.id))
            for (campo in listOf("monto", "referencia", "lena_visible", "lena_preguntada")) assertFalse(campo in visible)
            assertEquals(HttpStatusCode.NoContent, visibilidad(false).status)
            assertEquals(JsonPrimitive(false), propia()["lena_visible"])
            assertEquals(JsonPrimitive(true), propia()["lena_preguntada"])
            assertFalse("nivelLena" in vista())

            assertEquals(HttpStatusCode.NoContent, anular("prueba").status)
            assertEquals(saldo, e.social.chispas(e.id))
            assertEquals(JsonPrimitive(""), propia()["nivelLena"])
            assertEquals(HttpStatusCode.NoContent, anular("prueba").status)
            assertEquals(HttpStatusCode.Conflict, aporte("prueba", 15_009).status)
            assertEquals(saldo, e.social.chispas(e.id))

            assertEquals(HttpStatusCode.Created, aporte("gastado", 25_000).status)
            e.base.usar { registrarChispas(e.id, -(saldo + 2500L - 30), "compra:prueba", 0) }
            assertEquals(HttpStatusCode.NoContent, anular("gastado").status)
            assertEquals(0, e.social.chispas(e.id))
            assertEquals(JsonPrimitive(""), propia()["nivelLena"])
            val registro = e.social.equipo.registro(e.jefe, e.principal.token, "", "aporte", 0)
            assertEquals(listOf("aporte_registrar", "aporte_anular", "aporte_registrar", "aporte_anular"), registro.map { it.accion })
            assertContains(registro.last().detalle, "Chispas no recuperadas: 2470")
        }
    }

    @Test fun ambosCambiosExigenPrincipalYUnaSesionElevada() = Entorno().use { e ->
        e.rol(e.id, "administrador")
        e.elevar(e.id, e.persona.token)
        testApplication {
            application { fogonSocial(e.social) }
            suspend fun denegado(token: String, motivo: String) {
                val alta = client.post("/v1/admin/aportes") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"usuario":"persona","monto":100,"referencia":"prueba"}""")
                }
                val baja = client.post("/v1/admin/aportes/prueba/anular") { bearerAuth(token) }
                for (respuesta in listOf(alta, baja)) {
                    assertEquals(HttpStatusCode.Forbidden, respuesta.status)
                    assertContains(respuesta.bodyAsText(), motivo)
                }
            }
            denegado(e.persona.token, "solo_principal")
            denegado(e.principal.token, "mfa_requerida")
            e.activarMfa(e.jefe, e.principal.token)
            denegado(e.principal.token, "elevacion_requerida")
        }
    }
}
