package lol.mrrabbitson.fpmini

import android.app.Application
import lol.mrrabbitson.fpmini.auto.EventLog
import lol.mrrabbitson.fpmini.auto.Notifier
import lol.mrrabbitson.fpmini.data.FpClient
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set
    val log = EventLog()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repository(FpClient(prefs), prefs)
        Notifier.createChannels(this)
    }
}
