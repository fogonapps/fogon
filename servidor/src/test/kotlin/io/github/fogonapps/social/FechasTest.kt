package io.github.fogonapps.social

import kotlin.test.Test
import kotlin.test.assertEquals

class FechasTest {
    @Test fun fechaDeLosCorreos() {
        // 1794062262225 ms = 14:37 del 7 de noviembre de 2026, en UTC.
        assertEquals("7 de noviembre de 2026, 14:37 (UTC)", fechaLegible(1794062262225))
    }
}
