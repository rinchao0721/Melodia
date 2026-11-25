package com.lin0721.linmusic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.LoginSyncResult
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.onboarding.OnboardingCoverSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val userPreferences: UserPreferences,
    private val accountSwitcher: AccountSwitcher,
    private val coverSource: OnboardingCoverSource,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase
) : ViewModel() {

    // 为空时界面退回渐变色块
    private val _covers = MutableStateFlow<List<String>>(emptyList())
    val covers: StateFlow<List<String>> = _covers.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        viewModelScope.launch { loadCovers() }
    }

    private suspend fun loadCovers() {
        val covers = coverSource.load()
        if (covers.isNotEmpty()) _covers.value = covers
    }

    fun enterLocalMode() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                accountSwitcher.enterLocalMode()
                userPreferences.setOnboardingCompleted()
            } finally {
                _busy.value = false
            }
        }
    }

    fun onNeteaseLoginCookies(cookies: String) {
        _busy.value = true
        viewModelScope.launch {
            try {
                when (syncProfileAfterLoginUseCase.syncAccount(cookies)) {
                    is LoginSyncResult.Success -> userPreferences.setOnboardingCompleted()
                    LoginSyncResult.LimitReached -> _toastEvent.emit("最多只能登录 ${UserPreferences.MAX_ACCOUNTS} 个账户")
                    LoginSyncResult.Failed -> _toastEvent.emit("登录失败，会话无效或网络异常，请重试")
                }
            } finally {
                _busy.value = false
            }
        }
    }

    // 酷狗登录弹层内部已完成写库与平台切换
    fun onKugouLoggedIn() {
        viewModelScope.launch { userPreferences.setOnboardingCompleted() }
    }
}
