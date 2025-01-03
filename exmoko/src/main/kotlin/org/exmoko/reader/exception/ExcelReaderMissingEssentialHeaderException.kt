package org.exmoko.reader.exception

class ExcelReaderMissingEssentialHeaderException(
  message: String = "The Excel file is missing essential headers."
) : ExcelReaderException(message)
