package com.silentstudy.util

import com.silentstudy.data.local.UsageDao
import com.silentstudy.data.model.ApiUsageLog
import com.silentstudy.data.repository.BudgetSettings
import java.util.Calendar

sealed class BudgetCheckResult {
    object Allowed : BudgetCheckResult()
    data class Warning(val message: String, val percent: Int) : BudgetCheckResult()
    data class Blocked(val message: String) : BudgetCheckResult()
}

data class UsageStats(
    val todayTokens: Long,
    val todayCost: Double,
    val weekTokens: Long,
    val weekCost: Double,
    val monthTokens: Long,
    val monthCost: Double
)

class TokenCostManager(private val usageDao: UsageDao) {

    suspend fun checkBudget(budget: BudgetSettings, estimatedTokensForNextRequest: Int = 1200): BudgetCheckResult {
        val now = System.currentTimeMillis()

        // 1. Check daily token cap
        if (budget.dailyTokenCap > 0) {
            val startOfDay = getStartOfDayTimestamp(now)
            val usedToday = usageDao.getTotalTokensSince(startOfDay) ?: 0L
            val projected = usedToday + estimatedTokensForNextRequest

            if (projected >= budget.dailyTokenCap) {
                return BudgetCheckResult.Blocked(
                    "Daily token budget cap reached (${usedToday}/${budget.dailyTokenCap} tokens). Raise cap in Settings > Usage to continue."
                )
            } else if (usedToday.toDouble() / budget.dailyTokenCap.toDouble() >= (budget.warnThresholdPercent / 100.0)) {
                val pct = ((usedToday.toDouble() / budget.dailyTokenCap.toDouble()) * 100).toInt()
                return BudgetCheckResult.Warning("Daily token usage is at $pct% of your limit.", pct)
            }
        }

        // 2. Check monthly token cap
        if (budget.monthlyTokenCap > 0) {
            val startOfMonth = getStartOfMonthTimestamp(now)
            val usedMonth = usageDao.getTotalTokensSince(startOfMonth) ?: 0L
            if (usedMonth + estimatedTokensForNextRequest >= budget.monthlyTokenCap) {
                return BudgetCheckResult.Blocked(
                    "Monthly token budget cap reached (${usedMonth}/${budget.monthlyTokenCap} tokens). Raise cap in Settings > Usage to continue."
                )
            }
        }

        // 3. Check daily cost cap
        if (budget.dailyCostCapUsd > 0.0) {
            val startOfDay = getStartOfDayTimestamp(now)
            val costToday = usageDao.getTotalCostSince(startOfDay) ?: 0.0
            if (costToday >= budget.dailyCostCapUsd) {
                return BudgetCheckResult.Blocked(
                    "Daily spend cap reached ($${String.format("%.2f", costToday)} / $${String.format("%.2f", budget.dailyCostCapUsd)}). Raise cap in Settings."
                )
            }
        }

        // 4. Check monthly cost cap
        if (budget.monthlyCostCapUsd > 0.0) {
            val startOfMonth = getStartOfMonthTimestamp(now)
            val costMonth = usageDao.getTotalCostSince(startOfMonth) ?: 0.0
            if (costMonth >= budget.monthlyCostCapUsd) {
                return BudgetCheckResult.Blocked(
                    "Monthly spend cap reached ($${String.format("%.2f", costMonth)} / $${String.format("%.2f", budget.monthlyCostCapUsd)}). Raise cap in Settings."
                )
            }
        }

        return BudgetCheckResult.Allowed
    }

    suspend fun recordUsage(
        provider: String,
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        pricePerMInput: Double,
        pricePerMOutput: Double
    ) {
        val totalTokens = promptTokens + completionTokens
        val inputCost = (promptTokens.toDouble() / 1_000_000.0) * pricePerMInput
        val outputCost = (completionTokens.toDouble() / 1_000_000.0) * pricePerMOutput
        val totalCost = inputCost + outputCost

        usageDao.insertLog(
            ApiUsageLog(
                provider = provider,
                model = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                estimatedCostUsd = totalCost
            )
        )
    }

    suspend fun getUsageStats(): UsageStats {
        val now = System.currentTimeMillis()
        val startOfDay = getStartOfDayTimestamp(now)
        val startOfWeek = getStartOfWeekTimestamp(now)
        val startOfMonth = getStartOfMonthTimestamp(now)

        val todayTokens = usageDao.getTotalTokensSince(startOfDay) ?: 0L
        val todayCost = usageDao.getTotalCostSince(startOfDay) ?: 0.0
        val weekTokens = usageDao.getTotalTokensSince(startOfWeek) ?: 0L
        val weekCost = usageDao.getTotalCostSince(startOfWeek) ?: 0.0
        val monthTokens = usageDao.getTotalTokensSince(startOfMonth) ?: 0L
        val monthCost = usageDao.getTotalCostSince(startOfMonth) ?: 0.0

        return UsageStats(
            todayTokens = todayTokens,
            todayCost = todayCost,
            weekTokens = weekTokens,
            weekCost = weekCost,
            monthTokens = monthTokens,
            monthCost = monthCost
        )
    }

    private fun getStartOfDayTimestamp(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getStartOfWeekTimestamp(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getStartOfMonthTimestamp(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
