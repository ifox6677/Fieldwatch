package app.fieldwatch.domain

import android.content.res.Resources
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * English resources for plain JVM unit tests.
 *
 * Android's `Resources` is a stub on the local unit-test classpath, so the
 * constructor cannot be used. The instance is allocated without running the
 * constructor and only the string lookups are overridden. Values are read from
 * the checked-in English `values` XML, which keeps test assertions aligned with
 * the production defaults without adding Robolectric to this module.
 */
object TestResources {
    val res: Resources by lazy { allocate() }

    private val values: Map<Int, String> by lazy { loadStrings() }
    private val plurals: Map<Int, Map<Int, String>> by lazy { loadPlurals() }

    private fun allocate(): Resources {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, FakeResources::class.java) as Resources
    }

    private fun loadStrings(): Map<Int, String> {
        val out = HashMap<Int, String>()
        val r = Class.forName("app.fieldwatch.R\$string")
        for (file in resourceFiles()) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                if (name.isBlank()) continue
                val id = runCatching { r.getField(name).getInt(null) }.getOrNull() ?: continue
                out[id] = unescape(stripQuotes(el.textContent))
            }
        }
        return out
    }

    private fun loadPlurals(): Map<Int, Map<Int, String>> {
        val out = HashMap<Int, MutableMap<Int, String>>()
        val r = runCatching { Class.forName("app.fieldwatch.R\$plurals") }.getOrNull()
            ?: return out
        for (file in resourceFiles()) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("plurals")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                if (name.isBlank()) continue
                val id = runCatching { r.getField(name).getInt(null) }.getOrNull() ?: continue
                val items = HashMap<Int, String>()
                val children = el.getElementsByTagName("item")
                for (j in 0 until children.length) {
                    val item = children.item(j) as Element
                    val qty = when (item.getAttribute("quantity")) {
                        "one" -> 1
                        "two" -> 2
                        "few" -> 3
                        "many" -> 4
                        "other" -> 0
                        else -> -1
                    }
                    if (qty >= 0) items[qty] = unescape(stripQuotes(item.textContent))
                }
                out[id] = items
            }
        }
        return out
    }

    private fun resourceFiles(): List<File> {
        val roots = listOf(
            File("src/main/res/values"),
            File("../app/src/main/res/values"),
            File("app/src/main/res/values"),
        )
        val dir = roots.firstOrNull { it.isDirectory } ?: return emptyList()
        return dir.listFiles { file -> file.isFile && file.extension == "xml" }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    private fun stripQuotes(raw: String): String =
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }

    private fun unescape(raw: String): String = buildString(raw.length) {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i == raw.lastIndex) {
                append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> append('\n')
                't' -> append('\t')
                'r' -> append('\r')
                '\'' -> append('\'')
                '"' -> append('"')
                '\\' -> append('\\')
                'u' -> {
                    if (i + 5 < raw.length) {
                        val hex = raw.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            append(code.toChar())
                            i += 6
                            continue
                        }
                    }
                    append(next)
                }
                else -> append(next)
            }
            i += 2
        }
    }

    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = values[id] ?: missing(id)

        override fun getString(id: Int, vararg formatArgs: Any?): String {
            val raw = getString(id)
            return if (formatArgs.isEmpty()) raw else String.format(Locale.US, raw, *formatArgs)
        }

        override fun getText(id: Int): CharSequence = getString(id)

        override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
            val options = plurals[id] ?: return getString(id, *formatArgs)
            val raw = options[quantity] ?: options[0] ?: getString(id)
            return if (formatArgs.isEmpty()) raw else String.format(Locale.US, raw, *formatArgs)
        }

        override fun getTextArray(id: Int): Array<CharSequence> = emptyArray()

        override fun getStringArray(id: Int): Array<String> = emptyArray()

        private fun missing(id: Int): String = "[missing resource $id]"
    }
}
