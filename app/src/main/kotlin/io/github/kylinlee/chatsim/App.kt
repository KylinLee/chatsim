package io.github.kylinlee.chatsim

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.kylinlee.chatsim.data.scheduler.RecycleBinCleanScheduler
import io.github.kylinlee.chatsim.data.scheduler.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            TaskScheduler.reconcile(applicationContext)
            RecycleBinCleanScheduler.ensure(applicationContext)
        }
    }
}
