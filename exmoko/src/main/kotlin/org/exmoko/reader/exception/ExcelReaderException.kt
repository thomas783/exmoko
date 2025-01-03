package org.exmoko.reader.exception

import org.exmoko.reader.ExcelReaderErrorField

open class ExcelReaderException(
  message: String = "An error occurred while reading an Excel file.",
  val errorFieldList: List<ExcelReaderErrorField> = listOf()
) : RuntimeException(message) {
  override fun toString(): String {
    return "Something went wrong while reading the excel file. ${errorFieldList.joinToString("\n") { it.toString() }}"
  }
}
