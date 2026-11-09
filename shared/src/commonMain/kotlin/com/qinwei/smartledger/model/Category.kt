package com.qinwei.smartledger.model

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * 分类图标。项目约定不引入第三方图标库，所有图形都用 Compose Canvas 现画，
 * 这里统一采用 Material 风格的线性图标（24 × 24 虚拟画布、圆头描边、无填充）。
 */
enum class CategoryIcon {
    FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, HOME, MEDICAL,
    SALARY, BONUS, INVESTMENT, OTHER
}

/** 一个可选分类：显示名 + 图标 + 收支类型 */
data class CategoryOption(
    val label: String,
    val icon: CategoryIcon,
    val type: TransactionType
)

/** 支出分类 */
val ExpenseCategories: List<CategoryOption> = listOf(
    CategoryOption("餐饮", CategoryIcon.FOOD, TransactionType.EXPENSE),
    CategoryOption("交通", CategoryIcon.TRANSPORT, TransactionType.EXPENSE),
    CategoryOption("购物", CategoryIcon.SHOPPING, TransactionType.EXPENSE),
    CategoryOption("娱乐", CategoryIcon.ENTERTAINMENT, TransactionType.EXPENSE),
    CategoryOption("居住", CategoryIcon.HOME, TransactionType.EXPENSE),
    CategoryOption("医疗", CategoryIcon.MEDICAL, TransactionType.EXPENSE)
)

/** 收入分类 */
val IncomeCategories: List<CategoryOption> = listOf(
    CategoryOption("工资", CategoryIcon.SALARY, TransactionType.INCOME),
    CategoryOption("奖金", CategoryIcon.BONUS, TransactionType.INCOME),
    CategoryOption("理财", CategoryIcon.INVESTMENT, TransactionType.INCOME),
    CategoryOption("其他", CategoryIcon.OTHER, TransactionType.INCOME)
)

fun categoriesOf(type: TransactionType): List<CategoryOption> = when (type) {
    TransactionType.EXPENSE -> ExpenseCategories
    TransactionType.INCOME -> IncomeCategories
}

/**
 * 历史数据里可能存了自定义分类名（旧版本是手输的），
 * 找不到就退回「其他」的图标，避免列表里出现空白格子。
 */
fun iconOf(category: String): CategoryIcon =
    (ExpenseCategories + IncomeCategories).firstOrNull { it.label == category }?.icon
        ?: CategoryIcon.OTHER

/**
 * 绘制分类图标。
 *
 * 内部固定用 24×24 的设计坐标书写，再用 [DrawScope.scale] 等比映射到控件尺寸，
 * 所以调用方给任意尺寸（例如 22.dp）都能保持线宽和比例一致。
 *
 * @param color 线条颜色，一般传 MaterialTheme.colorScheme.primary
 * @param modifier 建议由外部给出固定尺寸
 */
@Composable
fun CategoryGlyph(
    icon: CategoryIcon,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val factor = size.minDimension / DESIGN_SIZE
        // 平移 + 等比缩放：把 0..24 的设计稿坐标铺满控件
        scale(factor, factor, pivot = Offset.Zero) {
            val stroke = Stroke(
                width = STROKE_WIDTH,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            when (icon) {
                CategoryIcon.FOOD -> drawFood(color, stroke)
                CategoryIcon.TRANSPORT -> drawTransport(color, stroke)
                CategoryIcon.SHOPPING -> drawShopping(color, stroke)
                CategoryIcon.ENTERTAINMENT -> drawEntertainment(color, stroke)
                CategoryIcon.HOME -> drawHome(color, stroke)
                CategoryIcon.MEDICAL -> drawMedical(color, stroke)
                CategoryIcon.SALARY -> drawSalary(color, stroke)
                CategoryIcon.BONUS -> drawBonus(color, stroke)
                CategoryIcon.INVESTMENT -> drawInvestment(color, stroke)
                CategoryIcon.OTHER -> drawOther(color, stroke)
            }
        }
    }
}

private const val DESIGN_SIZE = 24f
private const val STROKE_WIDTH = 1.9f

/* ---------------------------------------------------------------------------
 * 各图标画法。注释标明每一笔在 24×24 设计稿中的位置，
 * 方便对照 Material Symbols 的官方图形做微调。
 * ------------------------------------------------------------------------- */

