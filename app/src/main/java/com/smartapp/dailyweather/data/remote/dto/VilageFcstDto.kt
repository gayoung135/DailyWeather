package com.smartapp.dailyweather.data.remote.dto

data class VilageFcstResponse(
    val response: VilageFcstOuter?
)

data class VilageFcstOuter(
    val header: VilageFcstHeader?,
    val body: VilageFcstBody?
)

data class VilageFcstHeader(
    val resultCode: String?,
    val resultMsg: String?
)

data class VilageFcstBody(
    val dataType: String?,
    val items: VilageFcstItems?,
    val pageNo: Int?,
    val numOfRows: Int?,
    val totalCount: Int?
)

data class VilageFcstItems(
    val item: List<VilageFcstItem>?
)

data class VilageFcstItem(
    val baseDate: String,  // "20240628"
    val baseTime: String,  // "0500"
    val category: String,  // "TMP", "POP" ...
    val fcstDate: String,  // "20240628"
    val fcstTime: String,  // "0900"
    val fcstValue: String, // "21"
    val nx: Int,
    val ny: Int
)