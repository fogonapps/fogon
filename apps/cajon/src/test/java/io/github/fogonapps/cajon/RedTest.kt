package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Ajustes
import io.github.fogonapps.cajon.datos.ErrorSocial
import io.github.fogonapps.cajon.datos.SesionCuenta
import io.github.fogonapps.cajon.datos.SincronizacionesAgrupadas
import io.github.fogonapps.cajon.datos.SubidasPerfil
import io.github.fogonapps.cajon.datos.prepararSubidaPerfil
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.Lista
import io.github.fogonapps.cajon.datos.Logica
import io.github.fogonapps.cajon.datos.Obra
import io.github.fogonapps.cajon.datos.PartesPerfil
import io.github.fogonapps.cajon.datos.Perfil
import io.github.fogonapps.cajon.datos.PublicacionPropia
import io.github.fogonapps.cajon.datos.Registro
import io.github.fogonapps.cajon.datos.Visibilidad
import io.github.fogonapps.cajon.datos.Ocupado
import io.github.fogonapps.cajon.datos.Tipos
import io.github.fogonapps.cajon.datos.SinRed
import io.github.fogonapps.cajon.datos.ClaveMala
import kotlinx.coroutines.CompletableDeferred
import io.github.fogonapps.cajon.datos.RecuperacionCajon
import io.github.fogonapps.cajon.datos.perfilPendienteTrasMigrar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que El Cajón sube a la red: solo lo que la persona marcó como público o para sus seguidores. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RedTest {
    @Test fun sinInternetResumeLosAvisos() {
        assertEquals(listOf(R.string.buscar_sin_red), resumirAvisos(listOf(R.string.buscar_fallo, R.string.buscar_sin_red, R.string.buscar_sin_red)))
        assertEquals(listOf(R.string.buscar_fallo), resumirAvisos(listOf(R.string.buscar_fallo, R.string.buscar_fallo)))
    }

    @Test fun faltasTraducidasConPuntosYGravedad() {
        val textos = mapOf(R.string.rep_acoso to "Acoso", R.string.rep_falso to "False information",
            R.string.rep_otro to "Otra cosa", R.string.mod_grave_puntos to "Falta grave · 2 puntos",
            R.string.mod_puntos to "%1\$d puntos", R.string.mod_un_punto to "1 punto", R.string.mod_evadir_sancion to "Evasión",
            R.string.rep_violencia to "Amenazas", R.string.mod_cierre to "Cierre de la cuenta")
        fun texto(regla: String, puntos: Int) = io.github.fogonapps.cajon.datos.textoFalta(
            io.github.fogonapps.cajon.datos.FaltaSocial(regla, puntos, 0, 0)) { textos.getValue(it) }
        assertEquals("Acoso · 1 punto", texto("acoso", 1))
        assertEquals("False information · Falta grave · 2 puntos", texto("falso", 2))
        assertEquals("Otra cosa · 3 puntos", texto("desconocida", 3))
        assertEquals("Evasión · Cierre de la cuenta", texto("evadir_sancion", 99))
        assertEquals("Amenazas · Falta grave · 2 puntos", texto("amenaza_real", 2))
    }

    @Test fun reportesDeMensajeSinCabecerasYConTodoElContenido() {
        fun limpiar(texto: String) = io.github.fogonapps.cajon.datos.limpiarReporteMensaje(texto)
        assertEquals("Hola\nsegunda línea", limpiar("acoso: Acoso\nHola\nsegunda línea"))
        assertEquals("Uno\n---\nDos", limpiar("acoso: Acoso\nUno\n---\nfalso: False information\nDos"))
        assertEquals("", limpiar("spam: Spam\n"))
        assertEquals("Sin cabecera\ntexto", limpiar("Sin cabecera\ntexto"))
        assertEquals("acoso: texto sin salto", limpiar("acoso: texto sin salto"))
        assertEquals("", limpiar(""))
    }

    @Test fun bloquearRetiraDeListasCargadasYFeedSinModificarLosOriginales() {
        val ana = io.github.fogonapps.cajon.datos.PersonaSocial("ana")
        val beto = io.github.fogonapps.cajon.datos.PersonaSocial("beto")
        val gente = mapOf("seguidores:yo" to listOf(ana, beto), "siguiendo:yo" to listOf(beto),
            "seguidores:otra" to listOf(beto), "bloqueados" to listOf(beto))
        val limpia = io.github.fogonapps.cajon.datos.quitarCuentaBloqueada(gente, "beto")
        assertEquals(listOf(ana), limpia["seguidores:yo"])
        assertEquals(emptyList<io.github.fogonapps.cajon.datos.PersonaSocial>(), limpia["siguiendo:yo"])
        assertTrue(limpia.getValue("seguidores:otra").isEmpty())
        assertEquals(listOf(beto), limpia["bloqueados"])
        assertEquals(gente.keys, limpia.keys)
        assertEquals(2, gente.getValue("seguidores:yo").size)
        val publicacion = io.github.fogonapps.cajon.datos.PublicacionSocial(1, beto, "texto", 0, "publica", kotlinx.serialization.json.JsonObject(emptyMap()))
        val propia = publicacion.copy(id = 2, autor = ana)
        assertEquals(listOf(propia), io.github.fogonapps.cajon.datos.quitarPublicacionesBloqueadas(listOf(publicacion, propia), "beto"))
        assertEquals(null, io.github.fogonapps.cajon.datos.quitarPublicacionesBloqueadas(null, "beto"))
        assertEquals(gente, io.github.fogonapps.cajon.datos.quitarCuentaBloqueada(gente, "nadie"))
    }

    @Test fun recuperarSinRedEditarYReconectarPublicaLaEdicionEnElMismoIntento() = runTest {
        var conectado = false
        var pendiente = true
        var local = Estado(publicaciones = listOf(PublicacionPropia("p1", 1, "Antes", cambio = 1)))
        val remoto = io.github.fogonapps.cajon.datos.PerfilSocial(io.github.fogonapps.cajon.datos.PersonaSocial("ana", "Ana"))
        val confirmadas = SubidasPerfil()
        val sesion = SesionCuenta { "sesión de prueba" }
        val pasos = mutableListOf<String>()
        val enviadas = mutableListOf<String>()
        suspend fun sincronizar() {
            val preparada = prepararSubidaPerfil(
                pendiente = { pendiente },
                recuperarPerfil = {
                    sesion.intentar(trabajo = {
                        pasos += "perfil"
                        if (!conectado) throw java.io.IOException("sin red")
                        local = RedSocial.planEntrada(local, "ana", false, remoto).estado
                        pendiente = false
                    }, alError = {})
                },
                recuperarCajon = { pasos += "cajón"; true },
                vigente = { true },
            )
            if (!preparada) return
            val contenido = RedSocial.perfilDe(local) to RedSocial.publicacionesDe(local)
            confirmadas.subir(contenido) {
                pasos += "subir"
                enviadas += (contenido.second.single().datos["texto"] as JsonPrimitive).content
                true
            }
        }
        sincronizar()
        assertEquals(listOf("perfil"), pasos)
        assertTrue(enviadas.isEmpty())
        local = local.copy(publicaciones = listOf(local.publicaciones.single().copy(texto = "Editada sin red", cambio = 2)))
        conectado = true
        pasos.clear()
        sincronizar()
        assertEquals(listOf("perfil", "cajón", "subir"), pasos)
        assertEquals(listOf("Editada sin red"), enviadas)
        sincronizar()
        assertEquals(1, enviadas.size) // Solo se omite después de confirmar la subida, no después de descargar.
    }

    @Test fun un401DemoradoDeANoCierraBNiBorraSuLlave() = runTest {
        for (mismoToken in listOf(false, true)) {
            var token: String? = "sesión A de prueba"
            var llave: String? = "llave A de prueba"
            val sesiones = SesionCuenta { token }
            var cierres = 0
            val respuesta = CompletableDeferred<Unit>()
            fun cerrar(x: java.io.IOException) {
                if (x is ErrorSocial && x.codigo == 401) { token = null; llave = null; cierres++ }
            }
            val recuperacionA = async {
                sesiones.intentar(trabajo = { respuesta.await(); throw ErrorSocial(401, "") }, alError = ::cerrar)
            }
            runCurrent()
            sesiones.invalidar() // El mismo punto de invalidación que usa entro().
            val tokenB = if (mismoToken) token else "sesión B de prueba"
            token = tokenB
            llave = "llave B de prueba"
            respuesta.complete(Unit)
            assertEquals(false, recuperacionA.await())
            assertEquals(tokenB, token)
            assertEquals("llave B de prueba", llave)
            assertEquals(0, cierres)
            sesiones.intentar(trabajo = { throw ErrorSocial(401, "") }, alError = ::cerrar)
            assertEquals(1, cierres) // Un 401 vigente sí cierra la sesión.
            assertEquals(null, token)
            assertEquals(null, llave)
        }
    }



    @Test fun un401DeLaLlaveTrasEntrarPerteneceALaNuevaSesion() = runTest {
        var token: String? = null
        val sesiones = SesionCuenta { token }
        var cierres = 0
        sesiones.intentar(trabajo = {
            sesiones.invalidar()
            token = "cuenta nueva de prueba"
            // La preparación de la llave empieza con la marca autenticada, no con la anónima del acceso.
            sesiones.intentar(trabajo = { throw ErrorSocial(401, "") }, alError = {
                cierres++
                token = null
                sesiones.invalidar()
            })
        }, alError = { error("No corresponde a la etapa anónima") })
        assertEquals(1, cierres)
        assertEquals(null, token)
    }

    @Test fun cambiosRapidosAgrupanUnaSolaRepeticionDeLaSincronizacion() = runTest {
        val grupo = SincronizacionesAgrupadas(this)
        val primera = CompletableDeferred<Unit>()
        val segunda = CompletableDeferred<Unit>()
        var ejecuciones = 0
        val accion: suspend () -> Unit = {
            ejecuciones++
            if (ejecuciones == 1) primera.await()
            if (ejecuciones == 2) segunda.await()
        }
        val trabajo = grupo.solicitar(accion)
        runCurrent()
        repeat(100) { assertTrue(trabajo === grupo.solicitar(accion)) }
        assertEquals(1, ejecuciones)
        primera.complete(Unit)
        runCurrent()
        assertEquals(2, ejecuciones)
        segunda.complete(Unit)
        trabajo.join()
        assertEquals(2, ejecuciones)
        grupo.solicitar(accion).join()
        assertEquals(3, ejecuciones)
    }

    @Test fun subidaFallidaODeOtraSesionNoConfirmaElContenido() = runTest {
        val confirmadas = SubidasPerfil()
        val contenido = RedSocial.perfilDe(Estado()) to RedSocial.publicacionesDe(Estado())
        assertEquals(false, confirmadas.subir(contenido) { false })
        val respuesta = CompletableDeferred<Unit>()
        val antigua = async { confirmadas.subir(contenido) { respuesta.await(); true } }
        runCurrent()
        confirmadas.reiniciar()
        respuesta.complete(Unit)
        assertEquals(false, antigua.await())
        var envios = 0
        confirmadas.subir(contenido) { envios++; true }
        confirmadas.subir(contenido) { envios++; true }
        assertEquals(1, envios)
    }

    @Test fun sesionHeredadaQuedaPendienteUnaSolaVez() {
        assertEquals(true, perfilPendienteTrasMigrar(true, false, false))
        assertEquals(false, perfilPendienteTrasMigrar(true, true, false))
        assertEquals(true, perfilPendienteTrasMigrar(true, true, true))
        assertEquals(false, perfilPendienteTrasMigrar(false, false, false))
    }

    private class RecuperacionDePrueba {
        val coordinador = RecuperacionCajon()
        var llave = false
        var bloqueado = false
        var sesionValida = true
        suspend fun revisar(
            preparar: suspend () -> Unit = {},
            consultar: suspend () -> Boolean = { true },
            unir: suspend () -> Boolean = { true },
        ) = coordinador.revisar({ sesionValida }, preparar, { llave }, consultar, unir, { bloqueado = it })

        fun puedeSubir() = RedSocial.puedeSubirPerfil(false, coordinador.recuperado, bloqueado)
    }

    @Test fun tenerLlaveNoPermitePublicarHastaTerminarLaUnion() = runTest {
        val e = RecuperacionDePrueba().apply { llave = true; bloqueado = true }
        val respuesta = CompletableDeferred<Boolean>()
        val recuperacion = async { e.revisar(unir = { respuesta.await() }) }
        runCurrent()
        assertEquals(false, e.bloqueado) // La llave está disponible; la recuperación todavía no terminó.
        assertEquals(false, e.puedeSubir())
        respuesta.complete(true)
        assertEquals(true, recuperacion.await())
        assertEquals(true, e.puedeSubir())
    }

    @Test fun unionFallidaMantieneBloqueadaLaPublicacionYReintenta() = runTest {
        val e = RecuperacionDePrueba().apply { llave = true }
        var intentos = 0
        suspend fun unir(): Boolean { intentos++; return intentos > 1 }
        assertEquals(false, e.revisar(unir = { unir() }))
        assertEquals(false, e.puedeSubir())
        assertEquals(true, e.revisar(unir = { unir() }))
        assertEquals(2, intentos)
        assertEquals(true, e.puedeSubir())
        val fallo = runCatching { e.revisar(unir = { throw java.io.IOException("sin red") }) }
        assertTrue(fallo.exceptionOrNull() is java.io.IOException)
        assertEquals(false, e.puedeSubir())
        assertEquals(true, e.revisar())
        assertEquals(true, e.puedeSubir())
    }

    @Test fun comprobarYDesbloquearSeSerializanEnAmbosOrdenes() = runTest {
        for (comprobarPrimero in listOf(true, false)) {
            val e = RecuperacionDePrueba()
            val respuesta = CompletableDeferred<Boolean>()
            val primera = async {
                if (comprobarPrimero) e.revisar(consultar = { respuesta.await() })
                else e.revisar(preparar = { respuesta.await(); e.llave = true })
            }
            runCurrent()
            val segunda = async {
                if (comprobarPrimero) e.revisar(preparar = { e.llave = true })
                else e.revisar(consultar = { error("No debe consultar sin llave después de desbloquear") })
            }
            runCurrent()
            assertEquals(false, segunda.isCompleted)
            assertEquals(false, e.puedeSubir())
            respuesta.complete(true)
            primera.await(); segunda.await()
            assertEquals(false, e.bloqueado)
            assertEquals(true, e.puedeSubir())
        }
    }

    @Test fun respuestaViejaNoBloqueaUnaLlaveDisponibleNiOtraSesion() = runTest {
        val e = RecuperacionDePrueba()
        val respuesta = CompletableDeferred<Boolean>()
        val comprobacion = async { e.revisar(consultar = { respuesta.await() }) }
        runCurrent()
        e.llave = true
        respuesta.complete(true)
        assertEquals(true, comprobacion.await())
        assertEquals(false, e.bloqueado)

        e.llave = false
        val otraRespuesta = CompletableDeferred<Boolean>()
        val vieja = async { e.revisar(consultar = { otraRespuesta.await() }) }
        runCurrent()
        e.coordinador.reiniciar()
        otraRespuesta.complete(true)
        assertEquals(false, vieja.await())
        assertEquals(false, e.bloqueado)
        assertEquals(false, e.puedeSubir())
    }

    @Test fun cuentaNuevaSubeElApodoElegidoYNoElNombreLocal() {
        val local = Estado(ajustes = Ajustes(nombre = ""))
        val plan = RedSocial.planEntrada(local, "dana", nueva = true, nombre = "Dana Prueba")
        assertTrue(plan.subir)
        assertEquals("Dana Prueba", plan.estado.ajustes.nombre)
        assertEquals("dana", plan.estado.perfil.usuario)
        assertEquals("Caro", RedSocial.planEntrada(Estado(ajustes = Ajustes(nombre = "Caro")), "caro", nueva = true).estado.ajustes.nombre)
    }

    @Test fun entrarRecuperaIdentidadSinCambiarElCajon() {
        val local = Estado(ajustes = Ajustes(nombre = "Teléfono"), perfil = Perfil(bio = "Local", foto = "file:///vieja"))
        val remoto = io.github.fogonapps.cajon.datos.PerfilSocial(
            io.github.fogonapps.cajon.datos.PersonaSocial("ana", "Ana", foto = true),
            bio = "Servidor", portada = "https://foto/portada", enlaces = listOf("https://ejemplo.org"))
        val plan = RedSocial.planEntrada(local, "ana", false, remoto, "file:///descargada")
        assertEquals(false, plan.subir)
        val aplicado = plan.estado
        assertEquals("Ana", aplicado.ajustes.nombre)
        assertEquals("ana", aplicado.perfil.usuario)
        assertEquals(remoto.bio, aplicado.perfil.bio)
        assertEquals(remoto.portada, aplicado.perfil.portada)
        assertEquals(remoto.enlaces, aplicado.perfil.enlaces)
        assertEquals("file:///descargada", aplicado.perfil.foto)
        assertEquals(local.obras, aplicado.obras)
        for (nombreVacio in listOf("", "   ")) {
            val sinNombre = remoto.copy(persona = remoto.persona.copy(nombre = nombreVacio))
            val recuperado = RedSocial.planEntrada(local, "ana", false, sinNombre)
            assertEquals(local.ajustes.nombre, recuperado.estado.ajustes.nombre)
            assertEquals(false, recuperado.subir)
        }
        assertEquals("ninguna", RedSocial.accionFoto(aplicado.perfil.foto, aplicado.perfil.foto, false))
    }

    @Test fun cuentaNuevaSubeElPerfilLocal() {
        val local = Estado(ajustes = Ajustes(nombre = "Ana"), perfil = Perfil(bio = "Mi bio", foto = "file:///foto"))
        val plan = RedSocial.planEntrada(local, "ana", true)
        assertEquals(true, plan.subir)
        assertEquals("Ana", plan.estado.ajustes.nombre)
        assertEquals("Mi bio", plan.estado.perfil.bio)
        assertEquals("ana", plan.estado.perfil.usuario)
        assertEquals("subir", RedSocial.accionFoto(plan.estado.perfil.foto, null, false))
    }

    @Test fun sinRecuperarPerfilOSinRevisarCajonNoSeSube() {
        assertEquals(false, RedSocial.puedeSubirPerfil(true, true, false))
        assertEquals(false, RedSocial.puedeSubirPerfil(false, false, false))
        assertEquals(false, RedSocial.puedeSubirPerfil(false, true, true))
        assertEquals(true, RedSocial.puedeSubirPerfil(false, true, false))
    }

    @Test fun fotoVaciaNoEsUnaOrdenDeBorrado() {
        assertEquals("ninguna", RedSocial.accionFoto("", null, false))
        assertEquals("ninguna", RedSocial.accionFoto("", "file:///anterior", false))
        assertEquals("borrar", RedSocial.accionFoto("", null, true))
        assertEquals("subir", RedSocial.accionFoto("file:///nueva", null, false))
        assertEquals("subir", RedSocial.accionFoto("file:///nueva", null, true))
    }

    private val matrix = Obra("o1", "pelicula", "Matrix", portada = "https://img/matrix.jpg", registros = listOf(
        Registro("r1", "2026-11-01", nota = 9, texto = "Una joya"),
        Registro("r2", "2026-10-01", nota = 6, visibilidad = Visibilidad.PUBLICA),
        Registro("r3", "2026-09-01", visibilidad = Visibilidad.PRIVADA),
    ))
    private val libro = Obra("o2", "libro", "Rayuela", portada = "file:///local.jpg", registros = listOf(Registro("r4", "2026-08-01", visibilidad = Visibilidad.AMIGOS)))

    @Test fun soloLoCompartido() {
        // Sin decir nada en el perfil: el diario es privado y solo sube lo marcado una por una.
        val e = Estado(obras = listOf(matrix, libro), listas = listOf(Lista("l1", "Pendientes"), Lista("l2", "Top", obras = listOf("o1"), visibilidad = Visibilidad.PUBLICA)),
            publicaciones = listOf(PublicacionPropia("p1", 5, "¡Qué día!", imagen = "x".repeat(22)), PublicacionPropia("p2", 6, "borrada", borrada = true)))
        val subidas = RedSocial.publicacionesDe(Logica.normalizar(e))
        assertEquals(setOf("r:o1:r2", "r:o2:r4", "l:l2", "f:p1"), subidas.map { it.clave }.toSet())
        assertEquals(Visibilidad.AMIGOS, subidas.first { it.clave == "r:o2:r4" }.visibilidad)
        // Las portadas locales (de este teléfono) no viajan.
        assertEquals("", (subidas.first { it.clave == "r:o2:r4" }.datos["portada"] as JsonPrimitive).content)
        assertEquals(3.0, (subidas.first { it.clave == "r:o1:r2" }.datos["estrellas"] as JsonPrimitive).content.toDouble(), 0.0)

        // Con el diario público en el perfil, también lo que no dice nada (r1, una reseña); lo privado nunca.
        val publico = Logica.normalizar(e.copy(perfil = Perfil(visibilidad = mapOf(PartesPerfil.DIARIO to Visibilidad.PUBLICA))))
        val claves = RedSocial.publicacionesDe(publico).map { it.clave }
        assertTrue("r:o1:r1" in claves && "r:o1:r3" !in claves)
        assertEquals("resena", RedSocial.publicacionesDe(publico).first { it.clave == "r:o1:r1" }.tipo)
    }

    @Test fun perfilConSusPartes() {
        val e = Estado(obras = listOf(matrix), ajustes = Ajustes(nombre = "Ana", favoritas = listOf("o1")),
            perfil = Perfil(bio = "Cine", portada = "file:///mia.jpg", visibilidad = mapOf(PartesPerfil.FAVORITAS to Visibilidad.PUBLICA)))
        val p = RedSocial.perfilDe(e)
        assertEquals("Ana", p.nombre)
        assertEquals("", p.portada)                          // la portada local no sube
        assertEquals(setOf(PartesPerfil.FAVORITAS), p.partes.keys) // los destacados siguen privados
    }

    @Test fun lasPublicacionesPropiasSeSincronizan() {
        val a = Estado(publicaciones = listOf(PublicacionPropia("p1", 1, "hola", cambio = 1)))
        val b = Estado(publicaciones = listOf(PublicacionPropia("p1", 1, "hola", cambio = 5, borrada = true), PublicacionPropia("p2", 2, "otra", cambio = 2)))
        val unido = Logica.unir(a, b)
        assertEquals(2, unido.publicaciones.size)
        assertTrue(unido.publicaciones.first { it.clave == "p1" }.borrada)
    }
}

