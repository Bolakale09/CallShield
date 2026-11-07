package com.callshield.ng

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Every call CallShield turned away. Tap one to allow it, mark it as spam, block it or call back. */
class BlockedLogActivity : AppCompatActivity() {

    private lateinit var adapter: ArrayAdapter<BlockedCall>
    private lateinit var empty: View
    private lateinit var list: ListView
    private lateinit var actions: View
    private lateinit var prefs: Prefs

    private val saveCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { it.write(csvBytes()) } != null
            } catch (e: Exception) {
                false
            }
            Toast.makeText(this, if (ok) R.string.export_saved else R.string.export_failed, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_log)
        prefs = Prefs(this)

        findViewById<View>(R.id.headerBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.headerTitle).setText(R.string.btn_log)
        actions = findViewById(R.id.logActions)
        findViewById<View>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<View>(R.id.btnExport).setOnClickListener { chooseExport() }

        empty = findViewById(R.id.empty)
        list = findViewById(R.id.list)
        list.clipToOutline = true

        val inflater = LayoutInflater.from(this)
        adapter = object : ArrayAdapter<BlockedCall>(this, R.layout.item_call, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: inflater.inflate(R.layout.item_call, parent, false)
                CallDisplay.bind(v, getItem(position)!!)
                return v
            }
        }
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            if (item.number == "Private number") return@setOnItemClickListener
            val options = arrayOf(
                getString(R.string.action_allow),
                getString(R.string.action_spam),
                getString(R.string.action_block),
                getString(R.string.action_call)
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(NumberFormat.pretty(item.number))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            prefs.addToAllowList(item.number)
                            Toast.makeText(this, R.string.added_allow, Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            prefs.markSpam(item.number)
                            Toast.makeText(this, R.string.added_spam, Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            prefs.addToBlockList(item.number)
                            Toast.makeText(this, R.string.added_block, Toast.LENGTH_SHORT).show()
                        }
                        3 -> startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.number))
                        )
                    }
                }
                .show()
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    // ---------- Export (evidence for a complaint) ----------

    private fun csvBytes(): ByteArray {
        val rows = BlockedCallStore.all(this).map {
            val r = CallDisplay.reason(this, it.reason)
            CsvExport.Row(it.time, CallDisplay.number(this, it.number), r.text, r.spam)
        }
        // Byte-order mark so Excel opens the file as UTF-8
        return "\uFEFF".toByteArray(Charsets.UTF_8) + CsvExport.build(rows).toByteArray(Charsets.UTF_8)
    }

    private fun chooseExport() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setMessage(R.string.export_body)
            .setPositiveButton(R.string.export_share) { _, _ -> shareCsv() }
            .setNeutralButton(R.string.export_save) { _, _ ->
                saveCsvLauncher.launch(CsvExport.fileName(System.currentTimeMillis()))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareCsv() {
        try {
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // keep only the latest export
            val file = File(dir, CsvExport.fileName(System.currentTimeMillis()))
            file.writeBytes(csvBytes())
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.export_share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_title)
            .setMessage(R.string.clear_body)
            .setPositiveButton(R.string.clear_log) { _, _ ->
                BlockedCallStore.clear(this)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reload() {
        val items = BlockedCallStore.all(this)
        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        actions.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}
