package org.wordpress.android.util

import android.content.Context
import android.preference.PreferenceManager
import io.sentry.android.core.SentryAndroid
import io.sentry.core.Sentry
import io.sentry.core.protocol.User
import org.wordpress.android.BuildConfig
import org.wordpress.android.R
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.util.AppLog.T
import javax.inject.Inject
import javax.inject.Singleton

private const val EVENT_BUS_MODULE = "org.greenrobot.eventbus"
private const val EVENT_BUS_EXCEPTION = "EventBusException"
private const val EVENT_BUS_INVOKING_SUBSCRIBER_FAILED_ERROR = "Invoking subscriber failed"

@Singleton
class CrashLogging @Inject constructor(
    private val accountStore: AccountStore
) {
    fun start(context: Context) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.cacheDirPath = context.cacheDir.absolutePath
            options.isEnableSessionTracking = true // Release Health tracking
            options.setBeforeSend { event, _ ->

                if (!this.shouldSendEvents(context)) return@setBeforeSend null

                if (event.exceptions.size > 1) {
                    event.exceptions.lastOrNull()?.let { lastException ->
                        // Remove the "Invoking subscriber failed" exception so that the main error will show up
                        // in Sentry. This error only means that an exception occurred during an EventBus event and
                        // it's not particularly useful for debugging.
                        if (lastException.module == EVENT_BUS_MODULE &&
                                lastException.type == EVENT_BUS_EXCEPTION &&
                                lastException.value == EVENT_BUS_INVOKING_SUBSCRIBER_FAILED_ERROR) {
                            event.exceptions.remove(lastException)
                        }
                    }
                }
                event
            }
        }

        Sentry.setTag("version", BuildConfig.VERSION_NAME)

        this.accountStore.account.apply {
            val user = User()
            user.id = this.userId.toString()
            user.username = this.userName
            user.email = this.email

            Sentry.setUser(user)
        }
    }

    @JvmOverloads
    fun log(message: String, tag: AppLog.T? = null) = tag?.let {
        Sentry.addBreadcrumb(message, tag.toString())
    } ?: run {
        Sentry.addBreadcrumb(message)
    }

    @JvmOverloads
    fun logException(tr: Throwable, tag: AppLog.T? = null) = this.log(tr.toString(), tag)

    @JvmOverloads
    fun report(message: String, tag: AppLog.T? = null) {
        if (tag != null) {
            Sentry.setExtra("tag", tag.toString())
            Sentry.setTag("tag", tag.toString())
        }

        val sentryId = Sentry.captureMessage(message)
        AppLog.d(T.UTILS, "Captured Sentry Event: $sentryId")

        Sentry.removeExtra("tag")
        Sentry.removeTag("tag")
    }

    @JvmOverloads
    fun reportException(tr: Throwable, tag: AppLog.T? = null, message: String? = null) {
        if (message != null) {
            Sentry.setExtra("message", message)
        }

        if (tag != null) {
            Sentry.setExtra("tag", tag.toString())
            Sentry.setTag("tag", tag.toString())
        }

        val sentryId = Sentry.captureException(tr)
        AppLog.d(T.UTILS, "Captured Sentry Event: $sentryId")

        Sentry.removeExtra("tag")
        Sentry.removeTag("tag")
        Sentry.removeExtra("message")
    }

    private fun shouldSendEvents(context: Context): Boolean {
        if (PackageUtils.isDebugBuild()) {
            return false
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val hasUserOptedOut = !prefs.getBoolean(context.getString(R.string.pref_key_send_crash), true)
        return !hasUserOptedOut
    }
}
