package com.gutigu.alicia.data

import kotlinx.coroutines.CancellationException

/**
 * Como [kotlin.runCatching] pero relanza [CancellationException] en vez de convertirla en un
 * `Result.failure`. Atrapar una cancelación (incluida la de un `withTimeout`) rompe la
 * cancelación cooperativa de coroutines: quien puso el `withTimeout` nunca se entera de que
 * expiró, y la UI se queda colgada en vez de mostrar un error.
 */
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
