package lol.mrrabbitson.fpmini.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/** Состояние загрузки: данные остаются на экране, пока идёт обновление. */
@Stable
class LoadState<T> {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<Throwable?>(null)
    var loading by mutableStateOf(true)      // крутилка (первая загрузка / потянули вниз)
    internal var trigger by mutableIntStateOf(0)
    internal var silent = false

    fun refresh() { silent = false; trigger++ }
    fun retry() { silent = false; trigger++ }
    /** Тихо обновить (для автообновления, без крутилки). */
    fun reloadSilently() { silent = true; trigger++ }
}

/**
 * Загружает данные через [block]. Если [autoRefreshMs] > 0 — тихо обновляет их,
 * пока экран открыт (чаты, переписка).
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, autoRefreshMs: Long = 0, block: suspend () -> T): LoadState<T> {
    val state = remember(*keys) { LoadState<T>() }
    LaunchedEffect(state, state.trigger) {
        val silent = state.silent && state.data != null
        if (!silent) state.loading = true
        try {
            state.data = block()
            state.error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!silent || state.data == null) state.error = e
        } finally {
            state.loading = false
            state.silent = false
        }
    }
    if (autoRefreshMs > 0) {
        LaunchedEffect(state) {
            while (true) {
                delay(autoRefreshMs)
                if (!state.loading) state.reloadSilently()
            }
        }
    }
    return state
}
