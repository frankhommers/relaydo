package cn.ppps.forwarder.utils.update

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import java.io.File
import java.security.MessageDigest

/**
 * Hands a verified APK to the platform PackageInstaller (API 21+). On API 31+ the session asks for no user
 * action; Android then installs without confirmation when it allows it (this app updating itself, with
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION) and otherwise reports STATUS_PENDING_USER_ACTION, which leads to the
 * normal system confirmation: directly when the app is in the foreground, through a notification otherwise.
 */
object UpdateInstaller {
    private const val TAG = "Updater"
    const val ACTION_STATUS = "tools.franks.relaydo.UPDATE_INSTALL_STATUS"
    const val CHANNEL_ID = "app_updates"
    private const val NOTIFY_ID = 0x5F0D

    /** Confirmation intent of the committed session, kept for the About screen's "Install now" button (process lifetime). */
    @Volatile var pendingConfirmation: Intent? = null

    /** Opens the pending system confirmation; false when none is held any more (process restarted). */
    fun openPendingConfirmation(context: Context): Boolean {
        val confirm = pendingConfirmation ?: return false
        return try { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { pendingConfirmation = null; false }
    }

    /** Abandons this app's own unfinished install sessions, so a new attempt starts clean. */
    fun abandonOwnSessions(context: Context) {
        if (Build.VERSION.SDK_INT < 21) return
        val installer = context.packageManager.packageInstaller
        for (s in installer.mySessions) try { installer.abandonSession(s.sessionId) } catch (_: Exception) {}
        pendingConfirmation = null
    }

    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun installVerified(context: Context, file: File, manifest: UpdatePolicy.Manifest, interactive: Boolean) {
        if (Build.VERSION.SDK_INT < 21) { UpdateManager.recordInstallState(context, UpdateResult.INSTALL_UNSUPPORTED, manifest.versionName); return }
        if (!canRequestInstalls(context)) {
            // Keep the verified file; it is verified again before installing once the permission is granted.
            UpdateManager.recordInstallState(context, UpdateResult.INSTALL_PERMISSION, manifest.versionName)
            if (!interactive || !isForeground()) notify(context, context.getString(R.string.updater_notify_permission_title), context.getString(R.string.updater_notify_permission_text), aboutIntent(context))
            return
        }
        UpdateManager.recordInstallState(context, UpdateResult.INSTALLING, manifest.versionName)
        commit(context, file, manifest)
    }

    @SuppressLint("WrongConstant")
    private fun commit(context: Context, file: File, manifest: UpdatePolicy.Manifest) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(file.length())
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(
            if (cn.ppps.forwarder.BuildConfig.UPDATER_TEST_REQUIRE_USER_ACTION) PackageInstaller.SessionParams.USER_ACTION_REQUIRED
            else PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
        )
        if (Build.VERSION.SDK_INT >= 33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n); out.write(buffer, 0, n) }
                    }
                    session.fsync(out)
                }
                // The bytes written into the session must still be the verified ones.
                val written = digest.digest().joinToString("") { "%02x".format(it) }
                if (written != manifest.apkSha256) {
                    session.abandon(); file.delete()
                    UpdateManager.recordInstallState(context, UpdateResult.SHA256_MISMATCH, manifest.versionName)
                    return
                }
                val status = Intent(context, StatusReceiver::class.java).setAction(ACTION_STATUS).putExtra(EXTRA_VERSION_NAME, manifest.versionName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, status, flags)
                session.commit(pending.intentSender)
                Log.i(TAG, "session $sessionId committed for ${manifest.versionName}")
            }
            file.delete()
        } catch (e: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) {}
            file.delete()
            UpdateManager.recordInstallState(context, UpdateResult.INSTALL_FAILED, e.javaClass.simpleName)
        }
    }

    const val EXTRA_VERSION_NAME = "version_name"

    fun aboutIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_ABOUT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
    }
    const val EXTRA_OPEN_ABOUT = "open_about_for_update"

    fun notify(context: Context, title: String, text: String, intent: PendingIntent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.updater_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_forwarder).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(intent).setAutoCancel(true).build()
        try { nm.notify(NOTIFY_ID, n) } catch (e: SecurityException) { Log.w(TAG, "notification not allowed") }
    }

    fun cancelNotification(context: Context) = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFY_ID)

    /** Receives the PackageInstaller session status. Not exported: only reachable through our PendingIntent. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_STATUS) return
            val version = intent.getStringExtra(EXTRA_VERSION_NAME) ?: ""
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            Log.i(TAG, "install status $status for $version")
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    pendingConfirmation = Intent(confirm)
                    UpdateManager.recordInstallState(context, UpdateResult.WAITING_CONFIRMATION, version)
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    var shown = false
                    if (isForeground()) shown = try { context.startActivity(confirm); true } catch (e: Exception) { false }
                    if (!shown) {
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                        notify(context, context.getString(R.string.updater_notify_confirm_title), context.getString(R.string.updater_notify_confirm_text, version),
                            PendingIntent.getActivity(context, 2, confirm, flags))
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> { pendingConfirmation = null; UpdateManager.recordInstallState(context, UpdateResult.INSTALLED, version) }
                else -> {
                    pendingConfirmation = null
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: status.toString()
                    UpdateManager.recordInstallState(context, UpdateResult.INSTALL_FAILED, message.take(120))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun installerOfRecord(context: Context): String? = try {
        if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        else context.packageManager.getInstallerPackageName(context.packageName)
    } catch (_: PackageManager.NameNotFoundException) { null }
}
