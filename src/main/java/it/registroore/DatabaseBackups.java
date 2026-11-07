package it.registroore;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** SQLite consistent snapshots of the ACTUAL opened database. Never back up a live DB with Files.copy(). */
final class DatabaseBackups {
  private static final String PREFIX = "registro_ore_";
  private static final String SUFFIX = ".sqlite";
  private static final int MAX_DAILY = 30;
  private DatabaseBackups() { }

  static Path folder(Path db) { return db.toAbsolutePath().normalize().getParent().resolve("backup_giornalieri"); }
  static Path restoreFolder(Path db) { return db.toAbsolutePath().normalize().getParent().resolve("backup_ripristini"); }
  static Path dailyFile(Path db) { return folder(db).resolve(PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + SUFFIX); }

  /** Refreshes today's copy at most once every ten minutes; snapshots are atomic and verified. */
  static synchronized Path daily(Path db, boolean force) throws Exception {
    Path dir=folder(db);Files.createDirectories(dir);
    Path lockPath=dir.resolve(".backup.lock");
    try(FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
      FileLock lock=channel.tryLock();if(lock==null)return dailyFile(db);
      try(lock){
        Path dest=dailyFile(db);
        if(!force&&Files.isRegularFile(dest)&&Files.size(dest)>0){
          long mins=java.time.Duration.between(Files.getLastModifiedTime(dest).toInstant(),java.time.Instant.now()).toMinutes();
          if(mins>=0&&mins<10)return dest;
        }
        snapshot(db,dest);
        prune(dir);
        return dest;
      }
    }
  }
  static Path preRestore(Path db) throws Exception {
    Path dir=restoreFolder(db);Files.createDirectories(dir);
    Path dest=dir.resolve("registro_prima_ripristino_"+java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))+".sqlite");
    snapshot(db,dest);return dest;
  }
  private static void snapshot(Path db,Path dest) throws Exception {
    if(!Files.isRegularFile(db))throw new IOException("Database non trovato: "+db);
    Files.createDirectories(dest.getParent());
    Path temp=dest.resolveSibling("."+dest.getFileName()+".incompleto_"+UUID.randomUUID());
    try {
      try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());Statement s=c.createStatement()){
        s.execute("PRAGMA busy_timeout=8000");
        String path=temp.toAbsolutePath().toString().replace("'","''");
        s.execute("VACUUM INTO '"+path+"'");
      }
      verify(temp);
      try {Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
      catch(AtomicMoveNotSupportedException ex){Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);}
    } finally {Files.deleteIfExists(temp);}
  }
  static void verify(Path snapshot) throws Exception {
    if(!Files.isRegularFile(snapshot)||Files.size(snapshot)<4096)throw new IOException("Copia di sicurezza vuota o non valida: "+snapshot);
    try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+snapshot.toAbsolutePath());Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA integrity_check")){
      if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IOException("Integrita SQLite non verificata: "+snapshot);
    }
  }
  private static void prune(Path dir) throws IOException {
    List<Path> files=filesIn(dir);
    for(int i=MAX_DAILY;i<files.size();i++)Files.deleteIfExists(files.get(i));
  }
  static List<Path> files(Path db) throws IOException {return filesIn(folder(db));}
  private static List<Path> filesIn(Path dir) throws IOException {
    List<Path> paths=new ArrayList<>();if(!Files.isDirectory(dir))return paths;
    try(DirectoryStream<Path> stream=Files.newDirectoryStream(dir,PREFIX+"????-??-??"+SUFFIX)){
      for(Path p:stream)if(Files.isRegularFile(p))paths.add(p);
    }
    paths.sort(Comparator.comparing((Path p)->p.getFileName().toString()).reversed());return paths;
  }
  /** Called ONLY after the application's main SQLite connection has been closed. */
  static void replaceAfterClose(Path current,Path backup) throws Exception {
    Path selected=backup.toAbsolutePath().normalize();
    if(!files(current).contains(selected))throw new SecurityException("Il backup scelto non appartiene all'elenco giornaliero del database attivo.");
    verify(selected);
    Path wal=Paths.get(current.toString()+"-wal");
    if(Files.exists(wal)&&Files.size(wal)>0)throw new IOException("File WAL ancora presente: chiudi le altre applicazioni che accedono al database.");
    Path temp=current.resolveSibling("."+current.getFileName()+".ripristino_"+UUID.randomUUID());
    try {
      Files.copy(selected,temp);
      verify(temp);
      try {Files.move(temp,current,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
      catch(AtomicMoveNotSupportedException ex){Files.move(temp,current,StandardCopyOption.REPLACE_EXISTING);}
    } finally{Files.deleteIfExists(temp);}
  }
}
