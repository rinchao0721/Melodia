/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.decoder.ffmpeg;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import java.io.File;

/** Configures and queries the underlying native library. */
@UnstableApi
public final class FfmpegLibrary {

  static {
    MediaLibraryInfo.registerModule("media3.decoder.ffmpeg");
  }

  private static final String TAG = "FfmpegLibrary";

  // 本项目改动：改为从组件目录按绝对路径加载，支持安装后重试并捕获记录异常
  private static final Object LOCK = new Object();
  @Nullable private static String libraryPath;
  private static boolean isLoaded;

  // 本项目改动：改用已有 @Nullable，避免引入 checkerframework 新依赖
  @Nullable private static String version;
  private static int inputBufferPaddingSize = C.LENGTH_UNSET;

  private FfmpegLibrary() {}

  // 本项目改动：指定组件 libffmpegJNI.so 的绝对路径
  public static void setLibraryPath(@Nullable String path) {
    synchronized (LOCK) {
      libraryPath = path;
    }
  }

  /**
   * Override the names of the FFmpeg native libraries. If an application wishes to call this
   * method, it must do so before calling any other method defined by this class, and before
   * instantiating a {@link FfmpegAudioRenderer} or {@link ExperimentalFfmpegVideoRenderer}
   * instance.
   *
   * @param libraries The names of the FFmpeg native libraries.
   */
  public static void setLibraries(String... libraries) {
    // 本项目改动：改由 setLibraryPath 指定组件绝对路径，此处保留方法签名兼容
  }

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean isAvailable() {
    // 本项目改动：组件未安装时返回 false，安装后可再次尝试 System.load，不永久锁定失败状态
    synchronized (LOCK) {
      if (libraryPath == null) {
        return false;
      }
      File libraryFile = new File(libraryPath);
      if (!libraryFile.isFile()) {
        return false;
      }
      if (isLoaded) {
        return true;
      }
      try {
        System.load(libraryFile.getAbsolutePath());
        isLoaded = true;
      } catch (UnsatisfiedLinkError | SecurityException e) {
        Log.e(TAG, "加载 FFmpeg 组件失败: " + libraryPath, e);
      } catch (Throwable t) {
        Log.e(TAG, "加载 FFmpeg 组件异常: " + libraryPath, t);
      }
      return isLoaded;
    }
  }

  /** Returns the version of the underlying library if available, or null otherwise. */
  @Nullable
  public static String getVersion() {
    if (!isAvailable()) {
      return null;
    }
    if (version == null) {
      version = ffmpegGetVersion();
    }
    return version;
  }

  /**
   * Returns the required amount of padding for input buffers in bytes, or {@link C#LENGTH_UNSET} if
   * the underlying library is not available.
   */
  public static int getInputBufferPaddingSize() {
    if (!isAvailable()) {
      return C.LENGTH_UNSET;
    }
    if (inputBufferPaddingSize == C.LENGTH_UNSET) {
      inputBufferPaddingSize = ffmpegGetInputBufferPaddingSize();
    }
    return inputBufferPaddingSize;
  }

  /**
   * Returns whether the underlying library supports the specified MIME type.
   *
   * @param mimeType The MIME type to check.
   */
  public static boolean supportsFormat(String mimeType) {
    if (!isAvailable()) {
      return false;
    }
    @Nullable String codecName = getCodecName(mimeType);
    if (codecName == null) {
      return false;
    }
    if (!ffmpegHasDecoder(codecName)) {
      Log.w(TAG, "No " + codecName + " decoder available. Check the FFmpeg build configuration.");
      return false;
    }
    return true;
  }

  /**
   * Returns the name of the FFmpeg decoder that could be used to decode the format, or {@code null}
   * if it's unsupported.
   */
  @Nullable
  /* package */ static String getCodecName(String mimeType) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        return "aac";
      case MimeTypes.AUDIO_MPEG:
      case MimeTypes.AUDIO_MPEG_L1:
      case MimeTypes.AUDIO_MPEG_L2:
        return "mp3";
      case MimeTypes.AUDIO_AC3:
        return "ac3";
      case MimeTypes.AUDIO_E_AC3:
      case MimeTypes.AUDIO_E_AC3_JOC:
        return "eac3";
      case MimeTypes.AUDIO_TRUEHD:
        return "truehd";
      case MimeTypes.AUDIO_DTS:
      case MimeTypes.AUDIO_DTS_EXPRESS:
      case MimeTypes.AUDIO_DTS_HD:
        return "dca";
      case MimeTypes.AUDIO_VORBIS:
        return "vorbis";
      case MimeTypes.AUDIO_OPUS:
        return "opus";
      case MimeTypes.AUDIO_AMR_NB:
        return "amrnb";
      case MimeTypes.AUDIO_AMR_WB:
        return "amrwb";
      case MimeTypes.AUDIO_FLAC:
        return "flac";
      case MimeTypes.AUDIO_ALAC:
        return "alac";
      case MimeTypes.AUDIO_MLAW:
        return "pcm_mulaw";
      case MimeTypes.AUDIO_ALAW:
        return "pcm_alaw";
      case MimeTypes.VIDEO_H264:
        return "h264";
      case MimeTypes.VIDEO_H265:
        return "hevc";
      default:
        return null;
    }
  }

  private static native String ffmpegGetVersion();

  private static native int ffmpegGetInputBufferPaddingSize();

  private static native boolean ffmpegHasDecoder(String codecName);
}
