package com.qinwei.smartledger

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform