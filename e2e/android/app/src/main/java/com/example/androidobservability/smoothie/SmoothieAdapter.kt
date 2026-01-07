package com.example.androidobservability.smoothie

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.androidobservability.R
import com.launchdarkly.observability.api.ldMask

data class SmoothieItem(
    val title: String,
    val imageFileName: String
)

class SmoothieAdapter(
    private val smoothies: List<SmoothieItem>,
    private val imageLoader: (String) -> Bitmap?
) : RecyclerView.Adapter<SmoothieAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.smoothieImage)
//        init {
//            imageView.ldMask()
//        }
        val titleView: TextView = itemView.findViewById(R.id.smoothieTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_smoothie, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = smoothies[position]
        holder.titleView.text = item.title
        val bitmap = imageLoader(item.imageFileName)
        if (bitmap != null) {
            holder.imageView.setImageBitmap(bitmap)
        } else {
            holder.imageView.setImageResource(R.mipmap.ic_launcher)
        }
    }

    override fun getItemCount(): Int = smoothies.size
}

