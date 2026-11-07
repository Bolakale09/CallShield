package com.callshield.ng

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** List of calls CallShield has blocked. Tap one to allow, block for good, or call back. */
class BlockedLogActivity : AppCompatActivity() {

    private lateinit var adapter: ArrayAdapter<BlockedCall>
    private lateinit var empty: TextView
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_log)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.btn_log)
        prefs = Prefs(this)

        empty = findViewById(R.id.empty)
        val list = findViewById<ListView>(R.id.list)

        adapter = object : ArrayAdapter<BlockedCall>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val item = getItem(position)!!
                v.findViewById<TextView>(android.R.id.text1).text = item.number
                val ago = DateUtils.getRelativeDateTimeString(
                    context, item.time, DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS, 0
                )
                v.findViewById<TextView>(android.R.id.text2).text = "$ago · ${item.reason}"
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
                .setTitle(item.number)
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

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            BlockedCallStore.clear(this)
            reload()
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val items = BlockedCallStore.all(this)
        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