/** 餐饮：碗身 + 碗口 + 两支筷子 */
private fun DrawScope.drawFood(color: Color, stroke: Stroke) {
    // 碗身：直径 13 的下半圆（startAngle 0 → 180 顺时针，即下半圆）
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(5.5f, 7f),
        size = Size(13f, 13f),
        style = stroke
    )
    // 碗口
    drawLine(color, Offset(3.5f, 13.5f), Offset(20.5f, 13.5f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
    // 两支筷子（斜插在碗里）
    drawLine(color, Offset(9f, 7f), Offset(10.5f, 3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(15f, 7f), Offset(16.5f, 3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

/** 交通：车顶弧 + 车身 + 两个轮子 */
private fun DrawScope.drawTransport(color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(5.5f, 5.5f),
        size = Size(13f, 10f),
        style = stroke
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(2.5f, 11f),
        size = Size(19f, 6.5f),
        cornerRadius = CornerRadius(2.5f),
        style = stroke
    )
    drawCircle(color, radius = 1.7f, center = Offset(7f, 18.5f), style = stroke)
    drawCircle(color, radius = 1.7f, center = Offset(17f, 18.5f), style = stroke)
}

/** 购物：手提袋 + 提手 */
private fun DrawScope.drawShopping(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(4f, 8f),
        size = Size(16f, 12f),
        cornerRadius = CornerRadius(2.5f),
        style = stroke
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8f, 3.5f),
        size = Size(8f, 9f),
        style = stroke
    )
}

/** 娱乐：圆形外框 + 播放三角 */
private fun DrawScope.drawEntertainment(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    val play = Path().apply {
        moveTo(10.2f, 8.5f)
        lineTo(16f, 12f)
        lineTo(10.2f, 15.5f)
        close()
    }
    drawPath(play, color = color)
}

/** 居住：屋顶 + 墙体 + 门 */
private fun DrawScope.drawHome(color: Color, stroke: Stroke) {
    val roof = Path().apply {
        moveTo(3f, 11f)
        lineTo(12f, 3.5f)
        lineTo(21f, 11f)
    }
    drawPath(roof, color = color, style = stroke)
    val body = Path().apply {
        moveTo(5.5f, 10f)
        lineTo(5.5f, 20.5f)
        lineTo(18.5f, 20.5f)
        lineTo(18.5f, 10f)
    }
    drawPath(body, color = color, style = stroke)
    drawLine(color, Offset(12f, 20.5f), Offset(12f, 15f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
}

/** 医疗：十字 */
private fun DrawScope.drawMedical(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(10.2f, 4f),
        size = Size(3.6f, 16f),
        cornerRadius = CornerRadius(1.6f),
        style = stroke
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(4f, 10.2f),
        size = Size(16f, 3.6f),
        cornerRadius = CornerRadius(1.6f),
        style = stroke
    )
}

/** 工资：钞票 + 中间硬币 */
private fun DrawScope.drawSalary(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(2.5f, 6f),
        size = Size(19f, 12f),
        cornerRadius = CornerRadius(2f),
        style = stroke
    )
    drawCircle(color, radius = 2.8f, center = Offset(12f, 12f), style = stroke)
}

/** 奖金：礼物盒 + 竖带 + 蝴蝶结 */
private fun DrawScope.drawBonus(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(3.5f, 11f),
        size = Size(17f, 9.5f),
        cornerRadius = CornerRadius(1.5f),
        style = stroke
    )
    drawLine(color, Offset(2.5f, 11f), Offset(21.5f, 11f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(12f, 11f), Offset(12f, 20.5f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
    // 蝴蝶结左右两个半圆
    drawArc(
        color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(6.5f, 4f), size = Size(5.5f, 6f), style = stroke
    )
    drawArc(
        color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(12f, 4f), size = Size(5.5f, 6f), style = stroke
    )
}

/** 理财：坐标轴 + 上升折线 + 箭头 */
private fun DrawScope.drawInvestment(color: Color, stroke: Stroke) {
    val axis = Path().apply {
        moveTo(3.5f, 3.5f)
        lineTo(3.5f, 20.5f)
        lineTo(20.5f, 20.5f)
    }
    drawPath(axis, color = color, style = stroke)
    val line = Path().apply {
        moveTo(7f, 15.5f)
        lineTo(11f, 11f)
        lineTo(14f, 13.5f)
        lineTo(20.5f, 6.5f)
    }
    drawPath(line, color = color, style = stroke)
    // 折线终点箭头（两条短线）
    drawLine(color, Offset(20.5f, 6.5f), Offset(20.5f, 9.5f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(20.5f, 6.5f), Offset(17.5f, 6.5f),
        strokeWidth = stroke.width, cap = StrokeCap.Round)
}

/** 其他：圆环 + 三个点 */
private fun DrawScope.drawOther(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    drawCircle(color, radius = 1.3f, center = Offset(7.5f, 12f))
    drawCircle(color, radius = 1.3f, center = Offset(12f, 12f))
    drawCircle(color, radius = 1.3f, center = Offset(16.5f, 12f))
}
