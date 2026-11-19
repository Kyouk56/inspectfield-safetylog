package com.safetylog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.safetylog.app.BuildConfig
import com.safetylog.app.ui.theme.SafetyAmber
import com.safetylog.app.ui.theme.SafetyOrange
import com.safetylog.app.ui.theme.Slate200
import com.safetylog.app.ui.theme.Slate400
import com.safetylog.app.ui.theme.Slate800
import com.safetylog.app.ui.theme.Slate900

/**
 * Hybrid Google Mobile Ads (AdMob) & Native Safety Compliance Sponsor Banner component.
 * Strictly standard banner ads only (no interstitial or full-screen popups).
 *
 * Docked inside Scaffold `bottomBar` so `innerPadding` automatically offsets all screen
 * content, lists, forms, and FloatingActionButtons above the banner without covering anything.
 *
 * - Permanently dismissed (`if (isAdFree) return`) for users on any active paid tier:
 *   Ad-Free ($2.99 flat rate), Individual Share & Sync ($0.99/mo), or Enterprise ($0.50/seat/mo).
 * - Always visible for Standard Free users (or when a recurring subscription is cancelled/past-due):
 *   serves live Google Mobile Ads (`AdSize.BANNER`, 320x50 dp) when online/filled, and seamlessly
 *   presents the built-in industrial safety sponsor banner while loading or offline, alongside a
 *   1-tap "No Ads $2.99" upgrade action.
 */
@Composable
fun SafetyBannerAd(
    isAdFree: Boolean,
    onRemoveAdsClicked: () -> Unit,
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID
) {
    if (isAdFree) return

    var adMobLoaded by remember { mutableStateOf(false) }
    var adMobFailed by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Slate900,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalDivider(
                color = Slate800,
                thickness = 1.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. Live Google Mobile Ads AdView (always laid out at 50.dp height so AdMob never fails with 0x0 measurement)
                if (!adMobFailed) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
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
                        onRelease = { adView ->
                            try {
                                adView.destroy()
                            } catch (_: Exception) {}
                        }
                    )
                }

                // 2. Built-in Safety Compliance Sponsor Banner (displayed while AdMob is loading or when offline / no-fill)
                if (!adMobLoaded || adMobFailed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate900)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRemoveAdsClicked() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SafetyAmber)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AD",
                                    color = Color.Black,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = SafetyAmber,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Brooks & Amerex Certified Safety Gear",
                                    color = Slate200,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "NFPA 10 & 72 compliant equipment cabinets",
                                    color = Slate400,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onRemoveAdsClicked,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 32.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "No Ads $2.99",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Compact "Remove Ads ($2.99)" strip shown when live AdMob banner is rendered
            if (adMobLoaded && !adMobFailed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate900)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPONSORED • FREE TIER",
                        color = Slate400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Surface(
                        color = SafetyOrange.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, SafetyOrange.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRemoveAdsClicked() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = SafetyOrange,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Remove Ads ($2.99)",
                                color = SafetyOrange,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
