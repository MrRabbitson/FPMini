package lol.mrrabbitson.fpmini.data

import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.OrderStatus
import java.util.Calendar
import java.util.Locale

data class SalesStats(
    val count: Int,
    val earned: Double,        // закрытые заказы
    val earnedCount: Int,
    val inWork: Double,        // оплачены, ещё не закрыты
    val inWorkCount: Int,
    val refunds: Double,
    val refundCount: Int,
    val average: Double,
    val top: List<Pair<String, Double>>, // разделы с наибольшей выручкой
)

data class Unlock(val orderId: String, val amount: Double, val at: Long)

data class Funds(
    val total: Double?,
    val frozen: Double,
    val available: Double?,
    val unlocks: List<Unlock>, // ближайшие разблокировки, по времени
)

object Stats {
    /** Деньги за закрытый заказ FunPay держит примерно 48 часов. */
    const val HOLD_MS = 48L * 3600_000L

    fun money(v: Double): String {
        val s = String.format(Locale("ru"), "%,.2f", v).replace(' ', ' ')
        return s.removeSuffix(",00") + " ₽"
    }

    fun parseMoney(text: String?): Double? =
        text?.replace(Regex("[^0-9.,]"), "")?.replace(",", ".")?.trim('.')?.toDoubleOrNull()

    fun inRange(o: Order, from: Long?, to: Long?): Boolean {
        val t = o.timeMs ?: return from == null
        return (from == null || t >= from) && (to == null || t < to)
    }

    fun sales(orders: List<Order>): SalesStats {
        val closed = orders.filter { it.status == OrderStatus.CLOSED }
        val paid = orders.filter { it.status == OrderStatus.PAID }
        val refunds = orders.filter { it.status == OrderStatus.REFUND }
        val earned = closed.sumOf { it.priceValue ?: 0.0 }
        val top = closed.groupBy { it.category.ifBlank { "Без раздела" } }
            .map { (k, v) -> k to v.sumOf { it.priceValue ?: 0.0 } }
            .sortedByDescending { it.second }.take(3)
        return SalesStats(
            count = orders.size,
            earned = earned, earnedCount = closed.size,
            inWork = paid.sumOf { it.priceValue ?: 0.0 }, inWorkCount = paid.size,
            refunds = refunds.sumOf { it.priceValue ?: 0.0 }, refundCount = refunds.size,
            average = if (closed.isEmpty()) 0.0 else earned / closed.size,
            top = top,
        )
    }

    fun funds(balanceText: String?, orders: List<Order>, now: Long = System.currentTimeMillis()): Funds {
        val unlocks = orders.filter { it.status == OrderStatus.CLOSED && it.timeMs != null && it.timeMs!! + HOLD_MS > now }
            .map { Unlock(it.id, it.priceValue ?: 0.0, it.timeMs!! + HOLD_MS) }
            .sortedBy { it.at }
        val frozen = unlocks.sumOf { it.amount }
        val total = parseMoney(balanceText)
        return Funds(total, frozen, total?.let { maxOf(0.0, it - frozen) }, unlocks)
    }

    fun startOfDay(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Заработок по дням (закрытые заказы) за последние [days] дней, от старых к новым. */
    fun byDay(orders: List<Order>, days: Int = 14): List<Pair<Long, Double>> =
        (days - 1 downTo 0).map { ago ->
            val from = startOfDay(ago); val to = from + 24 * 3600_000L
            from to orders.filter { it.status == OrderStatus.CLOSED && inRange(it, from, to) }.sumOf { it.priceValue ?: 0.0 }
        }
}
