package com.example.aqpfact.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.aqpfact.data.Reading
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {
    fun generateReport(context: Context, readings: List<Reading>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        var y = 50f
        paint.textSize = 24f
        canvas.drawText("Report Consumi Acqua", 50f, y, paint)
        y += 40f

        paint.textSize = 14f
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val readingsByMeter = readings.groupBy { it.meterId }

        readingsByMeter.forEach { (meterId, meterReadings) ->
            val title = if (meterId == 0) "Contatore Generale" else "Utenza $meterId"
            canvas.drawText(title, 50f, y, paint)
            y += 20f

            meterReadings.take(10).forEach { reading ->
                canvas.drawText("${sdf.format(Date(reading.date))}: ${reading.value} m³", 70f, y, paint)
                y += 20f
            }
            y += 10f
        }

        pdfDocument.finishPage(page)

        val file = File(context.getExternalFilesDir(null), "Report_Consumi.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
