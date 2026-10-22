package com.safetylog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.safetylog.app.ui.theme.SafetyAmber
import com.safetylog.app.ui.theme.SafetyOrange
import com.safetylog.app.ui.theme.Slate200
import com.safetylog.app.ui.theme.Slate400
import com.safetylog.app.ui.theme.Slate800
import com.safetylog.app.ui.theme.Slate900

/**
 * Hybrid Google Mobile Ads (AdMob) & Native Safety Banner component.
 * Strictly banner ads only (no full-screen popups).
 * Automatically falls back to the native industrial equipment sponsor banner when offline or ad loading is unavailable.
 * Permanently dismissed for users with Ad-Free ($2.99 flat rate) or subscription tiers ($0.99/mo or $0.50/seat).
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = Slate900,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Slate800),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!adMobFailed) {
                // Google Mobile Ads AdView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (adMobLoaded) 50.dp else 0.dp),
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
                                loadAd(AdRequest.Builder().build())
                            }
                        },
                        modifier = Modifier.wrapContentSize()
                    )
                }
            }

            // Native safety compliance fallback banner (shown when AdMob fails, loading, or offline)
            if (adMobFailed || !adMobLoaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // "AD" badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SafetyAmber)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AD",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
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

                    // Quick Remove Ads Action ($2.99 flat rate)
                    Button(
                        onClick = onRemoveAdsClicked,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Quick shortcut when AdMob is displayed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onRemoveAdsClicked,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Text(
                            text = "Remove Ads ($2.99)",
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
