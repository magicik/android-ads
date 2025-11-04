# android-ads
* ***Native Ads***
**1. Thêm vào layout XML**

Chèn FrameLayout (hoặc container bạn muốn) vào file layout của Activity/Fragment:

`<FrameLayout
android:id="@+id/nativeAd"
android:layout_width="match_parent"
android:layout_height="wrap_content"
app:layout_constraintStart_toStartOf="parent"
app:layout_constraintEnd_toEndOf="parent"/>`


Lưu ý: bạn có thể đổi ConstraintLayout attributes tùy vào parent layout của bạn.

**2. Khởi tạo trong Activity / Fragment**

Ví dụ khởi tạo NativeAdManager và load vào container (binding.nativeAd):

`// khởi tạo nativeAdManager (ví dụ trong onCreate của Activity hoặc onViewCreated của Fragment)
nativeAdManager = NativeAdManager(
context = this, // hoặc requireContext() nếu trong Fragment
remoteConfigProvider = (application as TestAdsApplication).remoteConfigProvider,
admobUnit = AdMob.NATIVE_AD_UNIT,
maxUnit = Max.NATIVE_AD_UNIT,
admobViewFactory = { ctx ->
val tv = AdmobTemplateView(ctx)
// tùy chọn: set template nếu muốn dùng layout template tùy chỉnh
tv.setTemplate(R.layout.template_view_medium_native_ads) // optional; nếu không set sẽ dùng default
tv
},
maxViewFactory = { ctx ->
MaxTemplateView(ctx)
}
)

// load quảng cáo vào FrameLayout (giả sử bạn dùng ViewBinding)
nativeAdManager.loadInto(binding.nativeAd)`

Giải thích ngắn:

admobViewFactory / maxViewFactory: factory để trả về View hiển thị quảng cáo tương ứng.

AdmobViewFactory:
    Sử dụng template có sẵn là AdmobViewTemplate và
        setTemplate(...): Có thể sử dụng 3 template có sẵn trong thư viện là template_view_large_native_ads, template_view_medium_native_ads, template_view_small_native_ads
        hoặc có thể custom layout theo design từ module app rồi truyền vào
    Tạo 1 view class kế thừa AdMobNativeViewBinder( thay cho AdobTemplateView)   
MaxViewFactory:
    - Sử dụng MaxTemplateView
    - Tạo class kế thừa MaxNativeViewBinder(thay cho MaxTemplateView)
loadInto(...): method để NativeAdManager tự thêm view quảng cáo vào container.

**3. Xử lý onDestroy — giải phóng tài nguyên quảng cáo**

Khi Activity/Fragment bị huỷ, cần gọi destroy() cho các view quảng cáo con (nếu view đó có API destroy) để tránh leak memory:

override fun onDestroy() {
// destroy child ad resources
val c = findViewById<FrameLayout>(R.id.nativeAd)
for (i in 0 until c.childCount) {
val ch = c.getChildAt(i)
when (ch) {
is AdmobTemplateView -> ch.destroy()
is MaxTemplateView -> ch.destroy()
// nếu có view quảng cáo khác có method destroy() thì xử lý tương tự
}
}
super.onDestroy()
}
* ***Reward Ads***
Khởi tạo ở activity
  `rewardManager = RewardAdManagerImpl(
  context = this,
  admobUnit = AdMob.REWARDED_AD_UNIT,
  maxUnit = Max.REWARDED_AD_UNIT,
  remoteConfig = (application as TestAdsApplication).remoteConfigProvider,
  )
  rewardManager.load(this)`
Show reward
  `binding.btnRewardAds.setOnClickListener {
  rewardManager.show(this, object : RewardShowListener {
  override fun onUserEarnedReward(amount: Int, type: String) {
  Log.d("TestAdsActivity", "Earn reward")
  }

                override fun onAdClosed() {
                    Log.d("TestAdsActivity", "Reward closed")
                }

                override fun onShowFailed(error: String?) {
                    Log.d("TestAdsActivity", "Reward show false")
                }

            })
        }`
