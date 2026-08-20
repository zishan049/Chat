package com.chat.app.core.network

interface PortProvider {
    fun getActivePort(): Int
}