package lol.mrrabbitson.fpmini.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.OrderStatus
import lol.mrrabbitson.fpmini.core.UserShort
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SalesState(
    val orders: List<Order>? = null,  // null — ещё ни разу не загружено
    val loading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    val updatedAt: Long = 0,
)

/**
 * Вся история продаж для статистики. Грузится в фоне сразу при запуске приложения,
 * хранится на диске (при следующем запуске статистика видна сразу) и обновляется раз в 10 минут.
 */
class SalesStore(private val repo: Repository, private val file: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SalesState())
    val state: StateFlow<SalesState> = _state.asStateFlow()
    private var job: Job? = null
    private var ticker: Job? = null

    init {
        scope.launch { readDisk()?.let { (at, list) -> _state.update { if (it.orders == null) it.copy(orders = list, updatedAt = at) else it } } }
    }

    /** Запустить загрузку (если уже идёт — ничего не делать). */
    fun refresh(force: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(loading = true, progress = 0, error = null) }
            try {
                val list = repo.allOrders(force = force) { n -> _state.update { s -> s.copy(progress = n) } }
                val now = System.currentTimeMillis()
                _state.update { it.copy(orders = list, loading = false, updatedAt = now) }
                writeDisk(now, list)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Не удалось загрузить продажи") }
            }
        }
    }

    /** Загрузка при старте + обновление раз в 10 минут, пока приложение запущено. */
    fun start() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                refresh(force = true)
                delay(10 * 60_000L)
            }
        }
    }

    fun stopAndClear() {
        ticker?.cancel(); job?.cancel()
        _state.value = SalesState()
        runCatching { file.delete() }
    }

    // ---------- кэш на диске ----------

    private fun writeDisk(at: Long, list: List<Order>) = runCatching {
        val arr = JSONArray()
        list.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id); put("d", o.description); put("c", o.category)
                put("p", o.priceText); o.priceValue?.let { put("v", it) }
                put("dt", o.date); put("s", o.status.name); put("st", o.statusText)
                o.timeMs?.let { put("t", it) }
                o.buyer?.let { b -> put("bn", b.name); b.id?.let { put("bi", it) } }
            })
        }
        file.writeText(JSONObject().put("at", at).put("orders", arr).toString())
    }

    private fun readDisk(): Pair<Long, List<Order>>? = runCatching {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val arr = root.getJSONArray("orders")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Order(
                id = o.getString("id"),
                description = o.optString("d"),
                category = o.optString("c"),
                buyer = o.optString("bn").ifBlank { null }?.let { UserShort(if (o.has("bi")) o.getInt("bi") else null, it, null, null) },
                priceText = o.optString("p"),
                priceValue = if (o.has("v")) o.getDouble("v") else null,
                date = o.optString("dt"),
                status = runCatching { OrderStatus.valueOf(o.optString("s")) }.getOrDefault(OrderStatus.CLOSED),
                statusText = o.optString("st"),
                timeMs = if (o.has("t")) o.getLong("t") else null,
            )
        }
        root.optLong("at") to list
    }.getOrNull()
}
