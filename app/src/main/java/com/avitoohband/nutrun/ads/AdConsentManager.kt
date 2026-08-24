package com.avitoohband.nutrun.ads

import android.app.Activity
import android.content.Context
import com.avitoohband.nutrun.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdConsentEntryPoint {
    fun adConsentManager(): AdConsentManager
}

@Singleton
class AdConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mobileAdsInitialized = false

    fun requestConsentAndInitializeAds(activity: Activity) {
        if (!BuildConfig.PRODUCTION_ADS_CONFIGURED) {
            initializeMobileAdsIfNeeded()
            return
        }
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                    { initializeMobileAdsIfNeeded() }
                )
            },
            { initializeMobileAdsIfNeeded() }
        )
    }

    fun canRequestAds(): Boolean {
        if (!BuildConfig.PRODUCTION_ADS_CONFIGURED) return true
        return UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }

    private fun initializeMobileAdsIfNeeded() {
        if (mobileAdsInitialized) return
        MobileAds.initialize(context)
        mobileAdsInitialized = true
    }
}
