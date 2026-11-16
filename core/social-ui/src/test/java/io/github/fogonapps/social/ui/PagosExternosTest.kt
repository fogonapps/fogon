package io.github.fogonapps.social.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagosExternosTest {
    @Test fun playNoOfrecePagosExternos() {
        assertFalse(ofrecePagosExternos(tiendaPlay = true))
    }

    @Test fun distribucionHabitualConservaPagosExternos() {
        assertTrue(ofrecePagosExternos(tiendaPlay = false))
    }
}
