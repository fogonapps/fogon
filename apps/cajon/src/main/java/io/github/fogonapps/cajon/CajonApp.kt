package io.github.fogonapps.cajon

import android.app.Application
import androidx.datastore.core.DataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import io.github.fogonapps.cajon.datos.CajaFuerte
import io.github.fogonapps.cajon.datos.Estado
import io.github.fogonapps.cajon.datos.crearAlmacen

/** Objetos únicos por proceso: DataStore exige una sola instancia por archivo. */
class CajonApp : Application(), SingletonImageLoader.Factory {
    val almacen: DataStore<Estado> by lazy { crearAlmacen(this) }
    val caja: CajaFuerte by lazy { CajaFuerte(this) }

    /** Las portadas se guardan en la caché del teléfono (hasta 150 MB) para no bajarlas cada vez. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("portadas")).maxSizeBytes(150L * 1024 * 1024).build() }
        // Wikimedia (fotos de artistas) y MusicBrainz piden que cada app se identifique; sin esto, rechazan la descarga.
        .components {
            add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = {
                okhttp3.OkHttpClient.Builder().addInterceptor { c ->
                    c.proceed(c.request().newBuilder().header("User-Agent", "ElCajon-Fogon/${BuildConfig.VERSION_NAME} (fogonapps@gmail.com)").build())
                }.build()
            }))
        }
        .crossfade(true)
        .build()
}
