package com.lin0721.linmusic.core.network

import java.io.IOException

// 自定义 API 异常
class ApiException(message: String) : IOException(message)
