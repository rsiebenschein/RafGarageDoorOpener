package com.raf.garagedoor.myq

import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.RequiresSignIn
import java.io.IOException

sealed class MyQException(message: String, cause: Throwable? = null) :
    GarageDoorControlException(message, cause)

class MyQNotSignedInException : MyQException("No myQ session is stored; sign in first"), RequiresSignIn

class MyQAuthenticationException(message: String) : MyQException(message), RequiresSignIn

class MyQRateLimitedException :
    MyQException("myQ is temporarily rate limiting this account; wait a while before retrying")

class MyQApiException(val statusCode: Int, message: String) : MyQException(message) {
    override val isTransient: Boolean get() = statusCode >= HTTP_SERVER_ERROR

    private companion object {
        const val HTTP_SERVER_ERROR = 500
    }
}

class MyQNetworkException(cause: IOException) :
    MyQException("Could not reach myQ: ${cause.message}", cause) {
    override val isTransient: Boolean get() = true
}

class MyQResponseFormatException(message: String, cause: Throwable? = null) :
    MyQException(message, cause)

class MyQAppCheckException(message: String) : MyQException(message)

class MyQInvalidCallbackException(message: String) : MyQException(message)

class MyQDoorUnavailableException(serialNumber: String) :
    MyQException("myQ reports garage door $serialNumber is offline or unavailable")
