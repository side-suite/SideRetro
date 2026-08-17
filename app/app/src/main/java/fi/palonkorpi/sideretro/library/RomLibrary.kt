package fi.palonkorpi.sideretro.library

import android.content.Context
import fi.palonkorpi.sideretro.emu.GameSystem
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * SPEC.md §7.4 — a typographic list, not a grid. No network, no hash lookups, nothing leaves the
 * phone. There is no cover-art database entry for most of what people will load here, so a grid
 * would be mostly placeholder rectangles, and hash-matching a ROM library is functionally
 * identifying commercial games.
 */
data class Rom(
    val file: File,
    val system: GameSystem,
    val title: String,
) {
    /** Stable per-file key for save states. Name plus length is enough on a single-user device. */
    val key: String
        get() = buildString {
            file.name.forEach { c -> append(if (c.isLetterOrDigit()) c else '_') }
            append('-')
            append(file.length())
        }
}

class RomLibrary(context: Context) {

    sealed interface ImportResult {
        data class Imported(val rom: Rom) : ImportResult
        data class Rejected(val message: String) : ImportResult
    }

    /**
     * Internal storage, not `getExternalFilesDir`. On Android 11+ nothing else can browse
     * `/sdcard/Android/data` anyway, so external buys no user-visible access — and internal keeps
     * the library out of the way of file managers and media scanners.
     */
    val romsDir: File = File(context.filesDir, "roms").apply { mkdirs() }

    fun list(): List<Rom> =
        romsDir.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val system = GameSystem.forExtension(file.extension) ?: return@mapNotNull null
                Rom(file, system, prettyTitle(file.name))
            }
            ?.sortedBy { it.title.lowercase(Locale.ROOT) }
            ?: emptyList()

    fun isRecognised(displayName: String): Boolean =
        displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) in GameSystem.allExtensions + "zip"

    /** @return the imported file, or null if the name is not a system we support. */
    fun import(displayName: String, source: InputStream): Rom? =
        (importResult(displayName, source) as? ImportResult.Imported)?.rom

    /**
     * Imports a raw ROM, or the one supported ROM from a ZIP. A ZIP with zero or multiple games is
     * intentionally rejected: choosing one silently would make an import appear successful but
     * launch the wrong game.
     */
    fun importResult(displayName: String, source: InputStream): ImportResult {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension == "zip") {
            return when (val archive = ZipRomImporter.extract(source, File(romsDir, ".importing"))) {
                is ZipRomImporter.Result.Rejected -> ImportResult.Rejected(archive.reason.userMessage)
                is ZipRomImporter.Result.Extracted -> {
                    try {
                        archive.tempFile.inputStream().use { extracted -> importRaw(archive.displayName, extracted) }
                    } finally {
                        archive.tempFile.delete()
                    }
                }
            }
        }
        return importRaw(displayName, source)
    }

    private fun importRaw(displayName: String, source: InputStream): ImportResult {
        if (!isRecognised(displayName)) return ImportResult.Rejected("Not a game SideRetro can play")
        val system = GameSystem.forExtension(displayName.substringAfterLast('.'))
            ?: return ImportResult.Rejected("Not a game SideRetro can play")

        val safeName = displayName.replace(Regex("""[/\\:*?"<>|]"""), "_")
        var target = File(romsDir, safeName)
        var suffix = 1
        while (target.exists()) {
            val base = safeName.substringBeforeLast('.')
            val ext = safeName.substringAfterLast('.')
            target = File(romsDir, "$base ($suffix).$ext")
            suffix++
        }

        return try {
            source.use { input -> target.outputStream().use { input.copyTo(it) } }
            ImportResult.Imported(Rom(target, system, prettyTitle(target.name)))
        } catch (_: Exception) {
            target.delete()
            ImportResult.Rejected("Could not import this game")
        }
    }

    /**
     * Removes only a ROM SideRetro previously copied into its private library.  [Rom] is a data
     * class and can be constructed by a caller, so do not trust its [Rom.file] blindly here: this
     * guard makes the delete operation incapable of reaching a downloaded original or any other
     * app-owned file.
     */
    fun delete(rom: Rom): Boolean {
        val libraryRoot = romsDir.canonicalFile
        val candidate = try {
            rom.file.canonicalFile
        } catch (_: Exception) {
            return false
        }
        if (candidate.parentFile != libraryRoot || GameSystem.forExtension(candidate.extension) == null) {
            return false
        }
        return candidate.isFile && candidate.delete()
    }

    /**
     * Turns `Super_Homebrew_Quest (USA) [!].nes` into `Super Homebrew Quest`. Cosmetic only — the
     * file on disk keeps its original name so a re-import is recognisable.
     */
    private fun prettyTitle(fileName: String): String {
        var name = fileName.substringBeforeLast('.')
        name = name.replace(Regex("""[\[(][^\[\]()]*[\])]"""), " ")
        name = name.replace('_', ' ').replace('.', ' ')
        return name.replace(Regex("""\s+"""), " ").trim().ifEmpty { fileName }
    }
}
