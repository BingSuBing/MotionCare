package com.carfortable

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ai.pleos.playground.vehicle.Vehicle
import com.carfortable.shared.Paths
import com.carfortable.ui.theme.MotionCareTheme

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null
    private var navi: NaviController? = null
    private var hvac: HvacController? = null
    private var session: SessionManager? = null
    private var tempSync: TempApprovalSync? = null
    private var recommender: TempRecommender? = null
    private var requestSync: CareRequestSync? = null
    private var watcher: SessionWatcher? = null

    // 화면 상태
    private var started by mutableStateOf(false)          // false: 메인(QR) 화면, true: 여정 화면
    private var tab by mutableStateOf(CarTab.HOME)
    private var sessionId by mutableStateOf<String?>(null)
    private var joinUrl by mutableStateOf("")
    private var sessionError by mutableStateOf<String?>(null)
    private var passengers by mutableStateOf<List<PassengerItem>>(emptyList())
    private var cabin by mutableStateOf(CabinState())
    private var airBusy by mutableStateOf(false)
    private var pendingRequests by mutableStateOf<List<PendingRequest>>(emptyList())
    private var proposal by mutableStateOf<TempProposal?>(null)
    private var approving by mutableStateOf(false)
    private var naviState by mutableStateOf(NaviState())
    private var rest by mutableStateOf(RestUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1) Vehicle SDK 연결
        val v = Vehicle(this)
        v.initialize()
        vehicle = v

        // 1-1) 내비 연결 (현재 위치, 경로, 경유지)
        val n = NaviController(this)
        n.onState = { state -> naviState = state }
        n.connect()
        navi = n

        // 2) 세션 생성
        val h = HvacController(v)
        hvac = h
        session = SessionManager(h)
        startSession()

        setContent {
            MotionCareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (!started) {
                            LobbyScreen(
                                sessionId = sessionId,
                                joinUrl = joinUrl,
                                errorText = sessionError,
                                driverName = SessionManager.DRIVER_NAME,
                                passengers = passengers,
                                onRemove = { p -> watcher?.removePassenger(p.seat) },
                                onStart = { started = true }
                            )
                        } else {
                            CarShell(
                                tab = tab,
                                onTab = { tab = it },
                                cabin = cabin,
                                airBusy = airBusy,
                                onToggleAir = { toggleAirIntake() },
                                onEndTrip = { endTrip() },
                                overlay = {
                                    AlertsOverlay(
                                        requests = pendingRequests,
                                        passengers = passengers,
                                        recirc = cabin.recirc,
                                        airBusy = airBusy,
                                        onConfirm = { req -> requestSync?.acknowledge(req.id) },
                                        onFreshAirAndConfirm = { req -> freshAirAndConfirm(req) },
                                        onFindRest = { req ->
                                            requestSync?.acknowledge(req.id)
                                            tab = CarTab.JOURNEY
                                            findRestStops()
                                        },
                                        proposal = proposal,
                                        showProposal = tab != CarTab.CLIMATE,
                                        approving = approving,
                                        onApprove = { approveTemp() },
                                        onDecline = { tempSync?.decline() }
                                    )
                                }
                            ) {
                                when (tab) {
                                    CarTab.HOME -> HomeScreen(naviState, rest)
                                    CarTab.JOURNEY -> JourneyScreen(
                                        navi = naviState,
                                        rest = rest,
                                        onSearch = { findRestStops() },
                                        onAdd = { stop -> addRestStop(stop) },
                                        onOpenNaviSearch = { navi?.openNaviSearch("휴게소") }
                                    )
                                    CarTab.PASSENGERS -> PassengersScreen(
                                        driverName = SessionManager.DRIVER_NAME,
                                        cabin = cabin,
                                        passengers = passengers,
                                        onRename = { p, name -> watcher?.renamePassenger(p.seat, name) },
                                        onRemove = { p -> watcher?.removePassenger(p.seat) },
                                        onGoClimate = { tab = CarTab.CLIMATE }
                                    )
                                    CarTab.CLIMATE -> ClimateScreen(
                                        passengers = passengers,
                                        cabin = cabin,
                                        proposal = proposal,
                                        approving = approving,
                                        airBusy = airBusy,
                                        onApprove = { approveTemp() },
                                        onDecline = { tempSync?.decline() },
                                        onToggleAir = { toggleAirIntake() }
                                    )
                                    else -> ComingSoonScreen(tab)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 세션 시작
    //   debug: local.properties의 devSessionId, 없으면 공용 test01
    //   release: 무작위 6자리 ID
    private fun startSession() {
        val s = session ?: return
        val h = hvac ?: return
        val devSessionId = if (BuildConfig.DEBUG) {
            BuildConfig.DEV_SESSION_ID.ifBlank { Paths.DEV_SESSION_ID }
        } else {
            null
        }
        s.start(devSessionId = devSessionId) { result ->
            runOnUiThread {
                result
                    .onSuccess { sid ->
                        sessionId = sid
                        joinUrl = SessionManager.joinUrl(sid)
                        startWatcher(sid)
                        startTempSync(sid, h)
                        startRecommender(sid, h)
                        startRequestSync(sid)
                    }
                    .onFailure { e ->
                        Log.e(TAG, "세션 생성 실패", e)
                        sessionError = "세션 실패\n${e.message}"
                    }
            }
        }
    }

    // 여정 종료: active = false → 구독 정리 → 새 세션으로 처음 화면
    private fun endTrip() {
        val s = session ?: return
        s.end {
            runOnUiThread {
                stopSyncs()
                started = false
                tab = CarTab.HOME
                sessionId = null
                joinUrl = ""
                sessionError = null
                passengers = emptyList()
                cabin = CabinState()
                pendingRequests = emptyList()
                proposal = null
                rest = RestUiState()
                startSession()
            }
        }
    }

    private fun stopSyncs() {
        watcher?.stop()
        watcher = null
        tempSync?.stop()
        tempSync = null
        recommender?.stop()
        recommender = null
        requestSync?.stop()
        requestSync = null
    }

    // 3) 탑승자 목록·실내 상태 구독 (화면 표시용)
    private fun startWatcher(sid: String) {
        val w = SessionWatcher(sid)
        w.onPassengers = { items -> runOnUiThread { passengers = items } }
        w.onCabin = { state -> runOnUiThread { cabin = state } }
        w.start()
        watcher = w
    }

    // 4-1) 추천 온도 계산 (passengers + requests 구독, 30분 타이머)
    private fun startRecommender(sid: String, hvac: HvacController) {
        val r = TempRecommender(sid, hvac)
        r.start()
        recommender = r
    }

    // 4-2) 추천 온도를 운전자 확인 후 적용
    private fun startTempSync(sid: String, hvac: HvacController) {
        val sync = TempApprovalSync(sid, hvac)
        sync.onStatus = { text -> Log.d(TAG, "온도 동기화: $text") }
        sync.onProposal = { target ->
            runOnUiThread { proposal = target?.let { TempProposal(it) } }
        }
        sync.start()
        tempSync = sync
    }

    // 5) 불편 알림 구독 시작
    private fun startRequestSync(sid: String) {
        val sync = CareRequestSync(sid)
        sync.onChange = { items -> runOnUiThread { pendingRequests = items } }
        sync.start()
        requestSync = sync
    }

    // 추천 온도 [적용]: 차량에 적용, 실패하면 제안은 그대로 남아요
    private fun approveTemp() {
        val sync = tempSync ?: return
        if (approving) return
        approving = true
        sync.approve { ok ->
            runOnUiThread {
                approving = false
                if (!ok) {
                    Toast.makeText(this, "온도 적용에 실패했어요. 다시 눌러 주세요", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 하단바·실내 조율의 외기/내기 버튼: 지금과 반대로 전환
    private fun toggleAirIntake() {
        setAirIntake(!(cabin.recirc ?: true)) {}
    }

    // 메스꺼워요 팝업 [외기로 바꾸고 확인]: 외기 적용 성공하면 요청도 확인 처리
    private fun freshAirAndConfirm(req: PendingRequest) {
        setAirIntake(false) { ok ->
            if (ok) requestSync?.acknowledge(req.id)
        }
    }

    // 차량에 외기/내기를 먼저 적용하고, 성공하면 Firestore에 기록
    private fun setAirIntake(recirc: Boolean, onDone: (Boolean) -> Unit) {
        val h = hvac ?: return
        val w = watcher ?: return
        if (airBusy) return
        airBusy = true
        h.setAirIntake(recirc) { result ->
            runOnUiThread {
                airBusy = false
                when (result) {
                    is HvacResult.Success -> {
                        w.writeAirIntake(recirc)
                        onDone(true)
                    }
                    is HvacResult.Failure -> {
                        Log.e(TAG, "외기/내기 전환 실패: ${result.reason} ${result.message}")
                        Toast.makeText(this, "외기/내기 전환에 실패했어요", Toast.LENGTH_SHORT).show()
                        onDone(false)
                    }
                }
            }
        }
    }

    // 휴식처 찾기: 내비의 현재 위치 기준으로 카카오 검색 (실패하면 최근 결과)
    private fun findRestStops() {
        val lat = naviState.lat
        val lng = naviState.lng
        if (lat == null || lng == null) {
            navi?.refresh()
            Toast.makeText(this, "현재 위치를 아직 못 받았어요. 잠시 후 다시 눌러 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (rest.searching) return
        rest = rest.copy(searching = true)
        RestStopFinder.search(this, lat, lng) { result ->
            rest = RestUiState(searching = false, result = result, addedId = null)
        }
    }

    // [경유지로 추가]: 내비에 경유지 요청 → 웹 표시용으로 route.nextRestStop 기록
    private fun addRestStop(stop: RestStop) {
        val sent = navi?.addRestStop(stop) ?: false
        if (!sent) return
        rest = rest.copy(addedId = stop.id)
        val km = stop.distanceM / 1000.0
        // 도착 예상: 경로가 정해지기 전이라 평균 80km/h로 어림 (내비 정보가 오면 그쪽이 정확해요)
        val etaMin = maxOf(1, (km / 80.0 * 60).toInt())
        watcher?.writeNextRestStop(stop.name, Math.round(km * 10) / 10.0, etaMin)
    }

    override fun onDestroy() {
        // 구독·SDK 해제 (세션 종료는 [여정 종료] 버튼으로)
        stopSyncs()
        navi?.release()
        navi = null
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}