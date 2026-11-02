package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.get
import com.lin0721.linmusic.feature.recognition.data.PlayerManagerRecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPlayback
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.recognition.engine.AfpFingerprintEngine
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.MicrophoneRecorder
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionLauncher
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionState
import com.lin0721.linmusic.feature.recognition.service.RecognitionNotifications
import com.lin0721.linmusic.feature.recognition.ui.RecognitionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// 听歌识曲（feature/recognition）自成一组，日后独立成应用时整块迁出
val recognitionModule = module {
    single { RecognitionHistoryPreferences(PreferencesStores.get(androidContext(), PreferencesStores.RECOGNITION_HISTORY)) }
    single { RecognitionPreferences(PreferencesStores.get(androidContext(), PreferencesStores.RECOGNITION)) }
    single { PlaybackRecognitionState() }
    single { PlaybackRecognitionLauncher(context = get(), state = get()) }
    single { RecognitionNotifications(context = get()) }
    single<RecognitionPlayback> { PlayerManagerRecognitionPlayback(playerManager = get()) }
    // 引擎持有 WebView，随 ViewModel 创建与释放，不做单例
    factory { AfpFingerprintEngine(context = get()) }
    factory<AudioCapture> { MicrophoneRecorder(context = get()) }
    viewModelOf(::RecognitionViewModel)
}
