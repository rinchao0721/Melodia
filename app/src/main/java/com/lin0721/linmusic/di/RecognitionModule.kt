package com.lin0721.linmusic.di

import com.lin0721.linmusic.feature.recognition.data.PlayerManagerRecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionApi
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepositoryImpl
import com.lin0721.linmusic.feature.recognition.engine.AfpFingerprintEngine
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.MicrophoneRecorder
import com.lin0721.linmusic.feature.recognition.ui.RecognitionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

// 听歌识曲（feature/recognition）自成一组，日后独立成应用时整块迁出
val recognitionModule = module {
    single<RecognitionApi> { get<Retrofit>().create(RecognitionApi::class.java) }
    single<RecognitionRepository> { RecognitionRepositoryImpl(api = get()) }
    single { RecognitionHistoryPreferences(context = get()) }
    single<RecognitionPlayback> { PlayerManagerRecognitionPlayback(playerManager = get()) }
    // 引擎持有 WebView，随 ViewModel 创建与释放，不做单例
    factory { AfpFingerprintEngine(context = get()) }
    factory<AudioCapture> { MicrophoneRecorder(context = get()) }
    viewModelOf(::RecognitionViewModel)
}
