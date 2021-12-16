package com.kdw.storage_example

import android.os.Build

// 인라인(inline) 함수 사용하면 코드는 객체를 항상 새로 만드는 것이 아니다.
// 해당 함수의 내용을 호출한 함수에 넣는 방식으로 컴파일 코드 작성
inline fun <T> sdk29Up(onSdk29: () -> T) : T? {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onSdk29()
    } else null
}