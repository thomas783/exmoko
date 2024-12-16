package writer.tests

import excel.writer.annotation.ExcelWriterColumn
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.test.logging.info
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.apache.poi.ss.usermodel.CellType
import shared.ExcelWriterBaseTests.Companion.setExcelWriterCommonSpec
import writer.dto.ExcelWriterSampleDto
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

@OptIn(ExperimentalKotest::class)
class ExcelWriterCellTests : BehaviorSpec({
  val sampleDataSize = 1000
  val sampleDtoKClass = ExcelWriterSampleDto::class
  val baseTest = setExcelWriterCommonSpec<ExcelWriterSampleDto.Companion, ExcelWriterSampleDto>(
    sampleDataSize = sampleDataSize,
    path = "sample-cell-value-type-check",
  )

  given("ExcelWriterColumn Annotation") {
    val sheet = baseTest.workbook.getSheetAt(0)
    val sampleDtoMemberPropertiesMap = sampleDtoKClass.memberProperties
      .filter { it.hasAnnotation<ExcelWriterColumn>() }
      .associate { it.name to it.findAnnotation<ExcelWriterColumn>() }
    val sampleDtoConstructorParameters = sampleDtoKClass.constructors.flatMap { it.parameters }
    val sampleDtoConstructorReturnTypeInOrder = sampleDtoConstructorParameters.filter { parameter ->
      sampleDtoMemberPropertiesMap[parameter.name] != null
    }.map { Triple(it.name, it.type.jvmErasure, it.type.isMarkedNullable) }

    then("ExcelWriterColumn annotated column type is set to expected type") {
      sampleDtoConstructorReturnTypeInOrder.forEachIndexed { columnIdx, (propertyName, kClass,isMarkedNullable) ->
        (1..sampleDataSize).forEach { rowIdx ->
          val cell = sheet.getRow(rowIdx).getCell(columnIdx)
          val actualCellType = cell.cellType
          val expectedCellType = when {
            kClass.isSubclassOf(Enum::class) -> CellType.STRING
            else -> when (kClass) {
              String::class -> CellType.STRING
              Int::class, Long::class, Double::class, LocalDate::class, LocalDateTime::class -> CellType.NUMERIC
              else -> CellType.STRING
            }
          }
          val expectedCellTypes = if (isMarkedNullable) setOf(CellType.BLANK, expectedCellType) else setOf(expectedCellType)

          info { "rowIdx: $rowIdx, columnIdx: $columnIdx" }
          info { "Property Name: $propertyName Expected Cell Types: $expectedCellTypes" }
          info { "Actual Type: $actualCellType" }

          actualCellType shouldBeIn expectedCellTypes
        }
      }
    }

    then("ExcelWriterData is set to expected format") {
      sampleDtoConstructorReturnTypeInOrder.forEachIndexed { columnIdx, (propertyName, kClass,isMarkedNullable) ->
        (1..sampleDataSize).forEach { rowIdx ->
          val cell = sheet.getRow(rowIdx).getCell(columnIdx)
          val cellDataFormat = cell.cellStyle.dataFormatString
          val expectedDataFormat = when {
            kClass.isSubclassOf(Enum::class) -> "@"
            else -> when (kClass) {
              String::class -> "@"
              Int::class, Long::class -> "0"
              Double::class -> "0.0"
              LocalDate::class -> "yyyy-mm-dd"
              LocalDateTime::class -> "yyyy-mm-dd hh:mm:ss"
              else -> "@"
            }
          }
          val expectedDataFormats = if (isMarkedNullable) setOf("General", expectedDataFormat)
          else setOf(expectedDataFormat)

          info { "rowIdx: $rowIdx, columnIdx: $columnIdx" }
          info { "Property Name: $propertyName, Expected Data Formats: $expectedDataFormats" }
          info { "Actual Data Format: $cellDataFormat" }

          cellDataFormat shouldBeIn expectedDataFormats
        }
      }
    }
  }
})
