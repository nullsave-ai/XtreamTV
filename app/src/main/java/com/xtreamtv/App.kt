package com.xtreamtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.xtreamtv.data.JsonDiskCache
import com.xtreamtv.data.XtreamApi
import com.xtreamtv.data.XtreamRepository
import com.xtreamtv.data.local.AccountStore
import com.xtreamtv.data.local.SettingsStore
import com.xtreamtv.data.local.UserStore
import com.xtreamtv.domain.ScreenMemory
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Manual DI container: simple, no framework, light for on-device builds. */
class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val api = XtreamApi(http)
    val repo = XtreamRepository(api, JsonDiskCache(File(app.cacheDir, "xtream")))
    val accounts = AccountStore(app)
    val user = UserStore(app)
    val settings = SettingsStore(app)
    val memory = ConcurrentHashMap<String, ScreenMemory>()
}

class XtreamApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(File(cacheDir, "img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
