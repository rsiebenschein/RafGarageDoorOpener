package com.raf.garagedoor.myq.http

import com.raf.garagedoor.myq.config.MyQApiConfig
import okhttp3.Headers

class MyQRequestHeaders(private val config: MyQApiConfig) {

    fun tokenEndpoint(): Headers = Headers.Builder()
        .add("Accept", "application/json")
        .add("App-Version", config.appVersion)
        .add("BrandId", config.brandId)
        .add("User-Agent", config.apiUserAgent)
        .build()

    fun authorizedApi(accessToken: String): Headers = tokenEndpoint().newBuilder()
        .add("Authorization", "Bearer $accessToken")
        .build()
}
