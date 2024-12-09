package dto

import excel.writer.annotation.ExcelWriterColumn
import excel.writer.annotation.ExcelWriterHeader
import org.apache.poi.ss.usermodel.DataValidation
import org.apache.poi.ss.usermodel.DataValidationConstraint
import org.apache.poi.ss.usermodel.IndexedColors
import java.time.LocalDate
import java.time.LocalDateTime

@ExcelWriterHeader(
  essentialFields = [
    "countryCode", "sku", "orderNumber", "orderStatus", "price", "quantity"
  ]
)
data class ExcelWriterSampleDto(
  // Essential Fields
  @ExcelWriterColumn(
    headerName = "COUNTRY CODE",
    headerCellColor = IndexedColors.RED,
    validationType = DataValidationConstraint.ValidationType.FORMULA,
    validationIgnoreBlank = false,
    validationFormula = "AND(EXACT(UPPER(${ExcelWriterColumn.CURRENT_CELL}), ${ExcelWriterColumn.CURRENT_CELL}), LEN(${ExcelWriterColumn.CURRENT_CELL}) = 2)",
    validationPromptTitle = "COUNTRY CODE",
    validationErrorStyle = DataValidation.ErrorStyle.STOP,
    validationErrorTitle = "Invalid country code format",
    validationErrorText = "Country code should be two uppercase alphabets. Example: KR,JP,US...",
  )
  val countryCode: String,

  @ExcelWriterColumn(
    headerName = "SKU",
    headerCellColor = IndexedColors.RED,
    validationPromptTitle = "SKU"
  )
  val sku: String,

  @ExcelWriterColumn(
    headerName = "ORDER NUMBER",
    headerCellColor = IndexedColors.RED,
    validationPromptTitle = "ORDER NUMBER"
  )
  val orderNumber: String,

  @ExcelWriterColumn(
    headerName = "ORDER STATUS",
    headerCellColor = IndexedColors.RED,
    validationType = DataValidationConstraint.ValidationType.LIST,
    validationIgnoreBlank = false,
    validationErrorStyle = DataValidation.ErrorStyle.STOP,
    validationListEnum = OrderStatus::class,
    validationPromptTitle = "ORDER STATUS",
    validationErrorTitle = "Invalid order status format",
  )
  val orderStatus: OrderStatus,

  @ExcelWriterColumn(
    headerName = "PRICE",
    headerCellColor = IndexedColors.RED,
    validationPromptTitle = "PRICE"
  )
  val price: Double,

  @ExcelWriterColumn(
    headerName = "QUANTITY",
    headerCellColor = IndexedColors.RED,
    validationPromptTitle = "QUANTITY"
  )
  val quantity: Int,

  // Optional Fields
  @ExcelWriterColumn(
    headerName = "ORDERED AT",
    headerCellColor = IndexedColors.BLUE,
    validationPromptTitle = "ORDERED AT"
  )
  val orderedAt: LocalDateTime? = null,

  @ExcelWriterColumn(
    headerName = "PAID DATE",
    headerCellColor = IndexedColors.BLUE,
    validationPromptTitle = "PAID DATE"
  )
  val paidDate: LocalDate? = null,

  @ExcelWriterColumn(
    headerName = "PRODUCT NAME",
    headerCellColor = IndexedColors.BLUE,
    validationPromptTitle = "PRODUCT NAME"
  )
  val productName: String? = null,
) {
  companion object {
    enum class OrderStatus {
      ORDERED, PAID, SHIPPED, DELIVERED, CANCELED, REFUND_REQUESTED, REFUND, EXCHANGE_REQUESTED, EXCHANGED;
    }

    fun createSampleData(size: Int): List<ExcelWriterSampleDto> {
      return (1..size).map { number ->
        ExcelWriterSampleDto(
          countryCode = "KR",
          sku = "SKU-$number",
          orderNumber = number.toString(),
          orderStatus = OrderStatus.entries.toTypedArray().random(),
          price = (number % 10) * 1000.0,
          quantity = number % 3 + 1,
          orderedAt = LocalDateTime.now().minusSeconds(number.toLong()),
          paidDate = LocalDate.now().minusDays((number % 3).toLong()),
          productName = "Product $number"
        )
      }
    }
  }
}