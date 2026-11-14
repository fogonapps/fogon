package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ResenasTest {
    private val obra = Obra("obra", Tipos.SERIE, "Serie", fuente = "tmdb:tv:1", nota = 8,
        resena = "Opinión antigua", resenaVisibilidad = Visibilidad.AMIGOS, cambio = 10,
        registros = listOf(Registro("entrada", "2026-01-02", 7, texto = "La segunda vez", visibilidad = Visibilidad.PUBLICA)))

    @Test fun calificacionesNoCuentanParaLaInsigniaDeResenas() {
        val e = Estado(resenas = List(20) { Resena("n:$it", obra.id, nota = 10) } +
            Resena("n:texto", obra.id, texto = "a".repeat(280)))
        assertEquals(1, Insignias.de(e, 2026).first { it.id == "critico" }.lleva)
    }

    @Test fun estrellasYResenaDeCancionCompartenNotaSinMezclarDestinos() {
        val album = obra.copy(tipo = Tipos.ALBUM, canciones = listOf(
            Cancion("Primera", mbid = "uno"), Cancion("Segunda", mbid = "dos")))
        var e = Resenas.calificar(Estado(obras = listOf(album)), album.id, 8, 100, "cancion:uno")
        val primera = Resenas.deCancion(e, e.obras.single(), 0)!!
        assertEquals(8, primera.nota)
        assertEquals(Visibilidad.PRIVADA, primera.visibilidad)
        assertTrue(Resenas.valida(primera))
        assertEquals(listOf(8, 0), e.obras.single().canciones.map { it.nota })
        assertEquals(album.nota, e.obras.single().nota)
        e = Resenas.calificar(e, album.id, 4, 200, "cancion:dos")
        val segunda = Resenas.deCancion(e, e.obras.single(), 1)!!
        e = Resenas.guardar(e, primera.copy(texto = "Texto", nota = 6, visibilidad = Visibilidad.PUBLICA), 300)
        assertEquals(listOf(6, 4), e.obras.single().canciones.map { it.nota })
        assertEquals(segunda, Resenas.deCancion(e, e.obras.single(), 1))
        assertEquals(6, RedSocial.publicacionesDe(e).first { it.clave == primera.id }.datos["nota"]!!.jsonPrimitive.int)
        e = Resenas.borrar(e, primera.id, 400)
        assertEquals(listOf(0, 4), e.obras.single().canciones.map { it.nota })
        assertEquals(segunda, Resenas.deCancion(e, e.obras.single(), 1))
        assertTrue(RedSocial.publicacionesDe(e).none { it.clave == primera.id })
    }

    @Test fun cancionLocalConservaResenaAlCompletarIdentificadorYReordenar() {
        val album = obra.copy(tipo = Tipos.ALBUM, canciones = listOf(Cancion("Sin id"), Cancion("Otra")))
        val parte = Resenas.parteCancion(album, 0)
        val e = Resenas.calificar(Estado(obras = listOf(album)), album.id, 8, 100, parte)
        val completado = album.copy(canciones = listOf(Cancion("Otra", mbid = "dos"), Cancion("Sin id", mbid = "uno")))
        val nuevo = e.copy(obras = listOf(Resenas.actualizarNotasCanciones(e, completado)))
        assertEquals(listOf(0, 8), nuevo.obras.single().canciones.map { it.nota })
        assertTrue(Resenas.parteValida(completado, parte))
        assertEquals("Sin id", Resenas.etiqueta(parte, completado))
        assertNull(Resenas.deCancion(nuevo, completado, 0))
        val calificada = Resenas.calificar(nuevo, album.id, 6, 200, "cancion:uno")
        assertEquals(1, calificada.resenas.size)
        assertEquals(e.resenas.single().id, calificada.resenas.single().id)
        assertEquals(listOf(0, 6), calificada.obras.single().canciones.map { it.nota })
        val repetidas = album.copy(canciones = listOf(Cancion("Misma"), Cancion("Misma")))
        assertNotEquals(Resenas.parteCancion(repetidas, 0), Resenas.parteCancion(repetidas, 1))
    }

    @Test fun diarioComparteNotaTextoYVisibilidadSinMezclarRegistros() {
        val base = Estado(obras = listOf(obra.copy(registros = listOf(
            Registro("uno", "2026-01-01"), Registro("dos", "2026-01-02")))))
        var e = Resenas.registrar(base, obra.id, Registro("uno", "2026-01-01", 8), "", 100)
        val primera = Resenas.deDestino(e, obra.id, registro = "uno")!!
        assertEquals(8, primera.nota)
        assertEquals(Visibilidad.PRIVADA, primera.visibilidad)
        assertEquals(Visibilidad.PRIVADA, e.obras.single().registros.first { it.id == "uno" }.visibilidad)
        e = Resenas.registrar(e, obra.id, Registro("dos", "2026-01-02", 4), "Otra vez", 200)
        val segunda = Resenas.deDestino(e, obra.id, registro = "dos")!!
        e = Resenas.guardar(e, primera.copy(texto = "Mi vez", nota = 6, visibilidad = Visibilidad.AMIGOS), 300)
        assertEquals(6, e.obras.single().registros.first { it.id == "uno" }.nota)
        assertEquals(Visibilidad.AMIGOS, e.obras.single().registros.first { it.id == "uno" }.visibilidad)
        assertEquals(segunda, Resenas.deDestino(e, obra.id, registro = "dos"))
        assertEquals(obra.nota, e.obras.single().nota)
        e = Resenas.borrar(e, primera.id, 400)
        assertEquals(0, e.obras.single().registros.first { it.id == "uno" }.nota)
        assertEquals(Visibilidad.PRIVADA, e.obras.single().registros.first { it.id == "uno" }.visibilidad)
        assertEquals(segunda, Resenas.deDestino(e, obra.id, registro = "dos"))
        assertEquals(4, e.obras.single().registros.first { it.id == "dos" }.nota)
    }

    @Test fun editarDiarioActualizaLaResenaExistenteYPermiteTextoSinEstrellas() {
        val base = Estado(obras = listOf(obra))
        val r = Registro("entrada", "2026-01-02", 8, visibilidad = Visibilidad.PUBLICA)
        val primera = Resenas.registrar(base, obra.id, r, "Mi texto", 100)
        val segunda = Resenas.registrar(primera, obra.id, r.copy(nota = 6, visibilidad = Visibilidad.PRIVADA), "Nuevo texto", 200)
        assertEquals(primera.resenas.single().id, segunda.resenas.single().id)
        assertEquals(6, segunda.resenas.single().nota)
        assertEquals("Nuevo texto", segunda.resenas.single().texto)
        assertEquals(Visibilidad.PRIVADA, segunda.resenas.single().visibilidad)
        assertTrue(RedSocial.publicacionesDe(segunda).none { it.clave == segunda.resenas.single().id })
        val sinEstrellas = Resenas.registrar(segunda, obra.id, r.copy(nota = 0, visibilidad = Visibilidad.PRIVADA), "Texto", 300)
        assertNull(sinEstrellas.resenas.single().nota)
        assertEquals(0, sinEstrellas.obras.single().registros.single().nota)
        assertTrue(Resenas.valida(sinEstrellas.resenas.single()))
    }

    @Test fun notasSinResenaSeMigranUnaVezSinPublicarse() {
        val e = Estado(obras = listOf(obra.copy(resena = "", registros = emptyList(), notas = "Mis datos privados")))
        val migrado = Resenas.migrar(e)
        val r = Resenas.deObra(migrado, obra.id)!!
        assertEquals("Mis datos privados", r.texto)
        assertEquals(Visibilidad.PRIVADA, r.visibilidad)
        assertEquals(obra.nota, r.nota)
        assertEquals("", migrado.obras.single().notas)
        assertEquals(migrado, Resenas.migrar(migrado))
        assertTrue(RedSocial.publicacionesDe(migrado).none { it.clave == r.id })
    }

    @Test fun notasConResenaPrivadaSeAgreganSinPerderTexto() {
        val r = Resena("n:privada", obra.id, texto = "Reseña", nota = 6)
        val e = Estado(obras = listOf(obra.copy(resena = "", registros = emptyList(), notas = "Datos")), resenas = listOf(r))
        val migrado = Resenas.migrar(e)
        assertEquals("Reseña\n\nDatos", migrado.resenas.single().texto)
        assertEquals(r.id, migrado.resenas.single().id)
        assertEquals(Visibilidad.PRIVADA, migrado.resenas.single().visibilidad)
        assertEquals("", migrado.obras.single().notas)
        assertEquals(migrado, Resenas.migrar(migrado))
    }

    @Test fun notasConResenaCompartidaSeQuedanPrivadasEnElCampoAntiguo() {
        for (visibilidad in listOf(Visibilidad.PUBLICA, Visibilidad.AMIGOS)) {
            val r = Resena("n:publica", obra.id, texto = "Reseña publicada", visibilidad = visibilidad)
            val e = Estado(obras = listOf(obra.copy(resena = "", registros = emptyList(), notas = "No publicar")), resenas = listOf(r))
            assertEquals(e, Resenas.migrar(e))
            assertFalse(RedSocial.publicacionesDe(Resenas.migrar(e)).any { it.datos.toString().contains("No publicar") })
        }
    }

    @Test fun terminarDesdeLaFichaAnadeHoySinDuplicar() {
        val hoy = java.time.LocalDate.of(2026, 1, 3)
        val primera = Logica.cambiarEstadoConRegistro(obra, Estados.TERMINADO, hoy, "hoy")
        assertEquals(listOf("2026-01-02", "2026-01-03"), primera.registros.map { it.fecha })
        assertEquals(Visibilidad.PRIVADA, primera.registros.last().visibilidad)
        assertEquals(primera, Logica.cambiarEstadoConRegistro(primera, Estados.TERMINADO, hoy, "otro"))
        assertEquals(obra.registros, Logica.cambiarEstadoConRegistro(obra, Estados.EN_CURSO, hoy, "otro").registros)
    }

    @Test fun quintaFavoritaNoReemplazaNingunaDelPerfil() {
        val e = Estado(ajustes = Ajustes(favoritas = listOf("a", "b", "c", "d")))
        assertEquals(e, Logica.favorita(e, "e", 100))
        val sinUna = Logica.favorita(e, "b", 100)
        assertEquals(listOf("a", "c", "d"), sinUna.ajustes.favoritas)
        assertEquals(listOf("a", "c", "d", "e"), Logica.favorita(sinUna, "e", 200).ajustes.favoritas)
    }

    @Test fun guardarOtraEnteraReutilizaLaResenaDeLaFicha() {
        val e = Resenas.aplicarNota(Estado(obras = listOf(obra)), obra.id, 6, 100)
        val r = Resenas.deObra(e, obra.id)!!
        val guardado = Resenas.guardar(e, Resena("n:borrador", obra.id, texto = "Ahora con texto", nota = 8), 200)
        assertEquals(1, guardado.resenas.size)
        assertEquals(r.id, guardado.resenas.single().id)
        assertEquals("Ahora con texto", guardado.resenas.single().texto)
        assertEquals(8, guardado.obras.single().nota)
    }

    @Test fun estrellasSinResenaCreanUnaPrivadaConLaMismaNota() {
        val e = Resenas.aplicarNota(Estado(obras = listOf(obra.copy(nota = 0))), obra.id, 6, 100)
        val r = Resenas.deObra(e, obra.id)!!
        assertEquals(6, e.obras.single().nota)
        assertEquals(6, r.nota)
        assertEquals("", r.texto)
        assertEquals(Visibilidad.PRIVADA, r.visibilidad)
        assertTrue(Resenas.valida(r))
        val actualizado = Resenas.aplicarNota(e, obra.id, 8, 200)
        assertEquals(r.id, actualizado.resenas.single().id)
        assertEquals(8, actualizado.resenas.single().nota)
        val borrado = Resenas.borrar(actualizado, r.id, 300)
        assertEquals(0, borrado.obras.single().nota)
        assertTrue(borrado.resenas.single().borrada)
        assertEquals(300L, borrado.resenas.single().cambio)
        assertNull(Resenas.deObra(borrado, obra.id))
        val recreado = Resenas.aplicarNota(borrado, obra.id, 5, 400)
        assertNotEquals(r.id, Resenas.deObra(recreado, obra.id)!!.id)
    }

    @Test fun todasLasResenasPermitenSoloNota() {
        val e = Estado(obras = listOf(obra), artistas = listOf(Artista("a", "Autora")))
        val r = Resena("n:nota", obra.id, nota = 8, visibilidad = Visibilidad.PUBLICA)
        assertTrue(Resenas.valida(r))
        assertFalse(Resenas.valida(r.copy(nota = 0)))
        assertFalse(Resenas.valida(r.copy(nota = null)))
        assertTrue(Resenas.valida(r.copy(parte = "ep:S1E1")))
        assertTrue(Resenas.valida(r.copy(registro = "entrada")))
        assertTrue(Resenas.valida(r.copy(obra = "a")))
        val publica = Resenas.guardar(e, r, 100)
        assertEquals(8, RedSocial.publicacionesDe(publica).first { it.clave == r.id }.datos["nota"]!!.jsonPrimitive.int)
        val privada = Resenas.guardar(publica, r.copy(visibilidad = Visibilidad.PRIVADA), 200)
        assertTrue(RedSocial.publicacionesDe(privada).none { it.clave == r.id })
        assertEquals(8, Resenas.deObra(privada, obra.id)!!.nota)
    }

    @Test fun quitarEstrellasSinTextoBorraPeroConTextoConserva() {
        val e = Resenas.aplicarNota(Estado(obras = listOf(obra)), obra.id, 6, 100)
        val sinNota = Resenas.aplicarNota(e, obra.id, 0, 200)
        assertEquals(0, sinNota.obras.single().nota)
        assertTrue(sinNota.resenas.single().borrada)
        val conTexto = Resenas.guardar(e, e.resenas.single().copy(texto = "Opinión"), 200)
        val sinEstrellas = Resenas.aplicarNota(conTexto, obra.id, 0, 300)
        assertEquals("Opinión", Resenas.deObra(sinEstrellas, obra.id)!!.texto)
        assertNull(Resenas.deObra(sinEstrellas, obra.id)!!.nota)
        assertTrue(Resenas.valida(sinEstrellas.resenas.single()))
    }

    @Test fun guardarResenaEnteraActualizaNotaDeObra() {
        val r = Resena("n:entera", obra.id, texto = "Opinión", nota = 8)
        val e = Resenas.guardar(Estado(obras = listOf(obra.copy(nota = 2)), borradoresResena = listOf(r)), r, 100)
        assertEquals(8, e.obras.single().nota)
        assertEquals(8, e.resenas.single().nota)
        assertEquals(100L, e.obras.single().cambio)
        assertTrue(e.borradoresResena.single().borrada)
        for (nota in listOf(null, 0)) {
            val sinNota = Resenas.guardar(e, r.copy(nota = nota), 200)
            assertEquals(0, sinNota.obras.single().nota)
            assertNull(sinNota.resenas.single().nota)
        }
    }

    @Test fun notaDeFichaSoloCambiaResenasEnterasVivasYSePreparaParaSubir() {
        val entera = Resena("n:entera", obra.id, texto = "Opinión", nota = 8, visibilidad = Visibilidad.PUBLICA)
        val episodio = entera.copy(id = "n:episodio", parte = "ep:S1E1")
        val diario = entera.copy(id = "n:diario", registro = "entrada")
        val borrada = entera.copy(id = "n:borrada", borrada = true)
        val otra = entera.copy(id = "n:otra")
        val e = Estado(obras = listOf(obra), resenas = listOf(entera, episodio, diario, borrada, otra))
        val cambiado = Resenas.aplicarNota(e, obra.id, 6, 100)
        assertEquals(6, cambiado.obras.single().nota)
        assertEquals(listOf(entera.copy(nota = 6, cambio = 100), episodio, diario, borrada,
            otra.copy(nota = 6, cambio = 100)), cambiado.resenas)
        val publicacion = RedSocial.publicacionesDe(cambiado).first { it.clave == entera.id }
        assertEquals(6, publicacion.datos["nota"]!!.jsonPrimitive.int)
        assertNotEquals(RedSocial.publicacionesDe(e), RedSocial.publicacionesDe(cambiado))
    }

    @Test fun guardarParteODiarioNoCambiaNotaDeObra() {
        val e = Estado(obras = listOf(obra))
        for (r in listOf(Resena("n:episodio", obra.id, parte = "ep:S1E1", nota = 3),
            Resena("n:diario", obra.id, registro = "entrada", nota = 3))) {
            assertFalse(Resenas.esDeObraEntera(e, r))
            assertEquals(obra.nota, Resenas.guardar(e, r, 100).obras.single().nota)
        }
        val artista = Artista("artista", "Nombre")
        assertFalse(Resenas.esDeObraEntera(Estado(artistas = listOf(artista)), Resena("n:a", artista.id)))
        assertFalse(Resenas.esDeObraEntera(e, Resena("n:a", obra.id, parte = "autor:1")))
    }

    @Test fun migracionConIdsExactosFechaNotaYRespaldoSinCambiar() {
        val e = Resenas.migrar(Estado(obras = listOf(obra)))
        assertEquals(listOf("o:obra", "r:obra:entrada"), e.resenas.map { it.id })
        assertEquals(obra, e.obras.single())
        val opinion = e.resenas.first()
        assertEquals("Opinión antigua", opinion.texto)
        assertEquals(8, opinion.nota)
        assertEquals(Visibilidad.AMIGOS, opinion.visibilidad)
        val entrada = e.resenas.last()
        assertEquals("entrada", entrada.registro)
        assertEquals("2026-01-02", entrada.fecha)
        assertEquals(7, entrada.nota)
        assertEquals(Visibilidad.PUBLICA, entrada.visibilidad)
        assertEquals(e, Resenas.migrar(e))
        assertEquals(e, Logica.normalizar(Logica.normalizar(e)))
    }

    @Test fun cargaDeDiscoMigraYSerializacionConservaResenasYBorradores() = runTest {
        val inicial = Estado(obras = listOf(obra))
        val cargado = SerializadorEstado.readFrom(ByteArrayInputStream(JsonCajon.encodeToString(Estado.serializer(), inicial).toByteArray()))
        assertEquals(2, cargado.resenas.size)
        val e = cargado.copy(borradoresResena = listOf(Resena("n:borrador", "obra", texto = "pendiente")))
        assertEquals(e, JsonCajon.decodeFromString(Estado.serializer(), JsonCajon.encodeToString(Estado.serializer(), e)))
    }

    @Test fun camposVaciosNoSeMigranYDiarioHeredaVisibilidad() {
        assertTrue(Resenas.migrar(Estado(obras = listOf(obra.copy(resena = " ", registros = emptyList())))).resenas.isEmpty())
        val e = Resenas.migrar(Estado(obras = listOf(obra.copy(resena = "", registros = listOf(obra.registros.single().copy(visibilidad = "")))),
            perfil = Perfil(visibilidad = mapOf(PartesPerfil.DIARIO to Visibilidad.AMIGOS))))
        assertEquals(Visibilidad.AMIGOS, e.resenas.single().visibilidad)
    }

    @Test fun unionConservaEdicionesYBorradosSinResucitarElRespaldo() {
        val viejo = Estado(obras = listOf(obra))
        val migrado = Resenas.migrar(viejo)
        val editado = migrado.copy(resenas = migrado.resenas.map { it.copy(texto = "Nueva", cambio = 20) })
        assertEquals(editado.resenas, Logica.unir(viejo, editado).resenas)
        val borrado = editado.copy(resenas = editado.resenas.map { it.copy(borrada = true, cambio = 30) })
        assertEquals(borrado.resenas, Logica.normalizar(Logica.unir(borrado, viejo)).resenas)
        // Cambiar solo la nota de una obra en un cliente antiguo no puede restaurar su texto de respaldo.
        val respaldoTocado = viejo.copy(obras = listOf(obra.copy(cambio = 1000)))
        assertEquals(borrado.resenas, Logica.normalizar(Logica.unir(borrado, respaldoTocado)).resenas)
        val duplicados = migrado.copy(resenas = migrado.resenas + editado.resenas)
        assertEquals(editado.resenas, Logica.normalizar(duplicados).resenas)
        val nuevo = Resena("n:otra", "obra", texto = "Otra", cambio = 40)
        assertEquals(3, Logica.unir(editado, Estado(resenas = listOf(nuevo))).resenas.size)
    }

    @Test fun subidaUsaClavesDatosFotosYNoDuplicaDiario() {
        val migradas = Resenas.migrar(Estado(obras = listOf(obra)))
        val propias = migradas.resenas.map { it.copy(parte = "ep:S1E3", spoiler = true, fotos = listOf("uno.jpg", "dos.jpg"), nota = 0) }
        val e = migradas.copy(resenas = propias, borradoresResena = listOf(Resena("n:borrador", "obra", texto = "Nunca sale", visibilidad = Visibilidad.PUBLICA)))
        val subidas = RedSocial.publicacionesDe(e)
        assertEquals(setOf("o:obra", "r:obra:entrada"), subidas.map { it.clave }.toSet())
        subidas.forEach { p ->
            assertEquals("resena", p.tipo)
            assertEquals("tmdb:tv:1", p.datos["fuente"]!!.jsonPrimitive.content)
            assertEquals("ep:S1E3", p.datos["parte"]!!.jsonPrimitive.content)
            assertEquals(0, p.datos["nota"]!!.jsonPrimitive.int)
            assertTrue(p.datos["spoiler"]!!.jsonPrimitive.boolean)
            assertEquals(listOf("uno.jpg", "dos.jpg"), p.datos["fotos"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        assertTrue(RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(borrada = true) })).isEmpty())
        assertTrue(RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(visibilidad = Visibilidad.PRIVADA) })).isEmpty())
        val sinNota = RedSocial.publicacionesDe(e.copy(resenas = propias.map { it.copy(nota = null) }))
        assertTrue(sinNota.all { "nota" !in it.datos && "estrellas" !in it.datos })
    }

    @Test fun diarioSinResenaSigueSubiendo() {
        val e = Estado(obras = listOf(obra.copy(resena = "", registros = listOf(obra.registros.single().copy(texto = "")))))
        assertEquals("diario", RedSocial.publicacionesDe(e).single().tipo)
    }

    @Test fun limitesDelCompositor() {
        val r = Resena("n:1", "obra", texto = "x".repeat(5000), nota = 0, fotos = List(4) { "$it.jpg" })
        assertTrue(Resenas.valida(r))
        assertTrue(Resenas.valida(r.copy(nota = 10)))
        assertTrue(Resenas.valida(r.copy(nota = null)))
        assertFalse(Resenas.valida(r.copy(texto = "x".repeat(5001))))
        assertFalse(Resenas.valida(r.copy(texto = " ")))
        assertFalse(Resenas.valida(r.copy(nota = -1)))
        assertFalse(Resenas.valida(r.copy(nota = 11)))
        assertFalse(Resenas.valida(r.copy(fotos = List(5) { "$it.jpg" })))
        assertFalse(Resenas.valida(r.copy(fotos = listOf(""))))
        assertFalse(Resenas.valida(r.copy(obra = "")))
    }

    @Test fun partesSegunMedioYEtiquetas() {
        val serie = obra.copy(temporadas = listOf(Temporada(1, 3), Temporada(2, 1)))
        assertEquals(listOf("", "temp:1", "ep:S1E1", "ep:S1E2", "ep:S1E3", "temp:2", "ep:S2E1"), Resenas.partes(serie))
        assertEquals(Resenas.partes(serie), Resenas.partes(serie.copy(tipo = Tipos.ANIME)))
        assertTrue(Resenas.parteValida(serie, "ep:S1E3"))
        assertFalse(Resenas.parteValida(serie, "ep:S9E9"))
        val libro = obra.copy(tipo = Tipos.LIBRO)
        assertTrue(Resenas.parteValida(libro, "cap:12"))
        assertTrue(Resenas.parteValida(libro, "pag:200"))
        assertFalse(Resenas.parteValida(libro, "cap:"))
        assertFalse(Resenas.parteValida(libro, "pag:" + "x".repeat(41)))
        val album = obra.copy(tipo = Tipos.ALBUM, canciones = listOf(Cancion("Canción", mbid = "mbid"), Cancion("Sin id")))
        assertEquals(listOf("", "cancion:mbid", Resenas.parteCancion(album, 1)), Resenas.partes(album))
        assertEquals(listOf(""), Resenas.partes(obra.copy(tipo = Tipos.PELICULA)))
        assertTrue(Resenas.parteValida(obra, "artista:mb:artist:1"))
        assertTrue(Resenas.parteValida(libro, "autor:ol:author:1"))
        assertEquals("T1 · E3", Resenas.etiqueta("ep:S1E3"))
        assertEquals("S1 · E3", Resenas.etiqueta("ep:S1E3", ingles = true))
        assertEquals("T2", Resenas.etiqueta("temp:2"))
        assertEquals("Cap. 12", Resenas.etiqueta("cap:12"))
        assertEquals("Ch. 12", Resenas.etiqueta("cap:12", ingles = true))
        assertEquals("Pág. 200", Resenas.etiqueta("pag:200"))
        assertEquals("Canción", Resenas.etiqueta("cancion:mbid", album))
    }

    @Test fun publicaResenasDeAutoresYArtistas() {
        val a = Artista("a", "Autora", fuente = "ol:author:1", tipo = Personas.AUTOR)
        val e = Estado(artistas = listOf(a), resenas = listOf(Resena("n:1", "a", parte = "autor:ol:author:1", texto = "Opinión", visibilidad = Visibilidad.PUBLICA)))
        val p = RedSocial.publicacionesDe(e).single()
        assertEquals("ol:author:1", p.datos["fuente"]!!.jsonPrimitive.content)
        assertEquals("Autora", p.datos["titulo"]!!.jsonPrimitive.content)
    }
    @Test fun importarConvierteOpinionesSinEscribirLosCamposViejosNiDuplicarlas() {
        val (e, cantidad) = Importar.sumar(Estado(), listOf(obra), 50)
        assertEquals(1, cantidad)
        assertEquals(2, e.resenas.size)
        assertTrue(e.resenas.all { it.id.startsWith("n:") })
        assertEquals("", e.obras.single().resena)
        assertEquals("", e.obras.single().registros.single().texto)
        val (otra, repetidas) = Importar.sumar(e, listOf(obra), 60)
        assertEquals(0, repetidas)
        assertEquals(e.resenas, otra.resenas)
        assertEquals(2, Resenas.migrar(otra).resenas.size)
    }

    @Test fun votosUtilesSeLeenYLosBorradoresSeUnenSinSubir() {
        val p = JsonCajon.decodeFromString(PublicacionSocial.serializer(),
            """{"id":1,"autor":{"usuario":"ana"},"tipo":"resena","cuando":1,"visibilidad":"publica","datos":{},"utiles":3,"yoUtil":true}""")
        assertEquals(3, p.utiles)
        assertTrue(p.yoUtil)
        val r = Resena("n:borrador", "obra", texto = "Original", cambio = 10, visibilidad = Visibilidad.PUBLICA)
        val a = Estado(borradoresResena = listOf(r))
        val b = Estado(borradoresResena = listOf(r.copy(texto = "Editado", cambio = 20)))
        val e = Logica.unir(a, b)
        assertEquals("Editado", e.borradoresResena.single().texto)
        assertTrue(RedSocial.publicacionesDe(e).isEmpty())
    }

    @Test fun subidaNoLeeElTextoDeLosCamposDeRespaldo() {
        val e = Estado(obras = listOf(obra))
        val p = RedSocial.publicacionesDe(e).single()
        assertEquals("diario", p.tipo)
        assertFalse("texto" in p.datos)
        assertFalse(RedSocial.publicacionesDe(e).any { it.clave == "o:obra" })
    }


    @Test fun estado154ConservaResenasPeroNoReescribeCamposViejos() = runTest {
        val json154 = """{"obras":[{"id":"o154","tipo":"pelicula","titulo":"Antigua","resena":"Opinión 1.54","resenaVisibilidad":"publica","registros":[{"id":"g154","fecha":"2026-01-02","texto":"Diario 1.54"}]}]}"""
        val viejo = JsonCajon.decodeFromString(Estado.serializer(), json154)
        val esperado = Resenas.migrar(viejo).resenas
        val salida = java.io.ByteArrayOutputStream()
        SerializadorEstado.writeTo(viejo, salida)
        val guardado = JsonCajon.decodeFromString(Estado.serializer(), salida.toString("UTF-8"))
        assertEquals(esperado, guardado.resenas)
        assertEquals("", guardado.obras.single().resena)
        assertEquals("", guardado.obras.single().resenaVisibilidad)
        assertEquals("", guardado.obras.single().registros.single().texto)
        assertEquals(esperado, SerializadorEstado.readFrom(ByteArrayInputStream(salida.toByteArray())).resenas)
    }
}
