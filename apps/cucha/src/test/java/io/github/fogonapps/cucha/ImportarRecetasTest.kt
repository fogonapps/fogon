package io.github.fogonapps.cucha

import io.github.fogonapps.cucha.datos.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = CuchaApp::class)
class ImportarRecetasTest {
    private fun preparar(): CuchaViewModel {
        val app = RuntimeEnvironment.getApplication() as CuchaApp
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val vm = CuchaViewModel(app)
        // El estado cargado se fija como en el recorrido de pantallas, sin esperar a disco ni a la red.
        @Suppress("UNCHECKED_CAST")
        val estado = CuchaViewModel::class.java.getDeclaredField("_estado").apply { isAccessible = true }.get(vm) as MutableStateFlow<Estado?>
        estado.value = Estado(listo = true, recetas = listOf(Receta("existente", "Sopa propia", propia = true)))
        return vm
    }

    @Test fun importaFormatoDeRecetasSinSobrescribirLaExistente() {
        val vm = preparar()
        val receta = Receta("existente", "Piña, ñame y café", pasosEs = listOf("Decir \"sí\"\nCocinar"), nota = "Mañana")
        assertEquals(1, vm.importarRecetas(JsonCucha.encodeToString(kotlinx.serialization.builtins.ListSerializer(Receta.serializer()), listOf(receta))))
        val recetas = vm.estado.value!!.recetas
        assertEquals(2, recetas.size)
        assertEquals("Sopa propia", recetas.first().es)
        assertNotEquals(recetas.first().id, recetas.last().id)
        assertEquals(receta.copy(id = recetas.last().id, propia = true), recetas.last())
    }

    @Test fun rechazaDanadosVaciosYAjenosSinPerderRecetas() {
        val vm = preparar()
        val antes = vm.estado.value
        for (texto in listOf("", "[", "[]", "{}", "null", "[{\"Title\":\"Libro\"}]", "[{\"id\":\"r1\",\"es\":\" \"}]")) {
            assertEquals(0, vm.importarRecetas(texto))
            assertEquals(antes, vm.estado.value)
        }
    }
}
