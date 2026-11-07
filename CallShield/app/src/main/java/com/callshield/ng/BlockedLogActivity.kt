package com.callshield.ng

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
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
    private lateinit var clear: TextView
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_log)
        prefs = Prefs(this)

        findViewById<View>(R.id.headerBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.headerTitle).setText(R.string.btn_log)
        clear = findViewById(R.id.headerAction)
        clear.setText(R.string.clear_log)
        clear.setOnClickListener { confirmClear() }

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
        clear.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}
