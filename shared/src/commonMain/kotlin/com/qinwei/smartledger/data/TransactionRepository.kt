package com.qinwei.smartledger.data

import com.qinwei.smartledger.model.Transaction
import com.qinwei.smartledger.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * 账单仓储层：把 Dao 包一层，ViewModel 只依赖这个接口/类，
 * 将来要加网络同步、缓存之类的逻辑也不会污染 ViewModel。
 */
class TransactionRepository(private val dao: TransactionDao) {

    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()

    suspend fun add(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun update(transaction: Transaction) = dao.update(transaction)

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * 撤销删除：把刚才删掉的那条原样写回。
     * id 非 0 时 Room 会沿用该 id 插入，所以撤销后 id 保持不变。
     */
    suspend fun restore(transaction: Transaction): Long = dao.insert(transaction)

    /** 数据库为空时插入示例数据，避免首次启动是一片空白 */
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(sampleTransactions())
        }
    }

    companion object {
        /** 示例数据的时间戳往前排，保证列表能看出先后顺序 */
        private const val BASE_TIME = 1_700_000_000_000L

        fun sampleTransactions(): List<Transaction> = listOf(
            Transaction(
                amount = 8000.0,
                type = TransactionType.INCOME,
                category = "工资",
                timestamp = BASE_TIME,
                note = "本月工资"
            ),
            Transaction(
                amount = 35.5,
                type = TransactionType.EXPENSE,
                category = "餐饮",
                timestamp = BASE_TIME + 3_600_000L,
                note = "午饭"
            ),
            Transaction(
                amount = 128.0,
                type = TransactionType.EXPENSE,
                category = "交通",
                timestamp = BASE_TIME + 7_200_000L,
                note = "打车"
            )
        )
    }
}
