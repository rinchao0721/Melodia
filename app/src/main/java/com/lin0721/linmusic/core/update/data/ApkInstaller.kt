package com.lin0721.linmusic.core.update.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

class ApkInstaller(private val context: Context) {

    fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun buildUnknownSourceSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun buildInstallIntent(apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolveList = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resolveList) {
            val targetPkg = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                targetPkg,
                apkUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return intent
    }

    fun install(apkFile: File) {
        context.startActivity(buildInstallIntent(apkFile))
    }
}
