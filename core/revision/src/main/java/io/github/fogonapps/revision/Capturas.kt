package io.github.fogonapps.revision

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import java.io.File

fun ComposeTestRule.guardarCaptura(nombre: String, variante: String) {
    val archivo = File("build/reports/capturas-pantallas/$nombre-$variante.png")
    archivo.parentFile.mkdirs()
    archivo.outputStream().use {
        check(onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
