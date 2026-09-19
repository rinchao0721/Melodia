package com.hchen.superlyricapi

import android.os.Parcel
import android.os.Parcelable

class SuperLyricLine : Parcelable {
    var text: String = ""
    var words: Array<SuperLyricWord>? = null
    var startTime: Long = 0L
    var endTime: Long = 0L
    var delay: Long = 0L

    constructor(text: String) {
        this.text = text
    }

    constructor(text: String, startTime: Long, endTime: Long) {
        this.text = text
        this.startTime = startTime
        this.endTime = endTime
        this.delay = if (endTime > startTime) endTime - startTime else 0L
    }

    constructor(text: String, words: Array<SuperLyricWord>?, startTime: Long, endTime: Long) {
        this.text = text
        this.words = words
        this.startTime = startTime
        this.endTime = endTime
        this.delay = if (endTime > startTime) endTime - startTime else 0L
    }

    private constructor(parcel: Parcel) {
        text = parcel.readString() ?: ""
        words = parcel.createTypedArray(SuperLyricWord.CREATOR)
        startTime = parcel.readLong()
        endTime = parcel.readLong()
        delay = parcel.readLong()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(text)
        dest.writeTypedArray(words, flags)
        dest.writeLong(startTime)
        dest.writeLong(endTime)
        dest.writeLong(delay)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SuperLyricLine) return false
        if (text != other.text || startTime != other.startTime || endTime != other.endTime || delay != other.delay) return false
        return if (words != null) other.words != null && words.contentEquals(other.words) else other.words == null
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (words?.contentHashCode() ?: 0)
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        result = 31 * result + delay.hashCode()
        return result
    }

    companion object CREATOR : Parcelable.Creator<SuperLyricLine> {
        override fun createFromParcel(parcel: Parcel): SuperLyricLine = SuperLyricLine(parcel)
        override fun newArray(size: Int): Array<SuperLyricLine?> = arrayOfNulls(size)
    }
}
