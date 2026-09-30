package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkRepositoryCompatibilityTest {
    @Test
    fun `mihon style apk entry is accepted without enabling binary execution`() {
        val parser = ExtensionIndexParser()
        val entries = parser.parseExtensionList(
            """
            [
              {
                "name": "Example Extension",
                "pkg": "com.example.extension",
                "apk": "https://example.com/example.apk",
                "lang": "en",
                "code": 123,
                "version": "1.2.3"
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, entries.size)
        val manifest = requireNotNull(parser.toManifest(entries.single(), "community"))
        assertEquals("com.example.extension", manifest.id)
        assertEquals(ExtensionEngineType.UNSUPPORTED, manifest.engine.type)
        assertFalse(manifest.isSupported)
        assertTrue("apk" in manifest.tags)
    }
}
