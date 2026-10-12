package com.raf.garagedoor.network

import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Marks myQ calls made by routine status checks (the left-open check every few minutes, the
 * widget's refresh, the app screen's polling). They go over Wi-Fi first, with mobile data only as
 * a backup: bringing mobile data up for each of them while on Wi-Fi kept the cellular radio busy
 * about 15 minutes a day (field test 5). Door commands, and the reads just before them, still go
 * over mobile data first.
 */
object RoutineStatusCheck : AbstractCoroutineContextElement(RoutineStatusCheckKey) {
    suspend fun <T> run(block: suspend () -> T): T = withContext(this) { block() }

    fun isActiveIn(context: CoroutineContext): Boolean = context[RoutineStatusCheckKey] != null
}

private object RoutineStatusCheckKey : CoroutineContext.Key<RoutineStatusCheck>