class OrdenTest {
    private val ahora = 1_800_000_000_000L
    private fun pub(id: Long, autor: String, horas: Int, vistas: Int = 100, calidad: Double = 1.0, fuente: String = "", creador: String = "") =
        io.github.fogonapps.cajon.datos.PublicacionSocial(id, io.github.fogonapps.cajon.datos.PersonaSocial(autor), "foto", ahora - horas * 3_600_000L, "publica",
            kotlinx.serialization.json.buildJsonObject { if (fuente.isNotBlank()) put("fuente", kotlinx.serialization.json.JsonPrimitive(fuente)); if (creador.isNotBlank()) put("creador", kotlinx.serialization.json.JsonPrimitive(creador)) },
            vistas = vistas, calidad = calidad)

    @Test fun afinidadDiversidadYNuevas() {
        val e = Estado(obras = listOf(Obra("o1", "pelicula", "Matrix", fuente = "tmdb:movie:603", meGusta = true)),
            gustos = listOf(io.github.fogonapps.cajon.datos.Gusto("artista:spam", "Spam", io.github.fogonapps.cajon.datos.Senales.NO_INTERESA)))
        val lista = listOf(
            pub(1, "ana", 100), pub(2, "ana", 100), pub(3, "ana", 100),
            pub(4, "beto", 200, fuente = "tmdb:movie:603"),          // vieja pero de algo que te gusta
            pub(5, "caro", 100), pub(6, "dani", 100), pub(7, "eva", 100),
            pub(8, "nuevo", 2, vistas = 3),                          // nueva, pocas vistas
            pub(9, "x", 1, creador = "spam"),                        // no te interesa
        )
        val orden = io.github.fogonapps.cajon.RedSocial.ordenarPorGustos(lista, e, ahora)
        assertEquals(4L, orden.first().first.id)                     // lo tuyo primero
        assertTrue(orden.first().second is io.github.fogonapps.cajon.RedSocial.Companion.Motivo.TienesLaObra)
        assertTrue(orden.none { it.first.id == 9L })
        assertEquals(8L, orden[4].first.id)                          // la nueva, en el quinto lugar
        orden.zipWithNext().forEach { (a, b) -> assertTrue(a.first.autor.usuario != b.first.autor.usuario) }
    }
}


