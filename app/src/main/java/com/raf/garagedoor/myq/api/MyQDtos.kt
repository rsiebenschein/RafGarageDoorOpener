package com.raf.garagedoor.myq.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountsResponse(val accounts: List<AccountDto> = emptyList())

@Serializable
data class AccountDto(val id: String, val name: String? = null)

@Serializable
data class DevicesResponse(val items: List<DeviceDto> = emptyList())

@Serializable
data class DeviceDto(
    @SerialName("serial_number") val serialNumber: String,
    val name: String? = null,
    @SerialName("device_family") val deviceFamily: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    val state: DeviceStateDto? = null,
)

@Serializable
data class DeviceStateDto(
    @SerialName("door_state") val doorState: String? = null,
    val online: Boolean? = null,
    @SerialName("last_update") val lastUpdate: String? = null,
)
