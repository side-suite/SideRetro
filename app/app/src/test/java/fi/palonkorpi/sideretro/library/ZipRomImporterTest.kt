package fi.palonkorpi.sideretro.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipRomImporterTest {

    @Test fun `extracts one nested ROM and ignores Finder metadata`() = withTempDir { dir ->
        val result = ZipRomImporter.extract(
            zipOf(
                "__MACOSX/._game.gba" to byteArrayOf(1),
                ".DS_Store" to byteArrayOf(1),
                "Games/POKEMON.GBA" to byteArrayOf(7, 8, 9),
            ).inputStream(),
            dir,
        )

        assertTrue(result is ZipRomImporter.Result.Extracted)
        result as ZipRomImporter.Result.Extracted
        assertEquals("POKEMON.GBA", result.displayName)
        assertTrue(result.tempFile.readBytes().contentEquals(byteArrayOf(7, 8, 9)))
        result.tempFile.delete()
    }

    @Test fun `rejects an archive with multiple playable ROMs`() = withTempDir { dir ->
        val result = ZipRomImporter.extract(
            zipOf("one.gb" to byteArrayOf(1), "two.nes" to byteArrayOf(2)).inputStream(),
            dir,
        )

        assertEquals(
            ZipRomImporter.Reason.MULTIPLE_ROMS,
            (result as ZipRomImporter.Result.Rejected).reason,
        )
        assertFalse(dir.listFiles().orEmpty().any { it.isFile })
    }

    @Test fun `rejects traversal paths rather than using them as filenames`() = withTempDir { dir ->
        val result = ZipRomImporter.extract(zipOf("../../escape.nes" to byteArrayOf(1)).inputStream(), dir)

        assertEquals(
            ZipRomImporter.Reason.UNSAFE_PATH,
            (result as ZipRomImporter.Result.Rejected).reason,
        )
    }

    @Test fun `rejects corrupt data`() = withTempDir { dir ->
        val result = ZipRomImporter.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), dir)

        assertEquals(
            ZipRomImporter.Reason.UNREADABLE,
            (result as ZipRomImporter.Result.Rejected).reason,
        )
    }

    @Test fun `rejects archives with excessive entry count`() = withTempDir { dir ->
        val entries = buildMap {
            repeat(257) { index -> put("notes/$index.txt", byteArrayOf()) }
        }
        val result = ZipRomImporter.extract(zipOf(*entries.entries.map { it.key to it.value }.toTypedArray()).inputStream(), dir)

        assertEquals(
            ZipRomImporter.Reason.TOO_LARGE,
            (result as ZipRomImporter.Result.Rejected).reason,
        )
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDir(prefix = "zip-rom-importer-")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
