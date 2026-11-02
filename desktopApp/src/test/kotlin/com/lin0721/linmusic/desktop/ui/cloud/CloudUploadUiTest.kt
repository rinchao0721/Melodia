package com.lin0721.linmusic.desktop.ui.cloud

import com.lin0721.linmusic.desktop.data.cloud.FileUploadSource
import com.lin0721.linmusic.feature.cloud.upload.UploadStatus
import com.lin0721.linmusic.feature.cloud.upload.UploadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CloudUploadUiTest {

    private fun task(status: UploadStatus, progress: Float = 0f) =
        UploadTask(source = FileUploadSource(File("a.mp3")), fileName = "a.mp3", status = status, progress = progress)

    @Test
    fun overallProgressCountsFinishedAndPartialTasks() {
        val tasks = listOf(task(UploadStatus.SUCCESS), task(UploadStatus.FAILED), task(UploadStatus.UPLOADING, 0.5f), task(UploadStatus.QUEUED))
        assertEquals(0.625f, uploadOverallProgress(tasks)!!, 0.0001f)
        assertNull(uploadOverallProgress(listOf(task(UploadStatus.SUCCESS), task(UploadStatus.FAILED))))
    }

    @Test
    fun enqueueMessageReflectsDuplicates() {
        assertEquals("没有可上传的音频文件", uploadEnqueuedMessage(0, 0))
        assertEquals("这些歌曲已在上传队列中", uploadEnqueuedMessage(2, 0))
        assertEquals("已加入上传队列 1 首，其余已在队列中", uploadEnqueuedMessage(3, 1))
        assertEquals("已加入上传队列 3 首", uploadEnqueuedMessage(3, 3))
    }
}
