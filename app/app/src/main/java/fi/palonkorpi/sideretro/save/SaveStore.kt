package fi.palonkorpi.sideretro.save

import android.content.Context
import android.util.Log
import fi.palonkorpi.sideretro.library.Rom
import java.io.File

/**
 * SPEC.md §6 — invisible auto-resume, plus a single quick-save.
 *
 * This is a phone. Android will pause the game mid-boss-fight when a call arrives, and SideCall is
 * part of the same suite. So the emulator state is written on every pause and restored on every
 * launch: press a game and you are exactly where you stopped, mid-jump, mid-menu.
 *
 * Numbered slots are rejected as tinkerer territory; one manual quick-save earns its place because
 * much of the retro library is genuinely hard and the honest reason people want save states on NES
 * games is to survive a section without replaying an hour.
 *
 * ⚠️ **SaveRAM is persisted separately, and that is a requirement rather than a decision.** Games
 * with battery-backed saves write SaveRAM; if only the emulator state were serialised, a player who
 * saved *inside* the game and then took a call could lose it. LibretroDroid exposes both
 * serialisations and we call both.
 *
 * ⚠️ `unserializeSRAM`'s failure return is swallowed by the JNI wrapper, so its boolean is never
 * trusted here — the only real signal is whether a file existed at all.
 */
class SaveStore(context: Context, private val rom: Rom) {

    private val resumeDir = File(context.filesDir, "states").apply { mkdirs() }
    private val quickDir = File(context.filesDir, "quicksaves").apply { mkdirs() }
    private val sramDir = File(context.filesDir, "sram").apply { mkdirs() }

    private val resumeFile = File(resumeDir, "${rom.key}.state")
    private val quickFile = File(quickDir, "${rom.key}.state")
    private val sramFile = File(sramDir, "${rom.key}.srm")

    val hasResumePoint: Boolean get() = resumeFile.isFile && resumeFile.length() > 0
    val hasQuickSave: Boolean get() = quickFile.isFile && quickFile.length() > 0

    fun readSaveRam(): ByteArray? = sramFile.takeIf { it.isFile }?.readBytes()

    fun readResume(): ByteArray? = resumeFile.takeIf { it.isFile && it.length() > 0 }?.readBytes()

    fun readQuickSave(): ByteArray? = quickFile.takeIf { it.isFile && it.length() > 0 }?.readBytes()

    fun writeResume(state: ByteArray?, saveRam: ByteArray?) {
        write(resumeFile, state)
        write(sramFile, saveRam)
    }

    fun writeQuickSave(state: ByteArray?) = write(quickFile, state)

    /** Used by "restart game", which must not leave a resume point that would undo itself. */
    fun clearResume() {
        resumeFile.delete()
    }

    /** The ROM was deliberately removed from SideRetro's private library: remove its sidecars too. */
    fun deleteAll() {
        resumeFile.delete()
        quickFile.delete()
        sramFile.delete()
    }

    private fun write(target: File, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        try {
            // Write-then-rename: a process death partway through a write would otherwise leave a
            // truncated state that restores as a corrupted game.
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write ${target.name} for ${rom.title}", e)
        }
    }

    private companion object {
        const val TAG = "SideRetro"
    }
}
