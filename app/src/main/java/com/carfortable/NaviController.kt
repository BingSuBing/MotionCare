package com.carfortable

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

// 화면 표시용 내비 상태
data class NaviState(
    val connected: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val guiding: Boolean = false,
    val destinationName: String? = null,
    val remainDistanceM: Int? = null,
    val remainTimeS: Int? = null,
    val waypoints: List<String> = emptyList(),
    val message: String? = null   // 마지막 안내·오류 문구
)

// Pleos NaviHelper SDK 연결 (현재 위치, 경로 정보, 경유지 추가)
// 순서: initialize → onConnected에서 addListener → 요청(get…/addWaypoint) → 결과는 리스너 콜백으로 와요
class NaviController(context: Context) {

    private val helper = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var connected = false

    var state = NaviState()
        private set

    // 상태가 바뀔 때마다 호출 (메인 스레드)
    var onState: ((NaviState) -> Unit)? = null

    // 휴식처 경유지 처리 결과 (모두 메인 스레드)
    // - confirmed: 내비가 경유지 변경/경로 시작으로 받아들였을 때. etaMin은 모르면 null
    // - eta: 바로 안내한 경우 내비의 남은 시간이 오면 다시 알려줘요
    // - failed: 내비 오류이거나 응답이 없을 때
    // - cleared: 휴식처에 도착했거나 경로가 취소됐을 때
    var onRestStopConfirmed: ((RestStop, Int?) -> Unit)? = null
    var onRestStopEta: ((RestStop, Int) -> Unit)? = null
    var onRestStopFailed: ((RestStop, String) -> Unit)? = null
    var onRestStopCleared: (() -> Unit)? = null

    // 내비 응답을 기다리는 휴식처 (direct = 경로가 없어서 바로 안내)
    private var pendingRest: RestStop? = null
    private var pendingDirect = false
    // 내비가 받아들인 휴식처 (도착·취소 때 지우려고)
    private var activeRest: RestStop? = null
    // 바로 안내한 휴식처: 목적지 정보(남은 시간)가 오면 ETA를 알려줘요
    private var etaTarget: RestStop? = null

    private val restTimeout = Runnable {
        pendingRest?.let { failRest(it, "내비 응답이 없어요") }
    }

    private fun confirmRest() {
        val stop = pendingRest ?: return
        main.removeCallbacks(restTimeout)
        pendingRest = null
        activeRest = stop
        etaTarget = if (pendingDirect) stop else null
        onRestStopConfirmed?.invoke(stop, null)
    }

    private fun failRest(stop: RestStop, reason: String) {
        main.removeCallbacks(restTimeout)
        pendingRest = null
        update { it.copy(message = "${stop.name} 추가에 실패했어요: $reason") }
        onRestStopFailed?.invoke(stop, reason)
    }

    private fun clearRest() {
        if (activeRest == null) return
        activeRest = null
        etaTarget = null
        onRestStopCleared?.invoke()
    }

    private fun update(block: (NaviState) -> NaviState) {
        main.post {
            state = block(state)
            onState?.invoke(state)
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            update { it.copy(guiding = true) }
            // 내비가 새 경로(휴식처로 바로 안내 또는 경유지 반영)를 시작함
            main.post { if (pendingRest != null) confirmRest() }
            refresh()
        }

        override fun onRoutingCompleted() = refresh()

        // 에뮬레이터 내비 서비스(3.0.4)는 onRoutingCompleted 대신 이걸 보내요
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onRouteEnded() = refresh()

        // 경유지를 넣으면 재탐색으로 끝나는 경우도 있어요
        override fun onReRouteEnded() {
            main.post { if (pendingRest != null) confirmRest() }
            refresh()
        }

        override fun onRouteCancelled() {
            main.post { clearRest() }
            update {
                it.copy(guiding = false, destinationName = null, remainDistanceM = null,
                    remainTimeS = null, waypoints = emptyList())
            }
        }

        override fun onDestinationChanged() = refresh()

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            update { it.copy(message = "경유지가 바뀌었어요") }
            main.post { if (pendingRest != null) confirmRest() }
            refresh()
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            update { it.copy(message = "${waypointArrivedInfo.name}에 도착했어요") }
            main.post { clearRest() }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            update { it.copy(guiding = false, message = "${destinationArrivedInfo.name}에 도착했어요") }
            main.post { clearRest() }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 바로 안내한 휴식처면 내비의 남은 시간으로 ETA 갱신
            @Suppress("USELESS_ELVIS")
            val remainS: Int = destinationInfo.remainTime ?: 0
            main.post {
                val target = etaTarget
                if (target != null && remainS > 0) {
                    etaTarget = null
                    onRestStopEta?.invoke(target, maxOf(1, (remainS + 59) / 60))
                }
            }
            update {
                it.copy(
                    destinationName = destinationInfo.name?.takeIf { n -> n.isNotBlank() },
                    remainDistanceM = destinationInfo.remainDistance,
                    remainTimeS = destinationInfo.remainTime
                )
            }
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            update { it.copy(waypoints = waypointInfoList.mapNotNull { w -> w.name }) }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val lat = currentLocationInfo.latitude
            val lng = currentLocationInfo.longitude
            if (abs(lat) < 0.0001 && abs(lng) < 0.0001) return // 아직 위치 없음
            update { it.copy(lat = lat, lng = lng) }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            when (status) {
                NavigationStatus.ROUTE_DRIVE -> {
                    update { it.copy(guiding = true) }
                    refresh()
                }
                NavigationStatus.FREE_DRIVE, NavigationStatus.INACTIVE -> update { it.copy(guiding = false) }
                NavigationStatus.INITIALIZING -> Unit
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "내비 오류: $errorCode")
            update { it.copy(message = "내비 오류: ${errorCode.name}") }
            main.post { pendingRest?.let { failRest(it, "내비 오류 ${errorCode.name}") } }
        }

