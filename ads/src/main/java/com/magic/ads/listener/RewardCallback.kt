package com.magic.ads.listener

interface RewardCallback : AdCallback {
    fun onUserEarnedReward(rewardType: String, rewardAmount: Int)
}
