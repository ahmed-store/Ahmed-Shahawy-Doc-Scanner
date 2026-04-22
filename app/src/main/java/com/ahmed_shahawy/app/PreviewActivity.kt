package com.ahmed_shahawy.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmed_shahawy.app.databinding.ActivityPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * شاشة المعاينة قبل إنشاء PDF
 * - تعرض الصور أفقياً
 * - تطلب اسم الملف قبل الإنشاء
 * - تحفظ PDF في مجلد Documents العام
 * - تحذف صور الكاميرا فقط بعد الانتهاء
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private val imagePaths = mutableListOf<String>()
    private lateinit var fromCameraFlags: BooleanArray
    private lateinit var previewAdapter: PreviewAdapter
    private var currentPdfPath: String? = null

    companion object {
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
        const val EXTRA_FROM_CAMERA_FLAGS = "extra_from_camera_flags"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // استلام البيانات
        intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS)?.let { imagePaths.addAll(it) }
        fromCameraFlags = intent.getBooleanArrayExtra(EXTRA_FROM_CAMERA_FLAGS)
            ?: BooleanArray(imagePaths.size) { true }

        setupToolbar()
        setupRecyclerView()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "معاينة (${imagePaths.size} صورة)"
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        previewAdapter = PreviewAdapter(imagePaths)
        binding.recyclerViewPreview.apply {
            layoutManager = LinearLayoutManager(
                this@PreviewActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = previewAdapter
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnGeneratePdf.setOnClickListener { showFileNameDialog() }
        binding.btnShare.setOnClickListener {
            currentPdfPath?.let { sharePdf(it) }
        }
    }

    // ── اسم الملف ────────────────────────────────────────────────────────────
    private fun showFileNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filename, null)
        val etName = dialogView.findViewById<EditText>(R.id.etFileName)

        // اسم افتراضي بالتاريخ
        val defaultName = "مستند_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())}"
        etName.setText(defaultName)
        etName.setSelection(defaultName.length)

        AlertDialog.Builder(this)
            .setTitle("اسم ملف PDF")
            .setView(dialogView)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { defaultName }
                val safeName = name.replace(Regex("[/\\\\:*?\"<>|]"), "_") // إزالة الأحرف الغير مسموحة
                generatePdf(safeName)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ── إنشاء PDF ────────────────────────────────────────────────────────────
    private fun generatePdf(fileName: String) {
        // Progress dialog
        val progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val tvProgress = progressView.findViewById<TextView>(R.id.tvProgressMessage)
        val progressBar = progressView.findViewById<ProgressBar>(R.id.progressBar)

        val progressDialog = AlertDialog.Builder(this)
            .setView(progressView)
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                tvProgress.text = "جاري إنشاء PDF..."

                val pdfPath = withContext(Dispatchers.IO) {
                    PdfGenerator.generatePdf(
                        context = this@PreviewActivity,
                        imagePaths = imagePaths,
                        fileName = fileName
                    )
                }

                // حفظ في Documents العام
                tvProgress.text = "جاري الحفظ في المستندات..."
                val finalUri = withContext(Dispatchers.IO) {
                    saveToDocuments(pdfPath, "$fileName.pdf")
                }

                // تنظيف صور الكاميرا فقط
                tvProgress.text = "جاري التنظيف..."
                withContext(Dispatchers.IO) { cleanupCameraImages() }

                progressDialog.dismiss()

                currentPdfPath = pdfPath
                val size = PdfGenerator.getFileSizeString(pdfPath)
                showSuccessDialog(pdfPath, finalUri, fileName, size)

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@PreviewActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── حفظ في Documents العام ───────────────────────────────────────────────
    private fun saveToDocuments(sourcePath: String, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ → MediaStore API
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/DocScanner")
                }
                val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                uri?.let { dest ->
                    contentResolver.openOutputStream(dest)?.use { out ->
                        File(sourcePath).inputStream().copyTo(out)
                    }
                }
                uri
            } else {
                // Android 9 وأقل → مسار مباشر
                val docsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "DocScanner"
                )
                docsDir.mkdirs()
                val dest = File(docsDir, fileName)
                File(sourcePath).copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            null // فشل الحفظ العام → الملف لا يزال في cache التطبيق
        }
    }

    // ── حذف صور الكاميرا فقط ────────────────────────────────────────────────
    private fun cleanupCameraImages() {
        imagePaths.forEachIndexed { index, path ->
            if (index < fromCameraFlags.size && fromCameraFlags[index]) {
                // هذه الصورة من الكاميرا/ML Kit → احذفها
                try { File(path).delete() } catch (_: Exception) {}
                // حذف نسخة المعالجة أيضاً
                try { File(path.replace(".jpg", "_processed.jpg")).delete() } catch (_: Exception) {}
            }
            // صور المعرض لا تُمس
        }
    }

    // ── نافذة النجاح ─────────────────────────────────────────────────────────
    private fun showSuccessDialog(pdfPath: String, docUri: Uri?, fileName: String, size: String) {
        val location = if (docUri != null)
            "Documents/DocScanner/$fileName.pdf"
        else
            "التخزين الداخلي للتطبيق"

        AlertDialog.Builder(this)
            .setTitle("✓ تم إنشاء PDF بنجاح")
            .setMessage("الاسم: $fileName.pdf\nالحجم: $size\nالمسار: $location")
            .setPositiveButton("مشاركة") { _, _ -> sharePdf(pdfPath) }
            .setNegativeButton("فتح") { _, _ -> openPdf(docUri ?: Uri.fromFile(File(pdfPath))) }
            .setNeutralButton("إغلاق") { _, _ ->
                binding.btnShare.visibility = android.view.View.VISIBLE
            }
            .setCancelable(false)
            .show()
    }

    // ── مشاركة PDF ───────────────────────────────────────────────────────────
    private fun sharePdf(path: String) {
        val file = File(path)
        if (!file.exists()) { Toast.makeText(this, "الملف غير موجود", Toast.LENGTH_SHORT).show(); return }
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "مشاركة PDF"
            )
        )
    }

    private fun openPdf(uri: Uri) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق لفتح PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
