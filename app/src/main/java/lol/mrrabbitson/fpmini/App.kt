package lol.mrrabbitson.fpmini

import android.app.Application
import lol.mrrabbitson.fpmini.data.FpClient
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repository(FpClient(prefs), prefs)
    }
}
