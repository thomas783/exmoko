package org.excelkotlin.reader.exception

class ExcelReaderInvalidCellTypeException(
  message: String = "Invalid cell type."
) : RuntimeException(message)