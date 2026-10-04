package com.sadrazam.lusifer

import android.app.Application
import com.sadrazam.lusifer.core.Assistant

class LusiferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Assistant.attach(this)
    }
}
