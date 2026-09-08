package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.api.NeteaseApiService
import com.lin0721.linmusic.core.network.CryptoInterceptor
import com.lin0721.linmusic.core.network.EmptyBodyInterceptor
import com.lin0721.linmusic.core.network.HeaderInterceptor
import com.lin0721.linmusic.core.network.NeteaseEndpoints
import com.lin0721.linmusic.core.network.RealIpProvider
import com.lin0721.linmusic.feature.artist.data.ArtistApi
import com.lin0721.linmusic.feature.cloud.data.CloudApi
import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.NosUploadClient
import com.lin0721.linmusic.core.comment.data.CommentApi
import com.lin0721.linmusic.core.player.data.PlaybackApi
import com.lin0721.linmusic.core.songlike.SongLikeApi
import com.lin0721.linmusic.core.userartist.UserArtistApi
import com.lin0721.linmusic.core.userplaylist.UserPlaylistApi
import com.lin0721.linmusic.feature.create.data.CreateApi
import com.lin0721.linmusic.feature.home.data.HomeApi
import com.lin0721.linmusic.feature.music.data.MusicApi
import com.lin0721.linmusic.feature.podcast.data.PodcastApi
import com.lin0721.linmusic.feature.library.data.LibraryApi
import com.lin0721.linmusic.feature.listendata.data.ListenDataApi
import com.lin0721.linmusic.feature.newworks.data.NewWorksApi
import com.lin0721.linmusic.feature.player.data.PlayerApi
import com.lin0721.linmusic.feature.playlist.data.PlaylistApi
import com.lin0721.linmusic.feature.recent.data.RecentApi
import com.lin0721.linmusic.feature.search.data.SearchApi
import com.lin0721.linmusic.feature.settings.data.SettingsApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.lin0721.linmusic.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Koin 网络层依赖注入模块
 *
 * 提供：
 * - [Json]                ： kotlinx.serialization 全局实例
 * - [CryptoInterceptor]   ： 网易云加密拦截器
 * - [OkHttpClient]        ： 含加密拦截器 + 日志拦截器
 * - [Retrofit]            ： 基于 kotlinx.serialization 的转换器
 * - [NeteaseApiService]   ： 账号鉴权 Retrofit 代理接口（core/auth 跨域共用）
 * - 各业务域 Api           ： HomeApi/PlaylistApi/CreateApi/PlayerApi/ArtistApi/SearchApi/LibraryApi/SettingsApi
 * - core 共享 Api          ： CommentApi/SongLikeApi/PlaybackApi/UserPlaylistApi/UserArtistApi
 */
val networkModule = module {

    // ─── kotlinx.serialization Json 实例 ───
    single {
        Json {
            ignoreUnknownKeys = true       // 忽略服务端返回的未知字段
            coerceInputValues = true        // null → 默认值
            encodeDefaults = true           // 序列化时包含默认值
            isLenient = true                // 宽松解析
            explicitNulls = false           // 序列化时跳过 null 字段
        }
    }

    // ─── 加密拦截器 ───
    single { CryptoInterceptor() }

    // ─── 空响应体拦截器 ───
    single { EmptyBodyInterceptor() }

    // ─── IP 伪装选取（HeaderInterceptor 与网页登录共用，保证同一会话内伪装一致） ───
    single { RealIpProvider() }

    // ─── 请求头拦截器 ───
    single { HeaderInterceptor(get(), get(), get()) }

    // ─── OkHttpClient ───
    single {
        val settingsPreferences: SettingsPreferences = get()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            settingsPreferences.useProxy.collectLatest { enabled ->
                NetworkConfig.useProxy = enabled
            }
        }

        val proxySelector = object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> {
                return if (NetworkConfig.useProxy) {
                    java.net.ProxySelector.getDefault()?.select(uri) ?: mutableListOf(java.net.Proxy.NO_PROXY)
                } else {
                    mutableListOf(java.net.Proxy.NO_PROXY)
                }
            }

            override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                java.net.ProxySelector.getDefault()?.connectFailed(uri, sa, ioe)
            }
        }

        OkHttpClient.Builder()
            .proxySelector(proxySelector)
            .addInterceptor(get<EmptyBodyInterceptor>())
            .addInterceptor(get<HeaderInterceptor>())
            .addInterceptor(get<CryptoInterceptor>())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ─── Retrofit ───
    single {
        val json: Json = get()
        val contentType = "application/json".toMediaType()

        Retrofit.Builder()
            .baseUrl(NeteaseEndpoints.WEB_BASE_URL)
            .client(get())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    // ─── API Service（core/auth 跨域鉴权接口） ───
    single<NeteaseApiService> {
        get<Retrofit>().create(NeteaseApiService::class.java)
    }

    // ─── 按业务域拆分的 API Service ───
    single<HomeApi> { get<Retrofit>().create(HomeApi::class.java) }
    single<MusicApi> { get<Retrofit>().create(MusicApi::class.java) }
    single<PodcastApi> { get<Retrofit>().create(PodcastApi::class.java) }
    single<PlaylistApi> { get<Retrofit>().create(PlaylistApi::class.java) }
    single<CreateApi> { get<Retrofit>().create(CreateApi::class.java) }
    single<PlayerApi> { get<Retrofit>().create(PlayerApi::class.java) }
    single<ArtistApi> { get<Retrofit>().create(ArtistApi::class.java) }
    single<SearchApi> { get<Retrofit>().create(SearchApi::class.java) }
    single<LibraryApi> { get<Retrofit>().create(LibraryApi::class.java) }
    single<RecentApi> { get<Retrofit>().create(RecentApi::class.java) }
    single<CloudApi> { get<Retrofit>().create(CloudApi::class.java) }
    single<CloudUploadApi> { get<Retrofit>().create(CloudUploadApi::class.java) }

    // 云盘上传：跨域裸传输客户端与队列状态源，均不依赖上面这套挂了拦截器的共享 OkHttpClient/Retrofit
    single { NosUploadClient() }
    single { CloudUploadManager() }
    single<ListenDataApi> { get<Retrofit>().create(ListenDataApi::class.java) }
    single<NewWorksApi> { get<Retrofit>().create(NewWorksApi::class.java) }
    single<CommentApi> { get<Retrofit>().create(CommentApi::class.java) }
    single<SongLikeApi> { get<Retrofit>().create(SongLikeApi::class.java) }
    single<PlaybackApi> { get<Retrofit>().create(PlaybackApi::class.java) }
    single<UserPlaylistApi> { get<Retrofit>().create(UserPlaylistApi::class.java) }
    single<UserArtistApi> { get<Retrofit>().create(UserArtistApi::class.java) }
    single<SettingsApi> { get<Retrofit>().create(SettingsApi::class.java) }
}

object NetworkConfig {
    @Volatile
    var useProxy: Boolean = false
}
