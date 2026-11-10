package io.github.fogonapps.social.ui
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource

@Composable
fun GraciaBorrado(borrarEl: Long, decidir: (Boolean) -> Unit) {
if (borrarEl > 0) AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.social_red_por_borrar)) },
                text = { Text(stringResource(R.string.social_red_conservar_pregunta, java.text.DateFormat.getDateInstance().format(java.util.Date(borrarEl)))) },
                confirmButton = { TextButton({ decidir(true) }) { Text(stringResource(R.string.social_red_conservar)) } },
                dismissButton = { TextButton({ decidir(false) }) { Text(stringResource(R.string.social_red_seguir_borrado)) } },
            )
}
