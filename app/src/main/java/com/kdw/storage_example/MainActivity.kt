package com.kdw.storage_example

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.kdw.storage_example.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var internalPhotoAdapter: InternalPhotoAdapter
    private lateinit var externalPhotoAdapter: ExternalPhotoAdapter

    private var readPermission = false
    private var writePermission = false

    private lateinit var permissionLauncher : ActivityResultLauncher<Array<String>>
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver: ContentObserver

    private var deletedImageUri : Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            readPermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermission
            writePermission = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermission

            if(readPermission) {
                loadInternalStorageIntoRecyclerView()
            } else {
                Toast.makeText(this@MainActivity, "권한 허용 안됨", Toast.LENGTH_SHORT).show()
            }
            updateOrRequestPermission()
        }


    }

    private fun updateOrRequestPermission() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val sdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermission = hasReadPermission
        writePermission = hasWritePermission || sdk29

        val permissionToRequest = mutableListOf<String>()

        if(!writePermission) {
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(!readPermission) {
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if(permissionToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionToRequest.toTypedArray())
        }
    }

    private fun loadInternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photoItems = loadPhotoFromInternalStorage()
            internalPhotoAdapter.submitList(photoItems)
        }
    }

    private suspend fun loadPhotoFromInternalStorage() : List<InternalData> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalData(it.name, bmp)
            } ?: listOf()
        }
    }
}