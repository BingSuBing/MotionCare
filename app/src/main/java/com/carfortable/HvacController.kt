package com.carfortable

import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import kotlin.math.abs

sealed class HvacResult {
    data object Success : HvacResult()
    data class Failure(
        val reason: String,
        val message: String?
    ) : HvacResult()
}

// 차량 공조 제어 (온도 두 구역, 외기/내기)
class HvacController(vehicle: Vehicle) {

    private val hvac = vehicle.getHVAC()
    private val handler = Handler(Looper.getMainLooper())

    private val driverZone = SeatArea(SeatValue.ROW_1_LEFT)
    private val passengerZone = SeatArea(SeatValue.ROW_1_RIGHT)
    private val cabinZone = SeatArea(
        SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT
    )

    // 전원 켜기 → 두 구역 설정 → 잠깐 뒤 실제 값 확인
    fun setCabinTemperature(temp: Float, callback: (HvacResult) -> Unit) {
        if (temp < MIN_TEMP || temp > MAX_TEMP) {
            callback(HvacResult.Failure(REASON_OUT_OF_RANGE, "$temp is outside $MIN_TEMP~$MAX_TEMP"))
            return
        }
        ensurePowerOn {
            setZoneTemperature(driverZone, temp) { first ->
                if (first !is HvacResult.Success) {
                    callback(first)
                    return@setZoneTemperature
                }
                setZoneTemperature(passengerZone, temp) { second ->
                    if (second !is HvacResult.Success) {
                        callback(second)
                        return@setZoneTemperature
                    }
                    verifyTemperature(temp, callback)
                }
            }
        }
    }

    fun setAirIntake(recirc: Boolean, callback: (HvacResult) -> Unit) {
        ensurePowerOn {
            hvac.setHvacRecirculation(
                area = cabinZone,
                value = recirc,
                onComplete = { _, _ -> callback(HvacResult.Success) },
                onFailed = { e: Exception ->
                    Log.e(TAG, "외기/내기 설정 실패", e)
                    callback(toFailure(e))
                }
            )
        }
    }

    fun readFrontTemperatures(callback: (driver: Float?, passenger: Float?) -> Unit) {
        hvac.getHvacTemperature(
            area = driverZone,
            unit = TemperatureUnit.CELSIUS,
            onComplete = { driver: Float? ->
                hvac.getHvacTemperature(
                    area = passengerZone,
                    unit = TemperatureUnit.CELSIUS,
                    onComplete = { passenger: Float? -> callback(driver, passenger) },
                    onFailed = { _: Exception -> callback(driver, null) }
                )
            },
            onFailed = { _: Exception -> callback(null, null) }
        )
    }

    // 공조 전원을 켜고 다음 단계로 (실패해도 일단 진행, 확인 단계에서 걸러요)
    private fun ensurePowerOn(next: () -> Unit) {
        hvac.setHvacPower(
            area = cabinZone,
            value = true,
            onComplete = { _, _ -> next() },
            onFailed = { e: Exception ->
                Log.w(TAG, "공조 전원 켜기 실패", e)
                next()
            }
        )
    }

    private fun setZoneTemperature(zone: SeatArea, temp: Float, callback: (HvacResult) -> Unit) {
        hvac.setHvacTemperature(
            area = zone,
            value = temp,
            unit = TemperatureUnit.CELSIUS,
            onComplete = { _, _ -> callback(HvacResult.Success) },
            onFailed = { e: Exception ->
                Log.e(TAG, "온도 설정 실패: 요청=$temp", e)
                callback(toFailure(e))
            }
        )
    }

    // SDK 성공 응답만 믿지 않고, 잠깐 뒤 실제 값이 요청과 같은지 확인
    private fun verifyTemperature(temp: Float, callback: (HvacResult) -> Unit) {
        handler.postDelayed({
            readFrontTemperatures { driver, passenger ->
                val ok = driver != null && passenger != null &&
                        abs(driver - temp) < 0.01f && abs(passenger - temp) < 0.01f
                Log.d(TAG, "온도 확인: 요청=$temp 운전석=$driver 조수석=$passenger ok=$ok")
                if (ok) {
                    callback(HvacResult.Success)
                } else {
                    callback(HvacResult.Failure(REASON_SDK_ERROR, "요청 $temp, 실제 $driver / $passenger"))
                }
            }
        }, VERIFY_DELAY_MS)
    }

    private fun toFailure(e: Exception): HvacResult.Failure {
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        val text = chain.joinToString(" ") { it.toString() }
        return if (chain.any { it is IllegalArgumentException } || "IllegalArgumentException" in text) {
            HvacResult.Failure(REASON_UNSUPPORTED, e.message)
        } else {
            HvacResult.Failure(REASON_SDK_ERROR, e.message)
        }
    }

    companion object {
        private const val TAG = "MotionCare"
        private const val VERIFY_DELAY_MS = 500L
        const val MIN_TEMP = 16f
        const val MAX_TEMP = 30f
        const val REASON_OUT_OF_RANGE = "out_of_range"
        const val REASON_SDK_ERROR = "sdk_error"
        const val REASON_UNSUPPORTED = "unsupported"
    }
}