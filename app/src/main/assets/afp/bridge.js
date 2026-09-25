'use strict'
// 原版 GenerateFP 每次调用都重建 WASM 实例，这里只实例化一次并复用，结果经 AfpBridge 回传原生层
;(function () {
  var runtimePromise = null

  function loadRuntime() {
    if (!runtimePromise) {
      runtimePromise = instantiateRuntime()
    }
    return runtimePromise
  }

  function errorMessage(e) {
    return String((e && e.message) || e)
  }

  window.afpWarmUp = function () {
    loadRuntime().then(
      function () {
        AfpBridge.onReady()
      },
      function (e) {
        AfpBridge.onError(-1, errorMessage(e))
      }
    )
  }

  // pcmBase64：8 kHz 单声道 Float32 小端字节的 base64
  window.afpGenerate = function (requestId, pcmBase64) {
    loadRuntime()
      .then(function (runtime) {
        var bytes = b64decode(pcmBase64)
        var vector = runtime.ExtractQueryFP(bytes.buffer)
        try {
          var size = vector.size()
          var out = new Uint8Array(size)
          for (var i = 0; i < size; i++) {
            out[i] = vector.get(i)
          }
          AfpBridge.onFingerprint(requestId, b64encode(out))
        } finally {
          // embind 对象需手动释放
          vector.delete()
        }
      })
      .catch(function (e) {
        AfpBridge.onError(requestId, errorMessage(e))
      })
  }
})()
