package app.fieldwatch

import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Localized text for domain and radio code that has no Context.
 *
 * [install] is called once from [FieldwatchApp]. JVM tests and code that runs before
 * the Application starts get the English fallback.
 */
object AppText {
    @Volatile
    private var resources: Resources? = null

    fun install(res: Resources) {
        resources = res
    }

    fun get(@StringRes id: Int, fallback: String, vararg args: Any): String {
        val res = resources
        if (res != null) {
            runCatching { return res.getString(id, *args) }
        }
        if (args.isEmpty()) return fallback
        return runCatching { String.format(Locale.getDefault(), fallback, *args) }
            .getOrDefault(fallback)
    }
}