        // 아래는 지금 쓰지 않는 콜백 (SDK 3.0.5 인터페이스를 채우려고 둬요)
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = Unit
        override fun onRouteOptionChanged(routeOption: RouteOption) = Unit
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = Unit
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = Unit
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = Unit
        override fun onBookMarkChanged() = Unit
        override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) = Unit
        override fun onTripInfo(tripInfo: TripInfo) = Unit
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = Unit
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = Unit
        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) = Unit
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = Unit
    }

    // 위치는 계속 바뀌니까 10초마다 다시 물어봐요
    private val poll = object : Runnable {
        override fun run() {
            if (connected) runCatching { helper.getCurrentLocationInfo() }
            main.postDelayed(this, POLL_MS)
        }
    }

    fun connect() {
        try {
            helper.initialize(
                onConnected = {
                    helper.addListener(listener)
                    connected = true
                    update { it.copy(connected = true, message = null) }
                    refresh()
                    // 다시 연결돼도 위치 조회가 겹치지 않게
                    main.removeCallbacks(poll)
                    main.post(poll)
                },
                onFailed = { t ->
                    Log.e(TAG, "내비 연결 실패", t)
                    update { it.copy(connected = false, message = "내비에 연결하지 못했어요") }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "내비 initialize 예외", t)
            update { it.copy(connected = false, message = "내비에 연결하지 못했어요") }
        }
    }

    fun refresh() {
        if (!connected) return
        runCatching {
            helper.getCurrentLocationInfo()
            helper.getRouteStateInfo()
            helper.getDestinationInfo()
            helper.getWaypointInfo()
        }.onFailure { Log.w(TAG, "내비 정보 요청 실패", it) }
    }

    // 휴식처를 경유지로: 안내 중이면 첫 번째 경유지로 추가, 아니면 그곳으로 바로 경로 안내
    // true = 내비에 요청을 보냄. 실제 반영 여부는 onRestStopConfirmed / onRestStopFailed로 와요
    fun addRestStop(stop: RestStop): Boolean {
        if (!connected) {
            update { it.copy(message = "내비에 연결되지 않았어요") }
            return false
        }
        if (pendingRest != null) return false
        val direct = !state.guiding
        pendingRest = stop
        pendingDirect = direct
        main.removeCallbacks(restTimeout)
        main.postDelayed(restTimeout, REST_CONFIRM_TIMEOUT_MS)
        return try {
            if (!direct) {
                helper.addWaypoint(
                    RequestWaypointInfo(
                        latitude = stop.lat,
                        longitude = stop.lng,
                        poiName = stop.name,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        address = stop.address,
                        poiSubId = "0"
                    )
                )
                update { it.copy(message = "${stop.name}을(를) 경유지로 추가하는 중...") }
            } else {
                helper.requestRoute(
                    RouteInfo(
                        latitude = stop.lat,
                        longitude = stop.lng,
                        displayLatitude = null,
                        displayLongitude = null,
                        poiName = stop.name,
                        poiId = "",
                        poiSubId = "0",
                        address = stop.address,
                        routeOption = RouteOption.RECOMMENDED
                    )
                )
                update { it.copy(message = "안내 중인 경로가 없어서 ${stop.name}(으)로 바로 안내를 요청했어요") }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "경유지 추가 실패", t)
            main.removeCallbacks(restTimeout)
            pendingRest = null
            update { it.copy(message = "경유지 추가에 실패했어요: ${t.message}") }
            false
        }
    }

    // 직접 고르고 싶을 때: Pleos 내비의 장소 검색 화면 열기
    fun openNaviSearch(keyword: String) {
        if (!connected) return
        runCatching { helper.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword)) }
            .onFailure { Log.w(TAG, "내비 검색 화면 열기 실패", it) }
    }

    fun release() {
        main.removeCallbacks(poll)
        main.removeCallbacks(restTimeout)
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        connected = false
    }

    companion object {
        private const val TAG = "MotionCare"
        private const val POLL_MS = 10_000L
        private const val REST_CONFIRM_TIMEOUT_MS = 15_000L
    }
}