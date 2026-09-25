package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.feature.recognition.engine.AudioMatcher

// 未命中返回空列表；网络、风控、解析失败抛 RecognitionException(NETWORK)
interface RecognitionRepository : AudioMatcher
