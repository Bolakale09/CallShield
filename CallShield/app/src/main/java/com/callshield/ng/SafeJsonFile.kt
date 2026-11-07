package com.callshield.ng

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * A JSON array stored crash-safely. Writes go to a temporary file that replaces the
 * old one only when complete, so a phone dying mid-write can't wipe the history.
 */
internal class SafeJsonFile(context: Context, name: String) {

    private val base = File(context.applicationContext.filesDir, name)
    private val file = AtomicFile(base)

    /** The stored array, or an empty one if nothing has been saved yet. */
    fun read(): JSONArray {
        val bytes = try {
            file.readFully()
        } catch (e: FileNotFoundException) {
            return JSONArray()
        } catch (e: Exception) {
            return JSONArray()
        }
        return try {
            JSONArray(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            // Unreadable (e.g. written by a much older version): keep a copy, start fresh.
            base.renameTo(File(base.parentFile, base.name + ".corrupt"))
            JSONArray()
        }
    }

    fun write(array: JSONArray) {
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            if (out != null) file.failWrite(out)
        }
    }

    fun delete() {
        file.delete()
    }
}
