package com.hchen.superlyricapi

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

class SuperLyricData() : Parcelable {
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var lyric: SuperLyricLine? = null
    var secondary: SuperLyricLine? = null
    var translation: SuperLyricLine? = null
    var mediaMetadata: MediaMetadata? = null
    var playbackState: PlaybackState? = null
    var base64Icon: String? = null
    var extra: Bundle? = null

    fun setTitle(title: String?): SuperLyricData = apply { this.title = title }
    fun setArtist(artist: String?): SuperLyricData = apply { this.artist = artist }
    fun setAlbum(album: String?): SuperLyricData = apply { this.album = album }
    fun setLyric(lyric: SuperLyricLine?): SuperLyricData = apply { this.lyric = lyric }
    fun setSecondary(secondary: SuperLyricLine?): SuperLyricData = apply { this.secondary = secondary }
    fun setTranslation(translation: SuperLyricLine?): SuperLyricData = apply { this.translation = translation }
    fun setExtra(extra: Bundle?): SuperLyricData = apply {
        this.extra = if (extra != null) Bundle(extra) else null
    }

    private constructor(parcel: Parcel) : this() {
        val classLoader = SuperLyricData::class.java.classLoader
        title = parcel.readString()
        artist = parcel.readString()
        album = parcel.readString()
        lyric = parcel.readParcelable(classLoader)
        secondary = parcel.readParcelable(classLoader)
        translation = parcel.readParcelable(classLoader)
        mediaMetadata = parcel.readParcelable(classLoader)
        playbackState = parcel.readParcelable(classLoader)
        base64Icon = parcel.readString()
        extra = parcel.readBundle(classLoader)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(artist)
        dest.writeString(album)
        dest.writeParcelable(lyric, flags)
        dest.writeParcelable(secondary, flags)
        dest.writeParcelable(translation, flags)
        dest.writeParcelable(mediaMetadata, flags)
        dest.writeParcelable(playbackState, flags)
        dest.writeString(base64Icon)
        dest.writeBundle(extra)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SuperLyricData) return false
        return title == other.title &&
                artist == other.artist &&
                album == other.album &&
                lyric == other.lyric &&
                secondary == other.secondary &&
                translation == other.translation &&
                base64Icon == other.base64Icon
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (lyric?.hashCode() ?: 0)
        result = 31 * result + (secondary?.hashCode() ?: 0)
        result = 31 * result + (translation?.hashCode() ?: 0)
        result = 31 * result + (base64Icon?.hashCode() ?: 0)
        return result
    }

    companion object CREATOR : Parcelable.Creator<SuperLyricData> {
        override fun createFromParcel(parcel: Parcel): SuperLyricData = SuperLyricData(parcel)
        override fun newArray(size: Int): Array<SuperLyricData?> = arrayOfNulls(size)
    }
}
