package com.ahmed_shahawy.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.ahmed_shahawy.app.databinding.ActivityMainBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ScannedImageAdapter
    private lateinit var mlKitScanner: GmsDocumentScanner

    data class ScannedImage(val path: String, val fromCamera: Boolean)
    private val scannedImages = mutableListOf<ScannedImage>()

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.forEach { page ->
                val path = getRealPathFromUri(page.imageUri) ?: page.imageUri.toString()
                scannedImages.add(ScannedImage(path, fromCamera = true))
            }
            imageAdapter.notifyDataSetChanged()
            updateUI()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val path = getRealPathFromUri(uri) ?: uri.toString()
            scannedImages.add(ScannedImage(path, fromCamera = false))
        }
        if (uris.isNotEmpty()) {
            imageAdapter.notifyDataSetChanged()
            updateUI()
            Toast.makeText(this, "تم إضافة ${uris.size} صورة من المعرض", Toast.LENGTH_SHORT).show()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery()
        else Toast.makeText(this, "يجب منح صلاحية الوصول للصور", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupMlKitScanner()
        setupRecyclerView()
        setupButtons()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupMlKitScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .build()
        mlKitScanner = GmsDocumentScanning.getClient(options)
    }

    private fun setupRecyclerView() {
        imageAdapter = ScannedImageAdapter(
            images = scannedImages,
            onDeleteClick = { position ->
                scannedImages.removeAt(position)
                imageAdapter.notifyItemRemoved(position)
                imageAdapter.notifyItemRangeChanged(position, scannedImages.size)
                updateUI()
            }
        )
        binding.recyclerViewImages.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = imageAdapter
        }
    }

    private fun setupButtons() {
        binding.fabScan.setOnClickListener { openMlKitScanner() }
        binding.fabGallery.setOnClickListener { checkPermissionAndOpenGallery() }
        binding.btnCreatePdf.setOnClickListener {
            if (scannedImages.isEmpty())
                Toast.makeText(this, "الرجاء إضافة صورة واحدة على الأقل", Toast.LENGTH_SHORT).show()
            else openPreviewScreen()
        }
        binding.btnClearAll.setOnClickListener {
            scannedImages.clear()
            imageAdapter.notifyDataSetChanged()
            updateUI()
        }
    }

    private fun openMlKitScanner() {
        mlKitScanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "تعذر فتح الماسح: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            openGallery()
        else
            storagePermissionLauncher.launch(permission)
    }

    private fun openGallery() = galleryLauncher.launch("image/*")

    private fun openPreviewScreen() {
        startActivity(Intent(this, PreviewActivity::class.java).apply {
            putStringArrayListExtra(
                PreviewActivity.EXTRA_IMAGE_PATHS,
                ArrayList(scannedImages.map { it.path })
            )
            putExtra(
                PreviewActivity.EXTRA_FROM_CAMERA_FLAGS,
                BooleanArray(scannedImages.size) { i -> scannedImages[i].fromCamera }
            )
        })
    }

    private fun updateUI() {
        val count = scannedImages.size
        binding.tvImageCount.text = if (count == 0) "لا توجد صور" else "$count صورة"
        binding.btnCreatePdf.isEnabled = count > 0
        binding.btnClearAll.isEnabled = count > 0
        binding.layoutEmpty.visibility =
            if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerViewImages.visibility =
            if (count == 0) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = java.io.File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> inputStream.copyTo(out) }
            file.absolutePath
        } catch (e: Exception) { null }
    }
}
