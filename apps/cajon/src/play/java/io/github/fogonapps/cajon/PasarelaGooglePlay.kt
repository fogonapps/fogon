package io.github.fogonapps.cajon

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.fogonapps.social.CompraPlay
import io.github.fogonapps.social.PasarelaPlay
import io.github.fogonapps.social.ResultadoPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play Billing para la versión de Play. Solo habla con Google: verificar y acreditar lo hace
 * [io.github.fogonapps.social.TiendaPlay] con el servidor de Fogón, y esta clase consume una compra solo cuando
 * el servidor ya la acreditó.
 */
internal class PasarelaGooglePlay(app: Application) : PasarelaPlay {
    override val paquete: String = app.packageName
    private var receptor: (ResultadoPlay) -> Unit = {}
    private val detalles = mutableMapOf<String, ProductDetails>()
    private val conectando = Mutex()

    private val cliente: BillingClient = BillingClient.newBuilder(app)
        .setListener { resultado, compras -> receptor(traducir(resultado, compras)) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun escuchar(receptor: (ResultadoPlay) -> Unit) { this.receptor = receptor }

    /** Conecta con Play si hace falta; false si Play no está (teléfono sin Play Store o cuenta sin Google). */
    private suspend fun conectado(): Boolean = conectando.withLock {
        if (cliente.isReady) return@withLock true
        suspendCancellableCoroutine { sigue ->
            cliente.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(resultado: BillingResult) {
                    if (sigue.isActive) sigue.resume(resultado.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() { if (sigue.isActive) sigue.resume(false) }
            })
        }
    }

    override suspend fun precios(ids: List<String>): Map<String, String>? {
        if (ids.isEmpty() || !conectado()) return null
        val productos = ids.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        val r = cliente.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(productos).build())
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        val lista = r.productDetailsList.orEmpty()
        synchronized(detalles) { lista.forEach { detalles[it.productId] = it } }
        return lista.mapNotNull { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it } }.toMap()
    }

    override suspend fun comprar(actividad: Activity, producto: String, cuenta: String): ResultadoPlay? {
        if (!conectado()) return ResultadoPlay.NoDisponible
        val d = synchronized(detalles) { detalles[producto] } ?: run { precios(listOf(producto)); synchronized(detalles) { detalles[producto] } }
            ?: return ResultadoPlay.NoDisponible
        val parametros = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .setObfuscatedAccountId(cuenta)
            .build()
        val r = withContext(Dispatchers.Main) { cliente.launchBillingFlow(actividad, parametros) }
        // Abierto: el resultado llega al receptor. Si no se abrió, se dice por qué.
        return if (r.responseCode == BillingClient.BillingResponseCode.OK) null else traducir(r, null)
    }

    override suspend fun sinCerrar(): List<CompraPlay>? {
        if (!conectado()) return null
        val r = cliente.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return r.purchasesList.mapNotNull(::compra)
    }

    override suspend fun cerrar(token: String, unico: Boolean): Boolean {
        if (!conectado()) return false
        val r = if (unico) cliente.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
            else cliente.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(token).build()).billingResult
        return r.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun compra(p: Purchase): CompraPlay? {
        val producto = p.products.firstOrNull() ?: return null
        return when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> CompraPlay(producto, p.purchaseToken, pendiente = false, reconocida = p.isAcknowledged)
            Purchase.PurchaseState.PENDING -> CompraPlay(producto, p.purchaseToken, pendiente = true)
            else -> null
        }
    }

    private fun traducir(r: BillingResult, compras: List<Purchase>?): ResultadoPlay = when (r.responseCode) {
        BillingClient.BillingResponseCode.OK -> ResultadoPlay.Compras(compras.orEmpty().mapNotNull(::compra))
        BillingClient.BillingResponseCode.USER_CANCELED -> ResultadoPlay.Cancelada
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> ResultadoPlay.YaTenia
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> ResultadoPlay.NoDisponible
        else -> ResultadoPlay.Fallo
    }
}
