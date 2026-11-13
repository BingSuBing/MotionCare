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
    private var hvac: HvacController? = null
    private var session: SessionManager? = null
    private var tempSync: TempMedianSync? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1) Vehicle SDK 연결
        val v = Vehicle(this)
        v.initialize()
        vehicle = v

        // 2) 세션 생성
        //    debug: local.properties의 devSessionId, 없으면 공용 test01
        //    release: 무작위 6자리 ID
        val h = HvacController(v)
        hvac = h
        val s = SessionManager(h)
        session = s
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
                        startRequestSync(sid)
                    }
                    .onFailure { e ->
                        Log.e(TAG, "세션 생성 실패", e)
                        sessionError = "세션 실패\n${e.message}"
                    }
            }
        }

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
                                onToggleAir = { toggleAirIntake() }
                            ) {
                                when (tab) {
                                    CarTab.HOME -> HomeScreen(
                                        requests = pendingRequests,
                                        passengers = passengers,
                                        onConfirm = { req -> requestSync?.acknowledge(req.id) },
                                        proposal = proposal,
                                        approving = approving,
                                        onApprove = { approveTemp() },
                                        onDecline = { tempSync?.decline() }
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

    // 3) 탑승자 목록·실내 상태 구독 (화면 표시용)
    private fun startWatcher(sid: String) {
        val w = SessionWatcher(sid)
        w.onPassengers = { items -> runOnUiThread { passengers = items } }
        w.onCabin = { state -> runOnUiThread { cabin = state } }
        w.start()
        watcher = w
    }

    // 4) 희망 온도 중앙값 계산 시작 (적용은 운전자 확인 후)
    private fun startTempSync(sid: String, hvac: HvacController) {
        val sync = TempMedianSync(sid, hvac)
        sync.onStatus = { text -> Log.d(TAG, "온도 동기화: $text") }
        sync.onProposal = { median, count ->
            runOnUiThread { proposal = median?.let { TempProposal(it, count) } }
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

    // 희망 온도 제안 [적용]: 차량에 적용, 실패하면 제안은 그대로 남아요
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

    // 하단바 외기/내기 버튼: 차량에 먼저 적용하고, 성공하면 Firestore에 기록
    private fun toggleAirIntake() {
        val h = hvac ?: return
        val w = watcher ?: return
        if (airBusy) return
        val nextRecirc = !(cabin.recirc ?: true)
        airBusy = true
        h.setAirIntake(nextRecirc) { result ->
            runOnUiThread {
                airBusy = false
                when (result) {
                    is HvacResult.Success -> w.writeAirIntake(nextRecirc)
                    is HvacResult.Failure -> {
                        Log.e(TAG, "외기/내기 전환 실패: ${result.reason} ${result.message}")
                        Toast.makeText(this, "외기/내기 전환에 실패했어요", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // 구독·SDK 해제 (세션 종료는 나중에 종료 버튼으로)
        watcher?.stop()
        watcher = null
        tempSync?.stop()
        tempSync = null
        requestSync?.stop()
        requestSync = null
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}