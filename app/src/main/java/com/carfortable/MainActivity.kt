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

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null

    // 화면에 보여줄 상태
    private var doorStatus by mutableStateOf("도어 상태 조회 중...")

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

        setContent {
            MotionCareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StatusText(
                        text = doorStatus,
                        modifier = Modifier.padding(innerPadding)
                    )
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