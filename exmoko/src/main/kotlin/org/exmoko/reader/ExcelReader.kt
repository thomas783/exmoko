package org.exmoko.reader

import org.exmoko.reader.annotation.ExcelReaderHeader
import org.exmoko.reader.exception.ExcelReaderException
import org.exmoko.reader.exception.ExcelReaderFileExtensionException
import org.exmoko.reader.exception.ExcelReaderInvalidCellTypeException
import org.exmoko.reader.exception.ExcelReaderInvalidCellValueException
import org.exmoko.reader.exception.ExcelReaderMissingEssentialHeaderException
import com.github.drapostolos.typeparser.TypeParser
import com.github.drapostolos.typeparser.TypeParserException
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.poi.ss.formula.eval.ErrorEval
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.toMessage
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

/**
 * Excel Reader
 *
 * This class is used to read Excel files.
 * @param path The path of the Excel file to read.
 */
class ExcelReader(path: String) : AutoCloseable {
  lateinit var errorFieldList: MutableList<ExcelReaderErrorField>
  private lateinit var excelFile: File
  lateinit var workbook: Workbook

  val typeParser: TypeParser = TypeParser.newBuilder()
    .registerParser(LocalDate::class.java) { input, _ ->
      LocalDate.parse(input, DateTimeFormatter.ISO_DATE_TIME)
    }
    .registerParser(LocalDateTime::class.java) { input, _ ->
      LocalDateTime.parse(input, DateTimeFormatter.ISO_DATE_TIME)
    }
    .build()

  init {
    initExcelReaderItems(path)
  }

  override fun close() {
    workbook.close()
  }

  data class ExcelHeaderValue<T>(
    val headerName: String,
    val headerIdx: Int,
    val field: KMutableProperty1<T, *>
  )

  fun <T : Any> checkCellType(cell: Cell?, property: KProperty1<T, *>) {
    val cellType = cell?.cellType ?: return

    when (property.returnType.jvmErasure) {
      String::class -> if (cellType == CellType.NUMERIC)
        throw ExcelReaderInvalidCellTypeException("Invalid cell type. The field type must be a string type.")

      LocalDate::class, LocalDateTime::class -> if (cellType != CellType.NUMERIC)
        throw ExcelReaderInvalidCellTypeException("Invalid cell type. The field type must be a date type.")
    }
  }

  fun getValue(cell: Cell?): String? {
    if (cell == null || Objects.isNull(cell.cellType)) return ""

    return when (cell.cellType) {
      CellType.STRING -> cell.richStringCellValue.string
      CellType.FORMULA ->
        runCatching { cell.richStringCellValue.string }.getOrNull()
          ?: runCatching { cell.numericCellValue.toString() }.getOrNull()
          ?: ""

      CellType.NUMERIC -> {
        val value = if (DateUtil.isCellDateFormatted(cell)) cell.localDateTimeCellValue.toString()
        else cell.numericCellValue.toString()
        if (value.endsWith(".0")) value.substring(0, value.length - 2)
        else value
      }

      CellType.BOOLEAN -> cell.booleanCellValue.toString()
      CellType.ERROR -> ErrorEval.getText(cell.errorCellValue.toInt())
      CellType.BLANK, CellType._NONE -> ""
      else -> ""
    }
  }

  inline fun <reified T : Any> setObjectMapping(obj: T, row: Row): T {
    val headerMap = getHeader<T>()

    headerMap.mapValues { (_, excelHeaderValue) ->
      val (headerName, headerIdx, field) = excelHeaderValue
      var cellValue: String? = null
      val cell = row.getCell(headerIdx)

      runCatching {
        cellValue = getValue(cell)
        var setData: Any? = null

        if (!cellValue.isNullOrBlank()) checkCellType(cell, field)
        if (!StringUtils.isEmpty(cellValue)) setData =
          typeParser.parseType(cellValue, field.javaField?.type)
        field.isAccessible = true
        field.setter.call(obj, setData)
        checkValidation(obj, field.name)
      }.onFailure { exception ->
        val (error, message) = when (exception) {
          is ExcelReaderInvalidCellTypeException -> ExcelReaderFieldError.TYPE to ExcelReaderFieldError.TYPE.message
          is TypeParserException -> ExcelReaderFieldError.TYPE to "${exception.message} Field Type: ${field.javaField?.type?.simpleName}, Input Type: ${cellValue?.javaClass?.simpleName}"
          is ExcelReaderInvalidCellValueException -> ExcelReaderFieldError.VALID to ExcelReaderFieldError.VALID.message
          else -> ExcelReaderFieldError.UNKNOWN to ExcelReaderFieldError.UNKNOWN.message
        }
        errorFieldList.add(
          ExcelReaderErrorField(
            type = error.name,
            row = row.rowNum + 1,
            field = field.name,
            fieldHeader = headerName,
            inputData = cellValue,
            message = message,
            exceptionMessage = ExceptionUtils.getRootCauseMessage(exception)
          )
        )
      }
    }

    return obj
  }

