package com.kdw.storage_example


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kdw.storage_example.databinding.PhotoItemBinding

class InternalPhotoAdapter(
    private val onItemClick: (InternalData) -> Unit
) : ListAdapter<InternalData, InternalPhotoAdapter.InternalViewHolder>(diffUtil){

    inner class InternalViewHolder(val binding: PhotoItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<InternalData>() {
            override fun areItemsTheSame(oldItem: InternalData, newItem: InternalData): Boolean {
                return oldItem.name == newItem.name
            }

            override fun areContentsTheSame(oldItem: InternalData, newItem: InternalData): Boolean {
                return oldItem.name == newItem.name && oldItem.bitmap.sameAs(newItem.bitmap)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InternalViewHolder {
        return InternalViewHolder(
            PhotoItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: InternalViewHolder, position: Int) {
        val photo = currentList[position]

        holder.binding.apply {
            photoItem.setImageBitmap(photo.bitmap)

            val ratio = photo.bitmap.width.toFloat() / photo.bitmap.height.toFloat()

            ConstraintSet().apply {
                clone(root)
                setDimensionRatio(photoItem.id, ratio.toString())
                applyTo(root)
            }

            photoItem.setOnLongClickListener {
                onItemClick(photo)
                true
            }
        }
    }
}