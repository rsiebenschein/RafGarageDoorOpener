package com.raf.garagedoor.myq.http

import okhttp3.OkHttpClient

/** Picks the connection (and so the network) each myQ call goes over. */
interface MyQHttpRoute {
    /** Runs the blocking [send] with the client for the chosen network; called on an IO thread. */
    suspend fun <T> call(send: (OkHttpClient) -> T): T
}

/** Always the phone's default network, whatever Android has chosen (usually Wi-Fi when connected). */
class DefaultNetworkRoute(private val client: OkHttpClient) : MyQHttpRoute {
    override suspend fun <T> call(send: (OkHttpClient) -> T): T = send(client)
}
