package lol.mrrabbitson.fpmini.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

/** Состояние загрузки страницы: данные остаются на экране, пока идёт обновление. */
@Stable
class LoadState<T> {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<Throwable?>(null)
    var loading by mutableStateOf(true)
    internal var trigger by mutableIntStateOf(0)
    internal var force = false

    fun refresh() { force = true; trigger++ }
    fun retry() { force = false; trigger++ }
}

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend (force: Boolean) -> T): LoadState<T> {
    val state = remember(*keys) { LoadState<T>() }
    LaunchedEffect(state, state.trigger) {
        state.loading = true
        state.error = null
        try {
            state.data = block(state.force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            state.error = e
        } finally {
            state.loading = false
            state.force = false
        }
    }
    return state
}
