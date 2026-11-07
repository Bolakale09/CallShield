package com.callshield.ng

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var blockedCount: TextView
    private lateinit var spamCount: TextView
    private lateinit var detectionLevelHint: TextView
    private lateinit var btnSetup: Button
    private lateinit var swCalledBefore: MaterialSwitch

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!hasScreeningRole()) showManualRoleHelp()
            refreshStatus()
        }

    private val basePermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!PhoneUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
                Toast.makeText(this, R.string.need_contacts, Toast.LENGTH_LONG).show()
            } else if (!hasScreeningRole()) {
                requestScreeningRole()
            }
            refreshStatus()
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

        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        blockedCount = findViewById(R.id.blockedCount)
        spamCount = findViewById(R.id.spamCount)
        detectionLevelHint = findViewById(R.id.detectionLevelHint)
        btnSetup = findViewById(R.id.btnSetup)
        swCalledBefore = findViewById(R.id.swCalledBefore)

        btnSetup.setOnClickListener { startSetup() }

        // Loan-app spam detection strength
        val group = findViewById<MaterialButtonToggleGroup>(R.id.detectionGroup)
        group.check(
            when (prefs.detectionLevel) {
                0 -> R.id.detOff
                2 -> R.id.detStrict
                else -> R.id.detNormal
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.detectionLevel = when (checkedId) {
                R.id.detOff -> 0
                R.id.detStrict -> 2
                else -> 1
            }
            updateDetectionHint()
        }
        updateDetectionHint()

        bindSwitch(R.id.swAsk, prefs.askAboutUnknown) { prefs.askAboutUnknown = it }
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            if (PhoneUtils.hasPermission(this, Manifest.permission.READ_CALL_LOG)) scanCallHistory()
            else scanPermLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }

        bindSwitch(R.id.swEnabled, prefs.enabled) { prefs.enabled = it; refreshStatus() }
        bindSwitch(R.id.swUnknown, prefs.blockUnknown) { prefs.blockUnknown = it }
        bindSwitch(R.id.swHidden, prefs.blockHidden) { prefs.blockHidden = it }
        bindSwitch(R.id.swRepeat, prefs.allowRepeatCallers) { prefs.allowRepeatCallers = it }
        bindSwitch(R.id.swSilence, prefs.silenceInsteadOfReject) { prefs.silenceInsteadOfReject = it }
        bindSwitch(R.id.swNotify, prefs.notifyOnBlock) { checked ->
            prefs.notifyOnBlock = checked
            if (checked && Build.VERSION.SDK_INT >= 33 &&
                !PhoneUtils.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                basePermsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        bindSwitch(R.id.swCalledBefore, prefs.allowCalledBefore) { checked ->
            prefs.allowCalledBefore = checked
            if (checked && !PhoneUtils.hasPermission(this, Manifest.permission.READ_CALL_LOG)) {
                callLogPermLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }

        findViewById<Button>(R.id.btnLog).setOnClickListener {
            startActivity(Intent(this, BlockedLogActivity::class.java))
        }
        findViewById<Button>(R.id.btnAllow).setOnClickListener {
            startActivity(NumberListActivity.intent(this, NumberListActivity.MODE_ALLOW))
        }
        findViewById<Button>(R.id.btnBlock).setOnClickListener {
            startActivity(NumberListActivity.intent(this, NumberListActivity.MODE_BLOCK))
        }
        findViewById<Button>(R.id.btnDefaultApps).setOnClickListener { openDefaultAppsSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun updateDetectionHint() {
        detectionLevelHint.setText(
            when (prefs.detectionLevel) {
                0 -> R.string.det_off_hint
                2 -> R.string.det_strict_hint
                else -> R.string.det_normal_hint
            }
        )
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<MaterialSwitch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun hasScreeningRole(): Boolean {
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun startSetup() {
        val wanted = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,       // better spam detection (optional)
            Manifest.permission.ANSWER_PHONE_CALLS   // "Block as loan spam" hangs up (optional)
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val needed = wanted.filter { !PhoneUtils.hasPermission(this, it) }

        when {
            needed.isNotEmpty() -> basePermsLauncher.launch(needed.toTypedArray())
            !hasScreeningRole() -> requestScreeningRole()
            !prefs.enabled -> {
                prefs.enabled = true
                findViewById<MaterialSwitch>(R.id.swEnabled).isChecked = true
            }
        }
        refreshStatus()
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
            .setNegativeButton(android.R.string.cancel, null)
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
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val numbers = found.keys.toList()
        val labels = numbers.map { "$it\n${found[it]}" }.toTypedArray()
        val checked = BooleanArray(numbers.size) { true }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.scan_found, numbers.size))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.block_selected) { _, _ ->
                var n = 0
                numbers.forEachIndexed { i, num -> if (checked[i]) { prefs.markSpam(num); n++ } }
                Toast.makeText(this, getString(R.string.scan_blocked, n), Toast.LENGTH_LONG).show()
                refreshStatus()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshStatus() {
        val role = hasScreeningRole()
        val contacts = PhoneUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)
        val on = role && contacts && prefs.enabled

        statusIcon.setImageResource(if (on) R.drawable.ic_shield else R.drawable.ic_shield_off)
        statusTitle.setText(if (on) R.string.status_on else R.string.status_off)

        val problems = mutableListOf<String>()
        if (!contacts) problems += getString(R.string.problem_contacts)
        if (!role) problems += getString(R.string.problem_role)
        if (!prefs.enabled) problems += getString(R.string.problem_disabled)
        statusText.text = if (on) getString(R.string.status_on_detail)
        else problems.joinToString("\n") { "• $it" }

        btnSetup.visibility = if (on) View.GONE else View.VISIBLE
        blockedCount.text = getString(R.string.blocked_count, BlockedCallStore.all(this).size)
        spamCount.text = getString(R.string.spam_count, prefs.detectedSpam.size)
    }
}
