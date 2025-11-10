package com.smartapp.dailyweather.data.model

/**
 * 전국 행정구역 (시/도 → 시/군/구)
 * ManualLocationFragment와 ViewModel에서 공통 사용
 */

enum class Region(val displayName: String, val subRegions: List<SubRegion>) {

    SEOUL("서울특별시", listOf(
        SubRegion("종로구", 37.597012, 126.977207),
        SubRegion("중구", 37.561724, 126.995545),
        SubRegion("용산구", 37.552055, 126.980150),
        SubRegion("성동구", 37.559573, 127.038726),
        SubRegion("광진구", 37.547281, 127.083997),
        SubRegion("동대문구", 37.582694, 127.055476),
        SubRegion("중랑구", 37.596324, 127.094465),
        SubRegion("성북구", 37.609218, 127.018369),
        SubRegion("강북구", 37.646461, 127.011084),
        SubRegion("도봉구", 37.667775, 127.035181),
        SubRegion("노원구", 37.641740, 127.075514),
        SubRegion("은평구", 37.623159, 126.927308),
        SubRegion("서대문구", 37.579070, 126.935646),
        SubRegion("마포구", 37.561057, 126.907710),
        SubRegion("양천구", 37.527754, 126.855438),
        SubRegion("강서구", 37.564567, 126.806802),
        SubRegion("구로구", 37.498018, 126.855365),
        SubRegion("금천구", 37.463343, 126.900822),
        SubRegion("영등포구", 37.524994, 126.909444),
        SubRegion("동작구", 37.500437, 126.951157),
        SubRegion("관악구", 37.485815, 126.913214),
        SubRegion("서초구", 37.479447, 127.031585),
        SubRegion("강남구", 37.517236, 127.047325),
        SubRegion("송파구", 37.514575, 127.105399),
        SubRegion("강동구", 37.553342, 127.144457)
    )),

    BUSAN("부산광역시", listOf(
        SubRegion("연제구", 35.190082, 129.081414)
    )),

    DAEGU("대구광역시", listOf(
        SubRegion("중구", 35.867815, 128.594928)
    )),

    INCHEON("인천광역시", listOf(
        SubRegion("남동구", 37.403501, 126.706315)
    )),

    GWANGJU("광주광역시", listOf(
        SubRegion("서구", 35.152734, 126.856231)
    )),

    DAEJEON("대전광역시", listOf(
        SubRegion("서구", 36.284149, 127.344207)
    )),

    ULSAN("울산광역시", listOf(
        SubRegion("울산", 35.518567, 129.329176)
    )),

    SEJONG("세종특별자치시", listOf(
        SubRegion("보람동", 36.478919, 127.290139)
    )),

    GYEONGGI("경기도", listOf(
        SubRegion("성남시", 37.420000, 127.126000),
        SubRegion("의정부시", 37.758229, 127.053159),
        SubRegion("안양시", 37.385393, 126.935490),
        SubRegion("부천시", 37.510082, 126.784217),
        SubRegion("광명시", 37.483820, 126.862340),
        SubRegion("평택시", 37.032443, 126.846457),
        SubRegion("동두천시", 37.912453, 127.056659),
        SubRegion("안산시", 37.326329, 126.817748),
        SubRegion("고양시", 37.671334, 126.790173),
        SubRegion("과천시", 37.431906, 126.994565),
        SubRegion("구리시", 37.602103, 127.128995),
        SubRegion("남양주시", 37.632943, 127.174254),
        SubRegion("오산시", 37.156539, 127.073298),
        SubRegion("시흥시", 37.358776, 126.738795),
        SubRegion("군포시", 37.356048, 126.946013),
        SubRegion("의왕시", 37.356755, 126.977677),
        SubRegion("하남시", 37.555317, 127.205806),
        SubRegion("용인시", 37.253755, 127.189824),
        SubRegion("파주시", 37.772127, 126.787448),
        SubRegion("이천시", 37.307956, 127.448853),
        SubRegion("안성시", 37.026215, 127.273640),
        SubRegion("김포시", 37.681103, 126.586134),
        SubRegion("화성시", 37.176945, 126.899613),
        SubRegion("광주시", 37.442871, 127.267900),
        SubRegion("양주시", 37.792362, 127.047428),
        SubRegion("포천시", 37.929718, 127.208698),
        SubRegion("여주시", 37.341039, 127.628617),
        SubRegion("연천군", 38.024822, 127.071163),
        SubRegion("가평군", 37.845913, 127.512262),
        SubRegion("양평군", 37.501461, 127.496836)
    )),

    GANGWON("강원특별자치도", listOf(
        SubRegion("춘천시", 37.881317, 127.729971),
        SubRegion("원주시", 37.342219, 127.920162),
        SubRegion("강릉시", 37.751853, 128.876057)
    )),

    CHUNGBUK("충청북도", listOf(
        SubRegion("청주시", 36.642434, 127.489032),
        SubRegion("충주시", 36.991011, 127.925153)
    )),

    CHUNGNAM("충청남도", listOf(
        SubRegion("천안시", 36.815092, 127.113892),
        SubRegion("공주시", 36.446661, 127.119100),
        SubRegion("보령시", 36.333206, 126.612751)
    )),

    JEONBUK("전북특별자치도", listOf(
        SubRegion("전주시", 35.824223, 127.147953),
        SubRegion("익산시", 35.948286, 126.957599),
        SubRegion("군산시", 35.967677, 126.736629)
    )),

    JEONNAM("전라남도", listOf(
        SubRegion("목포시", 34.811835, 126.392166),
        SubRegion("여수시", 34.760373, 127.662222),
        SubRegion("순천시", 34.948081, 127.489056)
    )),

    GYEONGBUK("경상북도", listOf(
        SubRegion("포항시", 36.019018, 129.343480),
        SubRegion("경주시", 35.856171, 129.224748),
        SubRegion("구미시", 36.119562, 128.344589)
    )),

    GYEONGNAM("경상남도", listOf(
        SubRegion("창원시", 35.227830, 128.681239),
        SubRegion("진주시", 35.180188, 128.107621),
        SubRegion("거제시", 34.880630, 128.621660)
    )),

    JEJU("제주특별자치도", listOf(
        SubRegion("제주시", 33.499621, 126.531188),
        SubRegion("서귀포시", 33.254120, 126.560076)
    )),

    SELECT("시/도", listOf(SubRegion("시/군/구", 0.0, 0.0))),;

    companion object {
        fun fromDisplayName(name: String): Region? =
            entries.find { it.displayName == name }
    }
}

/**
 * 시/군/구 단위
 */
data class SubRegion(
    val name: String,
    val latitude: Double,
    val longitude: Double
)