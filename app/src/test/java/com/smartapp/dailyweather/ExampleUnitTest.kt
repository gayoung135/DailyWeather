package com.smartapp.dailyweather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

// "로컬 단위 테스트(Local Unit Test)"를 수행하는 클래스.
// 실제 안드로이드 기기(에뮬레이터) 없이,
// 개발자 PC(호스트 환경)에서 코드 로직이 올바르게 작동하는지 검증하는 용도.

// JUnit 기반 테스트이며, 메서드에 @Test를 붙이면 자동으로 테스트 항목으로 인식됨.
class ExampleUnitTest {

    // @Test: 이 함수가 단위 테스트임을 표시.
    // 단순 예시로 2 + 2 연산 결과가 4인지 확인하는 기본 테스트 코드.
    // 성공 시 PASS, 실패 시 FAIL 로 표시됨.
    @Test
    fun addition_isCorrect() {
        // assertEquals(예상값, 실제값)
        // → 실제 계산 결과가 예상값과 일치하는지 비교.
        assertEquals(4, 2 + 2)
    }
}

//요약
//개발자 PC 환경에서 코드 로직이
// 올바르게 동작하는지 확인하는 기본 단위 테스트 코드.