package com.qinwei.smartledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.qinwei.smartledger.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 账单数据的数据库访问对象。
 *
 * 查询方法返回 [Flow]，Room 会在表数据变化时自动重新发送新结果，
 * 上层 Repository/ViewModel 不需要手动刷新。
 */
@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Insert
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Transaction>>
}
