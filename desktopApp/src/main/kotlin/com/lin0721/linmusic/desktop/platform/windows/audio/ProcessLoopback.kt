package com.lin0721.linmusic.desktop.platform.windows.audio

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.ComObject
import com.lin0721.linmusic.desktop.platform.windows.Ole32
import com.lin0721.linmusic.desktop.platform.windows.comGuid
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "ProcessLoopback"

private const val VIRTUAL_DEVICE = "VAD\\Process_Loopback"
private const val ACTIVATE_TIMEOUT_MS = 5_000L

// IActivateAudioInterfaceAsyncOperation
private const val SLOT_GET_ACTIVATE_RESULT = 3

private const val S_OK = 0
private const val E_NOINTERFACE = 0x80004002.toInt()

private const val VT_BLOB: Short = 65
private const val ACTIVATION_TYPE_PROCESS_LOOPBACK = 1
private const val LOOPBACK_MODE_INCLUDE_TREE = 0
private const val ACTIVATION_PARAMS_SIZE = 12

private const val AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM = 0x80000000.toInt()
private const val AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000

// 200 毫秒，单位 100 纳秒
private const val BUFFER_DURATION_HNS = 2_000_000L

private const val FORMAT_SAMPLE_RATE = 48_000
private const val FORMAT_CHANNELS = 2
private const val FORMAT_BITS = 32
private const val FORMAT_BLOCK_ALIGN = FORMAT_CHANNELS * FORMAT_BITS / 8
private const val WAVE_FORMAT_IEEE_FLOAT: Short = 3
private const val WAVEFORMATEX_SIZE = 18L

// {00000000-0000-0000-C000-000000000046}
private val IidIUnknown = comGuid(0, 0, 0, 0xC0, 0, 0, 0, 0, 0, 0, 0x46)

// {94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}
private val IidIAgileObject = comGuid(0x94EA2B94.toInt(), 0xE9CC, 0x49E0, 0xC0, 0xFF, 0xEE, 0x64, 0xCA, 0x8F, 0x5B, 0x90)

// {41D949AB-9862-444A-80F6-C261334DA5EB}
private val IidCompletionHandler = comGuid(0x41D949AB, 0x9862, 0x444A, 0x80, 0xF6, 0xC2, 0x61, 0x33, 0x4D, 0xA5, 0xEB)

// 超时后系统仍可能回调，这些对象不能被回收
private val abandoned = ConcurrentLinkedQueue<ProcessActivation>()
private val recentActivations = ConcurrentLinkedQueue<ProcessActivation>()
private const val RECENT_ACTIVATIONS = 4

@Suppress("FunctionName")
internal interface Mmdevapi : Library {
    fun ActivateAudioInterfaceAsync(
        deviceInterfacePath: WString,
        riid: Pointer,
        activationParams: Pointer,
        completionHandler: Pointer,
        activationOperation: PointerByReference
    ): Int

    companion object {
        val INSTANCE: Mmdevapi by lazy { Native.load("Mmdevapi", Mmdevapi::class.java) }
    }
}

internal interface QueryInterfaceCallback : Callback {
    fun invoke(self: Pointer?, riid: Pointer?, target: Pointer?): Int
}

internal interface RefCountCallback : Callback {
    fun invoke(self: Pointer?): Int
}

internal interface ActivateCompletedCallback : Callback {
    fun invoke(self: Pointer?, operation: Pointer?): Int
}

// 一次异步激活用到的原生内存与完成回调；回调在 MTA 线程上执行，所以要实现 IAgileObject
private class ProcessActivation(processId: Long) {
    private val completed = CountDownLatch(1)
    private val supported = listOf(IidIUnknown, IidIAgileObject, IidCompletionHandler).map { it.getByteArray(0, IID_SIZE) }

    private val queryInterface = object : QueryInterfaceCallback {
        override fun invoke(self: Pointer?, riid: Pointer?, target: Pointer?): Int {
            val id = riid?.getByteArray(0, IID_SIZE)
            val matched = id != null && supported.any { it.contentEquals(id) }
            target?.setPointer(0, if (matched) self else Pointer.NULL)
            return if (matched) S_OK else E_NOINTERFACE
        }
    }

    // 对象由本类持有，引用计数不决定释放
    private val addRef = object : RefCountCallback {
        override fun invoke(self: Pointer?): Int = 1
    }
    private val release = object : RefCountCallback {
        override fun invoke(self: Pointer?): Int = 1
    }
    private val activateCompleted = object : ActivateCompletedCallback {
        override fun invoke(self: Pointer?, operation: Pointer?): Int {
            completed.countDown()
            return S_OK
        }
    }

