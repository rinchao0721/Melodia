package com.lin0721.linmusic.core.source

// 沙盒内的 window.lx 垫片，宿主消息经 ScriptSandbox 的 MelodiaHostBridge 发出，由 LxScriptHost 处理
object LxShim {

    fun script(env: String): String = """
        (function() {
            var pendingCallbacks = {};
            var reqIdCounter = 0;
            var requestHandler = null;

            window.lx = {
                version: "2.0.0",
                env: "$env",
                currentScriptInfo: {
                    name: "",
                    description: "",
                    version: "",
                    author: "",
                    rawScript: ""
                },
                EVENT_NAMES: {
                    request: "request",
                    inited: "inited",
                    updateAlert: "updateAlert"
                },
                request: function(url, opts, callback) {
                    var id = ++reqIdCounter;
                    pendingCallbacks[id] = callback;
                    var options = opts || {};
                    var method = options.method || 'GET';
                    var body = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
                    MelodiaHostBridge.post('http', JSON.stringify({ id: id, url: url, method: method, headers: options.headers || {}, body: body }));
                    return function() { delete pendingCallbacks[id]; };
                },
                on: function(event, handler) {
                    if (event === 'request') {
                        requestHandler = handler;
                    }
                    return Promise.resolve();
                },
                send: function(event, data) {
                    MelodiaHostBridge.post('event', JSON.stringify({ event: event, data: data || {} }));
                    return Promise.resolve();
                },
                utils: {
                    crypto: {
                        md5: function(str) { return MelodiaHostBridge.invokeSync('md5', String(str)); }
                    },
                    buffer: {
                        from: function(data) { return data; },
                        bufToString: function(buf) { return String(buf); }
                    }
                }
            };

            window.__melodia_handle_http_response = function(id, err, respJson, body) {
                var cb = pendingCallbacks[id];
                if (!cb) return;
                delete pendingCallbacks[id];
                var resp = null;
                try { resp = respJson ? JSON.parse(respJson) : {}; } catch(e) { resp = {}; }
                var parsedBody = body;
                if (typeof body === 'string') {
                    var trimmed = body.trim();
                    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
                        try { parsedBody = JSON.parse(trimmed); } catch(e) {}
                    }
                }
                resp.body = parsedBody;
                resp.statusCode = resp.statusCode || 200;
                cb(err ? new Error(err) : null, resp, parsedBody);
            };

            window.__melodia_resolve_music_url = function(callId, source, musicInfoJson, quality) {
                if (!requestHandler) {
                    MelodiaHostBridge.post('resolved', JSON.stringify({ callId: callId, url: null, error: 'No handler' }));
                    return;
                }
                var musicInfo = (typeof musicInfoJson === 'object') ? musicInfoJson : JSON.parse(musicInfoJson);
                Promise.resolve().then(function() {
                    return requestHandler({
                        source: source,
                        action: 'musicUrl',
                        info: {
                            type: quality,
                            musicInfo: musicInfo
                        }
                    });
                }).then(function(result) {
                    var url = typeof result === 'string' ? result : (result && result.url ? result.url : null);
                    MelodiaHostBridge.post('resolved', JSON.stringify({ callId: callId, url: url, error: null }));
                }).catch(function(err) {
                    MelodiaHostBridge.post('resolved', JSON.stringify({ callId: callId, url: null, error: err ? String(err.message || err) : 'Error' }));
                });
            };
        })();
    """.trimIndent()
}
