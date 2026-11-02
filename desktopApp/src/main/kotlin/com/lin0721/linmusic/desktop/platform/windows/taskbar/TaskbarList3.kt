package com.lin0721.linmusic.desktop.platform.windows.taskbar

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.Ole32
import com.lin0721.linmusic.desktop.platform.windows.comGuid
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

private const val TAG = "TaskbarList3"

// ITaskbarList3 的虚表序号：IUnknown 0-2，ITaskbarList 3-7，ITaskbarList2 8，ITaskbarList3 从 9 起
private const val SLOT_RELEASE = 2
private const val SLOT_HR_INIT = 3
private const val SLOT_THUMB_BAR_ADD_BUTTONS = 15
private const val SLOT_THUMB_BAR_UPDATE_BUTTONS = 16

// THUMBBUTTON 以 8 字节对齐：mask@0 id@4 bitmap@8 icon@16 tip@24(260 个 WCHAR) flags@544，总长 552
private const val BUTTON_STRUCT_SIZE = 552L
private const val OFFSET_MASK = 0L
private const val OFFSET_ID = 4L
private const val OFFSET_ICON = 16L
private const val OFFSET_TIP = 24L
private const val OFFSET_FLAGS = 544L
private const val TIP_MAX_CHARS = 259

private const val THB_ICON = 0x2
private const val THB_TOOLTIP = 0x4
private const val THB_FLAGS = 0x8
private const val THBF_ENABLED = 0x0
private const val THBF_HIDDEN = 0x8

internal class ThumbButtonData(val spec: ThumbButtonSpec, val icon: Pointer?)

// 只封装用到的几个方法；须在 CoInitializeEx 过的同一线程上调用
internal class TaskbarList3 private constructor(private val self: Pointer) {

    private fun slot(index: Int): Function {
        val vtable = self.getPointer(0)
        return Function.getFunction(vtable.getPointer(index.toLong() * Native.POINTER_SIZE))
    }

    fun hrInit(): Int = slot(SLOT_HR_INIT).invokeInt(arrayOf(self))

    // 返回 HRESULT；任务栏按钮尚未创建时会失败
    fun addButtons(hwnd: Pointer, buttons: List<ThumbButtonData>): Int =
        callWithButtons(SLOT_THUMB_BAR_ADD_BUTTONS, hwnd, buttons)

    fun updateButtons(hwnd: Pointer, buttons: List<ThumbButtonData>): Int =
        callWithButtons(SLOT_THUMB_BAR_UPDATE_BUTTONS, hwnd, buttons)

    fun release() {
        runCatching { slot(SLOT_RELEASE).invokeInt(arrayOf(self)) }
    }

    private fun callWithButtons(slotIndex: Int, hwnd: Pointer, buttons: List<ThumbButtonData>): Int {
        val memory = Memory(BUTTON_STRUCT_SIZE * buttons.size).apply { clear() }
        buttons.forEachIndexed { index, button ->
            val base = index * BUTTON_STRUCT_SIZE
            memory.setInt(base + OFFSET_MASK, THB_ICON or THB_TOOLTIP or THB_FLAGS)
            memory.setInt(base + OFFSET_ID, button.spec.id)
            memory.setPointer(base + OFFSET_ICON, button.icon)
            memory.setWideString(base + OFFSET_TIP, button.spec.tooltip.take(TIP_MAX_CHARS))
            memory.setInt(base + OFFSET_FLAGS, if (button.spec.hidden) THBF_HIDDEN else THBF_ENABLED)
        }
        return slot(slotIndex).invokeInt(arrayOf(self, hwnd, buttons.size, memory))
    }

    companion object {

        // CLSID_TaskbarList {56FDF344-FD6D-11d0-958A-006097C9A090}
        private val ClsidTaskbarList = comGuid(0x56FDF344, 0xFD6D, 0x11D0, 0x95, 0x8A, 0x00, 0x60, 0x97, 0xC9, 0xA0, 0x90)

        // IID_ITaskbarList3 {EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}
        private val IidTaskbarList3 = comGuid(0xEA1AFB91.toInt(), 0x9E28, 0x4B86, 0x90, 0xE9, 0x9E, 0x9F, 0x8A, 0x5E, 0xEF, 0xAF)

        // 创建并初始化；失败返回 null
        fun create(): TaskbarList3? {
            try {
                val result = PointerByReference()
                val hr = Ole32.INSTANCE.CoCreateInstance(
                    ClsidTaskbarList, null, Ole32.CLSCTX_INPROC_SERVER, IidTaskbarList3, result
                )
                val pointer = result.value
                if (hr != 0 || pointer == null) {
                    AppLogger.w(TAG, "创建 ITaskbarList3 失败 hr=0x${Integer.toHexString(hr)}")
                    return null
                }
                val list = TaskbarList3(pointer)
                val initHr = list.hrInit()
                if (initHr != 0) {
                    AppLogger.w(TAG, "ITaskbarList3 初始化失败 hr=0x${Integer.toHexString(initHr)}")
                    list.release()
                    return null
                }
                return list
            } catch (e: LinkageError) {
                AppLogger.w(TAG, "加载 COM 接口失败", e)
                return null
            }
        }
    }
}
