package it.registroore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Esportatore OOXML minimale (Excel .xlsx), senza dipendenze esterne. */
final class SimpleXlsx {
  private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final DateTimeFormatter DMY=DateTimeFormatter.ofPattern("dd/MM/uuuu",Locale.ITALY);
  private static final String[] WEEK={"Lun","Mar","Mer","Gio","Ven","Sab","Dom"};
  private SimpleXlsx() { }
  static void write(Path destination,int year,int month,List<App.LogEntry> records) throws IOException {
    try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination),StandardCharsets.UTF_8)){
      part(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
        "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"+
        "</Types>");
      part(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"+
        "</Relationships>");
      part(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"+
        "<sheets><sheet name=\"Registro\" sheetId=\"1\" r:id=\"rId1\"/>"+
        "<sheet name=\"Riepilogo\" sheetId=\"2\" r:id=\"rId2\"/></sheets>"+
        "</workbook>");
      part(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"+
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"+
        "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"+
        "</Relationships>");
      part(zip,"xl/styles.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<styleSheet xmlns=\""+NS+"\">"+
        "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"0.##\"/><numFmt numFmtId=\"165\" formatCode=\"dd/mm/yyyy\"/></numFmts>"+
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"+
        "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"+
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"+
        "<fill><patternFill patternType=\"gray125\"/></fill>"+
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF15243B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"+
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"+
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"+
        "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"+
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"+
        "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"+
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"+
        "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
      part(zip,"xl/worksheets/sheet1.xml",registerXml(records));
      part(zip,"xl/worksheets/sheet2.xml",summaryXml(year,month,records));
    }
  }
  private static void part(ZipOutputStream zip,String name,String xml) throws IOException{
    zip.putNextEntry(new ZipEntry(name));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
  }
  private static String head(String cols){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
    "<worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\">"+
    "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"+
    "<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>"+
    "</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"16\"/>"+cols+"<sheetData>";}
  private static String col(String c,int i,int style,String text){return "<c r=\""+c+i+"\" s=\""+style+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+escape(text)+"</t></is></c>";}
  private static String number(String c,int i,int style,double value){return "<c r=\""+c+i+"\" s=\""+style+"\"><v>"+Double.toString(value)+"</v></c>";}
  private static String cellDate(String c,int i,App.LogEntry e){return number(c,i,2,e.date().toEpochDay()+25569);}
  private static String escape(String text){
    if(text==null)return "";
    StringBuilder b=new StringBuilder();
    text.codePoints().forEach(cp->{if(cp!='\n'&&cp!='\r'&&cp!='\t'&&cp<32)return;
      switch(cp){case '&'->b.append("&amp;");case '<'->b.append("&lt;");case '>'->b.append("&gt;");
        case '"'->b.append("&quot;");case '\''->b.append("&apos;");default->b.appendCodePoint(cp);}});
    return b.toString();
  }
  private static String registerXml(List<App.LogEntry> records){
    StringBuilder s=new StringBuilder(head("<cols><col min=\"1\" max=\"1\" width=\"15\" customWidth=\"1\"/>"+
      "<col min=\"2\" max=\"2\" width=\"10\" customWidth=\"1\"/>"+
      "<col min=\"3\" max=\"3\" width=\"47\" customWidth=\"1\"/>"+
      "<col min=\"4\" max=\"4\" width=\"26\" customWidth=\"1\"/>"+
      "<col min=\"5\" max=\"5\" width=\"12\" customWidth=\"1\"/>"+
      "<col min=\"6\" max=\"6\" width=\"55\" customWidth=\"1\"/>"+
      "<col min=\"7\" max=\"7\" width=\"22\" customWidth=\"1\"/></cols>"));
    int r=1;s.append("<row r=\"1\" ht=\"27\" customHeight=\"1\">");
    String[] headers={"Data","Giorno","Attività","Categoria","Ore","Note","Origine"};
    for(int i=0;i<headers.length;i++)s.append(col(String.valueOf((char)('A'+i)),r,1,headers[i]));
    s.append("</row>");
    double total=0;
    for(App.LogEntry e:records){r++;s.append("<row r=\"").append(r).append("\">");
      s.append(cellDate("A",r,e));
      s.append(col("B",r,0,WEEK[e.date().getDayOfWeek().getValue()-1]));
      s.append(col("C",r,0,e.activity().name()));s.append(col("D",r,0,e.activity().category()));
      if(e.hours()!=null){total+=e.hours();s.append(number("E",r,3,e.hours()));}
      s.append(col("F",r,0,e.note()));s.append(col("G",r,0,e.source()));s.append("</row>");
    }
    int totalRow=r+2;
    s.append("<row r=\"").append(totalRow).append("\">").append(col("D",totalRow,1,"TOTALE ORE"))
      .append(number("E",totalRow,3,total)).append("</row>");
    s.append("</sheetData><autoFilter ref=\"A1:G").append(Math.max(1,r)).append("\"/>"+
      "<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>"+
      "</worksheet>");
    return s.toString();
  }
  private static String summaryXml(int year,int month,List<App.LogEntry> records){
    TreeMap<String,Double> values=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(App.LogEntry e:records)if(e.hours()!=null)
      values.merge(e.activity().category()+" | "+e.activity().name(),e.hours(),Double::sum);
    StringBuilder s=new StringBuilder(head("<cols><col min=\"1\" max=\"1\" width=\"62\" customWidth=\"1\"/>"+
      "<col min=\"2\" max=\"2\" width=\"15\" customWidth=\"1\"/></cols>"));
    s.append("<row r=\"1\">").append(col("A",1,1,"Attività · "+(month==0?"Anno "+year:month+"/"+year)))
      .append(col("B",1,1,"Totale ore")).append("</row>");
    int r=1;double total=0;
    for(var entry:values.entrySet()){r++;total+=entry.getValue();
      s.append("<row r=\"").append(r).append("\">").append(col("A",r,0,entry.getKey()))
        .append(number("B",r,3,entry.getValue())).append("</row>");}
    r+=2;s.append("<row r=\"").append(r).append("\">").append(col("A",r,1,"TOTALE COMPLESSIVO"))
      .append(number("B",r,3,total)).append("</row>");
    s.append("</sheetData></worksheet>");
    return s.toString();
  }
}
