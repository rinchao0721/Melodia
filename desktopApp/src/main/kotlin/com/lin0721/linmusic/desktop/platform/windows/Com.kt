package com.lin0721.linmusic.desktop.platform.windows

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

internal fun comGuid(data1: Int, data2: Int, data3: Int, vararg data4: Int): Memory = Memory(16).apply {
    setInt(0, data1)
    setShort(4, data2.toShort())
    setShort(6, data3.toShort())
    data4.forEachIndexed { index, value -> setByte(8L + index, value.toByte()) }
}

// 按虚表序号调用 COM 方法，第一个实参自动补 this；序号 0-2 为 IUnknown
internal class ComObject(val self: Pointer) {

    fun call(slot: Int, vararg args: Any?): Int {
        val vtable = self.getPointer(0)
        val function = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE))
        return function.invokeInt(arrayOf(self, *args))
    }

    fun release() {
        runCatching { call(2) }
    }
}
