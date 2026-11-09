package com.qinwei.smartledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qinwei.smartledger.data.TransactionRepository
import com.qinwei.smartledger.model.CategoryGlyph
import com.qinwei.smartledger.model.Transaction
import com.qinwei.smartledger.model.TransactionType
import com.qinwei.smartledger.model.categoriesOf
import com.qinwei.smartledger.model.iconOf
import com.qinwei.smartledger.util.formatTimeOfDay
import com.qinwei.smartledger.util.localDateOf
import com.qinwei.smartledger.util.toFullDate
import com.qinwei.smartledger.util.toGroupDateLabel
import com.qinwei.smartledger.util.today
import com.qinwei.smartledger.viewmodel.DailyGroup
import com.qinwei.smartledger.viewmodel.LedgerViewModel
import com.qinwei.smartledger.viewmodel.toChineseTitle
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)

/**
 * 应用主界面，由底部导航分为两个 Tab：
 * - 「明细」：[LedgerTab] 流水页
 * - 「统计」：[StatsScreen] 收支构成
 *
 * 布局结构（对照 XML 思路）：
 * - Scaffold          ≈ 带 topBar / bottomBar / floatingActionButton / snackbarHost 的 FrameLayout
 *   - topBar          ≈ 顶部标题栏（标题 + 月份切换器）
 *   - content Column  ≈ 竖向 LinearLayout，内容随当前 Tab 切换
 *   - bottomBar       ≈ Material3 NavigationBar，两个 Tab
 *   - floatingActionButton ≈ 仅在「明细」页显示
 *   - snackbarHostState    ≈ 底部提示条，删除后提供「撤销」
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun LedgerScreen(
    repository: TransactionRepository,
    viewModel: LedgerViewModel = viewModel { LedgerViewModel(repository) }
) {
    val groups by viewModel.groupedTransactions.collectAsState()
    val income by viewModel.monthlyIncome.collectAsState()
    val expense by viewModel.monthlyExpense.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val canGoNext by viewModel.canGoToNextMonth.collectAsState()
    val expenseBreakdown by viewModel.expenseBreakdown.collectAsState()
    val incomeBreakdown by viewModel.incomeBreakdown.collectAsState()

    // 当前选中的 Tab，0 = 明细，1 = 统计
    var currentTab by remember { mutableStateOf(0) }

    // 正在新增（null）或编辑的账单；null 表示对话框未打开
    var editorTarget by remember { mutableStateOf<EditorState?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** 删除后给一个可撤销的提示，撤销时把原对象写回数据库，id 保持不变 */
    fun requestDelete(transaction: Transaction) {
        viewModel.deleteTransaction(transaction) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "已删除「${transaction.category}」账单",
                    actionLabel = "撤销",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDelete(transaction)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    MonthSwitcher(
                        title = selectedMonth.toChineseTitle(),
                        canGoNext = canGoNext,
                        onPrevious = viewModel::showPreviousMonth,
                        onNext = viewModel::showNextMonth
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { LedgerTabIcon(LedgerTab.DETAILS, selected = currentTab == 0) },
                    label = { Text("明细") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { LedgerTabIcon(LedgerTab.STATS, selected = currentTab == 1) },
                    label = { Text("统计") }
                )
            }
        },
        floatingActionButton = {
            // 统计页不需要记账入口，只在明细页显示
            if (currentTab == 0) {
                FloatingActionButton(onClick = { editorTarget = EditorState.New }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            0 -> LedgerTab(
                groups = groups,
                income = income,
                expense = expense,
                balance = balance,
                onEdit = { editorTarget = EditorState.Edit(it) },
                onDelete = { requestDelete(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            else -> StatsScreen(
                expenseBreakdown = expenseBreakdown,
                incomeBreakdown = incomeBreakdown,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    editorTarget?.let { state ->
        TransactionEditorDialog(
            state = state,
            onDismiss = { editorTarget = null },
            onConfirm = { amount, type, category, note, date ->
                when (state) {
                    is EditorState.New -> viewModel.addTransaction(
                        amount = amount,
                        type = type,
                        category = category,
                        note = note,
                        date = date
                    )

                    is EditorState.Edit -> viewModel.updateTransaction(
                        id = state.transaction.id,
                        amount = amount,
                        type = type,
                        category = category,
                        note = note,
                        date = date,
                        originalTimestamp = state.transaction.timestamp
                    )
                }
                // 记账日期可能落在别的月份，保存后把月份切过去，
                // 否则新记录会被当前月份的过滤条件挡掉，看起来像没保存成功
                viewModel.selectMonth(date.yearMonth)
                editorTarget = null
            }
        )
    }
}

/* ---------------------------------------------------------------------------
 * 顶部月份切换器 & 底部 Tab 图标
 * ------------------------------------------------------------------------- */

/**
 * 月份切换器：`< 2026年11月 >`。
 * 顶部栏剩余空间被点击时不做任何事，只有两个箭头可点，
 * 避免用户误触标题造成月份跳变。
 *
 * @param canGoNext 已经到了本月就禁用右箭头，防止翻到未来空账期
 */
@Composable
private fun MonthSwitcher(
    title: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(
            direction = ArrowDirection.LEFT,
            enabled = true,
            description = "上个月",
            onClick = onPrevious
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        ArrowButton(
            direction = ArrowDirection.RIGHT,
            enabled = canGoNext,
            description = "下个月",
            onClick = onNext
        )
    }
}

private enum class ArrowDirection { LEFT, RIGHT }

/**
 * 月份切换箭头。用 Canvas 画三角形，省掉图标库依赖，
 * 禁用态用半透明表示（Material 的 disabled 语义）。
 */
@Composable
private fun ArrowButton(
    direction: ArrowDirection,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (direction == ArrowDirection.LEFT) {
                    moveTo(w * 0.65f, h * 0.2f)
                    lineTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.65f, h * 0.8f)
                } else {
                    moveTo(w * 0.35f, h * 0.2f)
                    lineTo(w * 0.7f, h * 0.5f)
                    lineTo(w * 0.35f, h * 0.8f)
                }
            }
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private enum class LedgerTab { DETAILS, STATS }

/** 底部导航图标：明细用清单线，统计用柱状线，同样手绘在 24 单位坐标系里 */
@Composable
private fun LedgerTabIcon(tab: LedgerTab, selected: Boolean) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(24.dp)) {
        val factor = size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (tab) {
                LedgerTab.DETAILS -> {
                    // 三条横线 + 左侧三个圆点，像一张清单
                    listOf(7f, 12f, 17f).forEach { y ->
                        drawCircle(color = tint, radius = 1.1f, center = Offset(4.5f, y))
                        drawLine(
                            color = tint,
                            start = Offset(9f, y),
                            end = Offset(20f, y),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round
                        )
                    }
                }

                LedgerTab.STATS -> {
                    // 三根高矮不同的柱子
                    val bars = listOf(6f to 15f, 12f to 9f, 18f to 4f)
                    bars.forEach { (x, top) ->
                        drawLine(
                            color = tint,
                            start = Offset(x, 20f),
                            end = Offset(x, top),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * 明细页
 * ------------------------------------------------------------------------- */

/**
 * 明细页：收支概览卡片 + 按日期分组的流水列表。
 */
@Composable
private fun LedgerTab(
    groups: List<DailyGroup>,
    income: Double,
    expense: Double,
    balance: Double,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OverviewCard(
            income = income,
            expense = expense,
            balance = balance,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (groups.isEmpty()) {
            EmptyHint(modifier = Modifier.fillMaxSize())
        } else {
            // RecyclerView 等价物：LazyColumn 只渲染可见区域
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 88.dp // 预留空间，避免最后一条被 FAB 遮住
                )
            ) {
                groups.forEach { group ->
                    // 每个日期分组 = 一条分组头 + 当天若干条账单
                    item(key = "header-${group.date}") {
                        DayHeader(group = group)
                    }
                    items(
                        items = group.items,
                        key = { transaction -> transaction.id }
                    ) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            onClick = { onEdit(transaction) },
                            onDelete = { onDelete(transaction) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * 概览卡片
 * ------------------------------------------------------------------------- */

/**
 * 收支概览卡片：上方显示大号结余，下方分两栏显示收入与支出
 */
@Composable
private fun OverviewCard(
    income: Double,
    expense: Double,
    balance: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "本月结余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "¥ ${formatMoney(balance)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            // 横向两栏：收入 / 支出
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCell(
                    label = "本月收入",
                    amount = income,
                    color = IncomeColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryCell(
                    label = "本月支出",
                    amount = expense,
                    color = ExpenseColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "¥ ${formatMoney(amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/* ---------------------------------------------------------------------------
 * 日期分组头
 * ------------------------------------------------------------------------- */

/**
 * 日期分组头：左侧「今天 / 昨天 / 11月3日」，右侧当天净收支小计。
 * 净收支为正显示绿色 +，为负显示红色 -，为 0 显示灰色「收支持平」。
 */
@Composable
private fun DayHeader(group: DailyGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.date.toGroupDateLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = group.date.toFullDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val net = group.net
        val netColor = when {
            net > 0.0 -> IncomeColor
            net < 0.0 -> ExpenseColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = when {
                net > 0.0 -> "收入 ${formatMoney(net)}"
                net < 0.0 -> "支出 ${formatMoney(-net)}"
                else -> "收支持平"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = netColor
        )
    }
}

/* ---------------------------------------------------------------------------
 * 账单行
 * ------------------------------------------------------------------------- */

/**
 * 单条账单行：左侧分类图标，中间分类 / 备注，右侧金额与删除按钮。
 * 整行可点击进入编辑；删除按钮走 [onDelete] 的撤销流程。
 */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val accent = if (isIncome) IncomeColor else ExpenseColor

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标底衬：浅色圆底 + 线性图标，比纯色文字圆点更贴近 Material 规范
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CategoryGlyph(
                        icon = iconOf(transaction.category),
                        color = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(formatTimeOfDay(transaction.timestamp))
                        if (transaction.note.isNotBlank()) {
                            append(" · ")
                            append(transaction.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                text = (if (isIncome) "+" else "-") + formatMoney(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )

            TextButton(onClick = onDelete) {
                Text("删除", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "还没有账单，点击右下角 + 记一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/* ---------------------------------------------------------------------------
 * 记账 / 编辑对话框
 * ------------------------------------------------------------------------- */

/** 对话框的两种状态：新增 / 编辑已有的某条账单 */
sealed interface EditorState {
    data object New : EditorState
    data class Edit(val transaction: Transaction) : EditorState
}

/**
 * 新增 / 编辑账单对话框。
 *
 * 内容从上到下：
 * 1. 收入 / 支出切换（切换时分类网格跟着换一套）
 * 2. 金额输入
 * 3. 日期选择（默认今天，点击展开 Material DatePicker）
 * 4. 分类网格单选（3 列，图标 + 名称）
 * 5. 备注
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun TransactionEditorDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onConfirm: (Double, TransactionType, String, String, LocalDate) -> Unit
) {
    val existing = (state as? EditorState.Edit)?.transaction

    // key(state)：切换编辑目标时重建全部表单状态，避免沿用上一条账单的内容
    var type by remember(state) { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by remember(state) {
        mutableStateOf(existing?.amount?.let { formatMoney(it) } ?: "")
    }
    var noteText by remember(state) { mutableStateOf(existing?.note ?: "") }
    var date by remember(state) {
        mutableStateOf(existing?.let { localDateOf(it.timestamp) } ?: today())
    }
    var category by remember(state) {
        mutableStateOf(existing?.category ?: categoriesOf(type).first().label)
    }
    var showDatePicker by remember(state) { mutableStateOf(false) }
    var showError by remember(state) { mutableStateOf(false) }

    val parsedAmount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "记一笔" else "编辑账单") },
        text = {
            // 表单在手机上会超出对话框高度，用 verticalScroll 保证分类网格也能完整滑到
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 类型切换：收入 / 支出
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransactionType.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = type == item,
                            onClick = {
                                if (type != item) {
                                    type = item
                                    // 类型变了分类集合也变了，默认选中第一项
                                    category = categoriesOf(item).first().label
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TransactionType.entries.size
                            )
                        ) {
                            Text(if (item == TransactionType.INCOME) "收入" else "支出")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        showError = false
                    },
                    label = { Text("金额") },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // 日期行：点击整行打开日期选择器
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = date.toGroupDateLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                // 分类网格：3 列，每格一个图标 + 名称，单选
                CategoryGrid(
                    type = type,
                    selected = category,
                    onSelect = { category = it }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请输入大于 0 的金额并选择分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parsedAmount
                    if (amount == null || amount <= 0 || category.isBlank()) {
                        showError = true
                    } else {
                        onConfirm(amount, type, category, noteText.trim(), date)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        LedgerDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            }
        )
    }
}

/**
 * 分类网格单选。用固定 3 列的手工 Row 而不是 LazyVerticalGrid，
 * 因为网格高度需要随内容自然撑开，外层对话框不宜嵌套可滚动容器。
 */
@Composable
private fun CategoryGrid(
    type: TransactionType,
    selected: String,
    onSelect: (String) -> Unit
) {
    val options = remember(type) { categoriesOf(type) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { option ->
                    val isSelected = option.label == selected
                    val bg = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    val fg = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(bg, MaterialTheme.shapes.medium)
                            .clickable { onSelect(option.label) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CategoryGlyph(
                            icon = option.icon,
                            color = fg,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = fg
                        )
                    }
                }
                // 最后一行不满 3 个时补空位，保持宽度一致
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 日期选择对话框。
 *
 * Material3 的 DatePicker 以 UTC 当天 00:00 的毫秒值表示选中日，
 * 所以这里要把 UTC millis 转回来的 LocalDate 做一次显式换算，
 * 避免因时区偏移导致选中日期差一天。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun LedgerDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val zone = TimeZone.UTC
    val todayUtcMillis = remember { today().atStartOfDayIn(zone).toEpochMilliseconds() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDayIn(zone).toEpochMilliseconds(),
        yearRange = 2000..today().year + 1,
        // 允许补记过去的账单，但不允许选未来日期
        selectableDates = remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayUtcMillis

                override fun isSelectableYear(year: Int): Boolean = year <= today().year
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    val picked = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(zone)
                        .date
                    onConfirm(picked)
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = "选择日期",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            },
            headline = {
                Text(
                    text = (state.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date
                    } ?: initialDate).toChinese()
                )
            },
            showModeToggle = false,
            colors = DatePickerDefaults.colors()
        )
    }
}

private fun LocalDate.toChinese(): String = "${year}年${month.number}月${day}日"

/**
 * 金额格式化：保留两位小数。
 * commonMain 里不能用 String.format（JVM API），所以用整数分位手工拼接。
 */
private fun formatMoney(value: Double): String {
    val negative = value < 0
    val cents = (abs(value) * 100).roundToLong()
    val intPart = cents / 100
    val decimalPart = cents % 100
    val decimalStr = if (decimalPart < 10) "0$decimalPart" else decimalPart.toString()
    return (if (negative) "-" else "") + "$intPart.$decimalStr"
}
