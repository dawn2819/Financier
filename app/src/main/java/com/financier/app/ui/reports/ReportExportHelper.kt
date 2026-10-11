package com.financier.app.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.financier.app.common.CurrencyFormatter
import com.financier.app.R
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExportHelper {

    fun exportToPdf(
        context: Context,
        outputStream: OutputStream,
        filterText: String,
        currency: String,
        totalIncome: Double,
        totalExpense: Double,
        categories: List<CategorySpending>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = context.getColor(R.color.primary)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Draw Title
        canvas.drawText("FINANCIER FINANCIAL REPORT", 40f, 60f, titlePaint)

        // Draw Meta Info
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        textPaint.textSize = 10f
        textPaint.color = Color.GRAY
        canvas.drawText("Generated at: $dateStr", 40f, 85f, textPaint)
        canvas.drawText("Filter period: $filterText", 40f, 100f, textPaint)

        // Draw line separator
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 115f, 555f, 115f, paint)

        // Summary Header
        headerPaint.textSize = 14f
        canvas.drawText("Summary Overview", 40f, 145f, headerPaint)

        textPaint.textSize = 12f
        textPaint.color = Color.BLACK
        canvas.drawText("Total Income:", 40f, 175f, textPaint)
        val incomeStr = CurrencyFormatter.format(totalIncome, currency)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(incomeStr, 200f, 175f, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Total Expenses:", 40f, 195f, textPaint)
        val expenseStr = CurrencyFormatter.format(totalExpense, currency)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(expenseStr, 200f, 195f, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Net Balance:", 40f, 215f, textPaint)
        val netBalance = totalIncome - totalExpense
        val netStr = CurrencyFormatter.format(netBalance, currency)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (netBalance >= 0) {
            textPaint.color = context.getColor(R.color.cat_income)
        } else {
            textPaint.color = context.getColor(R.color.error)
        }
        canvas.drawText(netStr, 200f, 215f, textPaint)

        // Reset text paint
        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.DEFAULT

        // Table Header
        canvas.drawLine(40f, 245f, 555f, 245f, paint)
        canvas.drawText("Category Spending Breakdown", 40f, 270f, headerPaint)

        var y = 290f
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRect(40f, y, 555f, y + 25f, paint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Category", 50f, y + 17f, textPaint)
        canvas.drawText("Amount", 300f, y + 17f, textPaint)
        canvas.drawText("Percentage", 450f, y + 17f, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        y += 25f

        for (item in categories) {
            if (y > 800) {
                // simple layout limit check
                break
            }
            // Draw separator line
            paint.color = Color.LTGRAY
            canvas.drawLine(40f, y, 555f, y, paint)

            canvas.drawText(item.category.replaceFirstChar { it.uppercase() }, 50f, y + 20f, textPaint)
            val amtStr = CurrencyFormatter.format(item.amount, currency)
            canvas.drawText(amtStr, 300f, y + 20f, textPaint)
            canvas.drawText(String.format(Locale.getDefault(), "%.1f%%", item.percentage), 450f, y + 20f, textPaint)

            y += 30f
        }
        canvas.drawLine(40f, y, 555f, y, paint)

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    fun exportToCsv(
        outputStream: OutputStream,
        filterText: String,
        currency: String,
        totalIncome: Double,
        totalExpense: Double,
        categories: List<CategorySpending>
    ) {
        val writer = outputStream.writer(Charsets.UTF_8)
        writer.write("\uFEFF")
        fun row(vararg values: String) {
            writer.write(values.joinToString(",") { value ->
                "\"${value.replace("\"", "\"\"")}\""
            })
            writer.write("\r\n")
        }

        row("Financier report", filterText)
        row("Currency", currency)
        row("Total income", totalIncome.toString())
        row("Total expenses", totalExpense.toString())
        row("Net balance", (totalIncome - totalExpense).toString())
        row()
        row("Category", "Amount", "Percentage")
        categories.forEach { item ->
            row(item.category, item.amount.toString(), String.format(Locale.US, "%.1f", item.percentage))
        }
        writer.flush()
    }
}
