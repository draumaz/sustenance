package xyz.draumaz.sustenance.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspend code: it also turns a [CancellationException] into a failure, so a
 * cancelled read (e.g. the widget worker REPLACEd by a newer refresh) would carry on as if HC had
 * returned nothing and then render or act on that. This rethrows cancellation and catches the rest.
 * Inline, so the block can call suspend functions.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
