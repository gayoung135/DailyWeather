package com.smartapp.dailyweather

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

// 실제 안드로이드 기기(또는 에뮬레이터) 환경에서 실행되는 테스트 코드.
// 즉, 코드가 에뮬레이터에서 정상 동작하는지 검증할 때 사용함.
// JUnit + AndroidJUnit4 테스트 프레임워크 기반.

// @RunWith: 어떤 테스트 러너로 실행할지를 지정
// AndroidJUnit4 → 안드로이드 환경에서 테스트 실행을 담당함.
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    // @Test: JUnit에서 테스트 메서드임을 의미.
    // 이 함수는 앱의 Context(앱 이름, 패키지 정보 등)를 가져와서
    // 실제 패키지명이 올바른지 확인하는 테스트임.
    @Test
    fun useAppContext() {
        // Context of the app under test.    //(테스트 중인 앱의 컨텍스트 가져오기)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // assertEquals(): 기대값과 실제값을 비교하는 테스트 구문
        // "com.smartapp.dailyweather"가 실제 앱의 패키지명과 동일한지 확인
        assertEquals("com.smartapp.dailyweather", appContext.packageName)
    }
}

//요약
//실제 안드로이드 기기에서 앱의 환경(Context)이
// 정상 작동하는지 확인하는 테스트 코드.