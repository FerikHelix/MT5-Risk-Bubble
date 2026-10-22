package com.ferik.mt5risk

import android.app.Application

class Mt5RiskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
