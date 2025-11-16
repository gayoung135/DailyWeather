package com.smartapp.dailyweather.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
/*
네트워크 연결 감지 클래스

1. isonline : 인터넷/인터넷x 실시간으로 알려주는 stateflow
2. start()/stop() : 감시 시작/종료(중복x)
3.판정 기준 : INTERNET && VALIDATED(두개 다 있어야 네트워크o)
*/

class NetworkObserver(
    private val context: Context
) {
    //실제 값을 저장한느 곳
    private val _isOnline = MutableStateFlow(false)
    //다른 화면들이 읽을 수 있는 감지신호 isOnline 생성
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    //네트워크 관리자 등록(네트워크 연결 상태가 어떤지 받는 것)
    private val cm: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    //콜백 등록
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    /*
    _isOnline.emit(true) : 인터넷 켜짐
    _isOnline.emit(false) : 인터넷 꺼짐
    emit() -> 값을 보내는 것
     */

    //네트워크 감지를 시작하는 함수
    fun start() {
        if (callback != null) return // 이미 시작되었다면 리턴
        //인터넷o(onAvailable), 꺼질 때(onLost)마다 호출되는 콜백 등록
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitNow()
            }
            override fun onLost(network: Network) {
                emitNow()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // VALIDATED 부여/해제 등 변화 즉시 반영
                emitNow()
            }
        }

        // SDK 버전에 따라 등록 방식 분리(신버전/구버전)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //네트워크 상태를 알아서 감지 해줘! 하는 것
            callback?.let { cm.registerDefaultNetworkCallback(it) }
        } else {
            //구버전은
            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            //
            callback?.let { cm.registerNetworkCallback(request, it) }
        }

        // 등록 직후 스냅샷 반영
        emitNow()
    }

    //네트워크 관찰을 멈추는 함수(메모리 누수 방지, 배터리 감소 방지)
    fun stop() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    //
    fun isOnlineNow(): Boolean = checkNow()


    //emit()을 즉시(지금 당장) 보내는 것
    private fun emitNow() {
        scope.launch { _isOnline.emit(checkNow()) }
    }

    private fun checkNow(): Boolean {
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            // 구버전은 VALIDATED 개념 없음 → INTERNET만 있으면 OK로 간주
            true
        }
        return hasInternet && validated
    }
}
