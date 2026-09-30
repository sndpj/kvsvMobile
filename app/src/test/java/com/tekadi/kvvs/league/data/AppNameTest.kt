package com.tekadi.kvvs.league.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Feature request: "Mobile Application Name: KvsV." Parses strings.xml directly with the JDK's
 * built-in XML parser rather than an instrumented androidTest — same philosophy as
 * ScoringEngineTest in this same directory: framework-free, runs as a plain JVM unit test, no
 * emulator/device needed. Gradle's testDebugUnitTest task runs with the module directory
 * (app/) as the working directory, so the relative path below resolves correctly in a real build.
 */
class AppNameTest {

    private fun readAppNameString(): String {
        val file = File("src/main/res/values/strings.xml")
        assertTrue("strings.xml should be found relative to the app module root", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.attributes.getNamedItem("name")?.nodeValue == "app_name") {
                return node.textContent
            }
        }
        fail("No <string name=\"app_name\"> entry found in strings.xml")
        return ""
    }

    // ---------------- positive ----------------

    @Test
    fun appNameIsKvsV() {
        assertEquals("KvsV", readAppNameString())
    }

    // ---------------- negative: guard against regressing to an old name ----------------

    @Test
    fun appNameIsNoLongerTheOldValue() {
        val name = readAppNameString()
        assertNotEquals("Cricket Scorer", name)
        assertNotEquals("Tekadi Cricket", name) // an even earlier name used before this project's rebrand
    }

    @Test
    fun manifestReferencesTheStringResourceRatherThanAHardcodedLabel() {
        // GAP CHECK: if android:label were ever hardcoded directly in AndroidManifest.xml
        // instead of @string/app_name, this rename would only be half-applied — the manifest
        // would still show the old literal name regardless of strings.xml.
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist", manifest.exists())
        val content = manifest.readText()
        assertTrue("android:label must reference @string/app_name, not a literal", content.contains("android:label=\"@string/app_name\""))
    }
}
