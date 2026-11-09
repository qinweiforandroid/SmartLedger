package com.qinwei.smartledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qinwei.smartledger.model.CategoryGlyph
import com.qinwei.smartledger.model.TransactionType
import com.qinwei.smartledger.model.iconOf
import com.qinwei.smartledger.viewmodel.CategoryTotal
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 统计页配色。
 *
 * 选色原则：同一张图里相邻扇区明度差异要够大，色盲用户也能区分；
 * 首色沿用全局的收入绿 / 支出红，让两页的语义颜色保持一致。
 */
private val ExpensePalette = listOf(
    Color(0xFFC62828), // 红
    Color(0xFFEF6C00), // 橙
    Color(0xFFF9A825), // 琥珀
    Color(0xFF00897B), // 青
    Color(0xFF5E35B1), // 紫
    Color(0xFF546E7A)  // 蓝灰
)

private val IncomePalette = listOf(
    Color(0xFF2E7D32), // 绿
    Color(0xFF00897B), // 青
    Color(0xFF3949AB), // 靛蓝
    Color(0xFF6D4C41)  // 棕
)

/** 按索引取色；分类多于调色板时循环复用，避免出现无色的扇区 */
private fun paletteOf(type: TransactionType): List<Color> =
    if (type == TransactionType.INCOME) IncomePalette else ExpensePalette

private fun colorAt(type: TransactionType, index: Int): Color {
    val palette = paletteOf(type)
    return palette[index % palette.size]
}

/**
 * 统计页：当前月份的收支构成。
 *
 * 布局（上→下）：
 * 1. 支出 / 收入 切换（分段按钮）
 * 2. 圆环图 + 圆心总额
 * 3. 分类排行：图标 + 名称 + 金额 + 占比进度条
 *
 * 整页可垂直滚动，避免分类多时内容被底部导航切断。
 */
@Composable
fun StatsScreen(
    expenseBreakdown: List<CategoryTotal>,
    incomeBreakdown: List<CategoryTotal>,
    modifier: Modifier = Modifier
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }

    val data = if (type == TransactionType.EXPENSE) expenseBreakdown else incomeBreakdown
    val total = data.sumOf { it.amount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // 支出 / 收入 切换
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { index, item ->
                SegmentedButton(
                    selected = type == item,
                    onClick = { type = item },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(if (item == TransactionType.EXPENSE) "支出构成" else "收入构成")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (data.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (type == TransactionType.EXPENSE) "本月还没有支出记录" else "本月还没有收入记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            DonutChart(
                data = data,
                type = type,
                total = total,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "分类排行",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            // 固定数量的排行条目，直接用一个 Column 渲染（外面已有 verticalScroll）
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.forEachIndexed { index, item ->
                    CategoryRankRow(
                        item = item,
                        color = colorAt(type, index),
                        type = type
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 圆环饼图。
 *
 * 画法：每个分类一段圆弧，`useCenter = false` + 粗描边就得到圆环；
 * 起始角固定 -90°（正上方），顺时针依次排布。
 * 每段之间留 [GAP_DEGREES] 的空隙，相邻颜色即使接近也不会糊成一片。
 *
 * 只有一个分类时不留缝，画成完整的圆。
 */
@Composable
private fun DonutChart(
    data: List<CategoryTotal>,
    type: TransactionType,
    total: Double,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = size.minDimension * 0.14f
            val inset = strokeWidthPx / 2f
            val arcSize = Size(
                width = size.width - strokeWidthPx,
                height = size.height - strokeWidthPx
            )
            val topLeft = Offset(inset, inset)

            val gap = if (data.size > 1) GAP_DEGREES else 0f
            var startAngle = -90f

            data.forEachIndexed { index, item ->
                val sweep = item.share * 360f
                // 段太小时不再扣缝隙，否则会出现负角度导致圆弧画不出来
                val drawn = if (sweep > gap * 2f) sweep - gap else sweep
                if (drawn > 0f) {
                    drawArc(
                        color = colorAt(type, index),
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = drawn,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                    )
                }
                startAngle += sweep
            }
        }

        // 圆心信息：类型说明 + 总额
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (type == TransactionType.EXPENSE) "总支出" else "总收入",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "¥ ${formatMoney(total)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private const val GAP_DEGREES = 3f

/**
 * 分类排行条目：色点 + 图标 + 名称，右侧金额，下方一条占比进度条。
 */
@Composable
private fun CategoryRankRow(
    item: CategoryTotal,
    color: Color,
    type: TransactionType
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.14f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CategoryGlyph(
                        icon = iconOf(item.category),
                        color = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = item.category,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "¥ ${formatMoney(item.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "${item.share.roundToIntSafe()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(6.dp))

        // 进度条：底层浅色轨道 + 上层按占比铺宽的色块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = item.share.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color = color, shape = RoundedCornerShape(3.dp))
            )
        }
    }
}

private fun Float.roundToIntSafe(): Int = if (isNaN()) 0 else (this * 100f).roundToInt()

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
