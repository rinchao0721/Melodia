package com.hchen.superlyricapi

import android.os.Parcel
import android.os.Parcelable

class SuperLyricWord : Parcelable {
    var word: String = ""
    var delay: Long = 0L
    var startTime: Long = 0L
    var endTime: Long = 0L

    constructor(word: String, startTime: Long, endTime: Long) {
        this.word = word
        this.startTime = startTime
        this.endTime = endTime
        this.delay = if (endTime > startTime) endTime - startTime else 0L
    }

    constructor(word: String, delay: Long) {
        this.word = word
        this.delay = delay
    }

    private constructor(parcel: Parcel) {
        word = parcel.readString() ?: ""
        delay = parcel.readLong()
        startTime = parcel.readLong()
        endTime = parcel.readLong()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(word)
        dest.writeLong(delay)
        dest.writeLong(startTime)
        dest.writeLong(endTime)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SuperLyricWord) return false
        return word == other.word && startTime == other.startTime && endTime == other.endTime && delay == other.delay
    }

    override fun hashCode(): Int {
        var result = word.hashCode()
        result = 31 * result + delay.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    companion object CREATOR : Parcelable.Creator<SuperLyricWord> {
        override fun createFromParcel(parcel: Parcel): SuperLyricWord = SuperLyricWord(parcel)
        override fun newArray(size: Int): Array<SuperLyricWord?> = arrayOfNulls(size)
    }
}
