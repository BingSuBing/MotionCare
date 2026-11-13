package com.carfortable

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit

sealed class HvacResult {
    data object Success : HvacResult()
    data class Failure(
        val reason: String,
        val message: String?,
        val hvacOff: Boolean = false
    ) : HvacResult()
}

class HvacController(vehicle: Vehicle) {

    private val hvac = vehicle.getHVAC()

    private val driverZone = SeatArea(SeatValue.ROW_1_LEFT)
    private val passengerZone = SeatArea(SeatValue.ROW_1_RIGHT)
    private val cabinZone = SeatArea(
        SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT
    )

    fun setCabinTemperature(temp: Float, callback: (HvacResult) -> Unit) {
        if (temp < MIN_TEMP || temp > MAX_TEMP) {
            callback(HvacResult.Failure(REASON_OUT_OF_RANGE, "$temp is outside $MIN_TEMP~$MAX_TEMP"))
            return
        }
        withPowerRetry({ done ->
            setZoneTemperature(driverZone, temp) { first ->
                if (first is HvacResult.Success) {
                    setZoneTemperature(passengerZone, temp, done)
                } else {
                    done(first)
                }
            }
        }, callback)
    }

    fun setAirIntake(recirc: Boolean, callback: (HvacResult) -> Unit) {
        withPowerRetry({ done ->
            hvac.setHvacRecirculation(
                area = cabinZone,
                value = recirc,
                onComplete = { _, _ -> done(HvacResult.Success) },
                onFailed = { e: Exception -> done(toFailure(e)) }
            )
        }, callback)
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

    private fun setZoneTemperature(zone: SeatArea, temp: Float, callback: (HvacResult) -> Unit) {
        hvac.setHvacTemperature(
            area = zone,
            value = temp,
            unit = TemperatureUnit.CELSIUS,
            onComplete = { _, _ -> callback(HvacResult.Success) },
            onFailed = { e: Exception -> callback(toFailure(e)) }
        )
    }

    private fun withPowerRetry(
        action: ((HvacResult) -> Unit) -> Unit,
        callback: (HvacResult) -> Unit
    ) {
        action { result ->
            if (result is HvacResult.Failure && result.hvacOff) {
                hvac.setHvacPower(
                    area = cabinZone,
                    value = true,
                    onComplete = { _, _ -> action(callback) },
                    onFailed = { e: Exception -> callback(toFailure(e)) }
                )
            } else {
                callback(result)
            }
        }
    }

    private fun toFailure(e: Exception): HvacResult.Failure {
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        val text = chain.joinToString(" ") { it.toString() }
        return when {
            chain.any { it is IllegalArgumentException } || "IllegalArgumentException" in text ->
                HvacResult.Failure(REASON_UNSUPPORTED, e.message)
            "PropertyNotAvailableException" in text ->
                HvacResult.Failure(REASON_SDK_ERROR, e.message, hvacOff = true)
            else ->
                HvacResult.Failure(REASON_SDK_ERROR, e.message)
        }
    }

    companion object {
        const val MIN_TEMP = 16f
        const val MAX_TEMP = 30f
        const val REASON_OUT_OF_RANGE = "out_of_range"
        const val REASON_SDK_ERROR = "sdk_error"
        const val REASON_UNSUPPORTED = "unsupported"
    }
}