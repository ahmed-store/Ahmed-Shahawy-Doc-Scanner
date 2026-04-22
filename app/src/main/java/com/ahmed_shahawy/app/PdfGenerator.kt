package com.ahmed_shahawy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import java.io.ByteArrayOutputStream
import java.io.File

object PdfGenerator {

    private const val TAG = "PdfGenerator"

    /**
     * إنشاء PDF وحفظه في cache التطبيق مؤقتاً
     * ثم PreviewActivity تنقله لـ Documents
     */
    fun generatePdf(context: Context, imagePaths: List<String>, fileName: String): String {
        val outFile = File(context.cacheDir, "$fileName.pdf")
        Log.d(TAG, "Generating PDF: ${outFile.absolutePath}, pages: ${imagePaths.size}")

        val writer = PdfWriter(outFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc).apply { setMargins(0f, 0f, 0f, 0f) }

        imagePaths.forEachIndexed { i, path ->
            Log.d(TAG, "Adding page ${i + 1}: $path")
            val bytes = loadImage(context, path) ?: return@forEachIndexed
            val imgData = ImageDataFactory.create(bytes)
            val image = Image(imgData)

            pdfDoc.addNewPage(PageSize.A4)
            image.scaleToFit(PageSize.A4.width, PageSize.A4.height)
            val x = (PageSize.A4.width - image.imageScaledWidth) / 2
            val y = (PageSize.A4.height - image.imageScaledHeight) / 2
            image.setFixedPosition(i + 1, x, y)
            document.add(image)
        }

        document.close()
        return outFile.absolutePath
    }

    private fun loadImage(context: Context, path: String): ByteArray? {
        return try {
            val inputStream = when {
                path.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(path))
                path.startsWith("file://") ->
                    context.contentResolver.openInputStream(Uri.parse(path))
                else -> File(path).inputStream()
            } ?: return null

            val bytes = inputStream.readBytes()
            inputStream.close()

            // ضغط للحجم المناسب داخل PDF
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: $path → ${e.message}")
            null
        }
    }

    fun getFileSizeString(path: String): String {
        val size = File(path).length()
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "%.1f MB".format(size / (1024.0 * 1024.0))
        }
    }
}
