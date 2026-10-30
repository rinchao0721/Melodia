package com.lin0721.linmusic.core.plugin

// 插件沙盒内的全局 melodia 对象：宿主调用一律异步，经 post('call') 发出、由 __melodia_reply 回填
// 宿主调插件经 __melodia_invoke，结果经 post('result') 返回；播放事件经 __melodia_emit 分发
object PluginPrelude {

    const val REPLY = "__melodia_reply"
    const val INVOKE = "__melodia_invoke"
    const val EMIT = "__melodia_emit"

    const val MESSAGE_CALL = "call"
    const val MESSAGE_RESULT = "result"
    const val MESSAGE_REGISTER = "register"

    const val KIND_AUDIO_SOURCE = "audioSource"
    const val KIND_LYRICS_SOURCE = "lyricsSource"
    const val KIND_ACTION = "action"
    const val KIND_COMMAND = "command"

    val script: String = """
        (function() {
            var seq = 0;
            var pending = {};
            var registry = { audioSource: {}, lyricsSource: {}, action: {}, command: {} };
            var listeners = {};

            function post(method, data) {
                MelodiaHostBridge.post(method, JSON.stringify(data));
            }

            function call(method, args) {
                return new Promise(function(resolve, reject) {
                    var id = ++seq;
                    pending[id] = { resolve: resolve, reject: reject };
                    post('$MESSAGE_CALL', { id: id, method: method, args: args === undefined ? null : args });
                });
            }

            function register(kind, name, impl) {
                if (typeof name !== 'string' || !impl) throw new Error('register 需要名称与实现');
                registry[kind][name] = impl;
                post('$MESSAGE_REGISTER', { kind: kind, name: name });
            }

            function errorText(e) {
                if (e && e.message) return String(e.message);
                return String(e);
            }

            window.$REPLY = function(id, error, result) {
                var p = pending[id];
                if (!p) return;
                delete pending[id];
                if (error !== null && error !== undefined) p.reject(new Error(error));
                else p.resolve(result);
            };

            window.$INVOKE = function(callId, kind, name, args) {
                var impl = registry[kind] && registry[kind][name];
                Promise.resolve().then(function() {
                    if (!impl) throw new Error('未注册 ' + kind + ':' + name);
                    if (typeof impl === 'function') return impl(args);
                    var method = args && args.method;
                    if (typeof impl[method] !== 'function') throw new Error(kind + ':' + name + ' 没有实现 ' + method);
                    return impl[method](args.params);
                }).then(function(result) {
                    post('$MESSAGE_RESULT', { callId: callId, result: result === undefined ? null : result });
                }, function(e) {
                    post('$MESSAGE_RESULT', { callId: callId, error: errorText(e) });
                });
            };

            window.$EMIT = function(event, data) {
                (listeners[event] || []).forEach(function(handler) {
                    try {
                        var r = handler(data);
                        if (r && typeof r.catch === 'function') r.catch(function(e) { console.error(event + ' 处理出错: ' + errorText(e)); });
                    } catch (e) {
                        console.error(event + ' 处理出错: ' + errorText(e));
                    }
                });
            };

            window.melodia = {
                version: $PLUGIN_FORMAT_VERSION,
                http: {
                    request: function(options) { return call('http.request', options); }
                },
                storage: {
                    get: function(key) { return call('storage.get', { key: key }); },
                    set: function(key, value) { return call('storage.set', { key: key, value: value === undefined ? null : value }); },
                    remove: function(key) { return call('storage.remove', { key: key }); }
                },
                player: {
                    current: function() { return call('player.current'); },
                    state: function() { return call('player.state'); },
                    play: function() { return call('player.play'); },
                    pause: function() { return call('player.pause'); },
                    next: function() { return call('player.next'); },
                    previous: function() { return call('player.previous'); },
                    enqueue: function(track) { return call('player.enqueue', track); }
                },
                ui: {
                    setState: function(page, patch) { return call('ui.setState', { page: page, patch: patch }); },
                    toast: function(text) { return call('ui.toast', { text: text }); }
                },
                settings: {
                    get: function(key) { return call('settings.get', { key: key }); },
                    all: function() { return call('settings.all'); }
                },
                on: function(event, handler) {
                    if (typeof handler !== 'function') throw new Error('on 需要处理函数');
                    (listeners[event] = listeners[event] || []).push(handler);
                },
                register: {
                    audioSource: function(name, impl) { register('$KIND_AUDIO_SOURCE', name, impl); },
                    lyricsSource: function(name, impl) { register('$KIND_LYRICS_SOURCE', name, impl); },
                    action: function(name, handler) { register('$KIND_ACTION', name, handler); },
                    command: function(name, handler) { register('$KIND_COMMAND', name, handler); }
                }
            };
        })();
    """.trimIndent()
}
