package com.qinwei.smartledger

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.data.appDatabase

fun main() = application {
    // Desktop 端根据用户主目录创建数据库，无需额外上下文
    val repository = TransactionRepository(appDatabase.transactionDao())

    Window(
        onCloseRequest = ::exitApplication,
        title = "SmartLedger",
    ) {
        App(repository = repository)
    }
}
