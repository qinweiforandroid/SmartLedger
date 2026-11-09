package com.qinwei.smartledger.data

import androidx.room.TypeConverter
import com.qinwei.smartledger.model.TransactionType

/**
 * Room 默认只认识基础类型，枚举需要手动转换成可存储的 String。
 */
class TransactionTypeConverters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}
