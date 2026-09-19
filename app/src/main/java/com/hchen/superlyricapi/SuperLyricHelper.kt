package com.hchen.superlyricapi

import android.os.IBinder
import android.os.RemoteException
import com.lin0721.linmusic.core.log.AppLogger

object SuperLyricHelper {
    private const val TAG = "SuperLyricHelper"

    @Volatile
    private var mManager: ISuperLyricManager? = null

    val isAvailable: Boolean
        get() {
            return try {
                ensureManager()
                mManager != null
            } catch (_: Exception) {
                false
            }
        }

    fun sendLyric(data: SuperLyricData): Boolean {
        return try {
            ensureManager()
            ensurePublisherRegistered()
            mManager?.sendLyric(data)
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "sendLyric 失败: ${e.message}")
            false
        }
    }

    fun sendStop(data: SuperLyricData): Boolean {
        return try {
            ensureManager()
            ensurePublisherRegistered()
            mManager?.sendStop(data)
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "sendStop 失败: ${e.message}")
            false
        }
    }

    fun registerPublisher(): Boolean {
        return try {
            ensureManager()
            mManager?.registerPublisher()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "registerPublisher 失败: ${e.message}")
            false
        }
    }

    fun unregisterPublisher(): Boolean {
        return try {
            ensureManager()
            mManager?.unregisterPublisher()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "unregisterPublisher 失败: ${e.message}")
            false
        }
    }

    fun isPublisherRegistered(): Boolean {
        return try {
            ensureManager()
            mManager?.isPublisherRegistered ?: false
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    private fun ensureManager() {
        if (mManager != null) return

        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val iBinder = getServiceMethod.invoke(null, "super_lyric") as? IBinder
                ?: throw IllegalStateException("SuperLyric 服务未挂载")

            mManager = ISuperLyricManager.Stub.asInterface(iBinder)
            iBinder.linkToDeath({
                mManager = null
            }, 0)
        } catch (e: Exception) {
            mManager = null
            throw IllegalStateException("获取 SuperLyricManager 失败: ${e.message}", e)
        }
    }

    private fun ensurePublisherRegistered() {
        val registered = try {
            mManager?.isPublisherRegistered ?: false
        } catch (_: RemoteException) {
            false
        }
        if (!registered) {
            mManager?.registerPublisher()
        }
    }
}
