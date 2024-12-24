package writer.dto.validation

import com.excelkotlin.writer.annotation.ExcelWritable
import com.excelkotlin.writer.annotation.ExcelWriterColumn
import com.excelkotlin.writer.annotation.ExcelWriterHeader
import org.apache.poi.ss.usermodel.DataValidationConstraint
import writer.dto.IExcelWriterCommonDto

@ExcelWritable
data class ExcelWriterValidationTypeIntegerErrorDto(
  @ExcelWriterHeader(
    name = "SAMPLE INTEGER"
  )
  @ExcelWriterColumn(
    validationType = DataValidationConstraint.ValidationType.INTEGER
  )
  val integer: Int,
) {
  companion object : IExcelWriterCommonDto<ExcelWriterValidationTypeIntegerErrorDto> {
    override fun createSampleData(size: Int): Collection<ExcelWriterValidationTypeIntegerErrorDto> {
      return (1..size).map {
        ExcelWriterValidationTypeIntegerErrorDto(
          integer = it
        )
      }
    }
  }
}
