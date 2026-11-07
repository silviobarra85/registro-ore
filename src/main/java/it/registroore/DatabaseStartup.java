package it.registroore;

import javax.swing.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.prefs.Preferences;

/** Non modifica alcun database: controlli prima delle migrazioni e avviso su percorsi diversi. */
final class DatabaseStartup {
  static final Preferences PREFS=Preferences.userNodeForPackage(DatabaseStartup.class);
  record Status(Path path, int count, double hours, String check, Path latestBackup) {
    String describe(){return path + "\n  Registrazioni: " + count + " | Ore: " +
      String.format(java.util.Locale.ITALY,"%.2f",hours) + " | Integrità: " + check +
      "\n  Ultimo backup: " + (latestBackup==null?"non ancora presente":latestBackup.getFileName());}
  }
  static Status inspect(Path file) throws Exception {
    Path p=file.toAbsolutePath().normalize();
    if(!Files.isRegularFile(p) || Files.size(p)<100)throw new IllegalArgumentException("Database non trovato o vuoto: " + p);
    try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+p);Statement st=c.createStatement()) {
      st.execute("PRAGMA query_only=ON");st.execute("PRAGMA busy_timeout=5000");
      String result;
      try(ResultSet r=st.executeQuery("PRAGMA quick_check")){
        if(!r.next())throw new SQLException("SQLite non ha restituito la verifica di integrità");
        result=r.getString(1);
      }
      if(!"ok".equalsIgnoreCase(result))throw new SQLException("Database danneggiato (quick_check): " + result + "\n"+p);
      int count=0;double hours=0;
      try(ResultSet r=st.executeQuery("SELECT COUNT(*),COALESCE(SUM(hours),0) FROM entries")){
        if(r.next()){count=r.getInt(1);hours=r.getDouble(2);}
      }
      List<Path> backups=DatabaseBackups.files(p);
      return new Status(p,count,hours,result,backups.isEmpty()?null:backups.get(0));
    }
  }
  static Path verifyAndChoose(Path proposed) throws Exception {
    Status current=inspect(proposed);
    String previousText=PREFS.get("last_verified_db_path", "");
    Path previous=null;
    if(!previousText.isBlank())try{previous=Path.of(previousText).toAbsolutePath().normalize();}catch(Exception ignored){}
    // Prima installazione della verifica: segnala anche un DB alternativo in Application Support,
    // scenario ricorrente per chi conserva ancora db_path.txt sulla vecchia installazione.
    if(previous==null){
      Path standard=Path.of(System.getProperty("user.home"),"Library","Application Support","RegistroOre","data","registro_ore_2026.sqlite").toAbsolutePath().normalize();
      if(!standard.equals(current.path())&&Files.isRegularFile(standard)){
        try{
          Status alternative=inspect(standard);
          String msg="Sono stati trovati DUE database di Registro Ore.\nControlla i numeri prima di proseguire.\n\n"+
            "DATABASE INDICATO DALL'APP:\n"+current.describe()+"\n\n"+
            "DATABASE STANDARD:\n"+alternative.describe()+"\n\n"+
            "Nessun dato verrà cancellato o copiato.";
          Object[] options={"Apri quello standard","Mantieni quello indicato","Annulla"};
          int result=JOptionPane.showOptionDialog(null,msg,"Attenzione: due database",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,options,options[2]);
          if(result==0){persistMacConfigIfCurrent(current.path(),alternative.path());current=alternative;}
          else if(result!=1)throw new IllegalStateException("Avvio annullato per evitare un database errato.");
        }catch(SQLException ex){throw ex;}
      }
    }
    boolean switched=previous!=null&&!previous.equals(current.path());
    Status old=null;
    if(switched&&Files.isRegularFile(previous))try{old=inspect(previous);}catch(Exception ignored){}
    if(switched){
      String message="ATTENZIONE: Registro Ore sta per aprire un database diverso da quello verificato al precedente avvio.\n\n"+
          "DATABASE SELEZIONATO:\n"+current.describe()+"\n\n"+
          "DATABASE PRECEDENTE:\n"+(old==null?previous+" (non leggibile o non presente)":old.describe())+
          "\n\nNessun database verrà copiato o sovrascritto.";
      if(old!=null){
        Object[] options={"Apri precedente","Usa quello selezionato","Annulla"};
        int choice=JOptionPane.showOptionDialog(null,message,"Verifica database all'avvio",JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,null,options,options[2]);
        if(choice==0){persistMacConfigIfCurrent(current.path(),old.path());current=old;}
        else if(choice!=1)throw new IllegalStateException("Avvio annullato per proteggere il database.");
      }else{
        int choice=JOptionPane.showConfirmDialog(null,message,"Verifica database all'avvio",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        if(choice!=JOptionPane.OK_OPTION)throw new IllegalStateException("Avvio annullato per proteggere il database.");
      }
    }
    String remembered=PREFS.get("last_verified_db_path","");
    if(remembered.equals(current.path().toString())){
      int oldCount=PREFS.getInt("last_verified_entry_count",-1);
      if(oldCount>current.count()){
        String info="ATTENZIONE: il database contiene meno registrazioni dell'ultimo avvio verificato.\n\n"+
          "Conteggio precedente: "+oldCount+"\nConteggio attuale: "+current.count()+
          "\n\n"+current.describe()+"\n\nVerifica che sia il database corretto e controlla le copie di sicurezza.";
        int decision=JOptionPane.showConfirmDialog(null,info,"Registrazioni diminuite",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        if(decision!=JOptionPane.OK_OPTION)throw new IllegalStateException("Avvio annullato per verificare le registrazioni.");
      }
    }
    PREFS.put("last_verified_db_path",current.path().toString());
    PREFS.putInt("last_verified_entry_count",current.count());
    return current.path();
  }
  private static void persistMacConfigIfCurrent(Path original, Path chosen) throws Exception {
    Path settings=Path.of(System.getProperty("user.home"),"Library","Application Support","RegistroOre","config","db_path.txt");
    if(Files.isRegularFile(settings)){
      String old=Files.readString(settings).trim();
      if(old.equals(original.toString())){
        Files.writeString(settings,chosen.toString()+"\n",java.nio.charset.StandardCharsets.UTF_8,
          StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
      }
    }
  }
  static String report(Path db) throws Exception {
    Status s=inspect(db);
    return "CONTROLLO DATABASE - Registro Ore\n\n"+s.describe()+"\n\n"+
        "Percorso verificato al precedente avvio:\n"+PREFS.get("last_verified_db_path","non disponibile")+
        "\n\nNota: il conteggio include le registrazioni prive di ore.";
  }
}
