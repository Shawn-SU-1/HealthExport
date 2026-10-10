package com.shawnsu.healthexport.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 一个数据集的明细表：工作表名、列名与逐行单元格（均为已格式化文本）。 */
internal class DetailSheet(val name: String, val columns: List<String>, val rows: List<List<String>>)

/**
 * 把导出明细写成 Excel（.xlsx）与 CSV 压缩包（.zip）。
 *
 * 只用 JDK 自带的 ZipOutputStream 手写最小 OOXML：多工作表、文本用 inlineStr、
 * 纯数字写成数值，便于在 Excel / WPS 里直接拉透视表。不引入任何第三方依赖，
 * 全部为纯函数，可在 JVM 单测中直接断言产物内容。
 */
internal object DetailWriter {
    /** 工作表名上限 31 字符，且不能包含 : \ / ? * [ ]。 */
    private const val MAX_SHEET_NAME = 31
    private val ILLEGAL_NAME_CHARS = charArrayOf(':', '\\', '/', '?', '*', '[', ']')

    /** 纯数字（不含前导零、不含千分位）才写成数值单元格，其余一律按文本处理。 */
    private val NUMBER = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?$")

    /** XML 命名空间，两个文档必需。 */
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val NS_OFFICE_DOCUMENT =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    private const val NS_WORKSHEET =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"

    /** 多工作表 Excel 工作簿；[sheets] 为空时返回 null。 */
    fun xlsx(sheets: List<DetailSheet>): ByteArray? {
        val named = namedSheets(sheets)
        if (named.isEmpty()) return null
        return runCatching {
            val buffer = ByteArrayOutputStream(1 shl 16)
            ZipOutputStream(buffer).use { zip ->
                zip.writeText("[Content_Types].xml", contentTypes(named.size))
                zip.writeText("_rels/.rels", rootRels())
                zip.writeText("xl/workbook.xml", workbook(named.map { it.first }))
                zip.writeText("xl/_rels/workbook.xml.rels", workbookRels(named.size))
                named.forEachIndexed { index, (_, sheet) ->
                    zip.writeText("xl/worksheets/sheet${index + 1}.xml") { out ->
                        writeWorksheet(out, sheet)
                    }
                }
            }
            buffer.toByteArray()
        }.getOrNull()
    }

    /** 每个数据集一份 CSV 打包成 zip；[sheets] 为空时返回 null。 */
    fun csvZip(sheets: List<DetailSheet>): ByteArray? {
        val named = namedSheets(sheets)
        if (named.isEmpty()) return null
        return runCatching {
            val buffer = ByteArrayOutputStream(1 shl 16)
            ZipOutputStream(buffer).use { zip ->
                named.forEach { (name, sheet) ->
                    zip.putNextEntry(ZipEntry("$name.csv"))
                    val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                    // BOM 让 Excel / WPS 直接按 UTF-8 打开，避免中文乱码。
                    writer.write("\uFEFF")
                    writer.write(csvLine(sheet.columns))
                    for (row in sheet.rows) writer.write(csvLine(row))
                    writer.flush()
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }.getOrNull()
    }

    /** 工作表名去掉非法字符、截断到 31 字符并保证互不重复；空名回退为 sheet。 */
    private fun namedSheets(sheets: List<DetailSheet>): List<Pair<String, DetailSheet>> {
        val used = HashSet<String>()
        return sheets.mapIndexedNotNull { index, sheet ->
            val base = sheet.name
                .map { if (it in ILLEGAL_NAME_CHARS || it.code < 0x20) '_' else it }
                .joinToString("")
                .trim()
                .ifEmpty { "sheet${index + 1}" }
                .take(MAX_SHEET_NAME)
            var name = base
            var suffix = 2
            while (!used.add(name)) {
                val tail = "_$suffix"
                name = base.take(MAX_SHEET_NAME - tail.length) + tail
                suffix++
            }
            name to sheet
        }
    }

    private fun csvLine(cells: List<String>): String = buildString {
        cells.forEachIndexed { index, cell ->
            if (index > 0) append(',')
            val needsQuote = cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (needsQuote) {
                append('"')
                append(cell.replace("\"", "\"\""))
                append('"')
            } else {
                append(cell)
            }
        }
        append("\r\n")
    }

    private fun writeWorksheet(out: OutputStream, sheet: DetailSheet) {
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        writer.write("<worksheet xmlns=\"$NS_MAIN\"><sheetData>")
        writeRow(writer, 1, sheet.columns)
        sheet.rows.forEachIndexed { index, row -> writeRow(writer, index + 2, row) }
        writer.write("</sheetData></worksheet>")
        writer.flush()
    }

    private fun writeRow(writer: OutputStreamWriter, rowNumber: Int, cells: List<String>) {
        writer.write("<row r=\"$rowNumber\">")
        cells.forEachIndexed { index, value ->
            if (value.isEmpty()) return@forEachIndexed
            val reference = columnName(index) + rowNumber
            if (NUMBER.matches(value)) {
                writer.write("<c r=\"$reference\"><v>$value</v></c>")
            } else {
                writer.write("<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                writer.write(escape(value))
                writer.write("</t></is></c>")
            }
        }
        writer.write("</row>")
    }

    private fun columnName(index: Int): String {
        var value = index
        val builder = StringBuilder(2)
        while (true) {
            builder.append(('A' + value % 26))
            value = value / 26 - 1
            if (value < 0) break
        }
        return builder.reverse().toString()
    }

    private fun escape(text: String): String = buildString(text.length + 8) {
        for (char in text) {
            when {
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char.code < 0x20 -> append(' ')
                else -> append(char)
            }
        }
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Types xmlns=\"$NS_CONTENT_TYPES\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append(
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"" +
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>",
        )
        for (index in 1..sheetCount) {
            append(
                "<Override PartName=\"/xl/worksheets/sheet$index.xml\" ContentType=\"" +
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>",
            )
        }
        append("</Types>")
    }

    private fun rootRels(): String = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"$NS_RELATIONSHIPS\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_OFFICE_DOCUMENT\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private fun workbook(sheetNames: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_OFFICE_DOCUMENT\"><sheets>")
        sheetNames.forEachIndexed { index, name ->
            append("<sheet name=\"${escape(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"$NS_RELATIONSHIPS\">")
        for (index in 1..sheetCount) {
            append("<Relationship Id=\"rId$index\" Type=\"$NS_WORKSHEET\" Target=\"worksheets/sheet$index.xml\"/>")
        }
        append("</Relationships>")
    }

    private fun ZipOutputStream.writeText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeText(name: String, body: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        body(this)
        closeEntry()
    }
}
