package com.qinwei.smartledger.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.qinwei.smartledger.model.Transaction

/**
 * 应用数据库。KMP 下版本号从 1 开始，结构变更时增加并补 Migration。
 *
 * @ConstructedBy 是 Room KMP 的要求：非 Android 平台没有反射可用的
 * 无参构造，必须由 KSP 生成一个 [RoomDatabaseConstructor] 实现来实例化数据库。
 */
@Database(
    entities = [Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TransactionTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

/**
 * 数据库构造器声明。
 *
 * commonMain 里只写 expect，真正的实现由 Room 的 KSP 处理器
 * 分别生成到 android / iOS / JVM 各目标下，所以这里不需要手写 actual。
 * 注意：KSP 生成的实现带 actual 修饰，编译器会假定本 expect 已有 actual，
 * 因此加 @Suppress("KotlinNoActualForExpect") 消除 IDE 的误报。
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
