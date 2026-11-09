package com.qinwei.smartledger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.model.Transaction
import com.qinwei.smartledger.model.TransactionType
import com.qinwei.smartledger.util.localDateOf
import com.qinwei.smartledger.util.nowMillis
import com.qinwei.smartledger.util.startOfDayMillis
import com.qinwei.smartledger.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

/**
 * 列表中的一个日期分组：同一天的所有账单 + 当天净收支小计。
 *
 * @param date 该分组的自然日（系统时区）
 * @param items 当天账单，已按时间倒序
 * @param income 当天收入合计
 * @param expense 当天支出合计
 */
data class DailyGroup(
    val date: LocalDate,
    val items: List<Transaction>
) {
    val income: Double = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense: Double = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val net: Double = income - expense
}

/**
 * 统计页的一个分类条目。
 *
 * @param category 分类名
 * @param amount 该分类在所选月份的合计金额
 * @param share 占同类型总额的比例，0f..1f
 */
data class CategoryTotal(
    val category: String,
    val amount: Double,
    val share: Float
)

/**
 * 账本页面的 ViewModel。
 *
 * 数据源来自 Room：Repository 暴露的 Flow 会在表数据变化时自动推送全量列表，
 * 这里按「当前选中月份」做一层过滤，再派生日期分组、月度合计和分类占比。
 *
 * 为什么过滤放在 ViewModel 而不是 Dao：
 * 月份是 UI 状态，会随用户切换而变化，放进查询里就得为每个月份新建一个 Flow；
 * 放在内存里过滤，切换月份是同步的、也没有订阅开销。
 */
class LedgerViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val allTransactions: StateFlow<List<Transaction>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前选中的月份，默认是本月 */
    private val _selectedMonth = MutableStateFlow(today().yearMonth)
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /** 已经是最新月份时，前进按钮应该禁用，避免翻到未来空账期 */
    val canGoToNextMonth: StateFlow<Boolean> = _selectedMonth
        .map { it < today().yearMonth }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 所选月份内的账单，Dao 已按 timestamp 倒序排好 */
    private val monthTransactions: StateFlow<List<Transaction>> =
        combine(allTransactions, _selectedMonth) { list, month ->
            list.filter { localDateOf(it.timestamp).yearMonth == month }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 账单列表（仅当前选中月份） */
    val transactions: StateFlow<List<Transaction>> = monthTransactions

    /**
     * 按自然日分组的账单列表。
     *
     * 分组顺序遵循原始列表顺序（timestamp 倒序），因此最近的一天永远在最上面；
     * 同一天内部由 Dao 的 ORDER BY timestamp DESC 保证倒序，这里不再重排。
     */
    val groupedTransactions: StateFlow<List<DailyGroup>> = monthTransactions
        .map { list ->
            list.groupBy { localDateOf(it.timestamp) }
                .map { (date, items) -> DailyGroup(date, items) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 所选月份总收入 */
    val monthlyIncome: StateFlow<Double> = monthTransactions
        .map { list -> list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** 所选月份总支出 */
    val monthlyExpense: StateFlow<Double> = monthTransactions
        .map { list -> list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** 结余 = 所选月份总收入 - 所选月份总支出 */
    val balance: StateFlow<Double> = combine(monthlyIncome, monthlyExpense) { income, expense ->
        income - expense
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** 所选月份的支出分类构成，金额从大到小 */
    val expenseBreakdown: StateFlow<List<CategoryTotal>> = monthTransactions
        .map { list -> breakdownOf(list, TransactionType.EXPENSE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 所选月份的收入分类构成，金额从大到小 */
    val incomeBreakdown: StateFlow<List<CategoryTotal>> = monthTransactions
        .map { list -> breakdownOf(list, TransactionType.INCOME) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 首次启动时若数据库为空则写入示例数据
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }

    /** 切到上一个月 */
    fun showPreviousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonth()
    }

    /** 切到下一个月；已经到了本月就不再前进 */
    fun showNextMonth() {
        val next = _selectedMonth.value.plusMonth()
        if (next <= today().yearMonth) {
            _selectedMonth.value = next
        }
    }

    /** 直接跳到某个月份，新增/编辑账单后用来跟随账单日期 */
    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    /**
     * 新增账单。id 传 0 交给 Room 自增。
     *
     * @param date 用户选中的自然日，时分秒沿用「现在」，
     *   这样补记前几天的账单时，同一天内的先后顺序依然符合直觉。
     */
    fun addTransaction(
        amount: Double,
        type: TransactionType,
        category: String,
        note: String = "",
        date: LocalDate = today()
    ) {
        viewModelScope.launch {
            repository.add(
                Transaction(
                    amount = amount,
                    type = type,
                    category = category,
                    timestamp = timestampForDate(date),
                    note = note
                )
            )
        }
    }

    /** 编辑已有账单，id 保持不变，列表会由 Room 的 Flow 自动刷新 */
    fun updateTransaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        category: String,
        note: String,
        date: LocalDate,
        originalTimestamp: Long
    ) {
        viewModelScope.launch {
            // 日期没改动时沿用原来的时间，避免编辑备注把手记的时分秒冲掉
            val timestamp =
                if (localDateOf(originalTimestamp) == date) originalTimestamp
                else timestampForDate(date)
            repository.update(
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    category = category,
                    timestamp = timestamp,
                    note = note
                )
            )
        }
    }

    /** 删除账单；回调里给出被删掉的对象，供 Snackbar 撤销时原样写回 */
    fun deleteTransaction(transaction: Transaction, onDeleted: (Transaction) -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(transaction.id)
            onDeleted(transaction)
        }
    }

    /** 撤销删除 */
    fun undoDelete(transaction: Transaction) {
        viewModelScope.launch {
            repository.restore(transaction)
        }
    }

    /**
     * 按分类聚合某一收支类型的金额，并算出占比。
     * 占比基于该类型的总额，而不是净额，这样饼图各扇区相加正好是 1。
     */
    private fun breakdownOf(
        list: List<Transaction>,
        type: TransactionType
    ): List<CategoryTotal> {
        val filtered = list.filter { it.type == type }
        val total = filtered.sumOf { it.amount }
        if (total <= 0.0) return emptyList()
        return filtered
            .groupBy { it.category }
            .map { (category, items) ->
                val amount = items.sumOf { it.amount }
                CategoryTotal(category = category, amount = amount, share = (amount / total).toFloat())
            }
            .sortedByDescending { it.amount }
    }

    /**
     * 把「日期 + 当前时刻的时分秒」组合成时间戳。
     * 选中的就是今天时直接返回 now，避免毫秒精度在反复编辑中被截断。
     */
    private fun timestampForDate(date: LocalDate): Long {
        val now = nowMillis()
        if (date == today()) return now
        val dayStart = date.startOfDayMillis()
        val hasPassedDays = dayStart <= now
        val offset = if (hasPassedDays) now - today().startOfDayMillis() else 0L
        return dayStart + offset.coerceAtLeast(0L)
    }
}

/** 月份中文标题，如 2026年11月 */
fun YearMonth.toChineseTitle(): String = "${year}年${month.number}月"
