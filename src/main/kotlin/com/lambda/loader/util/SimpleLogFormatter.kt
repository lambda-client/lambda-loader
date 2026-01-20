package com.lambda.loader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.Formatter
import java.util.logging.LogRecord

/**
 * Simple log formatter that removes class names and methods from log output.
 * Formats logs as: [HH:mm:ss] [LEVEL]: message
 */
class SimpleLogFormatter : Formatter() {
    private val dateFormat = SimpleDateFormat("HH:mm:ss")

    override fun format(record: LogRecord): String {
        val time = dateFormat.format(Date(record.millis))
        val level = record.level.name
        val message = formatMessage(record)

        return "[$time] [$level]: $message${System.lineSeparator()}"
    }
}
