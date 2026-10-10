package com.raf.garagedoor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.raf.garagedoor.automation.CarBluetoothDevice

class PairedBluetoothDevices(context: Context) {

    private val bluetoothManager = context.applicationContext.getSystemService(BluetoothManager::class.java)

    /** Paired devices sorted by name; empty when Bluetooth is off or BLUETOOTH_CONNECT is not granted. */
    @SuppressLint("MissingPermission")
    fun list(): List<CarBluetoothDevice> = try {
        bluetoothManager?.adapter?.bondedDevices.orEmpty()
            .map { CarBluetoothDevice(address = it.address, name = it.name ?: it.address) }
            .sortedBy { it.name.lowercase() }
    } catch (permissionMissing: SecurityException) {
        emptyList()
    }
}
