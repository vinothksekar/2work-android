package com.twowork.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.twowork.core.di.Graph

class TwoWorkApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        graph = Graph(this, debuggable)
    }
}
