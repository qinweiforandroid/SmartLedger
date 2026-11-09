package com.qinwei.smartledger.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单类型：收入 / 支出
 */
enum class TransactionType {
    INCOME,
    EXPENSE
}

/**
 * 单条账单记录（Room 实体，对应表 transactions）
 *
 * @param id 主键，自增。新增时传 0，Room 会分配真实 id
 * @param amount 金额（始终为正数，正负由 [type] 决定）
 * @param type 收入或支出
 * @param category 分类名称，例如 "餐饮"、"工资"
 * @param timestamp 记账时间，使用 epoch 毫秒（Long），避免使用 java.util.Date 以保持 KMP 兼容
 * @param note 备注信息
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["timestamp"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = ""
)
