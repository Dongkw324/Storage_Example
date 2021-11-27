package com.kdw.storage_example

import android.net.Uri

data class ExternalData(
    val id: Int,
    val contentUri: Uri,
    val name: String,
    val width: Int,
    val height: Int
)
