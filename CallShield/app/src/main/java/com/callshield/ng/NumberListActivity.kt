package com.callshield.ng

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows and edits either the allow list or the block list.
 * The block list also shows numbers caught by the loan-app pattern detector ("auto").
 */
class NumberListActivity : AppCompatActivity() {

    private data class Row(val entry: String, val auto: Boolean)

    private lateinit var prefs: Prefs
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var empty: TextView
    private var rows: List<Row> = emptyList()
    private var mode = MODE_ALLOW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_number_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = Prefs(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_ALLOW)

        title = getString(if (mode == MODE_ALLOW) R.string.btn_allow else R.string.btn_block)
        findViewById<TextView>(R.id.listHint).setText(
            if (mode == MODE_ALLOW) R.string.allow_hint else R.string.block_hint
        )

        empty = findViewById(R.id.empty)
        val input = findViewById<EditText>(R.id.input)
        val list = findViewById<ListView>(R.id.list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val entry = PhoneUtils.normalize(input.text?.toString())
            // Reject empty input and too-short prefixes like "0*" that would block everyone
            if (entry.isEmpty() || entry.trimEnd('*').length < 4) {
                Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            add(entry)
            input.setText("")
            reload()
        }

        findViewById<Button>(R.id.btnPaste).setOnClickListener { showPasteDialog() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareList() }

        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows.getOrNull(position) ?: return@setOnItemClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(row.entry)
                .setMessage(R.string.remove_question)
                .setPositiveButton(R.string.remove) { _, _ ->
                    when {
                        mode == MODE_ALLOW -> prefs.allowList = prefs.allowList - row.entry
                        row.auto -> prefs.detectedSpam = prefs.detectedSpam - row.entry
                        else -> prefs.blockList = prefs.blockList - row.entry
                    }
                    reload()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        reload()
    }

    private fun add(entry: String) {
        if (mode == MODE_ALLOW) prefs.addToAllowList(entry) else prefs.addToBlockList(entry)
    }

    /** Paste many numbers at once, e.g. a spam list shared in a WhatsApp group. */
    private fun showPasteDialog() {
        val box = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setHint(R.string.paste_hint)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paste_list)
            .setView(box)
            .setPositiveButton(R.string.add) { _, _ ->
                val entries = box.text.toString()
                    .split('\n', ',', ';')
                    .map { PhoneUtils.normalize(it) }
                    .filter { it.isNotEmpty() && it != "*" && it.trimEnd('*').length >= 4 }
                    .toSet()
                entries.forEach { add(it) }
                Toast.makeText(this, getString(R.string.pasted_count, entries.size), Toast.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareList() {
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.list_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val header = getString(if (mode == MODE_ALLOW) R.string.share_header_allow else R.string.share_header_block)
        val text = header + "\n" + rows.joinToString("\n") { it.entry }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share_list)
            )
        )
    }

    private fun reload() {
        rows = if (mode == MODE_ALLOW) {
            prefs.allowList.sorted().map { Row(it, false) }
        } else {
            prefs.blockList.sorted().map { Row(it, false) } +
                (prefs.detectedSpam - prefs.blockList).sorted().map { Row(it, true) }
        }
        adapter.clear()
        adapter.addAll(rows.map { if (it.auto) getString(R.string.auto_suffix, it.entry) else it.entry })
        adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val MODE_ALLOW = 0
        const val MODE_BLOCK = 1
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: Int): Intent =
            Intent(context, NumberListActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}
