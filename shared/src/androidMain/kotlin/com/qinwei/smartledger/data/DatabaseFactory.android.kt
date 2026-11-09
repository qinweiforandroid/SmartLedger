package com.qinwei.smartledger.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android 平台实现。
 *
 * 需要 Context 才能拿到应用私有目录，所以由 MainActivity 在启动时
 * 通过 [initializeDatabase] 注入一次 Context。
 */
private var appContext: Context? = null

fun initializeDatabase(context: Context) {
    appContext = context.applicationContext
}

actual fun createDatabase(): RoomDatabase.Builder<AppDatabase> {
    val context = requireNotNull(appContext) {
        "Android 端需要先调用 initializeDatabase(context) 再创建数据库"
    }
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = context.getDatabasePath("smartledger.db").absolutePath
    ).setDriver(BundledSQLiteDriver())
}
