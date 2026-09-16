# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`android-ads` is a two-module Gradle/Android project:

- **`:ads`** (`com.magic.ads`) — a standalone Android library implementing an ad-serving abstraction over AdMob and AppLovin MAX (interstitial, rewarded, app-open, banner, native). This is the actual product; it's meant to be reused across multiple apps.
- **`:app`** (`com.magic.example`) — a demo/example app (`TestAdsActivity`) that exercises every format in `:ads` end-to-end. Not a real product, just a live wiring test.

## Commands

```bash
./gradlew :ads:compileDebugKotlin   # fastest check while iterating on library code (no resource/manifest work)
./gradlew :ads:assembleDebug        # full library build including resource linking
./gradlew :app:assembleDebug        # full build of both modules — the real end-to-end check, since :app
                                     # depends on :ads and exercises its public API + XML resources
./gradlew test                      # JVM unit tests (only template stubs exist today, no real test suite)
./gradlew connectedAndroidTest       # instrumented tests (requires a device/emulator; also just stubs)
```

There is no lint/ktlint/detekt config in this repo — don't assume one exists.

`./gradlew :app:assembleDebug` is the most meaningful smoke test: `:ads` alone can compile with a broken public API (e.g. resource ID typos only surface during `:app`'s resource linking/binding step), so always build `:app`, not just `:ads`, before considering a change to the library done.

## Architecture (`:ads`)

### Waterfall + pool (`core/AdPool.kt`, `core/AdWaterfallLoader.kt`, `core/AdWrapper.kt`, `core/GlobalFullScreenAdCap.kt`)

**As of the `feature/waterfall-pool` branch, the old "no waterfall" constraint is gone.** Every placement is keyed by an app-supplied `placementKey: String` (constructor arg on each `format/*` manager) and can list multiple ad units (`PlacementConfig.listAds`, tried in order) plus an optional no-timeout "high floor" unit tried first (`PlacementConfig.highFloor`). `AdPool` is the single object all five format managers delegate to:

- **Waterfall**: `AdWaterfallLoader` tries each `AdUnitConfig` in `listAds` order, per-attempt timeout = `min(unit's own timeoutMs, remaining PlacementConfig.totalTimeoutMs budget)`.
- **Pool/cache**: successful loads are cached per `placementKey|AdType` (up to `maxAdsPerPlacement`, default 2), evicting lowest-tier/oldest first; a per-`AdType` cap (`maxNativePlacements`/`maxPlacementsPerType`) limits how many *distinct placements* may hold cached ads at once, evicting the whole oldest placement's ads when exceeded. TTL: 55 min, 4h for `APP_OPEN`.
- **Coalescing/throttling**: concurrent loads on the same key while already loading just queue a callback instead of firing a second request; concurrent in-flight loads are capped per `AdType` (`maxConcurrentPerType`), overflow queues and drains as slots free up.
- **Full-screen mutual exclusion** reuses `AdsManager.isShowingFullScreenAd`/`notifyFullScreenAdShowing` (not a separate flag) — `AdPool.showInterstitial`/`showRewarded`/`showAppOpen` claim/release it around the show call.
- **Frequency capping**: `GlobalFullScreenAdCap` (app-wide, not per-placement) gates every full-screen show via `tryConsume`/`recordShown` — min-interval, max-shows/day, "1 in every N attempts". Tune via `AdPool.setFullScreenMinIntervalSeconds/MaxShowsPerDay/OneInEveryN`. While `TestAdGuard.isTestMode` is true, the effective min-interval is floored at 60s regardless of the configured value — a safety net against spamming full-screen test ads during development (this existed in the pre-waterfall `AdsManager.canShowInterstitial()`, interstitial-only; now folded into `GlobalFullScreenAdCap` so it covers Rewarded/AppOpen too).
- Pool sizing knobs: `AdPool.setMaxAdsPerPlacement/setMaxNativePlacements/setMaxPlacementsPerType/setMaxConcurrentPerType`.
- `AdsManager.setMasterAdsEnabled(false)` and `AdsManager.setPremium(context, true)` both purge everything cached in the pool (`AdPool.removeAllAds()`), in addition to their existing effects.
- `AdsOptions` (data class + `Builder`, ported near-verbatim from the reference) bundles every tuning knob above (`maxAdsPerPlacement`, `maxNativePlacements`, `maxPlacementsPerType`, `maxConcurrentPerType`, `fullScreenMinIntervalSeconds`, `fullScreenMaxShowsPerDay`, `fullScreenOneInEveryN`, `masterAdsEnabled`) into one object apps can build once and hand to `AdsManager.applyOptions(options)`, instead of calling each individual setter. Purely a convenience wrapper — every setter it delegates to already existed and still works standalone.

This was ported from a reference library's `AdPool`/`AdWaterfallLoader`/`GlobalFullScreenAdCap` (`D:\Workspace\VongCho\color-pop-gp40\ads`), adapted to this library's `AdSdkProvider`/`AdsManager`/`TestAdGuard` shapes. Two deliberate deviations from that reference, both because this library still has real per-format wrapper classes (the reference doesn't):
- `AdPool.consumeNative` **removes** the cached `NativeAd` from the pool (the reference's generic engine only *peeks* on a cache hit) — required because AdMob forbids binding one `NativeAd` to more than one view; peeking would risk handing the same ad to two `NativeAdManager` instances.
- `AdPool.getHeldBannerAd` exists so `BannerAdManager` can retrieve the ad view reference for `destroyBannerAd` later — the reference has no per-format banner wrapper to need this.

`config/PlacementConfig.kt`'s JSON schema changed accordingly (breaking change, see below) — apps must supply `list_ads` instead of `admob_unit`/`max_unit`.

### Provider pattern (`provider/`)

`AdSdkProvider` is the single interface both `AdMobProvider` and `MaxProvider` implement. Format managers in `format/` only ever talk to `AdsManager.activeProvider` — no format manager imports AdMob or MAX SDK types directly (native ads are the one exception, see below). The ad object crossing the provider boundary is untyped (`Any`) since each provider returns its own SDK type.

Apps register providers and pick the active one explicitly:

```kotlin
AdsManager.registerProvider(AdMobProvider())
AdsManager.registerProvider(MaxProvider())
AdsManager.setActiveProvider("admob")   // app-supplied string, e.g. from its own remote config
AdsManager.initialize(context, AdsConfig.Builder().setMaxSdkKey(...).build())
```

`AdsManager` never fetches or interprets remote config itself — `setActiveProvider`/`PlacementConfig.fromJson` both just take values the app already resolved from wherever it keeps config (Firebase, its own backend, hardcoded).

### Placement config (`config/PlacementConfig.kt`)

`PlacementConfig.fromJson(json: String)` parses:
```json
{
  "enable": true,
  "total_timeout_ms": 15000,
  "list_ads": [
    { "enable_ad": true, "type": "admob", "timeout_ms": 10000, "adunit": "..." }
  ],
  "high_floor": { "enable": false, "adunit": "" }
}
```
`list_ads` is tried in order by `AdWaterfallLoader` (see above); `type` is parsed but not used to filter by network — this library only ever has one active `AdSdkProvider` at a time, so the app is responsible for supplying ad unit IDs valid for whichever provider is currently active. `high_floor`, if enabled, is tried first with no timeout of its own. The library only ever sees this JSON string; it does not know or care where the app got it. **Breaking change from the old flat `{admob_unit, max_unit}` schema** — any app JSON/remote-config producing `PlacementConfig` needs updating.

### Format managers (`format/`)

`InterstitialAdManager`, `RewardedAdManager`, `OpenAdManager`, `BannerAdManager`, `NativeAdManager` — each takes a `placementKey: String` constructor arg and is an independent, instantiable class (not a singleton), but all now delegate their actual load/show/cache to `core/AdPool.kt` keyed by that `placementKey` (see "Waterfall + pool" above) rather than talking to the provider directly. `NativeAdManager` still holds the currently-displayed `NativeAd` locally (`AdPool.consumeNative` hands it over once) since the rendering/style/shimmer logic (`showAd`, `showLoading`, `bindBuiltInLayout`, `applyStyle`) is unchanged.

`AdsManager.isShowingFullScreenAd` is a shared flag that Interstitial/Rewarded/OpenAdManager all toggle around their `showAd()` calls, so `AppOpenResumeHelper` never stacks an app-open ad on top of one that's already showing.

### App-open: preload-first (`format/OpenAdManager.kt`, `helper/AppOpenResumeHelper.kt`)

This is the one format where reactive loading was explicitly rejected. `AppOpenResumeHelper` never calls `loadAd()` from its resume handler — it only shows an already-cached ad (`if (adManager.isAdReady()) showAd(activity)`). Preloading happens from three separate trigger points instead: once after SDK init (`AdsManager.whenInitialized`), on every `onActivityResumed` (needed because `MaxAppOpenAd` requires a real `Activity` to construct, unlike AdMob's static `Context`-based load), and again immediately after every show finishes. All three call the same `preload()`, which is safe to call redundantly because `OpenAdManager.loadAd()` itself no-ops when already cached or already in flight. If you're touching app-open behavior, preserve this — do not add a load call inside the resume path.

### Native ads: two-tier rendering, AdMob-only (`format/NativeAdManager.kt`, `native_ad/`)

Native is deliberately **not** routed through `AdSdkProvider` — AdMob's `NativeAdView`/click-attribution model and MAX's native view/binder types are different enough that forcing a shared abstraction would leak implementation details. `NativeAdManager.loadAd()` checks `provider.name == "admob"` and fails fast otherwise. Adding MAX native support later means a parallel binder type, not extending the existing interface.

Two ways an app can render a loaded native ad:
- **Tier 1** — `showAd(container, layoutType: NativeLayoutType, style: NativeAdStyle)`: one of four built-in layouts (`native_ad_small.xml` / `native_ad_medium.xml` / `native_ad_large.xml` / `native_ad_fullscreen.xml`), cosmetically restyled (background, corner radius, text colors/appearance, CTA background) via `NativeAdStyle`. All four layouts share the same view-ID contract (`native_ad_card`, `ad_icon`, `ad_headline`, `ad_body`, `ad_media`, `ad_advertiser`, `ad_rating_bar`, `ad_badge`, `ad_call_to_action`, `ad_choices`) so `bindBuiltInLayout()`/`applyStyle()` are one shared implementation — a layout missing any of these views (e.g. `native_ad_medium.xml`/`native_ad_fullscreen.xml` have no `ad_rating_bar`) just gets a null back from that `findViewById` and the corresponding bind step is skipped. `FULLSCREEN` is `match_parent`/`match_parent` (edge-to-edge) unlike the other three's `wrap_content` height — meant for a placement that fills the whole screen (e.g. a native ad used in place of an interstitial); structurally ported from a reference "adlib_native_fullscreen.xml", ids remapped to this contract, badge styled to match this library's MEDIUM/LARGE rather than the reference's own badge style.
- **Tier 2** — `showAd(container, binder: NativeAdViewBinder)`: the app supplies its own layout entirely via `createView()`/`bind()`. The library only manages the `NativeAd` object's lifecycle, never assumes anything about the view hierarchy inside.

`loadAd(context, container, config, layoutType, style, showLoadingPlaceholder = true, callback)` is a convenience overload that also shows a shimmer skeleton (`*_shimmer.xml`, via `com.facebook.shimmer`) in `container` while loading, then swaps to the real ad. The container-less `loadAd(context, config, callback)` overload exists for callers that need to decide timing themselves (e.g. Tier 2 custom binding, or racing a load against a screen's own timeout).

**Auto-refresh**: if `PlacementConfig.refreshSeconds`/`refreshCount` are set (both default 0 = off), `NativeAdManager` silently reloads (`AdPool.loadNative(..., alwaysReload = true)`) and re-binds a currently-shown ad into the same container every `refreshSeconds`, up to `refreshCount` times, then stops. A tick is skipped and rescheduled (not dropped) while the container is detached/not visible, `AdsManager.isShowingFullScreenAd` is true, `DialogVisibilityTracker.isAnyDialogShowing()` is true, or `TestAdGuard.isTestMode` is true (never refresh against test inventory). The cycle is tied to whichever container the ad was last shown in via `View.OnAttachStateChangeListener` — it self-cancels on detach and restarts on every `showAd()` call (both Tier 1 and Tier 2). `destroyCurrentAd()`/`forceRemoveAd()` both stop it. Ported from a reference lib's `NativeAdBinder` refresh cycle; only the refresh mechanism was taken, not that reference's extra layout types (collapsible/countdown/etc. — out of scope, considered "UI flow").

**`goneWithTestMode`**: if set and `TestAdGuard.isTestMode` has already latched true (from an earlier test-ad fill anywhere in the app), `NativeAdManager.loadAd(context, config, callback)` fails the load immediately (`onAdFailedToLoad(-1, "gone_with_test_mode")`) without even attempting a fetch — for a placement the app wants to just not exist once it's known to be running against test inventory, as opposed to `hideTestMode`'s per-load behavior in the reference lib (not ported here — not needed).

### Premium / ad removal (`core/AdRemovalRegistry.kt`)

`BannerAdManager` and `NativeAdManager` register themselves (weakly) with `AdRemovalRegistry` on construction. `AdsManager.setPremium(context, true)` calls `forceRemoveAd()` on every live instance automatically — apps never need to manually tear down banner/native views on every screen when a user upgrades. Interstitial/Rewarded/OpenAdManager don't need this since they have no persistent on-screen view to tear down; they just check `AdsManager.isPremium()` before loading/showing.

### Test-ad detection (`testguard/TestAdGuard.kt`)

Network-agnostic by design — takes primitive values (`valueMicros: Long`, `headline: String?`) rather than AdMob's `AdValue`/`NativeAd` types, so both `AdMobProvider` and `MaxProvider` can report into it without either owning the type. `isEnabled` is derived at `AdsManager.initialize()` time from two signals combined: the build must be non-debuggable (`FLAG_DEBUGGABLE` unset) *and* the app must be installed from the Play Store (`PackageManager.getInstallSourceInfo`/`getInstallerPackageName` == `"com.android.vending"`) — a release-signed APK sideloaded outside the Play Store (e.g. an internal QA build) is treated the same as a debug build, guard off.

`isTestMode` is persisted (SharedPreferences), not an in-memory sticky flag — ported from the reference lib's revenue-based design (`color-pop-gp40`'s `core/TestAdGuard.kt`). It's derived from cumulative state rather than the latest event: `isNativeTest` (set permanently once any native ad's headline matches `isTestHeadline`) OR `totalRevenueMicros <= 0` (starts at 0 on a fresh install, only ever incremented by positive-value paid events — a zero-value fill is a no-op, not evidence either way). `AdsManager.initialize()` calls `TestAdGuard.init(context)` once to load this persisted state before any ad can report into it.

### Consent (`consent/ConsentManager.kt`)

Thin wrapper around Google's User Messaging Platform (UMP) SDK for GDPR/EEA consent — `com.google.android.ump.*` classes are already available transitively via `play-services-ads` (no extra Gradle dependency needed). `gatherConsent(activity, debugGeography?, testDeviceHashedIds, onGathered)` calls `requestConsentInfoUpdate` then `loadAndShowConsentFormIfRequired`, reporting `canRequestAds()` in `onGathered` either way (including on a `FormError`). Also exposes `canRequestAds()`, `isPrivacyOptionsRequired()`, `showPrivacyOptionsForm(activity)` for a later "change my consent choice" entry point (e.g. a Settings screen). This library doesn't decide *when* to gather consent or wire it into `AdsManager.initialize()` — the app calls `gatherConsent` itself, typically before `initialize()`, and only proceeds with ad requests once consent is resolved. No demo wiring in `:app` yet (would need restructuring `TestAdsApplication`'s init order around a real `Activity`, not attempted).

## Known stale docs

`README.md` describes the old pre-rewrite API (`admobViewFactory`, `AdmobViewTemplate`, `MaxTemplateView`, `template_view_*_native_ads.xml`) — none of it matches the current code. Don't treat it as a source of truth; it needs a rewrite.
