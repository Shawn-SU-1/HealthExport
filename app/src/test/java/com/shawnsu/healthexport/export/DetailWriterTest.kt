package com.shawnsu.healthexport.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** 明细导出是纯 JDK 逻辑，可直接解包 zip、解析 XML 断言产物结构。 */
class DetailWriterTest {
    private val sheet = DetailSheet(
        name = "心率",
        columns = listOf("type", "start_time", "point_value"),
        rows = listOf(
            listOf("281", "2026-01-02 08:00:00 (1767...)", "72"),
            listOf("281", "2026-01-02 09:00:00 (1767...)", "80"),
        ),
    )

    private fun entries(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return result
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test fun xlsxKeepsWorkbookStructureAndValues() {
        val bytes = DetailWriter.xlsx(listOf(sheet)) ?: error("xlsx should not be null")
        val entries = entries(bytes)
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/worksheets/sheet1.xml",
            ),
            entries.keys,
        )
        val book = parse(entries.getValue("xl/workbook.xml"))
        val sheets = book.getElementsByTagName("sheet")
        assertEquals(1, sheets.length)
        assertEquals("心率", sheets.item(0).attributes.getNamedItem("name").nodeValue)

        val worksheet = entries.getValue("xl/worksheets/sheet1.xml")
        val cells = parse(worksheet).getElementsByTagName("c")
        // 表头 3 格 + 数据 6 格；空单元格不写出。
        assertEquals(9, cells.length)
        // 表头是文本；纯数字写成数值单元格、其余写成 inlineStr，便于直接做透视表。
        assertEquals("type", cells.item(0).textContent)
        assertEquals("inlineStr", cells.item(0).attributes.getNamedItem("t").nodeValue)
        assertEquals("281", cells.item(3).textContent)
        assertNull(cells.item(3).attributes.getNamedItem("t"))
        assertEquals("inlineStr", cells.item(4).attributes.getNamedItem("t").nodeValue)
        assertEquals("2026-01-02 08:00:00 (1767...)", cells.item(4).textContent)
    }

    @Test fun xlsxEscapesXmlAndRejectsIllegalSheetNames() {
        val dirty = DetailSheet(
            name = "a:b/c?d*e[f]g" + "x".repeat(40),
            columns = listOf("note"),
            rows = listOf(listOf("<tag> & \"quote\"")),
        )
        val bytes = DetailWriter.xlsx(listOf(dirty, dirty)) ?: error("xlsx should not be null")
        val entries = entries(bytes)
        assertEquals("a_b_c_d_e_f_g" + "x".repeat(18), sheetName(entries.getValue("xl/workbook.xml"), 0))
        // 第二个同名工作表退化为加后缀，避免 Excel 报错。
        assertEquals("a_b_c_d_e_f_g" + "x".repeat(16) + "_2", sheetName(entries.getValue("xl/workbook.xml"), 1))
        val worksheet = entries.getValue("xl/worksheets/sheet1.xml")
        parse(worksheet)
        assertTrue(worksheet.contains("&lt;tag&gt; &amp; \"quote\""))
    }

    @Test fun csvZipQuotesSeparatorsAndAddsBom() {
        val tricky = DetailSheet(
            name = "睡眠",
            columns = listOf("note", "value"),
            rows = listOf(listOf("含,逗号", "换\n行"), listOf("带\"引号\"", "")),
        )
        val bytes = DetailWriter.csvZip(listOf(tricky)) ?: error("csv zip should not be null")
        val entries = entries(bytes)
        assertEquals(setOf("睡眠.csv"), entries.keys)
        val csv = entries.getValue("睡眠.csv")
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"含,逗号\",\"换\n行\""))
        assertTrue(csv.contains("\"带\"\"引号\"\"\","))
    }

    @Test fun emptyInputProducesNothing() {
        assertNull(DetailWriter.xlsx(emptyList()))
        assertNull(DetailWriter.csvZip(emptyList()))
    }

    private fun sheetName(workbook: String, index: Int): String =
        parse(workbook).getElementsByTagName("sheet").item(index).attributes.getNamedItem("name").nodeValue
}
