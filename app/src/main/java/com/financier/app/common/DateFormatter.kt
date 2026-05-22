package com.financier.app.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatter {

    private val timeFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    private val dateFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }

    private val dayFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("EEE", Locale.getDefault())
    }

    fun formatTime(ms: Long): String {
        return timeFormatThreadLocal.get()!!.format(Date(ms))
    }

    fun formatDate(ms: Long): String {
        return dateFormatThreadLocal.get()!!.format(Date(ms))
    }

    fun formatDay(date: Date): String {
        return dayFormatThreadLocal.get()!!.format(date)
    }

    fun getStartOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun getEndOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    fun getStartOfMonth(month: Int, year: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfMonth(month: Int, year: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
