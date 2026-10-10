package lol.mrrabbitson.fpmini.auto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EventKind { INFO, OK, WARN, ERROR }

data class Event(val time: String, val kind: EventKind, val text: String)

/** Журнал автоматизации (последние 150 событий), показывается на вкладке «Автоматизация». */
class EventLog {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _status = MutableStateFlow("Остановлено")
    val status: StateFlow<String> = _status.asStateFlow()

    fun add(kind: EventKind, text: String) {
        val e = Event(fmt.format(Date()), kind, text)
        _events.value = (listOf(e) + _events.value).take(150)
    }

    fun status(text: String) { _status.value = text }
    fun clear() { _events.value = emptyList() }
}
