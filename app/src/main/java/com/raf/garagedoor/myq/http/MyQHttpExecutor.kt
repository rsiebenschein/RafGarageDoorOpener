package com.raf.garagedoor.myq.http

import com.raf.garagedoor.myq.MyQApiException
import com.raf.garagedoor.myq.MyQAuthenticationException
import com.raf.garagedoor.myq.MyQNetworkException
import com.raf.garagedoor.myq.MyQRateLimitedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class MyQHttpExecutor(private val route: MyQHttpRoute) {

    constructor(client: OkHttpClient) : this(DefaultNetworkRoute(client))

    suspend fun execute(
        request: Request,
        sessionFailureStatuses: Set<Int> = DEFAULT_SESSION_FAILURE_STATUSES,
    ): String = withContext(Dispatchers.IO) {
        try {
            route.call { client -> client.newCall(request).execute().use { response -> readBody(response, sessionFailureStatuses) } }
        } catch (error: IOException) {
            throw MyQNetworkException(error)
        }
    }

    private fun readBody(response: Response, sessionFailureStatuses: Set<Int>): String {
        val status = response.code
        when {
            status in sessionFailureStatuses ->
                throw MyQAuthenticationException("myQ rejected the session (HTTP $status)")
            status == HTTP_TOO_MANY_REQUESTS -> throw MyQRateLimitedException()
            !response.isSuccessful -> throw MyQApiException(status, "myQ request failed (HTTP $status)")
        }
        return response.body?.string().orEmpty()
    }

    companion object {
        val DEFAULT_SESSION_FAILURE_STATUSES = setOf(401, 403)
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
