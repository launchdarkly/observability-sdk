package com.example.LDObserve

interface BridgeLogger {
    fun debug(message: String)
    fun info(message: String)
    fun error(message: String)
}

class SystemOutBridgeLogger : BridgeLogger {
    override fun debug(message: String) {
        System.out.println(message)
    }

    override fun info(message: String) {
        System.out.println(message)
    }

    override fun error(message: String) {
        System.err.println(message)
    }
}
