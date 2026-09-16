package com.magic.ads.consent

import android.app.Activity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * Thin wrapper around Google's User Messaging Platform (UMP) SDK for GDPR/EEA consent. This
 * library never decides *when* to gather consent — call [gatherConsent] wherever the app's own
 * startup flow wants it (typically before [com.magic.ads.core.AdsManager.initialize]), and only
 * proceed with ad requests once [onGathered] reports `canRequestAds = true`.
 */
object ConsentManager {

    private var consentInformation: ConsentInformation? = null

    /**
     * [debugGeography] (one of [ConsentDebugSettings]'s `DEBUG_GEOGRAPHY_*` constants) and
     * [testDeviceHashedIds] are for forcing an EEA/UK consent flow on a test device during
     * development — leave both at their defaults in production.
     */
    fun gatherConsent(
        activity: Activity,
        debugGeography: Int? = null,
        testDeviceHashedIds: List<String> = emptyList(),
        onGathered: (canRequestAds: Boolean) -> Unit
    ) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info

        val paramsBuilder = ConsentRequestParameters.Builder()
        if (debugGeography != null) {
            paramsBuilder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(debugGeography)
                    .apply { testDeviceHashedIds.forEach { addTestDeviceHashedId(it) } }
                    .build()
            )
        }

        info.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _: FormError? ->
                    onGathered(info.canRequestAds())
                }
            },
            { _: FormError -> onGathered(info.canRequestAds()) }
        )
    }

    fun canRequestAds(): Boolean = consentInformation?.canRequestAds() ?: false

    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation?.privacyOptionsRequirementStatus ==
            PrivacyOptionsRequirementStatus.REQUIRED

    /** Re-opens the privacy options form so the user can change their consent choice later
     * (e.g. from a Settings screen) — only meaningful when [isPrivacyOptionsRequired] is true. */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError -> onDismissed(formError) }
    }
}
