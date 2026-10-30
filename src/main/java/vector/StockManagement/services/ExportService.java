package vector.StockManagement.services;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import vector.StockManagement.model.dto.Views.ReportTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ExportService {

    public byte[] toXlsx(ReportTable table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(bold);

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue(table.title());
            tc.setCellStyle(boldStyle);
            r++;
            Row head = sheet.createRow(r++);
            for (int c = 0; c < table.headers().size(); c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(table.headers().get(c));
                cell.setCellStyle(header);
            }
            for (List<Object> row : table.rows()) writeRow(sheet.createRow(r++), row, null);
            if (table.totals() != null) writeRow(sheet.createRow(r), table.totals(), boldStyle);
            for (int c = 0; c < table.headers().size(); c++) sheet.autoSizeColumn(c);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("The spreadsheet could not be created");
        }
    }

    private void writeRow(Row row, List<Object> values, CellStyle style) {
        for (int c = 0; c < values.size(); c++) {
            Object v = values.get(c);
            Cell cell = row.createCell(c);
            if (v instanceof BigDecimal b) cell.setCellValue(b.doubleValue());
            else if (v instanceof Number n) cell.setCellValue(n.doubleValue());
            else cell.setCellValue(v == null ? "" : String.valueOf(v));
            if (style != null) cell.setCellStyle(style);
        }
    }
}
