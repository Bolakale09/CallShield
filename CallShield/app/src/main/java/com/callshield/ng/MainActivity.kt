package com.callshield.ng

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var swCalledBefore: MaterialSwitch
    private lateinit var swEnabled: MaterialSwitch

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!hasScreeningRole()) showManualRoleHelp()
            refresh()
        }

    private val basePermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!PhoneUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
                Toast.makeText(this, R.string.need_contacts, Toast.LENGTH_LONG).show()
            } else if (!hasScreeningRole()) {
                requestScreeningRole()
            }
            refresh()
        }

    private val callLogPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                swCalledBefore.isChecked = false
                prefs.allowCalledBefore = false
                Toast.makeText(this, R.string.need_call_log, Toast.LENGTH_LONG).show()
            }
        }

    private val scanPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanCallHistory()
            else Toast.makeText(this, R.string.need_call_log, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Notifier.ensureChannel(this)

        swCalledBefore = findViewById(R.id.swCalledBefore)
        swEnabled = findViewById(R.id.swEnabled)

        // Rounded groups clip their row ripples
        listOf(R.id.recentList, R.id.groupScan).forEach { findViewById<View>(it).clipToOutline = true }

        if (!BuildConfig.CALL_LOG_FEATURES) {
            findViewById<TextView>(R.id.edition).apply {
                visibility = View.VISIBLE
                setText(R.string.edition_lite)
            }
            findViewById<View>(R.id.groupScan).visibility = View.GONE
            findViewById<View>(R.id.rowCalled).visibility = View.GONE
            findViewById<View>(R.id.divCalled).visibility = View.GONE
            prefs.allowCalledBefore = false
        }

        findViewById<View>(R.id.btnHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.rowHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.btnSetup).setOnClickListener { onSetupPressed() }

        // Who can ring you
        bindSwitchRow(R.id.rowUnknown, R.id.swUnknown, prefs.blockUnknown) {
            prefs.blockUnknown = it; refresh()
        }
        bindSwitchRow(R.id.rowHidden, R.id.swHidden, prefs.blockHidden) { prefs.blockHidden = it }
        bindSwitchRow(R.id.rowRepeat, R.id.swRepeat, prefs.allowRepeatCallers) { prefs.allowRepeatCallers = it }
        bindSwitchRow(R.id.rowAsk, R.id.swAsk, prefs.askAboutUnknown) { checked ->
            prefs.askAboutUnknown = checked
            if (checked) askForNotifications()
        }

        // Loan-app detection
        val segments = mapOf(R.id.detOff to 0, R.id.detNormal to 1, R.id.detStrict to 2)
        segments.forEach { (id, level) ->
            findViewById<View>(id).setOnClickListener {
                prefs.detectionLevel = level
                showDetectionLevel()
                refresh()
            }
        }
        showDetectionLevel()
        findViewById<View>(R.id.rowScan).setOnClickListener {
            if (PhoneUtils.hasPermission(this, Manifest.permission.READ_CALL_LOG)) scanCallHistory()
            else scanPermLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }

        // Lists
        val openLog = View.OnClickListener { startActivity(Intent(this, BlockedLogActivity::class.java)) }
        findViewById<View>(R.id.btnSeeAll).setOnClickListener(openLog)
        findViewById<View>(R.id.rowLog).setOnClickListener(openLog)
        findViewById<View>(R.id.rowAllow).setOnClickListener {
            startActivity(NumberListActivity.intent(this, NumberListActivity.MODE_ALLOW))
        }
        findViewById<View>(R.id.rowBlock).setOnClickListener {
            startActivity(NumberListActivity.intent(this, NumberListActivity.MODE_BLOCK))
        }

        // More
        bindSwitchRow(R.id.rowCalled, R.id.swCalledBefore, prefs.allowCalledBefore) { checked ->
            prefs.allowCalledBefore = checked
            if (checked && !PhoneUtils.hasPermission(this, Manifest.permission.READ_CALL_LOG)) {
                callLogPermLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
        bindSwitchRow(R.id.rowSilence, R.id.swSilence, prefs.silenceInsteadOfReject) {
            prefs.silenceInsteadOfReject = it
        }
        bindSwitchRow(R.id.rowNotify, R.id.swNotify, prefs.notifyOnBlock) { checked ->
            prefs.notifyOnBlock = checked
            if (checked) askForNotifications()
        }
        bindSwitchRow(R.id.rowEnabled, R.id.swEnabled, prefs.enabled) {
            prefs.enabled = it; refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** A whole row toggles its switch, like Samsung's own settings. */
    private fun bindSwitchRow(rowId: Int, switchId: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<MaterialSwitch>(switchId)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        findViewById<View>(rowId).setOnClickListener { sw.toggle() }
    }

    private fun showDetectionLevel() {
        val level = prefs.detectionLevel
        findViewById<View>(R.id.detOff).isSelected = level == 0
        findViewById<View>(R.id.detNormal).isSelected = level == 1
        findViewById<View>(R.id.detStrict).isSelected = level == 2
        findViewById<TextView>(R.id.detectionNote).setText(
            when (level) {
                0 -> R.string.det_off_hint
                2 -> R.string.det_strict_hint
                else -> R.string.det_normal_hint
            }
        )
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !PhoneUtils.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            basePermsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun hasScreeningRole(): Boolean {
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun isReady(): Boolean =
        hasScreeningRole() && PhoneUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)

    private fun onSetupPressed() {
        if (isReady() && !prefs.enabled) {
            prefs.enabled = true
            swEnabled.isChecked = true
            refresh()
        } else {
            startSetup()
        }
    }

    private fun startSetup() {
        val wanted = mutableListOf(Manifest.permission.READ_CONTACTS)
        if (BuildConfig.CALL_LOG_FEATURES) {
            wanted += Manifest.permission.READ_CALL_LOG        // better spam detection (optional)
            wanted += Manifest.permission.ANSWER_PHONE_CALLS   // "Block as loan spam" hangs up (optional)
        }
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val needed = wanted.filter { !PhoneUtils.hasPermission(this, it) }

        when {
            needed.isNotEmpty() -> basePermsLauncher.launch(needed.toTypedArray())
            !hasScreeningRole() -> requestScreeningRole()
        }
        refresh()
    }

    private fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            showManualRoleHelp()
            return
        }
        try {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } catch (e: Exception) {
            showManualRoleHelp()
        }
    }

    private fun showManualRoleHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manual_title)
            .setMessage(R.string.manual_steps)
            .setPositiveButton(R.string.btn_default_apps) { _, _ -> openDefaultAppsSettings() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_body)
            .setPositiveButton(R.string.btn_default_apps) { _, _ -> openDefaultAppsSettings() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun openDefaultAppsSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Finds numbers in the last 30 days of calls that already behave like loan-app collectors. */
    private fun scanCallHistory() {
        Toast.makeText(this, R.string.scanning, Toast.LENGTH_SHORT).show()
        Thread {
            val data = PhoneUtils.readCallLogForScan(this)
            val found = if (data == null) emptyMap()
            else SpamScorer.scanHistory(data.first, data.second, System.currentTimeMillis())
                .filterKeys { it !in prefs.detectedSpam && it !in prefs.allowList && it !in prefs.blockList }
            runOnUiThread { if (!isFinishing) showScanResult(found) }
        }.start()
    }

    private fun showScanResult(found: Map<String, String>) {
        if (found.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scan_title)
                .setMessage(R.string.scan_none)
                .setPositiveButton(R.string.close, null)
                .show()
            return
        }
        val numbers = found.keys.toList()
        val labels = numbers.map { "${NumberFormat.pretty(it)}\n${found[it]}" }.toTypedArray()
        val checked = BooleanArray(numbers.size) { true }
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.scan_found, numbers.size, numbers.size))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.block_selected) { _, _ ->
                var n = 0
                numbers.forEachIndexed { i, num -> if (checked[i]) { prefs.markSpam(num); n++ } }
                Toast.makeText(
                    this, resources.getQuantityString(R.plurals.scan_blocked, n, n), Toast.LENGTH_LONG
                ).show()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Screen state ----------

    private fun refresh() {
        showGate()
        showRecent()
        findViewById<TextView>(R.id.countLog).text = BlockedCallStore.all(this).size.toString()
        findViewById<TextView>(R.id.countAllow).text = prefs.allowList.size.toString()
        findViewById<TextView>(R.id.countBlock).text =
            (prefs.blockList + prefs.detectedSpam).size.toString()
    }

    /** The dark panel at the top: one sentence saying who can ring you right now. */
    private fun showGate() {
        val ready = isReady()
        val on = ready && prefs.enabled

        val dot = findViewById<View>(R.id.statusDot)
        val statusLine = findViewById<TextView>(R.id.statusLine)
        val rule = findViewById<TextView>(R.id.heroRule)
        val detail = findViewById<TextView>(R.id.heroDetail)
        val setup = findViewById<TextView>(R.id.btnSetup)
        val stats = findViewById<View>(R.id.statsBlock)

        dot.background = ContextCompat.getDrawable(this, if (on) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
        statusLine.setText(
            when {
                on -> R.string.status_guarding
                ready -> R.string.status_paused
                else -> R.string.status_not_guarding
            }
        )

        rule.setText(
            when {
                !on -> R.string.rule_anyone
                prefs.blockUnknown -> R.string.rule_contacts_only
                prefs.detectionLevel > 0 -> R.string.rule_no_loan_apps
                else -> R.string.rule_block_list_only
            }
        )

        if (on) {
            detail.visibility = View.GONE
            setup.visibility = View.GONE
        } else {
            val problems = mutableListOf<String>()
            if (!PhoneUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
                problems += getString(R.string.problem_contacts)
            }
            if (!hasScreeningRole()) problems += getString(R.string.problem_role)
            detail.text = if (problems.isEmpty()) getString(R.string.problem_paused)
            else problems.joinToString("\n")
            detail.visibility = View.VISIBLE
            setup.visibility = View.VISIBLE
            setup.setText(if (ready) R.string.resume else R.string.turn_on)
        }

        val blocked = BlockStats.total(this)
        val spam = prefs.detectedSpam.size
        stats.visibility = if (on || blocked > 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.statBlocked).text = blocked.toString()
        findViewById<TextView>(R.id.statBlockedLabel).text =
            resources.getQuantityString(R.plurals.calls_turned_away, blocked)
        findViewById<TextView>(R.id.statSpam).text = spam.toString()
        findViewById<TextView>(R.id.statSpamLabel).text =
            resources.getQuantityString(R.plurals.spam_caught, spam)
    }

    /** The last three turned-away calls, straight from the log. */
    private fun showRecent() {
        val container = findViewById<LinearLayout>(R.id.recentList)
        container.removeAllViews()
        val calls = BlockedCallStore.all(this).take(3)
        findViewById<View>(R.id.btnSeeAll).visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE

        if (calls.isEmpty()) {
            val empty = TextView(this).apply {
                setText(R.string.recent_empty)
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 15f
                val pad = dp(20)
                setPadding(dp(18), pad, dp(18), pad)
            }
            container.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        calls.forEachIndexed { i, call ->
            if (i > 0) container.addView(divider())
            val row = inflater.inflate(R.layout.item_call, container, false)
            CallDisplay.bind(row, call)
            row.setOnClickListener { startActivity(Intent(this, BlockedLogActivity::class.java)) }
            container.addView(row)
        }
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(18)
        }
        setBackgroundColor(ContextCompat.getColor(context, R.color.line))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
