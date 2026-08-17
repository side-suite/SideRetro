package fi.palonkorpi.sideretro.library

import fi.palonkorpi.sideretro.emu.GameSystem
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Turns a deliberately simple ZIP archive into one temporary ROM file.
 *
 * Archives are not handed to a core: each core has subtly different archive support, and a
 * failure there is much less understandable than rejecting an ambiguous archive during import.
 * We accept exactly one supported file, including one inside a normal folder in the archive.
 */
internal object ZipRomImporter {

    private const val MAX_ENTRY_COUNT = 256
    private const val MAX_ROM_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 128L * 1024 * 1024
    private const val MAX_COMPRESSION_RATIO = 200.0
    private const val BUFFER_SIZE = 32 * 1024

    sealed interface Result {
        data class Extracted(val displayName: String, val tempFile: File) : Result
        data class Rejected(val reason: Reason) : Result
    }

    enum class Reason(val userMessage: String) {
        NO_SUPPORTED_ROM("This ZIP has no game SideRetro can play"),
        MULTIPLE_ROMS("This ZIP contains more than one game"),
        UNSAFE_PATH("This ZIP has an unsafe file path"),
        TOO_LARGE("This ZIP is too large to import"),
        UNREADABLE("Could not read this ZIP"),
    }

    /**
     * Streams every entry while enforcing bounds. The returned file has only a generated temporary
     * name; the archive path is never used as a destination, so archive traversal cannot escape
     * [tempDir]. The caller owns and must delete an [Result.Extracted.tempFile].
     */
    fun extract(source: InputStream, tempDir: File): Result {
        var candidate: File? = null
        var candidateName: String? = null
        var accepted = false
        var totalBytes = 0L
        var entryCount = 0

        try {
            if (!tempDir.exists() && !tempDir.mkdirs()) return Result.Rejected(Reason.UNREADABLE)
            val buffered = BufferedInputStream(source)
            buffered.mark(4)
            val signature = ByteArray(4)
            if (buffered.read(signature) != signature.size || !hasZipSignature(signature)) {
                return Result.Rejected(Reason.UNREADABLE)
            }
            buffered.reset()
            ZipInputStream(buffered).use { zip ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT) return Result.Rejected(Reason.TOO_LARGE)

                    val entryName = entry.name ?: return Result.Rejected(Reason.UNREADABLE)
                    if (isUnsafe(entryName)) return Result.Rejected(Reason.UNSAFE_PATH)

                    val baseName = entryName.substringAfterLast('/')
                    val isRom = !entry.isDirectory && !isIgnored(entryName) &&
                        GameSystem.forExtension(baseName.substringAfterLast('.', "")) != null
                    if (isRom) {
                        if (candidate != null) return Result.Rejected(Reason.MULTIPLE_ROMS)
                        candidate = File.createTempFile("zip-rom-", ".tmp", tempDir)
                        candidateName = baseName
                    }

                    var entryBytes = 0L
                    val output = if (isRom) candidate!!.outputStream() else null
                    try {
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES ||
                                (isRom && entryBytes > MAX_ROM_BYTES)
                            ) return Result.Rejected(Reason.TOO_LARGE)
                            output?.write(buffer, 0, count)
                        }
                    } finally {
                        output?.close()
                    }

                    // ZipInputStream may only know this after it has consumed the entry.
                    val compressedBytes = entry.compressedSize
                    if (compressedBytes > 0 && entryBytes / compressedBytes.toDouble() > MAX_COMPRESSION_RATIO) {
                        return Result.Rejected(Reason.TOO_LARGE)
                    }
                    zip.closeEntry()
                }
            }
            val extracted = candidate ?: return Result.Rejected(Reason.NO_SUPPORTED_ROM)
            accepted = true
            return Result.Extracted(checkNotNull(candidateName), extracted)
        } catch (_: ZipException) {
            return Result.Rejected(Reason.UNREADABLE)
        } catch (_: Exception) {
            return Result.Rejected(Reason.UNREADABLE)
        } finally {
            if (!accepted) candidate?.delete()
        }
    }

    private fun isIgnored(name: String): Boolean = name.split('/').any { segment ->
        segment == "__MACOSX" || segment.startsWith('.')
    }

    private fun isUnsafe(name: String): Boolean =
        name.startsWith('/') || name.startsWith('\\') || '\\' in name ||
            name.split('/').any { it == ".." }

    private fun hasZipSignature(bytes: ByteArray): Boolean =
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
                (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()) ||
                (bytes[2] == 0x07.toByte() && bytes[3] == 0x08.toByte()))
}
