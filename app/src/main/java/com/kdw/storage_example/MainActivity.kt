package com.kdw.storage_example

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.kdw.storage_example.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.*


// https://codechacha.com/ko/android-mediastore-insert-media-files/
// 참조 : https://github.com/philipplackner/AndroidStorage
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var internalPhotoAdapter: InternalPhotoAdapter // 내부 저장소 adapter
    private lateinit var externalPhotoAdapter: ExternalPhotoAdapter // 외부 저장소 adapter

    private var readPermission = false // 읽기 권한
    private var writePermission = false // 쓰기 권한

    private lateinit var permissionLauncher : ActivityResultLauncher<Array<String>> // 권한 허용
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver: ContentObserver

    private var deletedImageUri : Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalPhotoAdapter = InternalPhotoAdapter {
            lifecycleScope.launch {
                val isDeleted = deletePhotoFromInternal(it.name)
                if(isDeleted) {
                    loadInternalStorageIntoRecyclerView()
                    Toast.makeText(this@MainActivity, "사진 삭제 완료", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "사진 삭제 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        externalPhotoAdapter = ExternalPhotoAdapter {
            lifecycleScope.launch {
                deletePhotoFromExternal(it.contentUri)
                deletedImageUri = it.contentUri
            }
        }

        // 사용자의 응답을 처리하는 권한 콜백 등록, 복수 권한 요청하는 registerForActivityResult 콜백 함수
        // 즉시 실행되지는 않고 어떤 함수가 launch (실행) 시켰을 경우에만 실행된다.
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // 사용자에게 부여할 읽기 권한 지정
            readPermission =
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermission
            // 사용자에게 부여할 쓰기 권한 지정
            writePermission =
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermission

            //만일 읽기 권한이 있다면 내부 저장소의 이미지 파일들을 recyclerView 로 나타낸다.
            if (readPermission) {
                loadInternalStorageIntoRecyclerView()
            } else { // 읽기 권한이 없다면 권한 없다는 사실을 메시지 띄워 알림
                Toast.makeText(this@MainActivity, "권한 허용 안됨", Toast.LENGTH_SHORT).show()
            }

            intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if(it.resultCode == RESULT_OK) {
                    if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        lifecycleScope.launch {
                            deletePhotoFromExternal(deletedImageUri ?: return@launch)
                        }
                        Toast.makeText(this@MainActivity, "사진 삭제 완료", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "사진 삭제 불가능", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val takePhotos = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
                lifecycleScope.launch {
                    val isPrivate = binding.switchPrivate.isChecked
                    val isSaved = when {
                        isPrivate -> savePhotoToInternal(UUID.randomUUID().toString(), it!!)
                        writePermission -> savePhotoToExternal(UUID.randomUUID().toString(), it!!)
                        else -> false
                    }

                    if(isPrivate)
                        loadInternalStorageIntoRecyclerView()

                    if(isSaved) {
                        Toast.makeText(this@MainActivity, "사진 저장 성공", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "사진 저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.btnTakePhoto.setOnClickListener {
                takePhotos.launch()
            }

            // 맨 처음 실행되는 함수
            // 권한 요청 업데이트 함수
            updateOrRequestPermission()

            initContentObserver()


        }
    }

    private fun updateOrRequestPermission() {
        // checkSelfPermission() -> 앱이 퍼미션을 갖고 있는지 확인 가능
        // hasReadPermission 는 읽기 권한이 있는지 여부 확인.
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED // PERMISSION_GRANTED 가 리턴되었다면 읽기 권한이 있다는 것

        // 쓰기 권한 여부 확인
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val sdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q // API 레벨이 29 이상인지에 대한 Boolean 변수

        readPermission = hasReadPermission
        writePermission = hasWritePermission || sdk29 // 쓰기 권한은 권한을 허용했거나 API 버전이 29 이상이면 true 반환

        val permissionToRequest = mutableListOf<String>()

        if(!writePermission) {
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(!readPermission) {
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if(permissionToRequest.isNotEmpty()) { // 권한이 허용되지 않은 것이 있다면 permissionLauncher 콜백 함수 실행시킨다.
            permissionLauncher.launch(permissionToRequest.toTypedArray())
        }
    }

    private fun initContentObserver() {
        // 다른 Application 에서 미디어(이미지, 동영상 또는 파일) 추가/삭제 여부 감지하기 위해 ContentObserver 사용
        contentObserver = object: ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if(readPermission) {
                    loadExternalStorageIntoRecyclerView()
                }
            }
        }
        // URI : observe 할 Uri, boolean 값은 해당 Uri만 할 것인지, Uri 자손의 변화까지 감지할 것인지 설정
        // 마지막 인자는 변경되었을 때 호출될 observer
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private suspend fun savePhotoToExternal(displayName: String, bmp: Bitmap) : Boolean {
        return withContext(Dispatchers.IO) {
            val galleryCollection = sdk29Up {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bmp.width)
                put(MediaStore.Images.Media.HEIGHT, bmp.height)
            }

            try {
                contentResolver.insert(galleryCollection, contentValues)?.also { uri ->
                    contentResolver.openOutputStream(uri).use { outputStream ->
                        if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                            throw IOException("bitmap 저장 불가능")
                        }
                    }
                } ?: throw IOException("MediaStore entry 생성 불가능")
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun savePhotoToInternal(fileName: String, bmp: Bitmap) : Boolean {
        return withContext(Dispatchers.IO) {
            try{
                openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                    if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                        throw IOException("bitmap 저장 불가능")
                    }
                }
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun deletePhotoFromInternal(fileName: String): Boolean{
        return withContext(Dispatchers.IO) {
            try{
                deleteFile(fileName)
            } catch(e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun deletePhotoFromExternal(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try{
                contentResolver.delete(photoUri, null, null)
            } catch(e: SecurityException) {
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }

                intentSender?.let {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(it).build()
                    )
                }
            }
        }
    }


    private fun loadInternalStorageIntoRecyclerView() {
       // lifecycle 객체 대상(Activity, Fragment, Service 등등), lifecycle 이 끝나면 코루틴 작업이 자동으로 취소됨
        lifecycleScope.launch {
            val photoItems = loadPhotoFromInternalStorage() // 사진 객체를 얻어옴
            internalPhotoAdapter.submitList(photoItems) // 아이템 업데이트
        }

        // val photo = loadPhotoFromInternalStorage(), 이렇게 할 시 오류
    }

    // suspend fun 함수는 일시중단 가능한 함수를 지칭. 해당 함수는 무조건 코루틴 내부에서 실행해야 한다.
    private suspend fun loadPhotoFromInternalStorage() : List<InternalData> {
        // 코루틴에서 Dispatcher 변경할 때 사용, 이때 스레드도 변경
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles() // 파일 목록들을 가지고 온다
            // filter 함수는 리스트 내의 인자들 중 일치하는 인자만 필터링하는 함수
            // 읽기 가능한 파일, 파일 경로가 있는 파일, ".jpg"로 끝나는 파일만 필터링한다.
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes() // 파일의 전체 내용을 byte 배열로 가져온다.
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) // 사진 데이터 기반으로 bitmap 만들 때 사용
                InternalData(it.name, bmp)
            } ?: listOf()
        } // 결과 값을 반환할 때까지 기다린다. (await+async)
    }

    private fun loadExternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photoItems = loadPhotoFromExternalStorage()
            externalPhotoAdapter.submitList(photoItems)
        }
    }

    private suspend fun loadPhotoFromExternalStorage() : List<ExternalData> {
        return withContext(Dispatchers.IO) {
            val collection = sdk29Up {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
            val photos = mutableListOf<ExternalData>()

            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while(cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photos.add(ExternalData(id, contentUri, displayName, width, height))
                }
                photos.toList()
            }
        } ?: listOf()
    }

    override fun onDestroy() {
        super.onDestroy()
        // unregister
        contentResolver.unregisterContentObserver(contentObserver)
    }
}