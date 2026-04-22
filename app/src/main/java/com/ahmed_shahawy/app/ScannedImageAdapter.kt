package com.ahmed_shahawy.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

/**
 * Adapter للشاشة الرئيسية - يعرض الصور مع شارة تمييز المصدر
 * شارة خضراء = من المعرض (لن تُحذف)
 * بلا شارة = من الكاميرا (ستُحذف بعد إنشاء PDF)
 */
class ScannedImageAdapter(
    private val images: MutableList<MainActivity.ScannedImage>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ScannedImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivScannedImage)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteImage)
        val tvNumber: TextView = itemView.findViewById(R.id.tvImageNumber)
        val tvSource: TextView = itemView.findViewById(R.id.tvImageSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = images[position]

        holder.tvNumber.text = "${position + 1}"

        // شارة المصدر
        if (item.fromCamera) {
            holder.tvSource.visibility = View.GONE
        } else {
            holder.tvSource.visibility = View.VISIBLE
            holder.tvSource.text = "معرض"
        }

        // تحميل الصورة
        val source: Any = if (item.path.startsWith("content://") || item.path.startsWith("file://"))
            android.net.Uri.parse(item.path)
        else
            File(item.path)

        Glide.with(holder.itemView.context)
            .load(source)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.imageView)

        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onDeleteClick(pos)
        }
    }

    override fun getItemCount() = images.size
}
