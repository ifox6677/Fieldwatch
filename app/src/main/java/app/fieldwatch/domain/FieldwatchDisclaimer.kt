package app.fieldwatch.domain

import android.content.res.Resources
import app.fieldwatch.R

/**
 * Operator-facing disclaimer. First-run, Debrief, and AI Export share the same core.
 *
 * The MIT licence text itself stays in English: it is a legal document and is
 * reproduced verbatim. The sentences around it are app copy and are localized.
 */
object FieldwatchDisclaimer {
    fun hobby(res: Resources): String = res.getString(R.string.disclaimer_hobby)

    fun hypotheses(res: Resources): String = res.getString(R.string.disclaimer_hypotheses)

    fun liability(res: Resources): String = res.getString(R.string.disclaimer_liability)

    fun location(res: Resources): String = res.getString(R.string.disclaimer_location)

    fun accept(res: Resources): String = res.getString(R.string.disclaimer_accept)

    fun licenseTitle(res: Resources): String = res.getString(R.string.disclaimer_license_title)

    /** Body of LICENSE in the repository, without the title line. Legal text, English. */
    const val LICENSE_BODY =
        "Copyright (c) 2026 Off Grid Pete LLC\n" +
            "\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy " +
            "of this software and associated documentation files (the \"Software\"), to deal " +
            "in the Software without restriction, including without limitation the rights " +
            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell " +
            "copies of the Software, and to permit persons to whom the Software is " +
            "furnished to do so, subject to the following conditions:\n" +
            "\n" +
            "The above copyright notice and this permission notice shall be included in all " +
            "copies or substantial portions of the Software.\n" +
            "\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, " +
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE " +
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER " +
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, " +
            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
            "SOFTWARE."

    fun licenseText(res: Resources): String = licenseTitle(res) + "\n\n" + LICENSE_BODY

    fun firstRunDisclaimer(res: Resources): String =
        hobby(res) + "\n\n" + hypotheses(res) + "\n\n" + liability(res)

    fun firstRun(res: Resources): String =
        firstRunDisclaimer(res) + "\n\n" + licenseText(res) + "\n\n" + accept(res)

    /** Debrief text / PDF. Same core as first-run, without the click-through line. */
    fun report(res: Resources, window: DebriefWindow? = null): String {
        val source = if (window?.sitName != null) {
            res.getString(R.string.disclaimer_report_source_named, window.sitName)
        } else {
            res.getString(R.string.disclaimer_report_source_window)
        }
        return hobby(res) + "\n\n" + hypotheses(res) + "\n\n" + liability(res) + "\n\n" +
            location(res) + "\n\n" +
            res.getString(R.string.disclaimer_report_tail, source)
    }

    fun compare(res: Resources): String =
        hobby(res) + "\n\n" + hypotheses(res) + "\n\n" + liability(res) + "\n\n" +
            location(res) + "\n\n" +
            res.getString(R.string.disclaimer_compare_tail)

    fun experimentalMarkdown(res: Resources): String = buildString {
        appendLine(res.getString(R.string.disclaimer_experimental_heading))
        appendLine(hobby(res))
        appendLine(hypotheses(res))
        appendLine(liability(res))
        appendLine(location(res))
        appendLine(res.getString(R.string.disclaimer_experimental_tail))
        appendLine(res.getString(R.string.disclaimer_experimental_tail2))
    }
}
