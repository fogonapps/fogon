package io.github.fogonapps.cajon

import io.github.fogonapps.cajon.datos.Cifrado
import io.github.fogonapps.cajon.datos.JsonCajon
import io.github.fogonapps.cajon.datos.LlavesPublicas
import org.junit.Assert.*
import org.junit.Test

class CifradoCompatibleTest {
    @Test fun adaptadorYPrimitivaCompartenSobresYFormatoDeLlaves() {
        val ana = Cifrado.nuevas()
        val beto = io.github.fogonapps.social.Cifrado.nuevas()
        val publicasAna = Cifrado.publicas(ana)
        val publicasBeto = io.github.fogonapps.social.Cifrado.publicas(beto)
        val (sobre, firma) = Cifrado.cerrar("mensaje".encodeToByteArray(), "ana", "beto",
            LlavesPublicas(publicasBeto.cifrar, publicasBeto.firmar), ana)
        assertEquals("mensaje", io.github.fogonapps.social.Cifrado.abrir(sobre, firma, "ana", "beto",
            io.github.fogonapps.social.LlavesPublicas(publicasAna.cifrar, publicasAna.firmar), beto).decodeToString())
        val comunes = io.github.fogonapps.social.Cifrado.Propias(ana.cifrar, ana.firmar)
        assertEquals(JsonCajon.encodeToString(Cifrado.Propias.serializer(), ana),
            JsonCajon.encodeToString(io.github.fogonapps.social.Cifrado.Propias.serializer(), comunes))
        assertEquals(Cifrado.Propias.serializer().descriptor.serialName,
            io.github.fogonapps.social.Cifrado.Propias.serializer().descriptor.serialName)
    }
}
