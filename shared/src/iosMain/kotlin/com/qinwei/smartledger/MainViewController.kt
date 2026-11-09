package com.qinwei.smartledger

import androidx.compose.ui.window.ComposeUIViewController
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.data.appDatabase

fun MainViewController() = ComposeUIViewController {
    val repository = TransactionRepository(appDatabase.transactionDao())
    App(repository = repository)
}
