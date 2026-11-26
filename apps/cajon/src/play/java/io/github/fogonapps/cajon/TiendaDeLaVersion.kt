package io.github.fogonapps.cajon

import android.app.Application
import io.github.fogonapps.social.PasarelaPlay

/** La versión de Play vende chispas con Google Play Billing. */
internal fun pasarelaDeLaVersion(app: Application): PasarelaPlay? = PasarelaGooglePlay(app)
