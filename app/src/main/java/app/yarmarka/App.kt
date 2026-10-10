package app.yarmarka

import android.app.Application
import app.yarmarka.data.FpClient
import app.yarmarka.data.Prefs
import app.yarmarka.data.Repository

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
