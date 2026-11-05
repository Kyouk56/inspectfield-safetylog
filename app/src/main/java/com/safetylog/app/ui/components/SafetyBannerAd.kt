package com.safetylog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Clean Google Mobile Ads (AdMob) Banner component.
 * Strictly standard banner ads only (no interstitial or full-screen popups).
 * If ads cannot be served (no fill, offline, or test environment), collapses cleanly
 * without displaying artificial or broken placeholders.
 * Permanently dismissed for users with Ad-Free license or Pro subscription tiers.
 */
@Composable
fun SafetyBannerAd(
    isAdFree: Boolean,
    onRemoveAdsClicked: () -> Unit,
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-3940256099942544/6300978111" // Google standard test banner ID
) {
    if (isAdFree) return

    var adMobLoaded by remember { mutableStateOf(false) }
    var adMobFailed by remember { mutableStateOf(false) }

    // If ad fails or has no fill, collapse cleanly to preserve clean professional UI
    if (adMobFailed) return

    Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (adMobLoaded) {
                androidx.compose.material3.HorizontalDivider(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (adMobLoaded) 50.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    adMobLoaded = true
                                    adMobFailed = false
                                }

                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    adMobLoaded = false
                                    adMobFailed = true
                                }
                            }
                            try {
                                loadAd(AdRequest.Builder().build())
                            } catch (_: Exception) {
                                adMobFailed = true
                            }
                        }
                    },
                    modifier = Modifier.wrapContentSize()
                )
            }
        }
    }
}
