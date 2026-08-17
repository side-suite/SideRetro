package fi.palonkorpi.sideretro.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * SPEC.md §7.2 — the primary path for getting games onto the phone.
 *
 * The SP-01 ships Firefox *and* DuckDuckGo preinstalled, so the browser already is the URL box and
 * no computer is needed at any point. SideRetro watches Downloads and offers to import anything it
 * recognises; it never fetches a URL itself and never links to a ROM source.
 *
 * ### ⚠️ Two routes to the Downloads folder were tried on the device, and Android 12 blocks both
 *
 * 1. **`MediaStore.Downloads` with `READ_EXTERNAL_STORAGE`.** Scoped storage hides non-media files
 *    another app created, and a ROM is not media. Confirmed by pushing seven ROMs, forcing a
 *    MediaProvider volume scan until `adb shell content query` listed all seven, and watching the
 *    app — holding the permission — see none of them.
 * 2. **A persisted SAF grant on the Downloads folder.** The system picker refuses it outright:
 *    *"Can't use this folder — to protect your privacy, choose another folder."* The Download root
 *    is on Android 11+'s blocklist alongside the storage root and `Android/data`.
 *
 * What is left is `MANAGE_EXTERNAL_STORAGE` ("All files access"), which SideRetro does not ask for,
 * or a grant on some folder that is *not* the Downloads root — a subfolder of it, or an SD card —
 * which the picker does allow. So this class survives as a **watched folder of the user's choosing**
 * rather than an automatic Downloads watcher, and the everyday path becomes handing a file to
 * SideRetro directly (see the intent filters in the manifest) or picking it (`Add games…`).
 *
 * **SideRetro still declares no permissions.**
 */
class DownloadsWatcher(private val context: Context) {

    data class Candidate(val uri: Uri, val displayName: String, val size: Long)

    /**
     * A ZIP imports an inner ROM, so its archive name can never be found in [RomLibrary.romsDir].
     * Keep a tiny local source receipt for those successful archive imports. It contains no ROM
     * data or hash: the document URI/id, name and size are enough to avoid importing the same
     * downloaded archive again on every [LibraryActivity.onResume].
     */
    private val archiveReceipts = context.getSharedPreferences(
        "sideretro-imported-archives",
        Context.MODE_PRIVATE,
    )

    /**
     * Opens the picker already sitting in Downloads, so the user's job is to press the confirm
     * button rather than to go and find a folder.
     */
    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_TREE)
    }

    fun persist(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    /** A grant can be revoked from system settings, so this is re-checked rather than remembered. */
    fun isGranted(treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
    }

    fun scan(treeUri: Uri, library: RomLibrary): List<Candidate> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = mutableListOf<Candidate>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!library.isRecognised(name)) continue
                    out += Candidate(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0)),
                        displayName = name,
                        size = cursor.getLong(2),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.i(TAG, "Downloads folder grant is gone: ${e.message}")
        }
        return out
    }

    /**
     * Already-imported files are matched by name and length. Nothing is hashed — see §7.4 for why
     * SideRetro stays out of the business of identifying ROMs.
     */
    fun unimported(treeUri: Uri, library: RomLibrary): List<Candidate> {
        val existing = library.romsDir.listFiles()?.associate { it.name to it.length() } ?: emptyMap()
        return scan(treeUri, library).filterNot { candidate ->
            if (candidate.isZip) archiveReceipts.contains(candidate.receiptKey)
            else existing[candidate.displayName] == candidate.size
        }
    }

    fun import(candidate: Candidate, library: RomLibrary): Rom? =
        context.contentResolver.openInputStream(candidate.uri)?.use { stream ->
            library.import(candidate.displayName, stream)
        }?.also {
            // Record only a completed archive import; a corrupt/multi-ROM ZIP remains eligible so
            // replacing it with a corrected download is never hidden from the user.
            if (candidate.isZip) archiveReceipts.edit().putBoolean(candidate.receiptKey, true).apply()
        }

    private val Candidate.isZip: Boolean
        get() = displayName.substringAfterLast('.', "").equals("zip", ignoreCase = true)

    private val Candidate.receiptKey: String
        get() = "${uri}|$displayName|$size"

    private companion object {
        const val TAG = "SideRetro"

        /** The stock Downloads folder as the system file picker addresses it. */
        val DOWNLOADS_TREE: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download",
        )
    }
}
