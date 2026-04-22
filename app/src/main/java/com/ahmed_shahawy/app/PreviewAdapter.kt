package com.ahmed_shahawy.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class PreviewAdapter(private var images: List<String>) :
    RecyclerView.Adapter<PreviewAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivPreviewImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_preview_image, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val path = images[position]
        val src: Any = if (path.startsWith("content://") || path.startsWith("file://"))
            Uri.parse(path) else File(path)
        Glide.with(h.iv).load(src).fitCenter()
            .placeholder(android.R.drawable.ic_menu_gallery).into(h.iv)
    }

    override fun getItemCount() = images.size

    fun updateImages(newImages: List<String>) {
        images = newImages
        notifyDataSetChanged()
    }
}
