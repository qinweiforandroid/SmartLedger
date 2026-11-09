package com.qinwei.smartledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.data.appDatabase
import com.qinwei.smartledger.data.initializeDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Android 端的数据库需要 Context，先注入再访问 appDatabase
        initializeDatabase(this)
        val repository = TransactionRepository(appDatabase.transactionDao())

        setContent {
            App(repository = repository)
        }
    }
}
