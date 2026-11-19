package io.github.fogonapps.social.ui

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.fogonapps.social.EnviarContadores

internal fun accionEnvioContadores(contexto: Context): () -> Unit = {
    val trabajo = OneTimeWorkRequestBuilder<EnviarContadores>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(contexto).enqueue(trabajo)
}
