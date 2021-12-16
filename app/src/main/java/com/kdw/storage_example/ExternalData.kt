package com.kdw.storage_example

import android.net.Uri

data class ExternalData(
    val id: Long,
    val contentUri: Uri,
    val name: String,
    val width: Int,
    val height: Int
)
