package com.smartapp.dailyweather.core

fun normalizeSidoName(adminArea: String): String =
    when {
        adminArea.startsWith("서울") -> "서울"
        adminArea.startsWith("부산") -> "부산"
        adminArea.startsWith("대구") -> "대구"
        adminArea.startsWith("인천") -> "인천"
        adminArea.startsWith("광주") -> "광주"
        adminArea.startsWith("대전") -> "대전"
        adminArea.startsWith("울산") -> "울산"
        adminArea.startsWith("세종") -> "세종"
        adminArea.startsWith("경기도") || adminArea.startsWith("경기") -> "경기"
        adminArea.startsWith("강원") -> "강원"
        adminArea.startsWith("충청북도") || adminArea.startsWith("충북") -> "충북"
        adminArea.startsWith("충청남도") || adminArea.startsWith("충남") -> "충남"
        adminArea.startsWith("전라북도") || adminArea.startsWith("전북") -> "전북"
        adminArea.startsWith("전라남도") || adminArea.startsWith("전남") -> "전남"
        adminArea.startsWith("경상북도") || adminArea.startsWith("경북") -> "경북"
        adminArea.startsWith("경상남도") || adminArea.startsWith("경남") -> "경남"
        adminArea.startsWith("제주") -> "제주"
        else -> adminArea
    }