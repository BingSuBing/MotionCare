package com.carfortable

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.carfortable.ui.theme.MotionCareTheme
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import androidx.compose.foundation.layout.Column
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null

    // 화면에 보여줄 상태
    private var doorStatus by mutableStateOf("도어 상태 조회 중...")
    private var firebaseStatus by mutableStateOf("Firebase 연결 중...")
    private var hvacStatus by mutableStateOf("공조 조회 중...")
    private var recircStatus by mutableStateOf("외기/내기 시험 중...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1) Vehicle SDK 연결
        val v = Vehicle(this)
        v.initialize()
        vehicle = v

        // 2) 운전석 도어 위치 조회
        val door = v.getDoor()
        door.getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos: Int? ->
                Log.d(TAG, "도어 위치: $pos")
                runOnUiThread { doorStatus = "운전석 도어 위치: $pos" }
            },
            onFailed = { e: Exception ->
                Log.e(TAG, "Vehicle SDK 오류", e)
                runOnUiThread { doorStatus = "오류: ${e.message}" }
            }
        )

        // 4) 공조 모듈 시험: 앞좌석 두 구역 22.5도 → 외기 유입
        val hvac = HvacController(v)
        hvac.setCabinTemperature(22.5f) { result ->
            hvac.readFrontTemperatures { driver, passenger ->
                runOnUiThread { hvacStatus = "온도 설정: $result / 운전석 $driver, 조수석 $passenger" }
            }
        }
        hvac.setAirIntake(recirc = false) { result ->
            runOnUiThread { recircStatus = "외기 유입 설정: $result" }
        }

        // 3) Firebase 연결 테스트: 익명 로그인 → Firestore 쓰기
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                Log.d(TAG, "익명 로그인 성공: $uid")

                FirebaseFirestore.getInstance()
                    .collection("test").document("ping")
                    .set(mapOf(
                        "from" to "car-emulator",
                        "uid" to uid,
                        "at" to FieldValue.serverTimestamp()
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "Firestore 쓰기 성공")
                        firebaseStatus = "Firebase: 연결 성공 ✅"
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore 쓰기 실패", e)
                        firebaseStatus = "Firestore 실패: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "익명 로그인 실패", e)
                firebaseStatus = "로그인 실패: ${e.message}"
            }

        setContent {
            MotionCareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        StatusText(text = doorStatus)
                        StatusText(text = firebaseStatus)
                        StatusText(text = hvacStatus)
                        StatusText(text = recircStatus)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // 3) SDK 해제
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}

@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun StatusTextPreview() {
    MotionCareTheme {
        StatusText("운전석 도어 위치: 0")
    }
}