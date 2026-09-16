package com.magic.ads.core

data class AdsOptions(
    val maxAdsPerPlacement: Int = 2,
    val maxNativePlacements: Int = 6,
    val maxPlacementsPerType: Int = 2,
    val maxConcurrentPerType: Int = 3,
    val masterAdsEnabled: Boolean = true,
    val fullScreenMinIntervalSeconds: Int = 50,
    val fullScreenMaxShowsPerDay: Int = 0,
    val fullScreenOneInEveryN: Int = 1
) {
    class Builder {
        private var maxAdsPerPlacement = 2
        private var maxNativePlacements = 6
        private var maxPlacementsPerType = 2
        private var maxConcurrentPerType = 3
        private var masterAdsEnabled = true
        private var fullScreenMinIntervalSeconds = 50
        private var fullScreenMaxShowsPerDay = 0
        private var fullScreenOneInEveryN = 1

        fun setMaxAdsPerPlacement(n: Int) = apply { maxAdsPerPlacement = n }

        fun setMaxNativePlacements(n: Int) = apply { maxNativePlacements = n }

        fun setMaxPlacementsPerType(n: Int) = apply { maxPlacementsPerType = n }

        fun setMaxConcurrentPerType(n: Int) = apply { maxConcurrentPerType = n }

        fun setMasterAdsEnabled(enabled: Boolean) = apply { masterAdsEnabled = enabled }

        /** Min gap (giây) giữa 2 lần show bất kỳ của Interstitial/Rewarded/AppOpen, tính toàn app. 0 = không giới hạn. */
        fun setFullScreenMinIntervalSeconds(seconds: Int) = apply { fullScreenMinIntervalSeconds = seconds }

        /** Số lần show tối đa/ngày, gộp chung cho Interstitial/Rewarded/AppOpen toàn app. 0 = không giới hạn. */
        fun setFullScreenMaxShowsPerDay(n: Int) = apply { fullScreenMaxShowsPerDay = n }

        /** Chỉ show 1 lần trong mỗi N lần thử show (toàn app). 1 = không giới hạn. */
        fun setFullScreenOneInEveryN(n: Int) = apply { fullScreenOneInEveryN = n }

        fun build() = AdsOptions(
            maxAdsPerPlacement = maxAdsPerPlacement,
            maxNativePlacements = maxNativePlacements,
            maxPlacementsPerType = maxPlacementsPerType,
            maxConcurrentPerType = maxConcurrentPerType,
            masterAdsEnabled = masterAdsEnabled,
            fullScreenMinIntervalSeconds = fullScreenMinIntervalSeconds,
            fullScreenMaxShowsPerDay = fullScreenMaxShowsPerDay,
            fullScreenOneInEveryN = fullScreenOneInEveryN
        )
    }
}
