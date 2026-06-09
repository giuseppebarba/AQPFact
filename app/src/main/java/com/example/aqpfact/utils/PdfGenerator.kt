package com.example.aqpfact.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.aqpfact.data.Reading
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {
    fun generateReport(
        context: Context,
        readings: List<Reading>,
        meterNames: Map<Int, String>,
        totalBill: String,
        fixedCosts: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageCount = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()

        var y = 50f

        fun checkPage(neededSpace: Float) {
            if (y + neededSpace > pageHeight - 50f) {
                pdfDocument.finishPage(page)
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        fun drawPhoto(path: String?) {
            if (path == null) return
            try {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val originalWidth = bitmap.width
                        val originalHeight = bitmap.height
                        val maxWidth = 200f
                        val maxHeight = 150f
                        val ratio = (originalWidth.toFloat() / originalHeight.toFloat())
                        
                        var drawWidth = maxWidth
                        var drawHeight = maxWidth / ratio
                        if (drawHeight > maxHeight) {
                            drawHeight = maxHeight
                            drawWidth = maxHeight * ratio
                        }

                        checkPage(drawHeight + 10f)
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, drawWidth.toInt(), drawHeight.toInt(), true)
                        canvas.drawBitmap(scaledBitmap, 50f, y, paint)
                        y += drawHeight + 20f
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Report Dettagliato Consumi", 50f, y, paint)
        y += 40f

        // --- Parametri Bolletta ---
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Parametri Fatturazione", 50f, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        canvas.drawText("Totale Bolletta: $totalBill €", 60f, y, paint)
        y += 18f
        canvas.drawText("Spese Fisse: $fixedCosts €", 60f, y, paint)
        y += 30f

        // Pre-calcolo consumi per grafico e tabella (ultima sessione)
        val sortedMeters = readings.groupBy { it.meterId }.mapValues { it.value.sortedByDescending { r -> r.date } }
        val latestSessionDate = readings.maxOfOrNull { it.date }
        
        if (latestSessionDate != null) {
            val userConsumptions = (1..3).map { id ->
                val mReadings = sortedMeters[id] ?: emptyList()
                val latestInSession = mReadings.find { it.date == latestSessionDate }
                val previous = mReadings.find { it.date < latestSessionDate }
                val consumption = if (latestInSession != null && previous != null) {
                    (latestInSession.value - previous.value).coerceAtLeast(0.0)
                } else latestInSession?.value ?: 0.0
                id to consumption
            }
            val totalUserCons = userConsumptions.sumOf { it.second }

            // --- Tabella Ripartizione Spese ---
            checkPage(150f)
            paint.isFakeBoldText = true
            canvas.drawText("Ripartizione Spese (Ultima Sessione)", 50f, y, paint)
            y += 20f
            
            val billTotalVal = totalBill.toDoubleOrNull() ?: 0.0
            val fixedCostsVal = fixedCosts.toDoubleOrNull() ?: 0.0
            val variableTotalVal = (billTotalVal - fixedCostsVal).coerceAtLeast(0.0)
            val fixedPerUser = fixedCostsVal / 3.0

            // Header Tabella
            paint.textSize = 10f
            val startX = 50f
            val colWidths = listOf(100f, 80f, 90f, 80f, 90f) // Utenza, Consumo, Costo Cons., Q. Fissa, Totale
            val headers = listOf("Utenza", "Consumo (m³)", "Costo Cons.", "Quota Fissa", "Totale")
            
            var currentX = startX
            headers.forEachIndexed { index, h ->
                canvas.drawText(h, currentX, y, paint)
                currentX += colWidths[index]
            }
            y += 5f
            canvas.drawLine(startX, y, startX + colWidths.sum(), y, paint)
            y += 15f
            paint.isFakeBoldText = false

            userConsumptions.forEach { (id, cons) ->
                val varCost = if (totalUserCons > 0) (cons / totalUserCons) * variableTotalVal else 0.0
                val rowTotal = varCost + fixedPerUser
                
                currentX = startX
                val rowData = listOf(
                    meterNames[id] ?: "Utenza $id",
                    String.format(Locale.getDefault(), "%.2f", cons),
                    String.format(Locale.getDefault(), "%.2f €", varCost),
                    String.format(Locale.getDefault(), "%.2f €", fixedPerUser),
                    String.format(Locale.getDefault(), "%.2f €", rowTotal)
                )
                
                rowData.forEachIndexed { index, data ->
                    canvas.drawText(data, currentX, y, paint)
                    currentX += colWidths[index]
                }
                y += 15f
            }
            y += 20f

            if (totalUserCons > 0) {
                checkPage(180f)
                paint.textSize = 16f
                canvas.drawText("Ripartizione Ultima Lettura", 50f, y, paint)
                y += 20f
                val rectF = RectF(50f, y, 200f, y + 150f)
                val colors = listOf(Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"))
                var startAngle = -90f
                userConsumptions.forEachIndexed { index, pair ->
                    val sweepAngle = (pair.second / totalUserCons).toFloat() * 360f
                    paint.color = colors[index % colors.size]
                    paint.style = Paint.Style.FILL
                    canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                    paint.textSize = 12f
                    val label = "${meterNames[pair.first] ?: "Utenza ${pair.first}"}: ${(pair.second / totalUserCons * 100).toInt()}%"
                    canvas.drawRect(220f, y + (index * 25f), 235f, y + 15f + (index * 25f), paint)
                    paint.color = Color.BLACK
                    canvas.drawText(label, 245f, y + 12f + (index * 25f), paint)
                    startAngle += sweepAngle
                }
                y += 180f
            }
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sessions = readings.groupBy { it.date }.toList().sortedByDescending { it.first }

        // --- SOLO ULTIMA SESSIONE ---
        if (sessions.isNotEmpty()) {
            val (date, sessionReadings) = sessions.first()
            
            checkPage(100f)
            paint.color = Color.BLACK
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("Dettaglio Ultima Sessione: ${sdf.format(Date(date))}", 50f, y, paint)
            y += 30f

            // --- SEZIONE GENERALE ---
            val generalReading = sessionReadings.find { it.meterId == 0 }
            if (generalReading != null) {
                paint.textSize = 14f
                paint.isFakeBoldText = true
                val genName = meterNames[0] ?: "Contatore Generale"
                canvas.drawText(genName, 50f, y, paint)
                y += 20f
                paint.isFakeBoldText = false
                
                val prevGen = sortedMeters[0]?.find { it.date < date }
                val diffGen = if (prevGen != null) (generalReading.value - prevGen.value).coerceAtLeast(0.0) else generalReading.value
                
                canvas.drawText("Lettura Attuale: ${generalReading.value} m³", 60f, y, paint)
                y += 18f
                canvas.drawText("Lettura Precedente: ${prevGen?.value ?: "N/D"} m³", 60f, y, paint)
                y += 18f
                paint.isFakeBoldText = true
                canvas.drawText("Consumo Generale: $diffGen m³", 60f, y, paint)
                y += 20f
                paint.isFakeBoldText = false
                
                drawPhoto(generalReading.photoPath)
            }

            // --- SEZIONE UTENZE ---
            checkPage(40f)
            paint.isFakeBoldText = true
            paint.textSize = 14f
            canvas.drawText("Contatori di Sottrazione (Utenze)", 50f, y, paint)
            y += 25f
            paint.isFakeBoldText = false

            var sumUserCons = 0.0
            (1..3).forEach { id ->
                val userReading = sessionReadings.find { it.meterId == id }
                if (userReading != null) {
                    checkPage(80f)
                    val uName = meterNames[id] ?: "Utenza $id"
                    paint.isFakeBoldText = true
                    canvas.drawText(uName, 60f, y, paint)
                    y += 18f
                    paint.isFakeBoldText = false
                    
                    val prevU = sortedMeters[id]?.find { it.date < date }
                    val diffU = if (prevU != null) (userReading.value - prevU.value).coerceAtLeast(0.0) else userReading.value
                    sumUserCons += diffU

                    canvas.drawText("Attuale: ${userReading.value} | Precedente: ${prevU?.value ?: "N/D"} | Consumo: $diffU m³", 70f, y, paint)
                    y += 15f
                    
                    drawPhoto(userReading.photoPath)
                }
            }

            // --- BILANCIO ---
            val genCons = if (generalReading != null) {
                val prevGen = sortedMeters[0]?.find { it.date < date }
                if (prevGen != null) (generalReading.value - prevGen.value).coerceAtLeast(0.0) else generalReading.value
            } else 0.0
            
            val gap = (genCons - sumUserCons)
            
            checkPage(60f)
            paint.style = Paint.Style.STROKE
            canvas.drawRect(50f, y, 545f, y + 50f, paint)
            paint.style = Paint.Style.FILL
            paint.isFakeBoldText = true
            canvas.drawText("Bilancio Consumi Sessione", 60f, y + 20f, paint)
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Generale ($genCons) - Somma Utenze ($sumUserCons) = Sfrido/Perdita: ${String.format(Locale.getDefault(), "%.2f", gap)} m³", 60f, y + 40f, paint)
            y += 70f
        }

        // --- TABELLA SINTETICA STORICA ---
        checkPage(100f)
        y += 20f
        paint.isFakeBoldText = true
        paint.textSize = 16f
        canvas.drawText("Tabella Sintetica Letture Storiche", 50f, y, paint)
        y += 25f
        
        val tableStartX = 50f
        // Ridimensionamento colonne per far spazio allo sfrido
        val tableColWidths = listOf(90f, 80f, 80f, 80f, 80f, 80f) // Data, Gen, Ut1, Ut2, Ut3, Sfrido
        
        val header0 = meterNames[0] ?: "Gen."
        val header1 = meterNames[1] ?: "Ut.1"
        val header2 = meterNames[2] ?: "Ut.2"
        val header3 = meterNames[3] ?: "Ut.3"
        val tableHeaders = listOf("Data", header0, header1, header2, header3, "Sfrido")
        
        paint.textSize = 10f
        var curX = tableStartX
        tableHeaders.forEachIndexed { index, h ->
            // Troncamento se il nome è troppo lungo per la colonna
            val truncatedHeader = if (h.length > 12) h.substring(0, 10) + ".." else h
            canvas.drawText(truncatedHeader, curX, y, paint)
            curX += tableColWidths[index]
        }
        y += 5f
        canvas.drawLine(tableStartX, y, tableStartX + tableColWidths.sum(), y, paint)
        y += 15f
        paint.isFakeBoldText = false

        val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sessions.forEach { (date, sReadings) ->
            checkPage(20f)
            curX = tableStartX
            
            var genCons = 0.0
            var sumUserCons = 0.0
            val rowValues = mutableListOf(dateSdf.format(Date(date)))
            
            (0..3).forEach { mId ->
                val current = sReadings.find { it.meterId == mId }
                rowValues.add(current?.value?.toString() ?: "-")
                
                // Calcolo consumi per lo sfrido
                if (current != null) {
                    val prev = sortedMeters[mId]?.find { it.date < date }
                    val cons = if (prev != null) (current.value - prev.value).coerceAtLeast(0.0) else current.value
                    if (mId == 0) genCons = cons else sumUserCons += cons
                }
            }
            
            val sfrido = (genCons - sumUserCons)
            rowValues.add(String.format(Locale.getDefault(), "%.2f", sfrido))
            
            rowValues.forEachIndexed { index, value ->
                canvas.drawText(value, curX, y, paint)
                curX += tableColWidths[index]
            }
            y += 15f
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
