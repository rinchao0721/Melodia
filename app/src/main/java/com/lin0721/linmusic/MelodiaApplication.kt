package com.lin0721.linmusic

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lin0721.linmusic.core.download.DownloadWorkerFactory
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.log.CrashHandler
import com.lin0721.linmusic.core.update.UpdateManager
import com.lin0721.linmusic.di.downloadModule
import com.lin0721.linmusic.di.localModule
import com.lin0721.linmusic.di.localMusicModule
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.playerModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.di.updateModule
import com.lin0721.linmusic.di.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MelodiaApplication : Application() {

    private val updateManager: UpdateManager by inject()
    private val downloadWorkerFactory: DownloadWorkerFactory by inject()

    override fun onCreate() {
        super.onCreate()
        // 尽早初始化，覆盖 Koin/Coil 启动阶段的崩溃与日志
        AppLogger.init(this)
        CrashHandler.init(this)

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(4))
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(8))
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
        // 在后台线程强制触发 DiskLruCache.initialize()，避免首次图片加载时锁竞争
        Thread { imageLoader.diskCache }.start()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MelodiaApplication)
            modules(
                networkModule, repositoryModule, viewModelModule, playerModule,
                localModule, updateModule, downloadModule, localMusicModule
            )
        }

        // 初始化 WorkManager 并注入自定义 WorkerFactory
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(downloadWorkerFactory)
                .build()
        )

        // 延迟几秒后台检查更新，避开启动关键路径；进程生命周期内只检查这一次
        CoroutineScope(Dispatchers.IO).launch {
            delay(3000)
            updateManager.checkForUpdate(manual = false)
        }
    }
}
