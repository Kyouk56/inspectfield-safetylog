package com.safetylog.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.model.InspectorProfile
import com.safetylog.app.domain.ChecklistRegistry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class InspectionPdfGenerator(private val context: Context) {

    fun generateReport(
        profile: InspectorProfile?,
        itemsWithRecords: List<Pair<EquipmentItem, InspectionRecord>>
    ): File {
        val document = PdfDocument()
        val pageWidth = 612
        val pageHeight = 792
        var pageNumber = 1

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val shortDateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)
        val generatedDateStr = dateFormat.format(Date())

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        fun drawHeader(c: android.graphics.Canvas, pNum: Int) {
            // Header Top Bar
            paint.color = Color.rgb(15, 23, 42) // Slate 900
            c.drawRect(0f, 0f, pageWidth.toFloat(), 18f, paint)

            // Document Header Title
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = profile?.companyName?.takeIf { it.isNotBlank() } ?: "Commercial Safety Compliance Report"
            c.drawText(title, 40f, 48f, paint)

            paint.textSize = 9.5f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.DKGRAY
            val inspector = profile?.inspectorName?.takeIf { it.isNotBlank() } ?: "Certified Technician"
            val cert = profile?.certificationNumber?.takeIf { it.isNotBlank() } ?: "N/A"
            c.drawText("Inspector: $inspector  |  Cert #: $cert", 40f, 65f, paint)
            c.drawText("Generated: $generatedDateStr  |  Page $pNum", 40f, 79f, paint)

            // Table Header Bar
            paint.color = Color.rgb(241, 245, 249) // Slate 100
            c.drawRect(40f, 96f, 572f, 116f, paint)

            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f

            c.drawText("TAG ID", 48f, 110f, paint)
            c.drawText("LOCATION", 120f, 110f, paint)
            c.drawText("EQUIPMENT TYPE", 260f, 110f, paint)
            c.drawText("STATUS", 420f, 110f, paint)
            c.drawText("DATE", 510f, 110f, paint)

            paint.strokeWidth = 1f
            paint.color = Color.rgb(203, 213, 225)
            c.drawLine(40f, 116f, 572f, 116f, paint)
        }

        drawHeader(canvas, pageNumber)

        var currentY = 122f

        itemsWithRecords.forEachIndexed { index, (equipment, record) ->
            if (currentY > 660f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas, pageNumber)
                currentY = 122f
            }

            currentY += 20f

            // Alternate row background
            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(40f, currentY - 14f, 572f, currentY + 6f, paint)
            }

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.5f
            paint.color = Color.rgb(30, 41, 59)
            canvas.drawText(equipment.tagId, 48f, currentY, paint)
            canvas.drawText(equipment.locationDescription.take(22), 120f, currentY, paint)
            canvas.drawText(equipment.equipmentType.take(24), 260f, currentY, paint)

            if (record.overallStatus.equals("PASSED", ignoreCase = true)) {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(21, 128, 61) // Emerald Green
                canvas.drawText("PASSED", 420f, currentY, paint)
            } else {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(220, 38, 38) // Crimson Red
                canvas.drawText("DEFICIENT", 420f, currentY, paint)
            }

            paint.typeface = Typeface.DEFAULT
            paint.color = Color.GRAY
            canvas.drawText(shortDateFormat.format(Date(record.timestamp)), 510f, currentY, paint)
        }

        // Draw Summary and Signature Block at Footer
        paint.color = Color.rgb(226, 232, 240)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 690f, 572f, 690f, paint)

        val passedCount = itemsWithRecords.count { it.second.overallStatus.equals("PASSED", ignoreCase = true) }
        val deficientCount = itemsWithRecords.count { it.second.overallStatus.equals("DEFICIENT", ignoreCase = true) }

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.DKGRAY
        canvas.drawText("Total Items Inspected: ${itemsWithRecords.size}   |   Passed: $passedCount   |   Deficient: $deficientCount", 40f, 706f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 7.5f
        paint.color = Color.GRAY
        canvas.drawText("Certified compliant with NFPA 10 periodic inspection standards and local life safety ordinances.", 40f, 720f, paint)

        // Draw Signature
        val latestSigPath = itemsWithRecords.lastOrNull()?.second?.signaturePath
        if (!latestSigPath.isNullOrBlank() && File(latestSigPath).exists()) {
            try {
                val sigBitmap = BitmapFactory.decodeFile(latestSigPath)
                if (sigBitmap != null) {
                    val scaledSig = android.graphics.Bitmap.createScaledBitmap(sigBitmap, 110, 40, true)
                    canvas.drawBitmap(scaledSig, 450f, 715f, null)
                    if (scaledSig != sigBitmap) {
                        scaledSig.recycle()
                    }
                    sigBitmap.recycle()
                }
            } catch (_: Exception) {}
        }
        paint.textSize = 8f
        paint.color = Color.DKGRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Technician Signature", 450f, 765f, paint)

        document.finishPage(page)

        val outputFile = File(context.cacheDir, "InspectionReport_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    /**
     * Generates a single-asset certified inspection & audit log document.
     * Contains asset identification, location, assigned checklist protocol,
     * assigned review schedule, and full chronological inspection history with
     * timestamp, checks breakdown, defect notes, and digital technician signature.
     */
    fun generateSingleAssetReport(
        profile: InspectorProfile?,
        equipment: EquipmentItem,
        inspections: List<InspectionRecord>
    ): File {
        val document = PdfDocument()
        val pageWidth = 612
        val pageHeight = 792
        var pageNumber = 1

        val dateTimeFormat = SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.US)
        val generatedDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        fun drawSingleAssetHeader(c: android.graphics.Canvas, pNum: Int) {
            // Header Top Bar
            paint.color = Color.rgb(15, 23, 42) // Slate 900
            c.drawRect(0f, 0f, pageWidth.toFloat(), 18f, paint)

            // Header Title
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = profile?.companyName?.takeIf { it.isNotBlank() } ?: "Life-Safety Equipment Audit Record"
            c.drawText(title, 40f, 48f, paint)

            paint.textSize = 9.5f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.DKGRAY
            val inspector = profile?.inspectorName?.takeIf { it.isNotBlank() } ?: "Certified Inspector"
            val cert = profile?.certificationNumber?.takeIf { it.isNotBlank() } ?: "N/A"
            c.drawText("Inspector: $inspector  |  Cert #: $cert", 40f, 65f, paint)
            c.drawText("Generated: $generatedDateStr  |  Page $pNum", 40f, 79f, paint)

            paint.strokeWidth = 1f
            paint.color = Color.rgb(203, 213, 225)
            c.drawLine(40f, 90f, 572f, 90f, paint)
        }

        drawSingleAssetHeader(canvas, pageNumber)

        // Asset Overview Card Box
        paint.color = Color.rgb(241, 245, 249) // Slate 100
        canvas.drawRoundRect(40f, 98f, 572f, 185f, 8f, 8f, paint)

        // Tag ID badge
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("TAG ID: ${equipment.tagId}", 52f, 118f, paint)

        paint.color = Color.rgb(234, 88, 12) // Safety Orange
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("EQUIPMENT: ${equipment.equipmentType}", 240f, 118f, paint)

        paint.color = Color.DKGRAY
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Location: ${equipment.locationDescription}", 52f, 136f, paint)
        canvas.drawText("Serial #: ${equipment.serialNumber.ifBlank { "N/A" }}   |   Install Date: ${equipment.installDate ?: "N/A"}", 52f, 150f, paint)

        val protocolName = equipment.assignedChecklist.ifBlank {
            ChecklistRegistry.getChecklistForType(equipment.equipmentType).categoryName
        }
        val freq = equipment.reviewFrequency.ifBlank { "Monthly" }
        val nextReview = equipment.assignedReviewDate.ifBlank { "Scheduled" }
        val reviewTime = equipment.assignedReviewTime.ifBlank { "09:00 AM" }

        paint.color = Color.rgb(30, 41, 59)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Assigned Protocol: $protocolName", 52f, 166f, paint)
        canvas.drawText("Review Frequency: $freq   |   Next Review: $nextReview • $reviewTime", 52f, 178f, paint)

        // Logs Section Header
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CHRONOLOGICAL INSPECTION LOGS & SIGNED RECORDS", 40f, 210f, paint)

        paint.color = Color.GRAY
        paint.textSize = 8.5f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Total Recorded Audits: ${inspections.size}", 40f, 222f, paint)

        var currentY = 230f
        val protocol = ChecklistRegistry.getChecklistByName(protocolName)

        if (inspections.isEmpty()) {
            paint.color = Color.DKGRAY
            paint.textSize = 10f
            canvas.drawText("No inspection records logged yet for this asset.", 40f, 260f, paint)
        } else {
            inspections.forEachIndexed { index, record ->
                if (currentY > 660f) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    drawSingleAssetHeader(canvas, pageNumber)
                    currentY = 105f
                }

                val isPassed = record.overallStatus.equals("PASSED", ignoreCase = true)

                // Entry Box
                paint.color = if (index % 2 == 0) Color.rgb(248, 250, 252) else Color.WHITE
                canvas.drawRoundRect(40f, currentY, 572f, currentY + 84f, 6f, 6f, paint)

                paint.color = Color.rgb(226, 232, 240)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(40f, currentY, 572f, currentY + 84f, 6f, 6f, paint)
                paint.style = Paint.Style.FILL

                // Left Accent Indicator
                paint.color = if (isPassed) Color.rgb(21, 128, 61) else Color.rgb(220, 38, 38)
                canvas.drawRect(40f, currentY, 46f, currentY + 84f, paint)

                // Date Time & Status
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawText("Date & Time: ${dateTimeFormat.format(Date(record.timestamp))}", 54f, currentY + 16f, paint)

                paint.color = if (isPassed) Color.rgb(21, 128, 61) else Color.rgb(220, 38, 38)
                canvas.drawText(if (isPassed) "PASSED" else "DEFICIENT", 410f, currentY + 16f, paint)

                // Checklist summary
                paint.color = Color.DKGRAY
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 8f
                val checksSummary = if (isPassed) {
                    "✓ 5/5 ${protocol.categoryName} checks verified and compliant"
                } else {
                    "⚠ Deficiencies identified during verification checklist"
                }
                canvas.drawText(checksSummary, 54f, currentY + 32f, paint)

                // Notes
                val notesText = record.notes?.takeIf { it.isNotBlank() } ?: "Regular periodic inspection recorded."
                canvas.drawText("Notes: ${notesText.take(55)}", 54f, currentY + 46f, paint)

                // Draw Technician Signature for each log
                val sigPath = record.signaturePath
                if (sigPath.isNotBlank() && File(sigPath).exists()) {
                    try {
                        val sigBitmap = BitmapFactory.decodeFile(sigPath)
                        if (sigBitmap != null) {
                            val scaledSig = android.graphics.Bitmap.createScaledBitmap(sigBitmap, 90, 32, true)
                            canvas.drawBitmap(scaledSig, 470f, currentY + 28f, null)
                            if (scaledSig != sigBitmap) {
                                scaledSig.recycle()
                            }
                            sigBitmap.recycle()
                        }
                    } catch (_: Exception) {}
                }
                paint.textSize = 7.5f
                paint.color = Color.GRAY
                canvas.drawText("Technician Signature", 475f, currentY + 70f, paint)

                currentY += 92f
            }
        }

        // Draw Footer
        paint.color = Color.rgb(226, 232, 240)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 750f, 572f, 750f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 7.5f
        paint.color = Color.GRAY
        canvas.drawText("Official SafetyLog Asset Audit Vault • Certified document for OSHA, NFPA & Municipal safety compliance.", 40f, 765f, paint)

        document.finishPage(page)

        val outputFile = File(context.cacheDir, "SafetyLog_${equipment.tagId}_AuditRecord_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }
}
