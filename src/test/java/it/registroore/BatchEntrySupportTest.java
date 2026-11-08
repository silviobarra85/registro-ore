package it.registroore;

import java.time.LocalDate;
import java.util.List;

public final class BatchEntrySupportTest {
  private static void check(boolean condition,String description){
    if(!condition)throw new AssertionError(description);
  }
  public static void main(String[] args){
    LocalDate day=LocalDate.of(2026,11,12);
    App.Activity project=new App.Activity(1,"GATT","Ricerca su progetti finanziati","#dddddd");
    App.Activity meeting=new App.Activity(2,"Consiglio <DIETI>","Attività istituzionali e gestionali","#eeeeee");
    List<App.LogEntry> rows=List.of(
      new App.LogEntry(1,day,project,2.0,"Sperimentazione",null,null),
      new App.LogEntry(2,day,project,1.5,"Analisi",null,null),
      new App.LogEntry(3,day,meeting,3.0,"Riunione",null,null));
    check(Math.abs(BatchEntrySupport.activityHours(rows," gatt ")-3.5)<1e-9,"Ore attivita' sommate");
    check(Math.abs(BatchEntrySupport.total(rows)-6.5)<1e-9,"Totale giornaliero");
    String dayLabel=BatchEntrySupport.dayLabel(day,false,"GATT",rows);
    check(dayLabel.contains("3,5 h"),"Etichetta ore attivita' scelta");
    check(BatchEntrySupport.dayLabel(day,true,"",rows).contains("6,5 h"),"Totale quando non scelta");
    String tooltip=BatchEntrySupport.tooltip(day,rows,"GATT",2.0,false,"Pausa didattica");
    check(tooltip.contains("8,5 h")&&tooltip.contains("oltre 8 h")&&tooltip.contains("Sperimentazione"),"Tooltip con previsione e dettagli");
    check(tooltip.contains("&lt;DIETI&gt;"),"HTML escaped");
    check(BatchEntrySupport.tooltip(day,rows,"GATT",20.0,false,"").contains("supera 24 h"),"Vincolo fisico");
    check(BatchEntrySupport.mergeNotes(List.of("Analisi", "Sviluppo", "Analisi"),"Sviluppo")
      .equals("Analisi | Sviluppo"),"Note esistenti conservate senza duplicati");
    check(BatchEntrySupport.mergeNotes(List.of(""),"").isEmpty(),"Note vuote");
    System.out.println("OK: calendario multi-giorno, ore attivita', tooltip, note e limiti");
  }
}