    private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
        listOf(queryInterface, addRef, release, activateCompleted).forEachIndexed { index, callback ->
            setPointer(index.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callback))
        }
    }
    val handler = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }

    private val blob = Memory(ACTIVATION_PARAMS_SIZE.toLong()).apply {
        setInt(0, ACTIVATION_TYPE_PROCESS_LOOPBACK)
        setInt(4, processId.toInt())
        setInt(8, LOOPBACK_MODE_INCLUDE_TREE)
    }

    // PROPVARIANT：vt@0 cbSize@8 pBlobData@16
    val params = Memory(24).apply {
        clear()
        setShort(0, VT_BLOB)
        setInt(8, ACTIVATION_PARAMS_SIZE)
        setPointer(16, blob)
    }

    fun await(timeoutMs: Long): Boolean = completed.await(timeoutMs, TimeUnit.MILLISECONDS)

    private companion object {
        const val IID_SIZE = 16
    }
}

// 只录指定进程及其子进程的声音，与输出设备无关；虚拟设备不支持 GetMixFormat，格式自己指定、由系统转换
internal fun openProcessLoopback(processId: Long): LoopbackEndpoint {
    val activation = ProcessActivation(processId)
    val operationRef = PointerByReference()
    val hr = try {
        Mmdevapi.INSTANCE.ActivateAudioInterfaceAsync(WString(VIRTUAL_DEVICE), IidIAudioClient, activation.params, activation.handler, operationRef)
    } catch (e: LinkageError) {
        throw WasapiException("系统不支持进程内录", e)
    }
    requireSucceeded(hr, "激活进程内录")
    val operation = ComObject(operationRef.value)
    val owned = ArrayDeque<ComObject>()
    try {
        if (!activation.await(ACTIVATE_TIMEOUT_MS)) {
            abandoned += activation
            throw WasapiException("激活进程内录超时")
        }
        val result = IntByReference()
        val unknown = PointerByReference()
        requireSucceeded(operation.call(SLOT_GET_ACTIVATE_RESULT, result, unknown), "读取激活结果")
        requireSucceeded(result.value, "激活进程内录")
        val client = ComObject(unknown.value).also(owned::addFirst)
        val flags = AUDCLNT_STREAMFLAGS_LOOPBACK or AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM or AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY
        requireSucceeded(
            client.call(SLOT_INITIALIZE, AUDCLNT_SHAREMODE_SHARED, flags, BUFFER_DURATION_HNS, 0L, floatFormat(), null),
            "初始化进程内录"
        )
        val ref = PointerByReference()
        requireSucceeded(client.call(SLOT_GET_SERVICE, IidIAudioCaptureClient, ref), "获取采集接口")
        val captureClient = ComObject(ref.value).also(owned::addFirst)
        return LoopbackEndpoint(client, captureClient, LoopbackFormat(FORMAT_SAMPLE_RATE, FORMAT_CHANNELS, SampleFormat.FLOAT32), owned.toList())
    } catch (e: Throwable) {
        owned.forEach(ComObject::release)
        throw e
    } finally {
        operation.release()
        // 系统线程可能在这里返回之后才调用完成回调的 Release
        recentActivations += activation
        while (recentActivations.size > RECENT_ACTIVATIONS) recentActivations.poll()
    }
}

// WAVEFORMATEX：tag@0 channels@2 rate@4 avgBytes@8 blockAlign@12 bits@14 cbSize@16
private fun floatFormat(): Memory = Memory(WAVEFORMATEX_SIZE).apply {
    setShort(0, WAVE_FORMAT_IEEE_FLOAT)
    setShort(2, FORMAT_CHANNELS.toShort())
    setInt(4, FORMAT_SAMPLE_RATE)
    setInt(8, FORMAT_SAMPLE_RATE * FORMAT_BLOCK_ALIGN)
    setShort(12, FORMAT_BLOCK_ALIGN.toShort())
    setShort(14, FORMAT_BITS.toShort())
    setShort(16, 0)
}

// 优先只录 Melodia 自己，失败时退回默认设备混音
internal fun openPlaybackLoopback(ole32: Ole32, processId: Long): LoopbackEndpoint = try {
    openProcessLoopback(processId)
} catch (e: WasapiException) {
    AppLogger.w(TAG, "进程内录不可用，改录默认设备：${e.message}", e.cause)
    openDefaultLoopback(ole32)
}
