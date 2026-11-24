package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.HisabWithItems
import java.io.File
import java.io.FileOutputStream

data class SlipRenderData(
    val companyName: String = "",
    val ownerName: String = "",
    val partyName: String,
    val partyPhone: String,
    val hisabWithItems: HisabWithItems,
    val includeLiveBalance: Boolean,
    val currentLiveBalance: Double,
    val totalPaidAfterBill: Double,
    val computedPrevBalance: Double? = null,
    val computedMiscNet: Double = 0.0,
    val computedAdvancePaid: Double? = null,
    val computedBillBalance: Double? = null
)

object HandwrittenSlipRenderer {

    private const val WIDTH_PX = 1080
    private const val LINE_HEIGHT = 58f
    private const val MARGIN_LEFT = 120f
    private const val MARGIN_RIGHT = 52f

    fun renderSlipBitmap(context: Context, data: SlipRenderData): Bitmap {
        val kalamTypeface = ResourcesCompat.getFont(context, R.font.kalam) ?: Typeface.SERIF
        val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE

        val inkColor = Color.parseColor("#14356B")
        val secondaryInkColor = Color.parseColor("#2E5188")
        val accentRedInk = Color.parseColor("#B63E26")
        val settledGreenInk = Color.parseColor("#186A42")
        val paperBg = Color.parseColor("#FDF8EC")
        val headerBg = Color.parseColor("#F5ECD6")
        val blueRuleColor = Color.parseColor("#C6DBF0")
        val redMarginColor = Color.parseColor("#D97171")

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(kalamTypeface, Typeface.BOLD)
            textSize = 44f
        }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(kalamTypeface, Typeface.BOLD)
            textSize = 33f
        }
        val bodyHandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(kalamTypeface, Typeface.NORMAL)
            textSize = 31f
        }
        val subHandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryInkColor
            typeface = Typeface.create(kalamTypeface, Typeface.NORMAL)
            textSize = 25f
        }
        val numHandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(kalamTypeface, Typeface.BOLD)
            textSize = 30f
            textAlign = Paint.Align.RIGHT
        }
        val smallLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryInkColor
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
            textSize = 22f
        }

        val particularsMaxWidth = 480f
        data class RenderedRow(
            val titleLines: List<String>,
            val detailLines: List<String>,
            val qtyStr: String,
            val rateStr: String,
            val amountStr: String
        )

        val rows = data.hisabWithItems.items.mapIndexed { index, item ->
            val subCat = if (item.subCategoryName.isNotBlank()) " (${item.subCategoryName})" else ""
            val mainTitle = "${index + 1}. ${item.productName}$subCat"
            val titleLines = wrapText(mainTitle, bodyHandPaint, particularsMaxWidth)

            val detailLines = if (item.note.isNotBlank()) {
                wrapText(item.note, subHandPaint, particularsMaxWidth)
            } else {
                emptyList()
            }

            RenderedRow(
                titleLines = titleLines,
                detailLines = detailLines,
                qtyStr = "${CurrencyFormatter.formatQuantity(item.quantity)} ${item.unitLabel}",
                rateStr = CurrencyFormatter.formatRate(item.unitRate),
                amountStr = CurrencyFormatter.formatRupees(item.lineTotal, showDecimalsWhenWhole = true)
            )
        }

        val itemLineCount = rows.sumOf { it.titleLines.size + it.detailLines.size }
        val hisab = data.hisabWithItems.hisab

        val effectivePrevBal = data.computedPrevBalance ?: hisab.previousBalanceSnapshot
        val positivePrevBal = if (effectivePrevBal > 0.01) effectivePrevBal else 0.0
        val negativePrevAdvance = if (effectivePrevBal < -0.01) kotlin.math.abs(effectivePrevBal) else 0.0
        val miscNet = data.computedMiscNet
        val baseAdvancePaid = (data.computedAdvancePaid ?: hisab.paymentIncluded) + negativePrevAdvance
        val extraLaterPaid = if (data.includeLiveBalance && data.totalPaidAfterBill > 0.0) data.totalPaidAfterBill else 0.0
        val totalAdvancePaid = baseAdvancePaid + extraLaterPaid

        val summaryLinesCount = buildList {
            add("Subtotal")
            if (positivePrevBal > 0.01) {
                add("PrevBal")
                add("TotalDue")
            }
            if (kotlin.math.abs(miscNet) > 0.01) add("MiscNet")
            if (totalAdvancePaid > 0.01) add("AdvancePaid")
            add("FinalBal")
        }.size

        val notesLines = if (hisab.notes.isNotBlank()) {
            wrapText("Note: ${hisab.notes}", subHandPaint, WIDTH_PX - MARGIN_LEFT - MARGIN_RIGHT)
        } else {
            emptyList()
        }

        val totalLogicalLines = maxOf(12, 4 + itemLineCount + 1 + summaryLinesCount + notesLines.size + 1)
        val heightPx = (totalLogicalLines * LINE_HEIGHT + 70f).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(paperBg)
        val headerBandHeight = 2.0f * LINE_HEIGHT
        val headerBandPaint = Paint().apply { color = headerBg }
        canvas.drawRect(0f, 0f, WIDTH_PX.toFloat(), headerBandHeight, headerBandPaint)

        val rulePaint = Paint().apply {
            color = blueRuleColor
            strokeWidth = 2f
        }
        var y = headerBandHeight
        while (y < heightPx - 16f) {
            canvas.drawLine(0f, y, WIDTH_PX.toFloat(), y, rulePaint)
            y += LINE_HEIGHT
        }

        val redMarginPaint = Paint().apply {
            color = redMarginColor
            strokeWidth = 2.5f
        }
        canvas.drawLine(92f, 0f, 92f, heightPx.toFloat(), redMarginPaint)
        canvas.drawLine(101f, 0f, 101f, heightPx.toFloat(), redMarginPaint)

        val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E3DAC6") }
        var punchY = 140f
        while (punchY < heightPx - 80f) {
            canvas.drawCircle(44f, punchY, 15f, punchPaint)
            punchY += 320f
        }

        val rightEdge = WIDTH_PX - MARGIN_RIGHT
        val dateText = DateFormatter.formatSlipDate(hisab.dateMillis)
        val datePaint = TextPaint(subHandPaint).apply {
            color = inkColor
            textAlign = Paint.Align.LEFT
            textSize = 27f
            typeface = Typeface.create(kalamTypeface, Typeface.BOLD)
        }
        val dateWidth = datePaint.measureText(dateText)

        val partyCenterPaint = TextPaint(titlePaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = 40f
        }
        val partyWidth = partyCenterPaint.measureText(data.partyName)
        val partyLeftIfCentered = (WIDTH_PX / 2f) - (partyWidth / 2f)

        if (partyLeftIfCentered > MARGIN_LEFT + dateWidth + 24f && (WIDTH_PX / 2f) + (partyWidth / 2f) <= rightEdge) {
            canvas.drawText(dateText, MARGIN_LEFT, 74f, datePaint)
            canvas.drawText(data.partyName, WIDTH_PX / 2f, 76f, partyCenterPaint)
        } else {
            canvas.drawText(dateText, MARGIN_LEFT, 48f, datePaint)
            val scaledPaint = TextPaint(partyCenterPaint).apply {
                textSize = if (partyWidth > WIDTH_PX - MARGIN_LEFT - MARGIN_RIGHT) 32f else 38f
            }
            canvas.drawText(data.partyName, (MARGIN_LEFT + rightEdge) / 2f, 96f, scaledPaint)
        }

        val qtyRightX = 720f
        val rateRightX = 870f
        val amtRightX = rightEdge

        var currentLineIndex = 1
        fun lineBaseline(idx: Int): Float = headerBandHeight + idx * LINE_HEIGHT - 14f

        val colHeaderY = lineBaseline(currentLineIndex)
        canvas.drawText("PARTICULARS", MARGIN_LEFT, colHeaderY, smallLabelPaint)
        canvas.drawText("QTY", qtyRightX, colHeaderY, TextPaint(smallLabelPaint).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("RATE", rateRightX, colHeaderY, TextPaint(smallLabelPaint).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("AMOUNT", amtRightX, colHeaderY, TextPaint(smallLabelPaint).apply { textAlign = Paint.Align.RIGHT })

        val inkLinePaint = Paint().apply {
            color = inkColor
            strokeWidth = 3f
        }
        val headerRuleY = headerBandHeight + currentLineIndex * LINE_HEIGHT
        canvas.drawLine(MARGIN_LEFT, headerRuleY, rightEdge, headerRuleY, inkLinePaint)
        currentLineIndex++

        rows.forEach { row ->
            row.titleLines.forEachIndexed { idx, line ->
                val baseY = lineBaseline(currentLineIndex)
                canvas.drawText(line, MARGIN_LEFT, baseY, bodyHandPaint)
                if (idx == 0) {
                    canvas.drawText(
                        row.qtyStr,
                        qtyRightX,
                        baseY,
                        TextPaint(bodyHandPaint).apply { textAlign = Paint.Align.RIGHT; textSize = 28f }
                    )
                    canvas.drawText(
                        row.rateStr,
                        rateRightX,
                        baseY,
                        TextPaint(bodyHandPaint).apply { textAlign = Paint.Align.RIGHT; textSize = 28f }
                    )
                    canvas.drawText(row.amountStr, amtRightX, baseY, numHandPaint)
                }
                currentLineIndex++
            }
            row.detailLines.forEach { dLine ->
                val baseY = lineBaseline(currentLineIndex)
                canvas.drawText("   $dLine", MARGIN_LEFT, baseY, subHandPaint)
                currentLineIndex++
            }
        }

        val dividerY = headerBandHeight + currentLineIndex * LINE_HEIGHT - LINE_HEIGHT + 6f
        canvas.drawLine(MARGIN_LEFT, dividerY, rightEdge, dividerY, inkLinePaint)

        val summaryLabelX = 460f
        fun drawSummaryRow(label: String, amount: String, color: Int = inkColor, bold: Boolean = false) {
            val baseY = lineBaseline(currentLineIndex)
            val lPaint = TextPaint(bodyHandPaint).apply {
                this.color = color
                if (bold) typeface = Typeface.create(kalamTypeface, Typeface.BOLD)
            }
            val vPaint = TextPaint(numHandPaint).apply {
                this.color = color
                textSize = if (bold) 34f else 30f
            }
            canvas.drawText(label, summaryLabelX, baseY, lPaint)
            canvas.drawText(amount, amtRightX, baseY, vPaint)
            currentLineIndex++
        }

        drawSummaryRow(
            "Items Subtotal:",
            CurrencyFormatter.formatRupees(hisab.itemsSubtotal, showDecimalsWhenWhole = true)
        )

        if (positivePrevBal > 0.01) {
            drawSummaryRow(
                "(+) Prev. Balance:",
                CurrencyFormatter.formatRupees(positivePrevBal, showDecimalsWhenWhole = true),
                color = secondaryInkColor
            )
            val grossTotal = positivePrevBal + hisab.itemsSubtotal + miscNet
            drawSummaryRow(
                "Total Due:",
                CurrencyFormatter.formatRupees(grossTotal, showDecimalsWhenWhole = true),
                bold = true
            )
        }

        if (kotlin.math.abs(miscNet) > 0.01) {
            val miscLabel = if (miscNet > 0) "(+) Misc Additions:" else "(-) Misc Deductions:"
            drawSummaryRow(
                miscLabel,
                CurrencyFormatter.formatRupees(kotlin.math.abs(miscNet), showDecimalsWhenWhole = true),
                color = if (miscNet > 0) secondaryInkColor else settledGreenInk
            )
        }

        if (totalAdvancePaid > 0.01) {
            drawSummaryRow(
                "(-) Advance Paid:",
                CurrencyFormatter.formatRupees(totalAdvancePaid, showDecimalsWhenWhole = true),
                color = settledGreenInk
            )
        }

        val finalAmount = when {
            data.includeLiveBalance -> data.currentLiveBalance
            data.computedBillBalance != null -> data.computedBillBalance
            else -> hisab.netBalanceSnapshot
        }
        val finalLabel = if (finalAmount < -0.01) "ADVANCE BALANCE:" else "BALANCE:"
        val finalColor = if (finalAmount > 0.01) accentRedInk else settledGreenInk

        val boxTop = headerBandHeight + (currentLineIndex - 1) * LINE_HEIGHT + 6f
        val boxBottom = boxTop + LINE_HEIGHT - 4f
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (finalAmount > 0.01) Color.parseColor("#FCE9E4") else Color.parseColor("#E3F5EB")
        }
        canvas.drawRoundRect(
            RectF(summaryLabelX - 18f, boxTop, rightEdge + 10f, boxBottom),
            12f,
            12f,
            highlightPaint
        )
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = finalColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(
            RectF(summaryLabelX - 18f, boxTop, rightEdge + 10f, boxBottom),
            12f,
            12f,
            borderPaint
        )

        drawSummaryRow(
            finalLabel,
            CurrencyFormatter.formatRupees(finalAmount, showDecimalsWhenWhole = true),
            color = finalColor,
            bold = true
        )

        if (notesLines.isNotEmpty()) {
            currentLineIndex++
            notesLines.forEach { nLine ->
                canvas.drawText(nLine, MARGIN_LEFT, lineBaseline(currentLineIndex), subHandPaint)
                currentLineIndex++
            }
        }

        return bitmap
    }

    private fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    fun savePermanentSharedSlip(context: Context, bitmap: Bitmap, seedId: Long): Pair<String, Uri> {
        val dir = File(context.filesDir, "shared_slips")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "slip_${seedId}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file.absolutePath to uri
    }

    fun getShareUriForExistingFile(context: Context, imagePath: String): Uri? {
        if (imagePath.isBlank()) return null
        val file = File(imagePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveSlipAndGetUri(context: Context, bitmap: Bitmap, billNumber: Int): Uri {
        val dir = File(context.cacheDir, "shared_hisabs")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }

        val file = File(dir, "hisab_slip_${billNumber}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildTextSummary(data: SlipRenderData): String {
        val hisab = data.hisabWithItems.hisab
        val sb = StringBuilder()
        sb.appendLine("*${data.partyName}*")
        sb.appendLine("Date: ${DateFormatter.formatSlipDate(hisab.dateMillis)}")
        sb.appendLine("--------------------")
        data.hisabWithItems.items.forEachIndexed { idx, item ->
            val subCat = if (item.subCategoryName.isNotBlank()) " (${item.subCategoryName})" else ""
            sb.appendLine("${idx + 1}. ${item.productName}$subCat")
            sb.appendLine(
                "   ${CurrencyFormatter.formatQuantity(item.quantity)} ${item.unitLabel} × ${CurrencyFormatter.formatRate(item.unitRate)} = ${CurrencyFormatter.formatRupees(item.lineTotal, true)}"
            )
        }
        val effectivePrevBal = data.computedPrevBalance ?: hisab.previousBalanceSnapshot
        val positivePrevBal = if (effectivePrevBal > 0.01) effectivePrevBal else 0.0
        val negativePrevAdvance = if (effectivePrevBal < -0.01) kotlin.math.abs(effectivePrevBal) else 0.0
        val totalAdvancePaid = (data.computedAdvancePaid ?: hisab.paymentIncluded) +
            negativePrevAdvance +
            if (data.includeLiveBalance && data.totalPaidAfterBill > 0.0) data.totalPaidAfterBill else 0.0

        sb.appendLine("--------------------")
        sb.appendLine("Items Subtotal: ${CurrencyFormatter.formatRupees(hisab.itemsSubtotal, true)}")
        if (positivePrevBal > 0.01) {
            sb.appendLine("Prev. Balance: ${CurrencyFormatter.formatRupees(positivePrevBal, true)}")
        }
        if (kotlin.math.abs(data.computedMiscNet) > 0.01) {
            val miscSign = if (data.computedMiscNet > 0) "+" else "-"
            sb.appendLine("Misc: $miscSign${CurrencyFormatter.formatRupees(kotlin.math.abs(data.computedMiscNet), true)}")
        }
        if (totalAdvancePaid > 0.01) {
            sb.appendLine("Advance Paid: -${CurrencyFormatter.formatRupees(totalAdvancePaid, true)}")
        }
        val finalAmt = when {
            data.includeLiveBalance -> data.currentLiveBalance
            data.computedBillBalance != null -> data.computedBillBalance
            else -> hisab.netBalanceSnapshot
        }
        sb.appendLine("*Balance: ${CurrencyFormatter.formatRupees(finalAmt, true)}*")
        return sb.toString().trim()
    }

    fun launchShareImageIntent(context: Context, imageUri: Uri, data: SlipRenderData) {
        val summary = buildTextSummary(data)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Hisab Slip"))
    }

    fun launchShareTextIntent(context: Context, data: SlipRenderData) {
        val summary = buildTextSummary(data)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(intent, "Share Hisab Summary"))
    }
}
