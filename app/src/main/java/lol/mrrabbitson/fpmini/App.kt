package lol.mrrabbitson.fpmini

import android.app.Application
import lol.mrrabbitson.fpmini.auto.EventLog
import lol.mrrabbitson.fpmini.auto.Notifier
import lol.mrrabbitson.fpmini.data.FpClient
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.data.SalesStore

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set
    val log = EventLog()
    lateinit var sales: SalesStore
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repository(FpClient(prefs), prefs)
        sales = SalesStore(repo, java.io.File(filesDir, "sales.json"))
        Notifier.createChannels(this)
    }
}
