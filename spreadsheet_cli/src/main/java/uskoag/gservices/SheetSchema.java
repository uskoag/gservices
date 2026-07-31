package uskoag.gservices;

import java.util.List;

public record SheetSchema(
    String sheetName, int sheetId, int headerRowNum, int dataStartRow,
    int dataColumnStart, int dataColumnEnd, int dataSampledRows, int dataSampleSize,
    String dataUniquenessCriteriaColumn,
    List<SchemaHeaderCell> headerRow, List<SchemaSampleColumn> sampleData) {}
