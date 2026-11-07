package com.safetylog.app.ui.auth

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.ui.theme.*

@Composable
internal fun KeystoreDiagnosticsCard(
    uiState: AuthUiState,
    onRunDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StatusPassedLight.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            tint = StatusPassed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Android Keystore Security",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.keystoreDiagnostics?.securityLevel ?: "Hardware TEE / MasterKey Enclave",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = StatusPassed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, StatusPassed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StatusPassed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.keystoreDiagnostics?.isFunctional == true) "AES-256 ACTIVE" else "INITIALIZING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StatusPassed
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Cryptographic specs grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SecurityMetricItem(label = "Cipher Engine", value = "AES-256-GCM", subValue = "Hardware Authenticated")
                SecurityMetricItem(
                    label = "Key Protection",
                    value = uiState.keystoreDiagnostics?.securityLevel?.split(" ")?.firstOrNull() ?: "TEE / Hardware",
                    subValue = "Non-Exportable"
                )
                SecurityMetricItem(label = "Tamper Audit", value = "SHA-256 Checked", subValue = "Constant-Time")
            }

            // Diagnostic results box if diagnostics available
            uiState.keystoreDiagnostics?.let { diag ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusPassed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (diag.isFunctional) "Hardware Self-Test: PASSED (100% Match)" else "Self-Test Alert",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diag.isFunctional) StatusPassed else StatusDeficient
                                )
                            }
                            Text(
                                text = "${diag.latencyMs} ms",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Master: ${diag.masterKeyAlias.take(24)}... • Direct: ${diag.directCipherAlias}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            // Diagnostic run button
            OutlinedButton(
                onClick = onRunDiagnostics,
                enabled = !uiState.isRunningDiagnostics,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (uiState.isRunningDiagnostics) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying Hardware Cryptography...", fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Keystore Diagnostic Self-Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun SecurityMetricItem(
    label: String,
    value: String,
    subValue: String
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = subValue, style = MaterialTheme.typography.labelSmall, color = StatusPassed, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SessionDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun GoogleOAuthClientIdDialog(
    uiState: AuthUiState,
    context: Context,
    clientIdDraft: String,
    onClientIdDraftChange: (String) -> Unit,
    onSetSandboxMode: (Boolean) -> Unit,
    onSignInSandbox: () -> Unit,
    onSaveClientId: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Cloud OAuth Setup", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To enable official Google Sign-In with real Google accounts on your device, create an OAuth 2.0 Web Client ID in Google Cloud Console and paste it below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = clientIdDraft,
                    onValueChange = onClientIdDraftChange,
                    label = { Text("Web Client ID (.apps.googleusercontent.com)") },
                    placeholder = { Text("e.g. 123456789-abc.apps.googleusercontent.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Google Cloud Registration Parameters:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Package: com.safetylog.inspection.app", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Release SHA-1: 62:FC:B5...EE:21", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("62:FC:B5:63:A9:32:68:10:95:77:C7:78:6D:01:8F:34:14:74:EE:21"))
                                    Toast.makeText(context, "Copied Release SHA-1", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Copy", fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Debug SHA-1: 0E:58:7D...51:34", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("0E:58:7D:5D:00:F0:17:83:73:A2:72:C4:25:1D:29:AC:DD:CD:51:34"))
                                    Toast.makeText(context, "Copied Debug SHA-1", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Copy", fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = "Important for Play Store / Testers: If installed via Play Store track, copy the SHA-1 from Google Play Console > App Integrity > Play App Signing and add it to Google Cloud Console.",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mode Switch: Live vs Sandbox
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isSandboxMode) SafetyOrange.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (uiState.isSandboxMode) SafetyOrange.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Force Emulator Sandbox Mode", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = "Bypasses Google Play Services and simulates a verified local audit session for testing.",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = uiState.isSandboxMode, onCheckedChange = onSetSandboxMode)
                    }
                }

                // Direct sandbox login button inside dialog
                Button(
                    onClick = {
                        onSignInSandbox()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign In Now (Offline Sandbox)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "When in Sandbox mode or when offline, the app safely signs in using your active local Profile name and email.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveClientId(clientIdDraft.trim())
                    onDismiss()
                }
            ) {
                Text("Save Client ID")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().height(52.dp).shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, Color(0xFFDADCE0))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF4285F4))
            } else {
                GoogleLogoIcon(modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign in with Google", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1F1F1F), letterSpacing = 0.2.sp)
            }
        }
    }
}

@Composable
fun MicrosoftSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.fillMaxWidth().height(52.dp).shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2F2F2F),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MicrosoftLogoIcon(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Sign in with Microsoft", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, letterSpacing = 0.2.sp)
        }
    }
}

/**
 * Precision vector drawing of Google 4-color 'G' logo
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val blue = Color(0xFF4285F4)
        val green = Color(0xFF34A853)
        val yellow = Color(0xFFFBBC05)
        val red = Color(0xFFEA4335)

        drawRect(color = blue, topLeft = Offset(w * 0.48f, h * 0.40f), size = Size(w * 0.52f, h * 0.20f))

        val stroke = w * 0.20f
        val arcSize = Size(w - stroke, h - stroke)
        val arcTopLeft = Offset(stroke / 2, stroke / 2)

        drawArc(color = blue, startAngle = -45f, sweepAngle = 75f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = stroke))
        drawArc(color = green, startAngle = 30f, sweepAngle = 105f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = stroke))
        drawArc(color = yellow, startAngle = 135f, sweepAngle = 90f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = stroke))
        drawArc(color = red, startAngle = 225f, sweepAngle = 90f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = stroke))
    }
}

/**
 * Precision drawing of 4-tile Microsoft logo
 */
@Composable
fun MicrosoftLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width
        val tileSize = s * 0.44f
        val gap = s * 0.12f

        drawRect(Color(0xFFF25022), topLeft = Offset(0f, 0f), size = Size(tileSize, tileSize))
        drawRect(Color(0xFF7FBA00), topLeft = Offset(tileSize + gap, 0f), size = Size(tileSize, tileSize))
        drawRect(Color(0xFF00A4EF), topLeft = Offset(0f, tileSize + gap), size = Size(tileSize, tileSize))
        drawRect(Color(0xFFFFB900), topLeft = Offset(tileSize + gap, tileSize + gap), size = Size(tileSize, tileSize))
    }
}
