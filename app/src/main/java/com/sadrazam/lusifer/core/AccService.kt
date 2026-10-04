package com.sadrazam.lusifer.core

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Ekran okuma, tıklama, yazı yazma, genel hareketler (geri/ana ekran/kilit...). */
class AccService : AccessibilityService() {

    companion object {
        @Volatile var inst: AccService? = null

        fun readScreen(): String {
            val root = inst?.rootInActiveWindow ?: return "Erişilebilirlik servisi kapalı veya ekran okunamıyor."
            val out = ArrayList<String>()
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || out.size >= 40 || depth > 25) return
                val t = (n.text ?: n.contentDescription)?.toString()?.trim()
                if (!t.isNullOrEmpty()) out.add(t)
                for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
            }
            walk(root, 0)
            if (out.isEmpty()) return "Ekranda okunabilir yazı bulamadım."
            val all = out.joinToString("\n")
            return "Ekranda: " + out.take(8).joinToString(", ") + "\n---\n" + all
        }

        fun clickText(text: String): Boolean {
            val root = inst?.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (n in nodes) {
                var cur: AccessibilityNodeInfo? = n
                var hops = 0
                while (cur != null && hops < 6) {
                    if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    cur = cur.parent
                    hops++
                }
            }
            return false
        }

        fun typeText(text: String): Boolean {
            val root = inst?.rootInActiveWindow ?: return false
            val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val b = Bundle()
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        inst = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        inst = null
        super.onDestroy()
    }
}
