package it.registroore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.zip.*;

/** Project-specific, date-bounded reporting. Export includes own AND colleague hours. */
final class ProjectReportXlsx {
  private ProjectReportXlsx() {}
  private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu",Locale.ITALY);
  static void writeCsv(Path destination,String project,LocalDate from,LocalDate to,List<App.ProjectExportRow> rows) throws IOException {
    try(Writer out=new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(destination),StandardCharsets.UTF_8))){
      out.write('\ufeff');
      out.write("Data;Progetto;Tipo;Persona;Ruolo alla data;Ruolo nel progetto;Ore;Tariffa euro/ora;Costo euro;Note\n");
      double hours=0,cost=0;
      for(App.ProjectExportRow r:rows){
        String[] data={r.day().format(DATE),project,r.actor(),r.colleague(),r.role(),r.projectRole(),
          Double.toString(r.hours()).replace('.',','),Integer.toString(r.hourlyRate()),Double.toString(r.cost()).replace('.',','),r.note()};
        for(int i=0;i<data.length;i++){if(i>0)out.write(';');out.write(csv(data[i]));}out.write('\n');
        hours+=r.hours();cost+=r.cost();
      }
      out.write(";;;;;;"+csv(Double.toString(hours).replace('.',','))+";"+csv("")+";"+csv(Double.toString(cost).replace('.',','))+";"+csv("TOTALE")+"\n");
    }
  }
  private static String csv(String text){return "\""+(text==null?"":text).replace("\"","\"\"")+"\"";}
  static void write(Path output,String project,LocalDate from,LocalDate to,List<App.ProjectExportRow> rows) throws IOException {
    try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(output),StandardCharsets.UTF_8)){
      part(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
        "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
      part(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
      part(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"+
        "<sheets><sheet name=\"Registrazioni\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Riepilogo\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
      part(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"+
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"+
        "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
      part(z,"xl/styles.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<styleSheet xmlns=\""+NS+"\">"+
        "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"0.##\"/><numFmt numFmtId=\"165\" formatCode=\"dd/mm/yyyy\"/></numFmts>"+
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"+
        "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"+
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"+
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF15243B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"+
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"+
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"+
        "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"+
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"+
        "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"+
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"+
        "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
      part(z,"xl/worksheets/sheet1.xml",details(project,rows));
      part(z,"xl/worksheets/sheet2.xml",summary(project,from,to,rows));
    }
  }
  private static void part(ZipOutputStream z,String filename,String body) throws IOException {
    z.putNextEntry(new ZipEntry(filename));z.write(body.getBytes(StandardCharsets.UTF_8));z.closeEntry();
  }
  private static String esc(String s){
    if(s==null)return "";
    StringBuilder b=new StringBuilder();
    s.codePoints().forEach(cp->{if(cp!=10&&cp!=13&&cp!=9&&cp<32)return;
      switch(cp){case '&'->b.append("&amp;");case '<'->b.append("&lt;");case '>'->b.append("&gt;");case '"'->b.append("&quot;");default->b.appendCodePoint(cp);}});
    return b.toString();
  }
  private static String text(int r,int col,int st,String value){
    return "<c r=\""+(char)('A'+col)+r+"\" s=\""+st+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+esc(value)+"</t></is></c>";
  }
  private static String num(int r,int col,int st,double n){
    return "<c r=\""+(char)('A'+col)+r+"\" s=\""+st+"\"><v>"+Double.toString(n)+"</v></c>";
  }
  private static String openSheet(int widths){
    StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\">");
    s.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"+
      "<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews>");
    s.append("<cols>");int[] ws=widths==10?new int[]{16,28,16,26,22,25,14,19,19,62}:new int[]{18,28,22,16,18};
    for(int i=0;i<ws.length;i++)s.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(ws[i]).append("\" customWidth=\"1\"/>");
    s.append("</cols><sheetData>");return s.toString();
  }
  private static String details(String project,List<App.ProjectExportRow> rows){
    StringBuilder s=new StringBuilder(openSheet(10));int i=1;
    s.append("<row r=\"1\">");String[] headers={"Data","Progetto","Tipo","Persona","Ruolo alla data","Ruolo nel progetto","Ore","Tariffa euro/ora","Costo euro","Note"};
    for(int c=0;c<headers.length;c++)s.append(text(i,c,1,headers[c]));s.append("</row>");
    double hours=0,cost=0;
    for(App.ProjectExportRow r:rows){i++;s.append("<row r=\"").append(i).append("\">");
      s.append(num(i,0,2,r.day().toEpochDay()+25569)).append(text(i,1,0,project)).append(text(i,2,0,r.actor()));
      s.append(text(i,3,0,r.colleague())).append(text(i,4,0,r.role())).append(text(i,5,0,r.projectRole()));
      s.append(num(i,6,3,r.hours())).append(num(i,7,3,r.hourlyRate())).append(num(i,8,3,r.cost()));
      s.append(text(i,9,0,r.note())).append("</row>");hours+=r.hours();cost+=r.cost();
    }
    i+=2;s.append("<row r=\"").append(i).append("\">").append(text(i,5,1,"TOTALE"))
      .append(num(i,6,3,hours)).append(num(i,8,3,cost)).append("</row>");
    s.append("</sheetData><autoFilter ref=\"A1:J").append(Math.max(1,i-2)).append("\"/></worksheet>");return s.toString();
  }
  private record Key(YearMonth month,String person,String role) implements Comparable<Key>{
    public int compareTo(Key o){int cmp=month.compareTo(o.month);if(cmp!=0)return cmp;cmp=person.compareToIgnoreCase(o.person);return cmp!=0?cmp:role.compareToIgnoreCase(o.role);}
  }
  private static String summary(String project,LocalDate from,LocalDate to,List<App.ProjectExportRow> rows){
    TreeMap<Key,double[]> amounts=new TreeMap<>();for(App.ProjectExportRow r:rows){
      double[] value=amounts.computeIfAbsent(new Key(YearMonth.from(r.day()),r.colleague(),r.role()),k->new double[2]);
      value[0]+=r.hours();value[1]+=r.cost();
    }
    StringBuilder s=new StringBuilder(openSheet(5));int i=1;s.append("<row r=\"1\">");
    String[] headers={"Mese","Persona","Ruolo","Ore","Costo euro"};
    for(int c=0;c<headers.length;c++)s.append(text(i,c,1,headers[c]));s.append("</row>");
    double hours=0,cost=0;
    for(var e:amounts.entrySet()){i++;Key key=e.getKey();double[] sum=e.getValue();
      s.append("<row r=\"").append(i).append("\">");
      s.append(text(i,0,0,String.format("%02d/%04d",key.month().getMonthValue(),key.month().getYear())));
      s.append(text(i,1,0,key.person())).append(text(i,2,0,key.role()));
      s.append(num(i,3,3,sum[0])).append(num(i,4,3,sum[1])).append("</row>");hours+=sum[0];cost+=sum[1];
    }
    i+=2;s.append("<row r=\"").append(i).append("\">").append(text(i,2,1,"TOTALE"))
       .append(num(i,3,3,hours)).append(num(i,4,3,cost)).append("</row>");
    s.append("</sheetData></worksheet>");return s.toString();
  }
}
