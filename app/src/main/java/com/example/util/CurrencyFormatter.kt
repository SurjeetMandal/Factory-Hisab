package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyFormatter {

    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun formatRupees(amount: Double, showDecimalsWhenWhole: Boolean = false): String {
        val rounded = (amount * 100.0).roundToLong() / 100.0
        val sign = if (rounded < 0) "-" else ""
        val absValue = abs(rounded)
        val whole = absValue.toLong()
        val cents = ((absValue - whole) * 100.0).roundToLong()

        val wholeFormatted = formatIndianInteger(whole)
        return if (cents == 0L && !showDecimalsWhenWhole) {
            "${sign}₹$wholeFormatted"
        } else {
            val centStr = cents.toString().padStart(2, '0')
            "${sign}₹$wholeFormatted.$centStr"
        }
    }

    fun formatRate(rate: Double): String {
        return formatRupees(rate, showDecimalsWhenWhole = true)
    }

    fun formatQuantity(qty: Double): String {
        val rounded = (qty * 100.0).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) {
            formatIndianInteger(rounded.toLong())
        } else {
            val symbols = DecimalFormatSymbols(indianLocale)
            DecimalFormat("#,##0.##", symbols).format(rounded)
        }
    }

    private fun formatIndianInteger(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.takeLast(3)
        val rest = s.dropLast(3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count % 2 == 0 && i != 0) {
                sb.append(',')
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }
}

object DateFormatter {
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val compactDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val slipDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatShort(millis: Long): String = shortDateFormat.format(Date(millis))

    fun formatCompact(millis: Long): String = compactDateFormat.format(Date(millis))

    fun formatSlipDate(millis: Long): String = slipDateFormat.format(Date(millis))

    fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    fun currentYearRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        return start to cal.timeInMillis
    }

    fun mergeUtcPickerDate(utcMidnightMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        val localCal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        }
        return localCal.timeInMillis
    }
}
