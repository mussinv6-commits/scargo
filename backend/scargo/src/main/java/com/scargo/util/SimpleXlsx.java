package com.scargo.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 26.10.24 외부 라이브러리 없이 만드는 최소 .xlsx 작성기 (시트 여러 개, 문자열 · 숫자 셀, 첫 줄 굵게)
 * 일일 보고서 · 목록 내보내기용. 수식 · 서식은 지원하지 않는다.
 */
public class SimpleXlsx {

    private final List<String> names = new ArrayList<>();
    private final List<List<List<Object>>> sheets = new ArrayList<>();

    /** rows: 첫 행이 머리글. 값은 Number 면 숫자, 그 외는 문자열(null 은 빈 칸) */
    public SimpleXlsx addSheet(String name, List<List<Object>> rows) {
        names.add(safeName(name, names.size() + 1));
        sheets.add(rows);
        return this;
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", contentTypes());
                put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
                put(zip, "xl/workbook.xml", workbook());
                put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                put(zip, "xl/styles.xml", styles());
                for (int i = 0; i < sheets.size(); i++) {
                    put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i)));
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("xlsx 생성 실패", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < names.size(); i++) {
            sb.append("<sheet name=\"").append(esc(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    // 스타일 0 = 기본, 1 = 굵게(머리글)
    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheet(List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            List<Object> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                Object v = row.get(c);
                if (v == null) continue;
                String ref = col(c) + (r + 1);
                String style = r == 0 ? " s=\"1\"" : "";
                if (v instanceof Number n) {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append("><v>").append(n).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(esc(String.valueOf(v))).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static String col(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String safeName(String name, int fallback) {
        String s = name == null ? "" : name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (s.isEmpty()) s = "Sheet" + fallback;
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
}
