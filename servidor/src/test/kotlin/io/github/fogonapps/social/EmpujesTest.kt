package io.github.fogonapps.social

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class EmpujesTest {
    private fun esperar(cond: () -> Boolean) { repeat(100) { if (cond()) return; Thread.sleep(20) }; fail("no llegó") }
    private val t1 = "tokenAnaCajon:" + "a".repeat(30)
    private val t2 = "tokenAnaCucha:" + "b".repeat(30)
    private val t3 = "tokenAnaOtro:" + "c".repeat(30)

    @Test fun avisosYMensajesTocanElTelefonoSinDecirQue() {
        val dir = Files.createTempDirectory("empujes").toFile()
        val tocados = CopyOnWriteArrayList<String>()
        var codigo = 200
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"), mensajero = { tocados += it; codigo })
            val ana = s.crearCuenta(NuevaCuenta("anabel")); val beto = s.crearCuenta(NuevaCuenta("roberto"))
            val idAna = s.cuentaDe(ana.token)!!; val idBeto = s.cuentaDe(beto.token)!!
            assertEquals(400, assertFailsWith<Problema> { s.empujes.registrar(idAna, EmpujeNuevo("corto")) }.codigo)
            assertEquals(400, assertFailsWith<Problema> { s.empujes.registrar(idAna, EmpujeNuevo("https://x.co/" + "a".repeat(30))) }.codigo)
            s.empujes.registrar(idAna, EmpujeNuevo(t1, "cajon"))
            s.empujes.registrar(idAna, EmpujeNuevo(t2, "cucha"))
            // Seguir no es de una app: toca todas. Un mensaje también.
            s.seguir(idBeto, "anabel")
            esperar { tocados.size == 2 }
            assertEquals(setOf(t1, t2), tocados.toSet())
            // 404: el token ya no existe y se borra. (Los dos de antes esperan: se juntan cada 15 segundos.)
            codigo = 404
            s.empujes.registrar(idAna, EmpujeNuevo(t3, "alcancia"))
            s.mandar(idBeto, "anabel", SobreNuevo("x".repeat(40), "f".repeat(40)))
            esperar { t3 in tocados && b.usar { leer("SELECT COUNT(*) FROM empujes") { it.getInt(1) }.first() } == 2 }
            assertEquals(3, tocados.size)
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun alSalirElTokenDejaDeServir() {
        val dir = Files.createTempDirectory("empujes").toFile()
        val tocados = CopyOnWriteArrayList<String>()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"), mensajero = { tocados += it; 200 })
            val ana = s.crearCuenta(NuevaCuenta("anabel")); val beto = s.crearCuenta(NuevaCuenta("roberto"))
            val idAna = s.cuentaDe(ana.token)!!
            s.empujes.registrar(idAna, EmpujeNuevo(t1), ana.token)
            s.salir(ana.token)
            s.seguir(s.cuentaDe(beto.token)!!, "anabel")
            Thread.sleep(300)
            assertTrue(tocados.isEmpty())
            assertEquals(0, b.usar { leer("SELECT COUNT(*) FROM empujes") { it.getInt(1) }.first() })
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun sinCredencialesNoSeMandaNada() {
        val dir = Files.createTempDirectory("empujes").toFile()
        val b = Base(File(dir, "social.db").path)
        try {
            val s = Social(b, File(dir, "fotos"))
            val ana = s.crearCuenta(NuevaCuenta("anabel")); val beto = s.crearCuenta(NuevaCuenta("roberto"))
            s.empujes.registrar(s.cuentaDe(ana.token)!!, EmpujeNuevo(t1))
            s.seguir(s.cuentaDe(beto.token)!!, "anabel")
        } finally { b.cerrar(); dir.deleteRecursively() }
    }

    @Test fun fcmFirmaConLaCuentaDeServicio() {
        // Una llave RSA de prueba: el JSON se lee y la llave se carga (sin red).
        val par = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\\n" + java.util.Base64.getEncoder().encodeToString(par.private.encoded) + "\\n-----END PRIVATE KEY-----\\n"
        Fcm("""{"project_id":"fogon-prueba","client_email":"x@fogon-prueba.iam.gserviceaccount.com","private_key":"$pem"}""")
    }
}
