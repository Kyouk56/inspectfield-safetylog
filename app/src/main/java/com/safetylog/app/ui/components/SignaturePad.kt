package com.safetylog.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safetylog.app.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun SignaturePad(
    onSignatureCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var isConfirmed by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase
    val padBg = if (isDark) DarkSurfaceCard else Color(0xFFF8FAFC)
    val inkColor = if (isDark) Color.White else Color(0xFF0F172A)
    val guideColor = if (isDark) Slate400 else Slate500

    fun saveDrawnBitmap() {
        if (paths.isEmpty()) return
        val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 6f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        paths.forEach { path ->
            canvas.drawPath(path.asAndroidPath(), paint)
        }

        val signatureFile = File(context.filesDir, "sig_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(signatureFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        isConfirmed = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSignatureCaptured(signatureFile.absolutePath)
    }

    fun applyDigitalSeal() {
        val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 34f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
        }
        canvas.drawText("Electronically Certified & Signed", 40f, 130f, titlePaint)

        val subPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
            isAntiAlias = true
        }
        val stampDate = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.US).format(java.util.Date())
        canvas.drawText("Audit Timestamp: $stampDate", 40f, 190f, subPaint)

        val file = File(context.filesDir, "sig_seal_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        isConfirmed = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSignatureCaptured(file.absolutePath)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Technician Digital Signature",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isConfirmed) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "Signed & Certified",
                                color = if (isDark) StatusPassedTextDark else StatusPassedText,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDark) StatusPassedTextDark else StatusPassed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isDark) StatusPassedContainerDark else StatusPassedContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(padBg)
                    .border(
                        width = if (isConfirmed) 2.dp else 1.dp,
                        color = if (isConfirmed) StatusPassed else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isConfirmed = false
                                    val path = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath = path
                                    paths.add(path)
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    currentPath = null
                                    saveDrawnBitmap()
                                }
                            )
                        }
                ) {
                    paths.forEach { path ->
                        drawPath(
                            path = path,
                            color = inkColor,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }

                if (paths.isEmpty() && !isConfirmed) {
                    Text(
                        text = "Sign here with finger or stylus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = guideColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (paths.isEmpty() && isConfirmed) {
                    Text(
                        text = "Digital Timestamp Seal Applied",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = StatusPassed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        paths.clear()
                        isConfirmed = false
                        onSignatureCaptured("")
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    enabled = paths.isNotEmpty() || isConfirmed
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }

                if (!isConfirmed) {
                    FilledTonalButton(
                        onClick = { applyDigitalSeal() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Apply Digital Seal", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { saveDrawnBitmap() },
                        enabled = paths.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPassed)
                    ) {
                        Text("Signature Saved", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
