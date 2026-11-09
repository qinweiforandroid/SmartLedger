package com.qinwei.smartledger.util

/**
 * 当前时间（epoch 毫秒）。
 *
 * commonMain 不能直接用 java.util.Date / System.currentTimeMillis，
 * 所以按平台分发。之后接入 kotlinx-datetime 可以换成 Clock.System.now()。
 */
expect fun currentTimeMillis(): Long
