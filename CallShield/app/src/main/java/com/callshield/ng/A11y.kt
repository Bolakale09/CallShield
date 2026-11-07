package com.callshield.ng

import android.view.View
import android.widget.CompoundButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** TalkBack helpers: make custom rows and segments announce what they are and their state. */
object A11y {

    /** A settings row that toggles [sw]: read as "title, subtitle, switch, on/off". */
    fun asSwitch(row: View, sw: CompoundButton) {
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"
                info.isCheckable = true
                info.isChecked = sw.isChecked
            }
        })
    }

    /** One option of a single-choice group: read as "Normal, radio button, selected". */
    fun asRadio(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.RadioButton"
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        })
    }

    /** A row that opens another screen: read as a button. */
    fun asButton(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        })
    }
}
