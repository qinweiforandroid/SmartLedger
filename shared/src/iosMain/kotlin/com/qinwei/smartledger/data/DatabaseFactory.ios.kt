package com.qinwei.smartledger.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS 平台实现：数据库文件放在沙盒 Documents 目录下。
 */
actual fun createDatabase(): RoomDatabase.Builder<AppDatabase> {
    val documentsDir = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true
    ).first() as String

    return Room.databaseBuilder<AppDatabase>(name = "$documentsDir/smartledger.db")
        .setDriver(BundledSQLiteDriver())
}
