@file:OptIn(ExperimentalTime::class)

package com.qinwei.smartledger.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 日期工具：把时间戳与「本地自然日」互相转换。
 *
 * 为什么统一用 LocalDate 做分组键：
 * 用户看到的「今天 / 昨天 / 2026年11月3日」是按设备时区切的自然日，
 * 而数据库里存的是 UTC epoch 毫秒。只有先转成系统时区下的 LocalDate，
 * 跨时区、夏令时、跨年才不会把同一天的账单拆成两组。
 */

/** 取 [timestamp]（epoch 毫秒）在系统时区下所属的自然日 */
fun localDateOf(timestamp: Long): LocalDate =
    Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

/** 某个自然日 00:00 对应的 epoch 毫秒（系统时区） */
fun LocalDate.startOfDayMillis(): Long =
    atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** 今天的自然日 */
fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** 当前时刻的 epoch 毫秒 */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** 相对日期文案：今天 / 昨天 / 前天，其余返回 null 交给调用方格式化 */
fun relativeDayLabel(date: LocalDate, base: LocalDate = today()): String? = when (date) {
    base -> "今天"
    base.minus(1, DateTimeUnit.DAY) -> "昨天"
    base.minus(2, DateTimeUnit.DAY) -> "前天"
    else -> null
}

/** 2026年11月3日 */
fun LocalDate.toChineseDate(): String =
    "${year}年${month.number}月${day}日"

/** 分组标题的日期部分：最近三天用「今天 / 昨天 / 前天」，更早的用具体日期 */
fun LocalDate.toGroupDateLabel(base: LocalDate = today()): String =
    relativeDayLabel(this, base) ?: toChineseDate()

/** 分组标题下方的补充说明，带上星期几，方便用户确认 */
fun LocalDate.toFullDate(): String =
    "${year}年${month.number}月${day}日 ${weekdayLabel(dayOfWeek.ordinal + 1)}"

/** 账单行的时间，如 14:05 */
fun formatTimeOfDay(timestamp: Long): String {
    val time = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time
    return "${time.hour.pad2()}:${time.minute.pad2()}"
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

private fun weekdayLabel(isoDayNumber: Int): String = when (isoDayNumber) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
