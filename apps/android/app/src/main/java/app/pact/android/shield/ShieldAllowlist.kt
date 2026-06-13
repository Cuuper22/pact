package app.pact.android.shield

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager

/**
 * The set of packages the shield must NEVER overlay. Emergency calling, the
 * dialer, and the in-call UI are off-limits by architecture and by Play policy.
 *
 * We resolve the device's real default dialer/phone packages dynamically so this
 * holds across OEMs, and we always keep our own app and core system surfaces.
 */
object ShieldAllowlist {

    /** Static fallbacks for well-known dialer / in-call / system packages. */
    private val STATIC_ALLOW = setOf(
        // Common dialer / phone / in-call packages across OEMs.
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.google.android.apps.tachyon", // Meet/Duo (calls)
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
        "com.android.emergency", // Emergency information / SOS
        "com.google.android.apps.safetyhub",
        // System UI surfaces we must not fight.
        "com.android.systemui",
        "android",
        "com.android.settings", // user must be able to revoke / adjust
    )

    fun build(context: Context): Set<String> {
        val set = HashSet<String>(STATIC_ALLOW)
        set += context.packageName // never shield ourselves (the night screen is ours)
        set += resolveDefaultDialer(context)
        set += resolveDialIntentPackages(context)
        set += resolveLauncher(context) // the home launcher: the table clock lives over apps, not home
        return set
    }

    private fun resolveDefaultDialer(context: Context): Set<String> {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val pkg = tm?.defaultDialerPackage
            if (pkg.isNullOrBlank()) emptySet() else setOf(pkg)
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun resolveDialIntentPackages(context: Context): Set<String> {
        val out = HashSet<String>()
        val pm = context.packageManager
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
            Intent(Intent.ACTION_CALL, Uri.parse("tel:")),
        ).forEach { intent ->
            runCatching {
                pm.queryIntentActivities(intent, 0).forEach { ri ->
                    ri.activityInfo?.packageName?.let { out += it }
                }
            }
        }
        return out
    }

    private fun resolveLauncher(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val ri = pm.resolveActivity(intent, 0)
            val pkg = ri?.activityInfo?.packageName
            if (pkg.isNullOrBlank()) emptySet() else setOf(pkg)
        } catch (_: Throwable) {
            emptySet()
        }
    }
}
