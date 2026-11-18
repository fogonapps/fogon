package io.github.fogonapps.social

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class MercadoTest {
    private class Entorno : AutoCloseable {
        val dir = Files.createTempDirectory("mercado").toFile()
        val b = Base(File(dir,"social.db").path)
        var reloj = 2_000_000_000_000L
        val s = Social(b,File(dir,"fotos"), { reloj })
        val creador = alta("creadora").also { c -> b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,20000,'prueba',1)", c.first) } }
        val comprador = alta("compradora")
        val equipo = alta("revisora")
        fun alta(nombre: String): Pair<Long,String> { val t=s.crearCuenta(NuevaCuenta(nombre)).token; return s.cuentaDe(t)!! to t }
        fun elevar() {
            b.usar { cambiar("INSERT INTO roles VALUES(?,'equipo','servidor',?)",equipo.first,reloj); cambiar("INSERT INTO permisos VALUES(?,'revisar_mercado','servidor',?)",equipo.first,reloj) }
            val i=s.equipo.activar(equipo.first,Factor(),equipo.second)
            val secreto=Totp.decodificar(i.secreto)
            s.equipo.confirmar(equipo.first,Factor(codigo=Totp.codigo(secreto,reloj/30000)),equipo.second)
            reloj+=30000
            s.equipo.elevar(equipo.first,Factor(codigo=Totp.codigo(secreto,reloj/30000)),equipo.second)
        }
        fun imagen(yo: Long = creador.first, marco: Boolean = false): String {
            val bytes = if (!marco) jpegDePrueba(true) else java.io.ByteArrayOutputStream().also {
                val img=java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB)
                img.setRGB(0,0,0xffff0000.toInt()); javax.imageio.ImageIO.write(img,"png",it)
            }.toByteArray()
            return s.subirImagen(yo,bytes).nombre
        }
        fun publicar(precio: Int=601, marco: Boolean=false, yo: Long=creador.first, clave: String="publicar_prueba") = s.mercado.publicar(yo,PublicarMercado(clave,imagen(yo,marco),"arte propio",if(marco) "marco" else "banner",precio))
        fun decidir(a: ArticuloMercado, accion: String, motivo: String="revisado") = s.mercado.decidir(equipo.first,a.id,DecisionMercado(accion,motivo),equipo.second)
        override fun close() { b.cerrar(); dir.deleteRecursively() }
    }
    @Test fun publicacionRevisionRechazoYReintentos() = Entorno().use { e ->
        e.elevar()
        val antes=e.s.chispas(e.creador.first)
        val d=PublicarMercado("publicar_prueba",e.imagen(),"obra","banner",600)
        val a=e.s.mercado.publicar(e.creador.first,d)
        assertEquals("revision",a.estado); assertEquals(antes-TARIFA_PUBLICAR,e.s.chispas(e.creador.first))
        assertEquals(a,e.s.mercado.publicar(e.creador.first,d))
        assertEquals(409,assertFailsWith<Problema>{ e.s.mercado.publicar(e.creador.first,d.copy(precio=700)) }.codigo)
        assertTrue(e.s.mercado.listar(null).articulos.isEmpty())
        e.decidir(a,"rechazar","arte ajeno"); e.decidir(a,"rechazar","arte ajeno")
        // Rechazado: se devuelve la tarifa una sola vez y el creador no recibe ejemplar.
        assertEquals(antes,e.s.chispas(e.creador.first))
        assertFalse("banner:m${a.id}" in e.s.temas(e.creador.first).reclamados)
        assertEquals("arte ajeno",e.s.mercado.listar(e.creador.first,mios=true).articulos.single().motivo)
        val b=e.publicar(clave="otra_publicacion")
        assertFalse("banner:m${b.id}" in e.s.temas(e.creador.first).reclamados)
        e.decidir(b,"aprobar"); e.decidir(b,"aprobar")
        assertTrue("banner:m${b.id}" in e.s.temas(e.creador.first).reclamados)
        assertEquals(b.id,e.s.mercado.listar(null,tipo="banner").articulos.single().id)
        assertTrue(e.s.mercado.listar(null,tipo="marco").articulos.isEmpty())
        assertTrue(e.s.equipo.verificarCadena())
    }
    @Test fun comprasRepartoParImparInventarioYRegalos() = Entorno().use { e ->
        e.elevar()
        for ((i,precio) in listOf(600,601).withIndex()) {
            val a=e.publicar(precio,marco=i==1,clave="publicar_$i")
            e.decidir(a,"aprobar")
            val antes=e.s.chispas(e.creador.first); val saldo=e.s.chispas(e.comprador.first)
            assertEquals("a_si_mismo",assertFailsWith<Problema>{e.s.mercado.comprar(e.creador.first,a.id,CompraMercado("comprar_propio"))}.mensaje)
            val d=CompraMercado("compra_prueba_$i")
            e.s.mercado.comprar(e.comprador.first,a.id,d)
            repeat(3) {e.s.mercado.comprar(e.comprador.first,a.id,d)}
            e.s.mercado.comprar(e.comprador.first,a.id,CompraMercado("otra_clave_$i"))
            assertEquals(antes+precio*PARTE_CREADOR/100,e.s.chispas(e.creador.first)); assertEquals(saldo-precio,e.s.chispas(e.comprador.first))
            val item="${a.tipo}:m${a.id}"
            assertTrue(item in e.s.temas(e.comprador.first).reclamados)
            e.s.ponerTema(e.comprador.first,item)
            if (i==0) assertEquals(item,e.s.verPerfil(e.comprador.first,"compradora").portada) else assertEquals("m${a.id}",e.s.temas(e.comprador.first).marco)
            val otra=e.alta("otra$i")
            e.s.regalar(e.comprador.first,Regalo(item,"otra$i"))
            e.s.regalar(e.comprador.first,Regalo(item,"otra$i"))
            assertTrue(item in e.s.temas(otra.first).reclamados)
            assertEquals(2,e.s.mercado.detalle(null,a.id).ventas)
            assertEquals(antes+2*(precio*PARTE_CREADOR/100),e.s.chispas(e.creador.first))
            // La siguiente iteración necesita saldo para probar otro artículo.
            e.b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,3000,'prueba',?)",e.comprador.first,e.reloj) }
        }
        val movimientos=e.b.usar { leer("SELECT cantidad FROM chispas WHERE cuenta=? AND motivo LIKE 'mercado:venta:%'",e.creador.first) { it.getInt(1) } }
        assertEquals(listOf(180,180,180,180),movimientos)
    }
    @Test fun oficialGratisTopeYPermiso() = Entorno().use { e ->
        e.b.usar { cambiar("INSERT INTO roles VALUES(?,'principal','servidor',?)",e.creador.first,e.reloj) }
        val saldo=e.s.chispas(e.creador.first)
        repeat(5) { i -> val a=e.publicar(clave="publicar_$i"); assertTrue(a.oficial); assertEquals("aprobado",a.estado) }
        assertEquals(saldo,e.s.chispas(e.creador.first))
        assertEquals(5,e.s.mercado.listar(null,oficial=true).articulos.size)
        assertEquals("tope_publicaciones",assertFailsWith<Problema>{e.publicar(clave="publicar_sexto")}.mensaje)
        val a=e.s.mercado.listar(null).articulos.first()
        assertEquals("sin_permiso",assertFailsWith<Problema>{e.decidir(a,"retirar")}.mensaje)
        e.elevar(); e.decidir(a,"retirar")
        assertEquals(4,e.s.mercado.listar(null).articulos.size)
    }
    @Test fun retiroConservaYDerechosRevocaTambienEquipados() = Entorno().use { e ->
        e.elevar()
        for (marco in listOf(false,true)) {
            val a=e.publicar(300,marco,clave="publicar_$marco");e.decidir(a,"aprobar")
            val item="${a.tipo}:m${a.id}"
            e.s.mercado.comprar(e.comprador.first,a.id,CompraMercado("comprar_$marco"))
            e.s.ponerTema(e.comprador.first,item);e.s.ponerTema(e.creador.first,item)
            e.s.reportar(e.comprador.first,NuevoReporte("mercado",a.id.toString(),"otro: derechos"))
            e.decidir(a,"retirar")
            e.s.ponerTema(e.comprador.first,item)
            assertTrue(e.s.mercado.permitido(e.comprador.first,item))
            assertEquals(a.id,e.s.mercado.listar(e.comprador.first,adquiridos=true).articulos.single().id)
            e.decidir(a,"derechos","autoría acreditada")
            assertFalse(e.s.mercado.permitido(e.comprador.first,item));assertFalse(e.s.mercado.permitido(e.creador.first,item))
            assertFalse(item in e.s.temas(e.creador.first).reclamados)
            assertEquals("",e.s.temas(e.comprador.first).marco)
            assertEquals("",e.s.verPerfil(e.comprador.first,"compradora").portada)
            assertFailsWith<Problema>{e.s.mercado.imagen(a.id,null,null)}
        }
    }
    @Test fun validacionesSaldoImagenAjenaYAlfa() = Entorno().use { e ->
        val img=e.imagen()
        fun d()=PublicarMercado("publicar_prueba",img,"obra","banner",300)
        for (precio in listOf(299,2001)) assertFailsWith<Problema>{e.s.mercado.publicar(e.creador.first,d().copy(precio=precio))}
        assertFailsWith<Problema>{e.s.mercado.publicar(e.comprador.first,d())}
        assertEquals("marco_sin_alfa",assertFailsWith<Problema>{e.s.mercado.publicar(e.creador.first,d().copy(tipo="marco"))}.mensaje)
        assertFailsWith<Problema>{e.s.mercado.publicar(e.creador.first,d().copy(nombre="x".repeat(41)))}
        val antes=e.s.chispas(e.creador.first)
        e.b.usar { cambiar("INSERT INTO chispas(cuenta,cantidad,motivo,cuando) VALUES(?,?,'prueba',?)",e.creador.first,-antes,e.reloj) }
        assertEquals(402,assertFailsWith<Problema>{e.s.mercado.publicar(e.creador.first,d())}.codigo)
        assertTrue(e.s.mercado.listar(e.creador.first,mios=true).articulos.isEmpty())
    }
    @Test fun reintentosConcurrentesYSinSaldoNoMuevenDinero() = Entorno().use { e ->
        e.elevar()
        val a=e.publicar(601);e.decidir(a,"aprobar")
        val saldo=e.s.chispas(e.creador.first)
        val ejecutor=java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val tareas=(1..16).map { ejecutor.submit<Boolean> { e.s.mercado.comprar(e.comprador.first,a.id,CompraMercado("compra_concurrente")) } }
            assertEquals(1,tareas.count { it.get() })
        } finally { ejecutor.shutdownNow() }
        assertEquals(899,e.s.chispas(e.comprador.first));assertEquals(saldo+601*PARTE_CREADOR/100,e.s.chispas(e.creador.first))
        assertEquals(1,e.s.mercado.detalle(null,a.id).ventas)
        val caro=e.publicar(2000,clave="publicar_caro");e.decidir(caro,"aprobar")
        val antes=e.s.chispas(e.creador.first)
        assertEquals(402,assertFailsWith<Problema>{e.s.mercado.comprar(e.comprador.first,caro.id,CompraMercado("comprar_caro"))}.codigo)
        assertEquals(antes,e.s.chispas(e.creador.first));assertEquals(899,e.s.chispas(e.comprador.first))
        assertEquals(0,e.s.mercado.detalle(null,caro.id).ventas)
    }
    @Test fun imagenesSeConservanAlLimpiarYRevocanPorAmbasRutas() = Entorno().use { e ->
        e.elevar()
        val imagen=e.imagen(marco=true)
        val a=e.s.mercado.publicar(e.creador.first,PublicarMercado("publicar_png",imagen,"marco","marco",300))
        e.s.borrarImagenSuelta(imagen)
        assertTrue(e.s.esPng(e.s.imagen(imagen)))
        val png=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(e.s.imagen(imagen)))
        assertEquals(0,png.getRGB(10,10).ushr(24))
        e.decidir(a,"aprobar");e.decidir(a,"derechos")
        assertFailsWith<Problema>{e.s.imagen(imagen)}
        assertFailsWith<Problema>{e.s.mercado.imagen(a.id,null,null)}
        Unit
    }
    @Test fun contratoHttpImagenPngYCamposSinDefaults() = Entorno().use { e ->
        e.elevar()
        val a=e.publicar(marco=true); e.decidir(a,"aprobar")
        testApplication {
            application { fogonSocial(e.s) }
            val respuesta=client.get("/v1/mercado?tipo=marco")
            assertEquals(HttpStatusCode.OK,respuesta.status)
            val articulo=JsonSocial.parseToJsonElement(respuesta.bodyAsText()).jsonObject.getValue("articulos").jsonArray.single().jsonObject
            for (campo in listOf("oficial","ventas","adquirido","propio","motivo","estado")) assertTrue(campo in articulo,campo)
            val imagen=client.get("/v1/mercado/${a.id}/imagen")
            assertEquals(ContentType.Image.PNG,imagen.contentType()?.withoutParameters())
            assertEquals(HttpStatusCode.Forbidden,client.get("/v1/admin/mercado") {header(HttpHeaders.Authorization,"Bearer ${e.comprador.second}")}.status)
            assertEquals(HttpStatusCode.OK,client.get("/v1/admin/mercado") {header(HttpHeaders.Authorization,"Bearer ${e.equipo.second}")}.status)
        }
    }
}
