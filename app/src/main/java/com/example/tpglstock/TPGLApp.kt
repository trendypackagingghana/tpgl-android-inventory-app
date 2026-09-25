package com.example.tpglstock

import android.app.Application
import com.example.tpglstock.ai.StockAiService
import com.example.tpglstock.data.SettingsStore
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.remote.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; small enough not to need a DI framework. */
class AppContainer(app: Application, scope: CoroutineScope) {
    val repository = StockRepository(SupabaseApi(), scope)
    val settings = SettingsStore(app)
    val ai = StockAiService()
}

class TPGLApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
    }
}
