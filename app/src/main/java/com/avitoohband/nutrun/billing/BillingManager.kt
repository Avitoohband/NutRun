package com.avitoohband.nutrun.billing

import android.app.Activity
import android.content.Context
import com.avitoohband.nutrun.data.AppPreferences
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class BillingUiState(
    val connected: Boolean = false,
    val products: Map<String, ProductDetails> = emptyMap(),
    val pendingVerificationTokens: List<String> = emptyList(),
    val message: String? = null
)

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val verificationService: BillingVerificationService,
    private val preferences: AppPreferences
) {
    private val packageName = context.packageName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases.orEmpty())
            } else {
                mutableState.value = mutableState.value.copy(message = result.debugMessage)
            }
        }
        .build()

    fun connect() {
        if (client.isReady) {
            queryProducts()
            restore()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val connected = result.responseCode == BillingClient.BillingResponseCode.OK
                mutableState.value = mutableState.value.copy(connected = connected)
                if (connected) {
                    queryProducts()
                    restore()
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(connected = false)
            }
        })
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, details ->
            mutableState.value = mutableState.value.copy(
                products = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    details.associateBy(ProductDetails::getProductId)
                } else {
                    emptyMap()
                },
                message = result.debugMessage.takeIf {
                    result.responseCode != BillingClient.BillingResponseCode.OK
                }
            )
        }
    }

    fun launch(activity: Activity, productId: String) {
        val details = mutableState.value.products[productId] ?: run {
            mutableState.value = mutableState.value.copy(
                message = "This subscription is not available from the current Play build."
            )
            return
        }
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        )
    }

    fun restore() {
        if (!client.isReady) {
            connect()
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isEmpty()) refreshEntitlement()
                else processPurchases(purchases)
            } else {
                mutableState.value = mutableState.value.copy(message = result.debugMessage)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val purchased = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val tokens = purchased.map(Purchase::getPurchaseToken)
        mutableState.value = mutableState.value.copy(
            pendingVerificationTokens = tokens,
            message = if (tokens.isEmpty()) {
                "Purchase is still pending."
            } else {
                "Verifying subscription..."
            }
        )
        purchased.forEach { purchase ->
            scope.launch {
                runCatching {
                    val productId = purchase.products.firstOrNull()
                        ?: error("The Play purchase did not include a product.")
                    verificationService.verify(packageName, productId, purchase.purchaseToken)
                }.onSuccess { entitlement ->
                    preferences.currentSession().authenticatedUserId?.let { userId ->
                        preferences.setSubscriber(userId, entitlement.subscriber)
                    }
                    if (entitlement.acknowledged && !purchase.isAcknowledged) {
                        acknowledgeAfterServerVerification(purchase.purchaseToken)
                    }
                    mutableState.value = mutableState.value.copy(
                        pendingVerificationTokens =
                            mutableState.value.pendingVerificationTokens - purchase.purchaseToken,
                        message = if (entitlement.subscriber) {
                            "Subscription active. Ads are now disabled."
                        } else {
                            "This subscription is not currently active."
                        }
                    )
                }.onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        message = error.message ?: "Purchase verification failed. Try Restore."
                    )
                }
            }
        }
    }

    private fun refreshEntitlement() {
        scope.launch {
            runCatching { verificationService.entitlement() }
                .onSuccess { entitlement ->
                    preferences.currentSession().authenticatedUserId?.let { userId ->
                        preferences.setSubscriber(userId, entitlement.subscriber)
                    }
                    mutableState.value = mutableState.value.copy(
                        pendingVerificationTokens = emptyList(),
                        message = if (entitlement.subscriber) {
                            "Subscription restored."
                        } else {
                            "No active Play subscription found."
                        }
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        message = error.message ?: "Could not refresh subscription."
                    )
                }
        }
    }

    private fun acknowledgeAfterServerVerification(purchaseToken: String) {
        if (!client.isReady) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(message = result.debugMessage)
            }
        }
    }

    companion object {
        const val MONTHLY = "nutrun_ad_free_monthly"
        const val ANNUAL = "nutrun_ad_free_annual"
        val PRODUCT_IDS = listOf(MONTHLY, ANNUAL)
    }
}
