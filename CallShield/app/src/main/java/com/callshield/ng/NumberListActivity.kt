package com.callshield.ng

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows and edits either the allow list or the block list.
 * The block list also shows numbers caught by the loan-app pattern detector.
 */
class NumberListActivity : AppCompatActivity() {

    private data class Row(val entry: String, val auto: Boolean)

    private lateinit var prefs: Prefs
    private lateinit var adapter: ArrayAdapter<Row>
    private lateinit var empty: View
    private lateinit var list: ListView
    private var rows: List<Row> = emptyList()
    private var mode = MODE_ALLOW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_number_list)
        prefs = Prefs(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_ALLOW)

        findViewById<View>(R.id.headerBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.headerTitle).setText(
            if (mode == MODE_ALLOW) R.string.btn_allow else R.string.btn_block
        )
        findViewById<TextView>(R.id.listHint).setText(
            if (mode == MODE_ALLOW) R.string.allow_hint else R.string.block_hint
        )

        empty = findViewById(R.id.empty)
        list = findViewById(R.id.list)
        list.clipToOutline = true
        val input = findViewById<EditText>(R.id.input)

        val inflater = LayoutInflater.from(this)
        adapter = object : ArrayAdapter<Row>(this, R.layout.item_number, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: inflater.inflate(R.layout.item_number, parent, false)
                val row = getItem(position)!!
                v.findViewById<TextView>(R.id.itemNumber).text = NumberFormat.pretty(row.entry)
                v.findViewById<View>(R.id.itemNote).visibility = if (row.auto) View.VISIBLE else View.GONE
                if (row.auto) v.findViewById<TextView>(R.id.itemNote).setText(R.string.auto_note)
                return v
            }
        }
        list.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener {
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

        findViewById<View>(R.id.btnPaste).setOnClickListener { showPasteDialog() }
        findViewById<View>(R.id.btnShare).setOnClickListener { shareList() }

        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows.getOrNull(position) ?: return@setOnItemClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(NumberFormat.pretty(row.entry))
                .setMessage(R.string.remove_question)
                .setPositiveButton(R.string.remove) { _, _ ->
                    when {
                        mode == MODE_ALLOW -> prefs.allowList = prefs.allowList - row.entry
                        row.auto -> prefs.detectedSpam = prefs.detectedSpam - row.entry
                        else -> prefs.blockList = prefs.blockList - row.entry
                    }
                    reload()
                }
                .setNegativeButton(R.string.cancel, null)
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
        val pad = (20 * resources.displayMetrics.density).toInt()
        val frame = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(box)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paste_list)
            .setView(frame)
            .setPositiveButton(R.string.add) { _, _ ->
                val entries = box.text.toString()
                    .split('\n', ',', ';')
                    .map { PhoneUtils.normalize(it) }
                    .filter { it.isNotEmpty() && it.trimEnd('*').length >= 4 }
                    .toSet()
                entries.forEach { add(it) }
                Toast.makeText(
                    this, resources.getQuantityString(R.plurals.pasted_count, entries.size, entries.size),
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
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
        adapter.addAll(rows)
        adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        const val MODE_ALLOW = 0
        const val MODE_BLOCK = 1
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: Int): Intent =
            Intent(context, NumberListActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}
