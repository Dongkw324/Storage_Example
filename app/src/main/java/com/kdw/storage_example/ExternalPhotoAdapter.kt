package com.kdw.storage_example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kdw.storage_example.databinding.PhotoItemBinding

class ExternalPhotoAdapter(
    private val onItemClick: (ExternalData) -> Unit
) : ListAdapter<ExternalData, ExternalPhotoAdapter.ExternalViewHolder>(diffUtil){

    inner class ExternalViewHolder(val binding: PhotoItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<ExternalData>() {
            override fun areItemsTheSame(oldItem: ExternalData, newItem: ExternalData): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ExternalData, newItem: ExternalData): Boolean {
                return oldItem.id == newItem.id
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExternalViewHolder {
        return ExternalViewHolder(
            PhotoItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ExternalViewHolder, position: Int) {
        val photo = currentList[position]

        holder.binding.apply {
            photoItem.setImageURI(photo.contentUri)

            val ratio = photo.width.toFloat() / photo.height.toFloat()

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