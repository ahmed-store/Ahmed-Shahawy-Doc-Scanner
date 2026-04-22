package com.ahmed_shahawy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ahmed_shahawy.app.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "عن التطبيق"
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        // عرض رقم الإصدار
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0" }
        binding.tvVersion.text = "الإصدار: $versionName"

        // زر مشاركة التطبيق
        binding.btnShareApp.setOnClickListener {
            val apkUrl = "https://github.com/ahmed-store/Ahmed-Shahawy-Doc-Scanner/releases/download/Version_1.0/Ahmed-Shahawy-DocScanner.apk"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ahmed Shahawy Doc Scanner")
                putExtra(Intent.EXTRA_TEXT,
                    "حمّل تطبيق Ahmed Shahawy Doc Scanner:\n$apkUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة التطبيق"))
        }
    }
}
