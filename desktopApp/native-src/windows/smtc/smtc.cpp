// 系统媒体传输控制（SMTC）桥接：借用 MediaPlayer 自带的 SMTC，向 JVM 暴露纯 C 接口
#include <windows.h>

#include <atomic>
#include <chrono>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Playback.h>
#include <winrt/Windows.Storage.Streams.h>

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Media;
using namespace winrt::Windows::Media::Playback;
using namespace winrt::Windows::Storage::Streams;

using SmtcCallback = void(__cdecl*)(int command, long long value);

namespace {

// 与 Kotlin 侧 SmtcCommand 保持一致
enum Command : int {
    CMD_PLAY = 1,
    CMD_PAUSE = 2,
    CMD_NEXT = 3,
    CMD_PREVIOUS = 4,
    CMD_SEEK = 5,
    CMD_SHUFFLE = 6,
    CMD_REPEAT = 7,
};

MediaPlayer g_player{nullptr};
SystemMediaTransportControls g_smtc{nullptr};
// 事件在 WinRT 线程池触发，回调指针需原子读写
std::atomic<SmtcCallback> g_callback{nullptr};

void Emit(int command, long long value) {
    if (auto callback = g_callback.load()) callback(command, value);
}

TimeSpan FromMillis(long long ms) {
    return std::chrono::duration_cast<TimeSpan>(std::chrono::milliseconds(ms < 0 ? 0 : ms));
}

hstring ToHString(const wchar_t* text) {
    return text ? hstring(text) : hstring();
}

}  // namespace

extern "C" {

// 必须在调用线程上首次调用；成功返回 0
__declspec(dllexport) int smtc_init(SmtcCallback callback) {
    try {
        init_apartment(apartment_type::multi_threaded);
    } catch (const hresult_error&) {
        // 线程已初始化为其他套间时沿用现状
    }
    try {
        if (g_smtc) return 0;
        g_callback.store(callback);
        g_player = MediaPlayer();
        g_player.CommandManager().IsEnabled(false);
        g_smtc = g_player.SystemMediaTransportControls();
        g_smtc.IsPlayEnabled(true);
        g_smtc.IsPauseEnabled(true);
        g_smtc.IsNextEnabled(true);
        g_smtc.IsPreviousEnabled(true);
        g_smtc.IsStopEnabled(false);

        g_smtc.ButtonPressed([](const auto&, const SystemMediaTransportControlsButtonPressedEventArgs& args) {
            switch (args.Button()) {
                case SystemMediaTransportControlsButton::Play: Emit(CMD_PLAY, 0); break;
                case SystemMediaTransportControlsButton::Pause: Emit(CMD_PAUSE, 0); break;
                case SystemMediaTransportControlsButton::Next: Emit(CMD_NEXT, 0); break;
                case SystemMediaTransportControlsButton::Previous: Emit(CMD_PREVIOUS, 0); break;
                default: break;
            }
        });
        g_smtc.PlaybackPositionChangeRequested([](const auto&, const PlaybackPositionChangeRequestedEventArgs& args) {
            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(args.RequestedPlaybackPosition()).count();
            Emit(CMD_SEEK, ms);
        });
        g_smtc.ShuffleEnabledChangeRequested([](const auto&, const ShuffleEnabledChangeRequestedEventArgs& args) {
            Emit(CMD_SHUFFLE, args.RequestedShuffleEnabled() ? 1 : 0);
        });
        g_smtc.AutoRepeatModeChangeRequested([](const auto&, const AutoRepeatModeChangeRequestedEventArgs& args) {
            Emit(CMD_REPEAT, static_cast<long long>(args.RequestedAutoRepeatMode()));
        });

        g_smtc.PlaybackStatus(MediaPlaybackStatus::Closed);
        g_smtc.IsEnabled(true);
        return 0;
    } catch (...) {
        g_callback.store(nullptr);
        g_smtc = nullptr;
        g_player = nullptr;
        return -1;
    }
}

__declspec(dllexport) void smtc_set_enabled(int enabled) {
    if (!g_smtc) return;
    try {
        g_smtc.IsEnabled(enabled != 0);
    } catch (...) {
    }
}

__declspec(dllexport) void smtc_set_metadata(const wchar_t* title, const wchar_t* artist, const wchar_t* album,
                                             const wchar_t* coverUrl) {
    if (!g_smtc) return;
    try {
        auto updater = g_smtc.DisplayUpdater();
        updater.ClearAll();
        updater.Type(MediaPlaybackType::Music);
        auto music = updater.MusicProperties();
        music.Title(ToHString(title));
        music.Artist(ToHString(artist));
        music.AlbumTitle(ToHString(album));
        if (coverUrl && *coverUrl) {
            updater.Thumbnail(RandomAccessStreamReference::CreateFromUri(Uri(ToHString(coverUrl))));
        }
        updater.Update();
    } catch (...) {
    }
}

// 0 已停止，1 播放中，2 已暂停
__declspec(dllexport) void smtc_set_status(int status) {
    if (!g_smtc) return;
    try {
        switch (status) {
            case 1: g_smtc.PlaybackStatus(MediaPlaybackStatus::Playing); break;
            case 2: g_smtc.PlaybackStatus(MediaPlaybackStatus::Paused); break;
            default: g_smtc.PlaybackStatus(MediaPlaybackStatus::Stopped); break;
        }
    } catch (...) {
    }
}

__declspec(dllexport) void smtc_set_timeline(long long positionMs, long long durationMs) {
    if (!g_smtc) return;
    try {
        SystemMediaTransportControlsTimelineProperties timeline;
        timeline.StartTime(TimeSpan::zero());
        timeline.MinSeekTime(TimeSpan::zero());
        timeline.EndTime(FromMillis(durationMs));
        timeline.MaxSeekTime(FromMillis(durationMs));
        timeline.Position(FromMillis(positionMs < durationMs || durationMs <= 0 ? positionMs : durationMs));
        g_smtc.UpdateTimelineProperties(timeline);
    } catch (...) {
    }
}

__declspec(dllexport) void smtc_set_shuffle(int enabled) {
    if (!g_smtc) return;
    try {
        g_smtc.ShuffleEnabled(enabled != 0);
    } catch (...) {
    }
}

// 0 不循环，1 单曲，2 列表
__declspec(dllexport) void smtc_set_repeat(int mode) {
    if (!g_smtc) return;
    try {
        switch (mode) {
            case 1: g_smtc.AutoRepeatMode(MediaPlaybackAutoRepeatMode::Track); break;
            case 2: g_smtc.AutoRepeatMode(MediaPlaybackAutoRepeatMode::List); break;
            default: g_smtc.AutoRepeatMode(MediaPlaybackAutoRepeatMode::None); break;
        }
    } catch (...) {
    }
}

__declspec(dllexport) void smtc_shutdown() {
    g_callback.store(nullptr);
    try {
        if (g_smtc) {
            g_smtc.PlaybackStatus(MediaPlaybackStatus::Closed);
            g_smtc.IsEnabled(false);
        }
        if (g_player) g_player.Close();
    } catch (...) {
    }
    g_smtc = nullptr;
    g_player = nullptr;
}

}  // extern "C"
