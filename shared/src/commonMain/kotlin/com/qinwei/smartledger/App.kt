package com.qinwei.smartledger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.ui.LedgerScreen

/**
 * 应用入口。数据库由各平台入口创建后，通过 [repository] 注入进来，
 * commonMain 本身不感知平台细节。
 */
@Composable
fun App(repository: TransactionRepository) {
    MaterialTheme {
        LedgerScreen(repository = repository)
    }
}
