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

        // 사용자의 응답을 처리하는 권한 콜백 등록, 복수 권한 요청하는 registerForActivityResult 콜백 함수
        // 즉시 실행되지는 않고 어떤 함수가 launch (실행) 시켰을 경우에만 실행된다.
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // 사용자에게 부여할 읽기 권한 지정
            readPermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermission
            // 사용자에게 부여할 쓰기 권한 지정
            writePermission = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermission

            //만일 읽기 권한이 있다면 내부 저장소의 이미지 파일들을 recyclerView 로 나타낸다.
            if(readPermission) {
                loadInternalStorageIntoRecyclerView()
            } else { // 읽기 권한이 없다면 권한 없다는 사실을 메시지 띄워 알림
                Toast.makeText(this@MainActivity, "권한 허용 안됨", Toast.LENGTH_SHORT).show()
            }

            // 맨 처음 실행되는 함수
            // 권한 요청 업데이트 함수
            updateOrRequestPermission()
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
}