/** Un fallo de una consulta hija queda en su fuente sin detener las demás búsquedas. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BusquedaFuentesTest {
    @Test fun falloHijoNoCancelaOtrasFuentes() = runTest {
        for ((fallo, aviso) in listOf(
            SinRed() to R.string.buscar_sin_red,
            ClaveMala() to R.string.buscar_tmdb_mala,
            Ocupado() to R.string.buscar_fallo,
            IllegalArgumentException("Respuesta mal formada") to R.string.buscar_fallo,
        )) {
            val errores = mutableListOf<Int>()
            val cargando = mutableSetOf("tmdb", "libros")
            val resultados = mutableListOf<String>()
            val cine = launch {
                buscarEnFuente(errores::add, { cargando.remove("tmdb") }) {
                    val series = async { throw fallo }
                    delay(10)
                    series.await()
                }
            }
            val libros = launch {
                buscarEnFuente(errores::add, { cargando.remove("libros") }) {
                    delay(20)
                    resultados.add("libro")
                }
            }
            cine.join()
            libros.join()
            assertEquals(listOf(aviso), errores)
            assertEquals(listOf("libro"), resultados)
            assertTrue(cargando.isEmpty())
            assertTrue(!cine.isCancelled && !libros.isCancelled)
        }
    }

    @Test fun cancelarSePropagaYTerminaLaCarga() = runTest {
        val errores = mutableListOf<Int>()
        var cargando = true
        var cancelacionPropagada = false
        val fuente = launch {
            try {
                buscarEnFuente(errores::add, { cargando = false }) {
                    async { awaitCancellation() }.await()
                }
            } catch (e: CancellationException) {
                cancelacionPropagada = true
                throw e
            }
        }
        runCurrent()
        fuente.cancel()
        fuente.join()
        assertTrue(cancelacionPropagada)
        assertTrue(fuente.isCancelled)
        assertTrue(!cargando)
        assertTrue(errores.isEmpty())
    }
}

class AvisosBusquedaTest {
    @Test fun todoFalloMuestraAvisoSinAfirmarQueNoExiste() {
        val b = Busqueda(texto = "Interstellar", errores = mapOf(
            "tmdb" to R.string.buscar_sin_red, "libros" to R.string.buscar_sin_red,
            "musica" to R.string.buscar_sin_red, "personas" to R.string.buscar_sin_red,
        ))
        assertEquals(listOf(R.string.buscar_sin_red), b.avisos)
        assertTrue(!b.mostrarNoEncontrado)
        assertTrue(b.copy(errores = emptyMap()).mostrarNoEncontrado)
        assertTrue(!b.copy(errores = emptyMap(), cargando = setOf("tmdb")).mostrarNoEncontrado)
        assertTrue(!b.copy(errores = mapOf("directores" to R.string.buscar_fallo)).mostrarNoEncontrado)
    }

    @Test fun clasificaTransporteServicioYRespuestaInvalida() {
        for (error in listOf(SinRed(), java.net.UnknownHostException(), java.net.ConnectException(),
            java.net.SocketTimeoutException(), java.io.IOException())) {
            assertEquals(R.string.buscar_sin_red, avisoErrorBusqueda(error))
        }
        for (error in listOf(Ocupado(), io.github.fogonapps.cajon.datos.ErrorSocial(503, ""),
            IllegalStateException("HTTP 500"), IllegalArgumentException("JSON inválido"))) {
            assertEquals(R.string.buscar_fallo, avisoErrorBusqueda(error))
        }
        assertEquals(R.string.buscar_tmdb_mala, avisoErrorBusqueda(ClaveMala()))
    }

    @Test fun consultasFallidasTerminanConAvisoYConservanLoRecibido() = runTest {
        var b = Busqueda(texto = "Interstellar", cargando = setOf("tmdb", "libros", "personas"))
        for ((nombre, fallo) in listOf("tmdb" to SinRed(), "libros" to java.net.UnknownHostException(),
            "personas" to java.net.ConnectException())) {
            buscarEnFuente(
                error = { b = b.copy(errores = b.errores + (nombre to it)) },
                terminar = { b = b.copy(cargando = b.cargando - nombre) },
            ) { throw fallo }
        }
        assertTrue(b.cargando.isEmpty())
        assertEquals(listOf(R.string.buscar_sin_red), b.avisos)
        assertTrue(!b.mostrarNoEncontrado)
        val obra = io.github.fogonapps.cajon.datos.Encontrado(Tipos.PELICULA, "Interstellar", 2014, "", "", "tmdb:movie:157336")
        buscarEnFuente(
            error = { b = b.copy(errores = b.errores + ("otra" to it)) }, terminar = {},
        ) {
            b = b.copy(obras = mapOf(Tipos.PELICULA to listOf(obra)))
            runCatching<Unit> { throw SinRed() }.avisarFallo { b = b.copy(errores = b.errores + ("fotos" to it)) }
        }
        assertEquals(listOf(obra), b.obras[Tipos.PELICULA])
        assertEquals(listOf(R.string.buscar_sin_red), b.avisos)
    }

    @Test fun cancelarConsultaOpcionalSePropagaSinAviso() {
        val avisos = mutableListOf<Int>()
        var cancelada = false
        try {
            runCatching<Unit> { throw CancellationException() }.avisarFallo(avisos::add)
        } catch (_: CancellationException) { cancelada = true }
        assertTrue(cancelada)
        assertTrue(avisos.isEmpty())
    }
    @Test
    fun avisosDeBusquedaAgrupaMensajesYConservaLosDistintos() {
        val repetidos = Busqueda(errores = linkedMapOf("tmdb" to 1, "libros" to 1, "musica" to 1))
        assertEquals(listOf(1), repetidos.avisos)
        assertEquals(listOf(2, 1), repetidos.copy(
            errores = linkedMapOf("tmdb" to 2, "libros" to 1, "musica" to 1),
        ).avisos)
        assertEquals(emptyList<Int>(), Busqueda().avisos)
        assertEquals(listOf(1), repetidos.copy(errores = mapOf("libros" to 1)).avisos)
    }

}

class ContenidoReporteTest {
    @Test fun publicacionConObra() {
        val previa = io.github.fogonapps.cajon.datos.leerContenidoReporte(
            """{"titulo":"Una obra","tipoObra":"libro","portada":"https://ejemplo.org/portada.jpg","anio":2020,"creador":"Una autora","texto":"Mi comentario","tipo":"foto"}"""
        )
        assertEquals("Una obra", previa.titulo)
        assertEquals("libro", previa.tipoObra)
        assertEquals("https://ejemplo.org/portada.jpg", previa.portada)
        assertEquals("2020", previa.anio)
        assertEquals("Una autora", previa.creador)
        assertEquals("Mi comentario", previa.texto)
    }

    @Test fun textoSinJsonSeConserva() {
        assertEquals("Un comentario\ncon dos líneas", io.github.fogonapps.cajon.datos.leerContenidoReporte("Un comentario\ncon dos líneas").texto)
    }

    @Test fun jsonRotoSeAcota() {
        val roto = "{\"texto\":\"" + "a".repeat(700)
        assertEquals(roto.take(600), io.github.fogonapps.cajon.datos.leerContenidoReporte(roto).texto)
    }

    @Test fun jsonSinCamposLegiblesNoExponeElObjeto() {
        val previa = io.github.fogonapps.cajon.datos.leerContenidoReporte("""{"titulo":null,"texto":{"interno":"dato"}}""")
        assertEquals("", previa.titulo)
        assertEquals("", previa.texto)
    }
}

class BorrarPublicacionTest {
    @Test fun eliminaDeTodasLasVistasSinTocarOtrasCuentas() {
        fun publicacion(id: Long, usuario: String, campo: String, clave: String) =
            io.github.fogonapps.cajon.datos.PublicacionSocial(id,
                io.github.fogonapps.cajon.datos.PersonaSocial(usuario), "foto", 1, "publica",
                kotlinx.serialization.json.buildJsonObject { put(campo, JsonPrimitive(clave)) })
        val propia = publicacion(1, "ana", "clave", "f:p1")
        val antigua = publicacion(2, "ana", "propia", "p1")
        val otraCuenta = publicacion(3, "beto", "clave", "f:p1")
        val otraPublicacion = publicacion(4, "ana", "clave", "f:p2")
        val lista = listOf(propia, antigua, otraCuenta, otraPublicacion)
        val perfil = io.github.fogonapps.cajon.datos.PerfilSocial(propia.autor, publicaciones = lista)
        val memoria = RedSocial.Companion.MemoriaPublicaciones(lista, lista, lista,
            lista.associateBy { it.id }, mapOf("ana" to perfil))
        val limpia = memoria.sinPropia("ana", "p1")
        val esperadas = listOf(otraCuenta, otraPublicacion)
        assertEquals(esperadas, limpia.feed)
        assertEquals(esperadas, limpia.explorar)
        assertEquals(esperadas, limpia.guardadas)
        assertEquals(esperadas.associateBy { it.id }, limpia.vistas)
        assertEquals(esperadas, limpia.perfiles.getValue("ana").publicaciones)
        assertEquals(limpia, limpia.sinPropia("ana", "p1"))
        val sinCargar = RedSocial.Companion.MemoriaPublicaciones(null, null, null, emptyMap(), emptyMap())
        assertEquals(sinCargar, sinCargar.sinPropia("ana", "p1"))
    }
}