  @Throws(ConstraintViolationException::class)
  fun <T : Any> checkValidation(obj: T, fieldName: String) {
    if (obj !is IExcelReaderCommonDto) return

    runCatching {
      obj.validate()
    }.onFailure { exception ->
      (exception as ConstraintViolationException).constraintViolations
        .firstOrNull { it.property == fieldName }?.toMessage()
        ?.let { throw ExcelReaderInvalidCellValueException(it.toString()) }
    }
  }

  @Throws(ExcelReaderException::class)
  fun initExcelReaderItems(path: String) {
    excelFile = File(path).also {
      validateFileExtension(it)
    }
    workbook = runCatching {
      excelFile.inputStream().use {
        WorkbookFactory.create(excelFile)
      }
    }.onFailure {
      throw ExcelReaderException(it.message.toString())
    }.getOrThrow()

    errorFieldList = mutableListOf()
  }

  @Throws(ExcelReaderFileExtensionException::class)
  private fun validateFileExtension(file: File) {
    val fileExtension = file.name.substring(file.name.lastIndexOf(".") + 1)

    if (fileExtension !in excelFileExtensions)
      throw ExcelReaderFileExtensionException(
        "Invalid file extension $fileExtension. Only ${
          excelFileExtensions.joinToString(
            ", "
          ) { ".$it" }
        } file extension is allowed."
      )
  }

  @Throws(ExcelReaderException::class)
  inline fun <reified T : Any> readExcelFile(startRow: Int = 1): List<T> {
    val sheet = workbook.getSheetAt(0)
    val rowCount = sheet.physicalNumberOfRows
    val objectList = (startRow until rowCount)
      .filterNot { rowIdx -> isRowAllBlank(sheet.getRow(rowIdx)) }
      .map { rowIdx -> readRow<T>(sheet.getRow(rowIdx)) }

    if (errorFieldList.isNotEmpty())
      throw ExcelReaderException(errorFieldList = errorFieldList)

    return objectList
  }

  inline fun <reified T : Any> readRow(row: Row): T = setObjectMapping(T::class.createInstance(), row)

  fun isRowAllBlank(row: Row): Boolean {
    return row.cellIterator().asSequence().all { it.cellType == CellType.BLANK }
  }

  inline fun <reified T : Any> getHeader(rowNum: Int = 0): MutableMap<String, ExcelHeaderValue<T>> {
    val memberProperties = T::class.memberProperties
    val headers = workbook.getSheetAt(0).getRow(rowNum)
    val essentialHeaders = T::class.findAnnotation<ExcelReaderHeader>()?.essentialFields
    val readHeaders: MutableMap<String, ExcelHeaderValue<T>> =
      (0 until headers.physicalNumberOfCells).mapNotNull { cellIdx ->
        val headerName = headers.getCell(cellIdx).stringCellValue
        val field = memberProperties.firstOrNull { it.name == headerName } as KMutableProperty1<T, *>?
        if (field != null) ExcelHeaderValue(headerName, cellIdx, field)
        else null
      }.associateBy { it.headerName }.toMutableMap()

    if (essentialHeaders != null) validateEssentialHeaders(essentialHeaders, readHeaders)

    return readHeaders
  }

  @Throws(ExcelReaderMissingEssentialHeaderException::class)
  fun <T : Any> validateEssentialHeaders(
    essentialHeaders: Array<String>,
    readHeaders: Map<String, ExcelHeaderValue<T>>,
  ) {
    val missingEssentialHeaders = essentialHeaders.filter {
      !readHeaders.keys.contains(it)
    }
    if (missingEssentialHeaders.isNotEmpty())
      throw ExcelReaderMissingEssentialHeaderException("Essential headers are missing. $missingEssentialHeaders")
  }

  companion object {
    private val excelFileExtensions = listOf("xlsx", "xls")
  }
}
