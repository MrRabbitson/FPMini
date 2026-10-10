package lol.mrrabbitson.fpmini.core

import java.util.Calendar

/** Даты в формате FunPay: «Сегодня, 14:00», «Вчера, 9:05», «10 октября, 14:00», «3 марта 2024, 12:00». */
object FpDates {
    private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    /** Время в миллисекундах или null, если строку не удалось разобрать. [now] — для проверок. */
    fun parse(text: String, now: Calendar = Calendar.getInstance()): Long? {
        val s = text.lowercase().trim()
        if (s.isEmpty()) return null
        val tm = Regex("(\\d{1,2}):(\\d{2})").find(s)
        val h = tm?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = tm?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val cal = now.clone() as Calendar
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        when {
            s.startsWith("сегодня") -> Unit
            s.startsWith("вчера") -> cal.add(Calendar.DAY_OF_YEAR, -1)
            else -> {
                val mi = months.indexOfFirst { s.contains(it) }
                if (mi < 0) return null
                val day = Regex("(\\d{1,2})\\s+[а-яё]").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val year = Regex("\\b(20\\d{2})\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.MONTH, mi)
                cal.set(Calendar.DAY_OF_MONTH, day)
                if (year != null) cal.set(Calendar.YEAR, year)
                else if (cal.after(now)) cal.add(Calendar.YEAR, -1) // без года — значит, в прошлом году, если дата ещё не наступила
            }
        }
        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
        return cal.timeInMillis
    }
}

/** Небольшой разбор JSON (без внешних библиотек) — для атрибутов вроде data-fields. */
object MiniJson {
    fun parse(s: String): Any? = P(s).run { val v = value(); v }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> num()
            }
        }
        fun obj(): Map<String, Any?> {
            val m = linkedMapOf<String, Any?>(); i++
            while (true) {
                ws(); if (i >= s.length) return m
                if (s[i] == '}') { i++; return m }
                if (s[i] == ',') { i++; continue }
                val k = str(); ws(); if (i < s.length && s[i] == ':') i++
                m[k] = value()
            }
        }
        fun arr(): List<Any?> {
            val l = mutableListOf<Any?>(); i++
            while (true) {
                ws(); if (i >= s.length) return l
                if (s[i] == ']') { i++; return l }
                if (s[i] == ',') { i++; continue }
                l += value()
            }
        }
        fun str(): String {
            val sb = StringBuilder(); i++
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\' && i + 1 < s.length) {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> {}
                        'u' -> { sb.append(s.substring(i + 1, minOf(i + 5, s.length)).toIntOrNull(16)?.toChar() ?: '?'); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++
            return sb.toString()
        }
        fun num(): Any? {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            if (st == i) { i++; return null }
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull()
        }
    }
}
