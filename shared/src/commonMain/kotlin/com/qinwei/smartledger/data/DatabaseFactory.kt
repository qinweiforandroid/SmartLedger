package com.qinwei.smartledger.data

import androidx.room.RoomDatabase

/**
 * 各平台创建并构建数据库实例。
 *
 * - Android：走 Context 拿 databaseBuilder，文件位于应用私有目录
 * - iOS：走 Documents 目录拼路径
 * - JVM(Desktop)：走 user.home 下的应用目录
 *
 * 三端统一使用 BundledSQLiteDriver，这样行为一致、不依赖系统 SQLite 版本。
 */
expect fun createDatabase(): RoomDatabase.Builder<AppDatabase>

/**
 * 全局唯一的数据库实例。
 *
 * Room 的数据库对象长期复用即可，每次重建都会重新打开文件、重建连接池。
 * 用 lazy 而不是 synchronized：后者是 JVM 专有 API，commonMain 里 K/N 编译不过。
 */
val appDatabase: AppDatabase by lazy { createDatabase().build() }
