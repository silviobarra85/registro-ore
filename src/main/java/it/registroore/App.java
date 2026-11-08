package it.registroore;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;
import java.util.prefs.Preferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.List;
import java.util.stream.*;
import java.util.concurrent.*;
import java.awt.datatransfer.StringSelection;

/** Registro ore accademico multiannuale. Desktop Swing + SQLite su file. */
public final class App extends JFrame {
  private static final String VERSION = "3.11.1";
  private static final int DEFAULT_YEAR = 2026;
  private static final double YEAR_LIMIT = 1500.0;
  private static final String UNSET_ROLE = "Da impostare";
  private static final String[] ROLES = {UNSET_ROLE, "Ordinario", "Associato", "Ricercatore"};
  private static final Color NAVY = new Color(21, 36, 59), INK = new Color(34, 47, 65), BG = new Color(245, 248, 252), ORANGE = new Color(242, 153, 53);
  private static final Color RED = new Color(255, 228, 230), ALERT = new Color(255, 175, 175), SELECT_BG = new Color(47, 103, 179), LINE = new Color(221, 228, 237), WHITE = Color.WHITE;
  private static final String[] GROUPS = {"DIDATTICA FRONTALE", "DIDATTICA INTEGRATIVA", "PROGETTI", "ALTRE ATTIVITÀ", "ATTIVITÀ ORDINARIA"};
  private static final String[] MONTHS = {"Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"};
  private static final String[] SHORT_MONTHS = {"Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic"};
  private static final String[] DAYS = {"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"};
  private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALY);
  private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALY).withResolverStyle(ResolverStyle.STRICT);
  private final Store store;
  private final JPanel root = new JPanel(new BorderLayout());
  private final JPanel body = new JPanel(new BorderLayout());
  private final JLabel pageTitle = new JLabel();
  private final Map<String,JButton> navigation = new LinkedHashMap<>();
  private String page = "Calendario";
  private int selectedYear = DEFAULT_YEAR;
  private LocalDate chosen = LocalDate.of(DEFAULT_YEAR, 1, 1);
  private JComboBox<Integer> yearPicker;
  private JPanel sidebarView;
  private JScrollPane sidebarProjectsScroll;
  // Mantiene fermo il giorno visualizzato quando il Registro viene aggiornato dopo un salvataggio.
  // I pannelli vengono ricreati, ma si conserva il loro offset nel viewport.
  private JScrollPane registerScroll;
  private final Map<LocalDate,JPanel> registerDayCards = new HashMap<>();
  private record RegisterViewport(LocalDate anchorDay, int dayOffset, int oldScrollY) {}

  private RegisterViewport captureRegisterViewport() {
    if (!"Registro".equals(page) || registerScroll == null) return null;
    JViewport viewport = registerScroll.getViewport();
    int scrollY = viewport.getViewPosition().y;
    int height = viewport.getExtentSize().height;
    JPanel target = registerDayCards.get(chosen);
    LocalDate anchor = chosen;
    // Il giorno modificato e' l'ancora preferita, se e' visibile nello schermo attuale.
    if (target == null || target.getY() >= scrollY + height || target.getY() + target.getHeight() <= scrollY) {
      anchor = null;
      for (var entry : registerDayCards.entrySet()) {
        JPanel card = entry.getValue();
        if (card.getY() + card.getHeight() > scrollY && card.getY() < scrollY + height &&
          (anchor == null || entry.getKey().isBefore(anchor))) anchor = entry.getKey();
      }
      target = anchor == null ? null : registerDayCards.get(anchor);
    }
    return new RegisterViewport(anchor, target == null ? 0 : target.getY() - scrollY, scrollY);
  }

  // Calcolo isolato: se le altezze delle giornate prima dell'ancora cambiano,
  // compensa tale variazione preservando l'offset visivo del giorno selezionato.
  static int registerViewportTarget(int oldY, int anchorY, int anchorOffset, int maxScroll, boolean anchored) {
    long candidate = anchored ? (long)anchorY - anchorOffset : oldY;
    return (int)Math.max(0, Math.min((long)maxScroll, candidate));
  }

  private void restoreRegisterViewport(RegisterViewport saved) {
    if (saved == null || !"Registro".equals(page) || registerScroll == null) return;
    // Differire la posizione dopo il layout della nuova lista: Swing non conosce
    // l'altezza delle schede finche' la vista non e' stata misurata.
    JScrollPane scroll = registerScroll;
    JPanel anchor = saved.anchorDay() == null ? null : registerDayCards.get(saved.anchorDay());
    SwingUtilities.invokeLater(() -> {
      if (scroll != registerScroll || !"Registro".equals(page)) return;
      JViewport viewport = scroll.getViewport();
      Component view = viewport.getView();
      if (view == null) return;
      // Assicura un layout valido prima di leggere la posizione della scheda.
      view.setSize(Math.max(view.getWidth(), viewport.getExtentSize().width), view.getPreferredSize().height);
      if (view instanceof Container container) container.doLayout();
      int max = Math.max(0, view.getHeight() - viewport.getExtentSize().height);
      int y = registerViewportTarget(saved.oldScrollY(), anchor == null ? 0 : anchor.getY(), saved.dayOffset(), max, anchor != null);
      viewport.setViewPosition(new Point(0, y));
    });
  }

  private boolean sidebarCollapsed=Preferences.userNodeForPackage(App.class).getBoolean("sidebarCollapsed",false);
  private final Map<String,Boolean> openNavigationSections=new HashMap<>();
  private final Map<String,List<String>> sectionPages=new LinkedHashMap<>();

  private int projectTabIndex=0, teamTabIndex=0, profileTabIndex=0;
  private int selectedProjectId=-1;
  private boolean updatingYearPicker=false;
  private JLabel topYearTag;
  private final JLabel liveClock = new JLabel();
  private final JLabel profileHeader = new JLabel();
  private final JLabel lastSaveLabel = new JLabel();
  private javax.swing.Timer clockTimer;
  private ScheduledExecutorService dailyBackupExecutor;
  private final java.util.concurrent.atomic.AtomicBoolean restoringBackup=new java.util.concurrent.atomic.AtomicBoolean(false);
  private AnnualInfo annual = new AnnualInfo(DEFAULT_YEAR, "", UNSET_ROLE);
  private List<Activity> activities = List.of();
  private List<Category> categories = List.of();
  private Set<Integer> projectHistoryIds=Set.of();
  private Set<Integer> archivedIds=Set.of();
  private static final Preferences FILE_PREFERENCES=Preferences.userNodeForPackage(App.class).node("documenti");
  private List<LogEntry> logs = List.of();
  private List<Mission> missions = List.of();
  private Map<LocalDate,List<LogEntry>> byDate = Map.of();
  private Map<LocalDate,String> suspensions = Map.of();

  record Activity(int id, String name, String category, String color) { }
  record Category(String name,String kind,boolean system) { }
  record LogEntry(int id, LocalDate date, Activity activity, Double hours, String note, String source, Integer missionId) { }
  record CalendarRow(LocalDate date, LogEntry entry) { }
  record NoteRow(int id, String text) { }
  record SummaryRow(String label, double[] months, int kind) { }
  record AnnualInfo(int year, String note, String role) { }
  record LockedMonth(int year, int month, String reason, String lockedAt) { }
  record ProfileInfo(String name, String defaultRole) { }
  record ProjectHistory(String project, int year, double hours, String role, double cost) { }
  record ProjectBrand(String funding,String acronym,String fullName) { }
  record Mission(int id,String title,String type,String role,String location,LocalDate from,LocalDate to,
                 String conference,String paper,String doi,Integer projectId,String authorization,String notes) {
    @Override public String toString(){return title+" ("+from.format(DISPLAY)+" - "+to.format(DISPLAY)+")";}
  }
  record MissionDocument(int id,String filename,long size) { }
  record MissionChoice(Integer id,String text) { @Override public String toString(){return text;} }
  record LegacyMapping(String oldCategory,String newCategory,boolean review) { }
  record ProjectDetails(int activityId,String type,String funder,String code,String projectRole,Integer startYear,Integer startMonth,Integer endYear,Integer endMonth,String notes,LocalDate startDate,LocalDate endDate,boolean closed) { }
  record ProjectMonthLock(int activityId,int year,int month,String reason) { }
  record ProjectPerson(int id,int activityId,String name,String qualification,String projectRole,String notes) { }
  record PersonHours(int id,int personId,String name,LocalDate date,double hours,String note) { }
  record ProjectDocument(int id,int activityId,String filename,String description,Integer year,Integer month,long size,String createdAt) { }
  record OwnHours(LocalDate date,double hours) { }
  record RolePeriod(int id, Integer personId, LocalDate effective, String role) { }
  record ProjectExportRow(LocalDate day, String actor, String colleague, String role, String projectRole,
                          double hours, int hourlyRate, String note) {
    double cost(){return hours*hourlyRate;}
  }
  record ColleagueAssignment(int personId,String name,String project,String projectRole,
                             String roleAtYearEnd,double hours,double cost) { }
  record CostHours(double hours, double cost, double unpricedHours) { }
  record AuditRecord(long id,String when,String who,String event,String entity,String rowKey,String before,String after) { }

  private static int rate(String role) { return switch (role) { case "Ordinario" -> 81; case "Associato" -> 53; case "Ricercatore" -> 34; default -> 0; }; }
  private static String eur(double value) { return String.format(Locale.ITALY, "%,.2f €", value); } // kind: 0 attivita, 1 titolo, 2 subtotale, 3 totale

  public static void main(String[] args) {
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
    UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 13));
    UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 12));
    SwingUtilities.invokeLater(() -> {
      try {
        Path file = Paths.get(System.getProperty("registro.db", "data/registro_ore_2026.sqlite")).toAbsolutePath();
        if (!Files.isRegularFile(file)) throw new IOException("Database non trovato: " + file + "\nAvvia il programma dalla cartella del progetto, senza spostare il database.");
        // Il controllo è effettuato prima di qualsiasi migrazione o scrittura.
        file=DatabaseStartup.verifyAndChoose(file);
        new App(new Store(file)).setVisible(true);
      } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(null, "Impossibile avviare Registro Ore:\n"+ex.getMessage(), "Errore di avvio", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private App(Store db) throws SQLException {
    super("Registro Ore " + VERSION + " | Gestione progetti e annualità"); store = db;
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    try(InputStream icon=App.class.getResourceAsStream("/registro-ore-icon.png")){
      if(icon!=null)setIconImage(javax.imageio.ImageIO.read(icon));
    }catch(Exception ignored){}
    setSize(1460, 930); setMinimumSize(new Dimension(1050, 690)); setLocationRelativeTo(null);
    getContentPane().setBackground(BG);
    root.setBackground(BG); add(root);
    store.migrate();
    store.enableOrdinaryKind();
    store.migrateCategoriesV311();
    store.migrateMissionsV311();
    store.installSaveTracking();
    store.installAuditTracking();
    dailyBackupExecutor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"backup-giornalieri");t.setDaemon(true);return t;});
    dailyBackupExecutor.scheduleWithFixedDelay(()->{try{DatabaseBackups.daily(store.databasePath,false);}catch(Exception ex){System.err.println("Backup giornaliero non riuscito: "+ex.getMessage());}},0,10,TimeUnit.MINUTES);
    sidebarView=sidebar();root.add(sidebarView,BorderLayout.WEST);
    JPanel work = new JPanel(new BorderLayout()); work.setBackground(BG);
    work.add(topbar(),BorderLayout.NORTH);
    body.setBackground(BG);
    work.add(body,BorderLayout.CENTER);
    work.add(appFooter(),BorderLayout.SOUTH);
    root.add(work,BorderLayout.CENTER);
    reload();refreshSidebar();showPage("Calendario");
    clockTimer=new javax.swing.Timer(1000,e->{updateClock();updateLastSave();});clockTimer.start();updateClock();updateLastSave();
    addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e) {try{if(clockTimer!=null)clockTimer.stop();if(dailyBackupExecutor!=null)dailyBackupExecutor.shutdownNow();if(!restoringBackup.get())DatabaseBackups.daily(store.databasePath,true);store.close();}catch(Exception ex){System.err.println("Salvataggio copia alla chiusura: "+ex.getMessage());try{store.close();}catch(Exception ignored){}} } });
  }
  /** Barra laterale moderna: sezioni espandibili e modalità icone con menu al passaggio del mouse. */
  private void initNavigationSections(){
    if(!sectionPages.isEmpty())return;
    sectionPages.put("CALENDARIO E REGISTRO",List.of("Calendario","Registro","Cerca","Riepilogo"));
    sectionPages.put("RENDICONTAZIONE",List.of(CategoryTaxonomy.names()));
    sectionPages.put("GESTIONE",List.of("Attività","Archivio attività","Categorie","Guida alle categorie","Missioni","Sospensioni","Annualità","Profilo","Note"));
    sectionPages.put("SCHEDE PROGETTO",List.of());
    sectionPages.put("SISTEMA",List.of("Backup e ripristino","Storico modifiche","Stato database","Esporta CSV / Excel","Salva copia database"));
    for(String key:sectionPages.keySet())openNavigationSections.putIfAbsent(key,
        key.equals("CALENDARIO E REGISTRO")||key.equals("RENDICONTAZIONE")) ;
  }
  private List<Activity> availableProjects(){
    return activities.stream().filter(a->!archivedIds.contains(a.id())&&(isProject(a.category())||projectHistoryIds.contains(a.id())))
      .sorted(Comparator.comparing(Activity::name,String.CASE_INSENSITIVE_ORDER)).toList();
  }
  private static String defaultAcronym(String name){
    String text=name==null?"":name.trim();
    return text.replaceFirst("(?i)^PROGETTO\\s+","").trim();
  }
  private String projectNavLabel(Activity a) {
    try {
      ProjectDetails d=store.projectDetails(a.id());
      ProjectBrand b=store.projectBrand(a.id());
      String funding=b.funding().isBlank()?d.type():b.funding();
      String acronym=b.acronym().isBlank()?defaultAcronym(a.name()):b.acronym();
      String tag=funding.trim();
      if(tag.startsWith("[")&&tag.endsWith("]"))tag=tag.substring(1,tag.length()-1);
      return (tag.isBlank()?"":"["+tag+"] ")+acronym;
    } catch(SQLException e) { return a.name(); }
  }
  private static String projectFullTitle(Activity a,ProjectBrand b) {
    String acronym=b.acronym().isBlank()?defaultAcronym(a.name()):b.acronym();
    String full=b.fullName().isBlank()?a.name():b.fullName();
    return acronym.equalsIgnoreCase(full)?acronym:acronym+" — "+full;
  }
  private boolean groupContainsCurrentPage(String group){
    if("SCHEDE PROGETTO".equals(group))return page.equals("Scheda progetto");
    return sectionPages.getOrDefault(group,List.of()).contains(page);
  }
  private void openProject(int id){selectedProjectId=id;projectTabIndex=0;teamTabIndex=0;showPage("Scheda progetto");}
  private void navClick(String title){
    switch(title){
      case "Esporta CSV / Excel" -> exportRegister();
      case "Salva copia database" -> backup();
      case "Stato database" -> showDatabaseStatus();
      default -> showPage(title);
    }
  }
  private void showDatabaseStatus(){
    try{JTextArea box=new JTextArea(DatabaseStartup.report(store.databasePath),12,62);
      box.setEditable(false);box.setLineWrap(true);box.setWrapStyleWord(true);
      JOptionPane.showMessageDialog(this,new JScrollPane(box),"Verifica database",JOptionPane.INFORMATION_MESSAGE);
    }catch(Exception ex){error(ex);}
  }
  private JPanel sidebar(){
    initNavigationSections();navigation.clear();sidebarProjectsScroll=null;
    JPanel side=new JPanel(new BorderLayout());side.setBackground(NAVY);
    side.setPreferredSize(new Dimension(sidebarCollapsed?67:263,680));
    JPanel top=new JPanel(new BorderLayout());top.setBackground(NAVY);
    top.setBorder(new EmptyBorder(13,8,11,8));
    if(!sidebarCollapsed){
      JLabel brand=new JLabel("  REGISTRO ORE");brand.setFont(new Font("SansSerif",Font.BOLD,17));brand.setForeground(WHITE);
      top.add(brand,BorderLayout.CENTER);
    }
    JButton toggle=new JButton(NavigationIcons.sidebar(WHITE));toggle.setToolTipText(sidebarCollapsed?"Espandi barra laterale":"Nascondi barra laterale");
    toggle.setFocusPainted(false);
    toggle.setForeground(WHITE);toggle.setBackground(new Color(36,59,85));toggle.setOpaque(true);
    toggle.setBorder(new EmptyBorder(7,10,7,10));
    toggle.addActionListener(e->{sidebarCollapsed=!sidebarCollapsed;
      Preferences.userNodeForPackage(App.class).putBoolean("sidebarCollapsed",sidebarCollapsed);refreshSidebar();});
    top.add(toggle,sidebarCollapsed?BorderLayout.CENTER:BorderLayout.EAST);
    side.add(top,BorderLayout.NORTH);
    JPanel sections=new JPanel();sections.setBackground(NAVY);sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
    for(String group:sectionPages.keySet()){
      if(sidebarCollapsed){
        JButton icon=new JButton(NavigationIcons.section(group,groupContainsCurrentPage(group)?NAVY:WHITE));
        icon.setForeground(groupContainsCurrentPage(group)?NAVY:WHITE);
        icon.setBackground(groupContainsCurrentPage(group)?ORANGE:NAVY);icon.setOpaque(true);
        icon.setFocusPainted(false);icon.setToolTipText(group+" (clicca o passa sopra per aprire)");
        icon.setHorizontalAlignment(SwingConstants.CENTER);icon.setMaximumSize(new Dimension(65,57));
        icon.setBorder(new EmptyBorder(13,5,13,5));icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        Runnable popup=()->{if(!icon.isShowing())return;JPopupMenu menu=sectionMenu(group);
          if(menu.getComponentCount()>0)menu.show(icon,icon.getWidth()-2,0);};
        icon.addActionListener(e->popup.run());
        icon.addMouseListener(new MouseAdapter(){public void mouseEntered(MouseEvent e){
          SwingUtilities.invokeLater(()->{popup.run();});
        }});
        sections.add(icon);sections.add(Box.createVerticalStrut(5));
      }else{
        boolean open=openNavigationSections.getOrDefault(group,false);
        Color headingColor=groupContainsCurrentPage(group)?ORANGE:new Color(205,220,239);
        JButton header=new JButton("  "+group,NavigationIcons.header(group,open,headingColor));
        header.setIconTextGap(5);
        header.setHorizontalAlignment(SwingConstants.LEFT);header.setForeground(headingColor);
        header.setBackground(new Color(27,47,73));header.setOpaque(true);header.setFocusPainted(false);
        header.setFont(new Font("SansSerif",Font.BOLD,11));header.setBorder(new EmptyBorder(11,13,11,8));
        header.setMaximumSize(new Dimension(249,39));header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.addActionListener(e->{openNavigationSections.put(group,!open);refreshSidebar();});
        sections.add(header);
        if(open){
          List<String> items=sectionPages.get(group);
          for(String title:items){JButton btn=sidebarButton(sidebarCategoryLabel(title),true);btn.setMaximumSize(new Dimension(249,34));
            btn.setToolTipText(title);
            btn.setBorder(new EmptyBorder(8,30,8,6));btn.addActionListener(e->navClick(title));
            boolean active=page.equals(title);btn.setBackground(active?ORANGE:NAVY);btn.setForeground(active?NAVY:WHITE);
            sections.add(btn);navigation.put(title,btn);
          }
          if("SCHEDE PROGETTO".equals(group)){
            List<Activity> projects=availableProjects();
            for(Activity a:projects){JButton b=sidebarButton("• "+projectNavLabel(a),true);b.setMaximumSize(new Dimension(249,32));
              b.setBorder(new EmptyBorder(6,29,6,4));b.setFont(new Font("SansSerif",Font.PLAIN,11));
              b.setToolTipText(projectNavLabel(a)+" · "+a.name());b.addActionListener(e->openProject(a.id()));
              boolean active=page.equals("Scheda progetto")&&a.id()==selectedProjectId;
              b.setBackground(active?ORANGE:NAVY);b.setForeground(active?NAVY:WHITE);sections.add(b);
            }
            if(projects.isEmpty()){JLabel empty=sidebarHeader("Nessun progetto");sections.add(empty);}
          }
        }
        sections.add(Box.createVerticalStrut(7));
      }
    }
    JScrollPane navScroll=new JScrollPane(sections);navScroll.setBorder(null);
    navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    navScroll.getVerticalScrollBar().setUnitIncrement(16);navScroll.getViewport().setBackground(NAVY);
    sidebarProjectsScroll=navScroll;side.add(navScroll,BorderLayout.CENTER);
    if(!sidebarCollapsed){
      JPanel footer=new JPanel();footer.setLayout(new BoxLayout(footer,BoxLayout.Y_AXIS));footer.setBackground(NAVY);
      String activePath=store.databasePath.toAbsolutePath().normalize().toString();
      JTextArea db=new JTextArea("DB in uso: "+activePath);db.setEditable(false);db.setOpaque(false);
      db.setForeground(new Color(188,209,228));db.setFont(new Font("SansSerif",Font.PLAIN,10));
      db.setLineWrap(true);db.setWrapStyleWord(true);db.setToolTipText(activePath);
      db.setBorder(new EmptyBorder(8,12,4,8));db.setMaximumSize(new Dimension(255,65));db.setAlignmentX(Component.LEFT_ALIGNMENT);
      footer.add(db);
      JButton copy=sidebarButton("Copia percorso DB",true);copy.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(activePath),null));footer.add(copy);
      lastSaveLabel.setForeground(new Color(217,232,245));lastSaveLabel.setFont(new Font("SansSerif",Font.PLAIN,11));
      lastSaveLabel.setBorder(new EmptyBorder(6,12,6,5));lastSaveLabel.setMaximumSize(new Dimension(255,50));
      lastSaveLabel.setAlignmentX(Component.LEFT_ALIGNMENT);footer.add(lastSaveLabel);updateLastSave();
      side.add(footer,BorderLayout.SOUTH);
    } else {
      JButton dbIcon=new JButton(NavigationIcons.database(WHITE));dbIcon.setToolTipText("Database: "+store.databasePath+" | Apri stato database");
      dbIcon.setForeground(WHITE);dbIcon.setBackground(NAVY);dbIcon.setOpaque(true);
      dbIcon.addActionListener(e->showDatabaseStatus());
      side.add(dbIcon,BorderLayout.SOUTH);
    }
    return side;
  }
  private JPopupMenu sectionMenu(String group){
    JPopupMenu menu=new JPopupMenu();
    if("SCHEDE PROGETTO".equals(group)){
      for(Activity a:availableProjects()){JMenuItem item=new JMenuItem(projectNavLabel(a));item.addActionListener(e->openProject(a.id()));menu.add(item);}
      if(menu.getComponentCount()==0){JMenuItem empty=new JMenuItem("Nessun progetto");empty.setEnabled(false);menu.add(empty);}
    }else for(String title:sectionPages.get(group)){
      JMenuItem item=new JMenuItem(title);item.addActionListener(e->navClick(title));menu.add(item);
    }
    return menu;
  }
  private static String sidebarCategoryLabel(String title){
    if(title.equals(CategoryTaxonomy.INTEGRATIVE))return "Didattica integrativa";
    if(title.equals(CategoryTaxonomy.FUNDED))return "Progetti finanziati";
    if(title.equals(CategoryTaxonomy.ADMIN))return "Attività istituzionali";
    if(title.equals(CategoryTaxonomy.THIRD))return "Terza missione";
    if(title.equals(CategoryTaxonomy.TRAINING))return "Formazione e aggiornamento";
    return title;
  }
  private JButton sidebarButton(String title,boolean compact){
    JButton btn=new JButton(title);btn.setForeground(WHITE);btn.setBackground(NAVY);
    btn.setHorizontalAlignment(SwingConstants.LEFT);btn.setFont(new Font("SansSerif",compact?Font.PLAIN:Font.BOLD,compact?12:13));
    btn.setBorder(new EmptyBorder(7,18,7,8));btn.setFocusPainted(false);btn.setOpaque(true);
    btn.setMaximumSize(new Dimension(249,34));btn.setAlignmentX(Component.LEFT_ALIGNMENT);return btn;
  }
  private JLabel sidebarHeader(String name){JLabel l=new JLabel("  "+name);l.setForeground(new Color(177,200,225));l.setFont(new Font("SansSerif",Font.BOLD,11));l.setBorder(new EmptyBorder(6,5,7,0));l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
  private void refreshSidebar(){
    int oldScroll=sidebarProjectsScroll==null?0:sidebarProjectsScroll.getVerticalScrollBar().getValue();
    if(sidebarView!=null)root.remove(sidebarView);sidebarView=sidebar();root.add(sidebarView,BorderLayout.WEST);
    root.revalidate();root.repaint();
    SwingUtilities.invokeLater(()->{if(sidebarProjectsScroll!=null)sidebarProjectsScroll.getVerticalScrollBar().setValue(oldScroll);});
  }
  private JButton navAction(String s){JButton b=new JButton(s);b.setHorizontalAlignment(SwingConstants.LEFT);b.setForeground(WHITE);b.setBackground(new Color(39,58,83));b.setOpaque(true);b.setBorder(new EmptyBorder(13,20,13,10));b.setMaximumSize(new Dimension(215,45));b.setAlignmentX(Component.LEFT_ALIGNMENT);return b;}
  private JPanel topbar(){
    JPanel wrapper=new JPanel(new BorderLayout());
    JPanel brand=new JPanel(new BorderLayout(12,0));
    brand.setBackground(new Color(30,53,79));
    brand.setBorder(new CompoundBorder(new MatteBorder(0,0,3,0,ORANGE),new EmptyBorder(16,25,16,27)));
    JPanel heading=new JPanel();heading.setOpaque(false);heading.setLayout(new BoxLayout(heading,BoxLayout.Y_AXIS));
    JLabel eyebrow=new JLabel("REGISTRO ORE  /  DIDATTICA E PROGETTI");
    eyebrow.setForeground(new Color(190,210,230));eyebrow.setFont(new Font("SansSerif",Font.BOLD,11));
    heading.add(eyebrow);heading.add(Box.createVerticalStrut(5));
    profileHeader.setFont(new Font("SansSerif",Font.BOLD,24));profileHeader.setForeground(WHITE);
    heading.add(profileHeader);brand.add(heading,BorderLayout.WEST);
    JLabel versionTag=new JLabel("v"+VERSION);
    versionTag.setFont(new Font("SansSerif",Font.BOLD,12));versionTag.setForeground(new Color(212,225,238));
    brand.add(versionTag,BorderLayout.EAST);
    wrapper.add(brand,BorderLayout.NORTH);
    JPanel bar=new JPanel(new BorderLayout());bar.setBackground(WHITE);bar.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,LINE),new EmptyBorder(13,28,13,26)));
    pageTitle.setFont(new Font("SansSerif",Font.BOLD,25));pageTitle.setForeground(INK);bar.add(pageTitle,BorderLayout.WEST);
    JPanel years=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,0));years.setOpaque(false);
    liveClock.setFont(new Font("SansSerif", Font.BOLD, 12));liveClock.setForeground(new Color(56,82,108));
    liveClock.setBorder(new EmptyBorder(0,0,0,12));years.add(liveClock);
    topYearTag=label("ANNO SOLARE",11,true);years.add(topYearTag);
    yearPicker=new JComboBox<>();yearPicker.setPreferredSize(new Dimension(112,37));
    refreshYears();yearPicker.addActionListener(e->{Integer y=(Integer)yearPicker.getSelectedItem();if(!updatingYearPicker&&y!=null&&y!=selectedYear){try{selectedYear=y;chosen=LocalDate.of(y,1,1);reload();showPage(page);}catch(Exception x){error(x);}}});
    years.add(yearPicker);JButton more=action("+ Anno");more.addActionListener(e->newYearDialog());years.add(more);
    bar.add(years,BorderLayout.EAST);wrapper.add(bar,BorderLayout.SOUTH);return wrapper;}
  private JPanel appFooter(){
    JPanel foot=new JPanel(new BorderLayout());foot.setBackground(new Color(236,241,247));
    foot.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,LINE),new EmptyBorder(6,18,7,21)));
    JLabel copyright=new JLabel("© " + Year.now().getValue() + " Silvio Barra (DIETI-UNINA) & ChatGPT  ·  Registro Ore v" + VERSION);
    copyright.setFont(new Font("SansSerif",Font.PLAIN,10));
    copyright.setForeground(new Color(111,125,142));
    foot.add(copyright,BorderLayout.EAST);
    return foot;
  }
  private void refreshProfileHeader(){
    try {
      ProfileInfo info=store.getProfile();
      String display=info.name()==null?"":info.name().trim();
      profileHeader.setText(display.isBlank()?"Profilo da completare":display);
      profileHeader.setToolTipText(display.isBlank()?"Imposta il nome nella scheda Profilo":display);
    }catch(Exception ex){profileHeader.setText("Profilo non disponibile");}
  }
  private void updateLastSave(){
    try {
      String iso=store.lastSavedAt();
      String txt="Ultimo salvataggio: non ancora rilevato";
      if(iso!=null&&!iso.isBlank()) {
        Instant instant=Instant.parse(iso);
        txt="Ultimo salvataggio: "+instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"));
      }
      String value=txt.startsWith("Ultimo salvataggio: ")?txt.substring("Ultimo salvataggio: ".length()):txt;
      lastSaveLabel.setText("<html>Ultimo salvataggio:<br><b>"+value+"</b></html>");
      lastSaveLabel.setToolTipText(txt + " (ultima modifica confermata nel database; i backup sono separati)");
    }catch(Exception ex){lastSaveLabel.setText("Ultimo salvataggio: non disponibile");}
  }
  private void updateClock(){liveClock.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE dd/MM/uuuu  HH:mm:ss",Locale.ITALY)));}
  private void refreshYears() {
    if(yearPicker==null)return;
    updatingYearPicker=true;yearPicker.removeAllItems();
    try{for(int y:store.getYears())yearPicker.addItem(y);yearPicker.setSelectedItem(selectedYear);}catch(Exception ex){error(ex);}finally{updatingYearPicker=false;}
  }
  private void newYearDialog() {
    JTextField year=new JTextField(Integer.toString(selectedYear+1),8);
    JTextArea note=new JTextArea(4,35);note.setLineWrap(true);note.setWrapStyleWord(true);
    JPanel p=new JPanel(new BorderLayout(5,7));JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT));row.add(new JLabel("Nuova annualità (es. 2027):"));row.add(year);p.add(row,BorderLayout.NORTH);p.add(new JScrollPane(note),BorderLayout.CENTER);
    p.add(new JLabel("Nota iniziale (facoltativa). Le sospensioni si aggiungono per ogni anno."),BorderLayout.SOUTH);
    if(JOptionPane.showConfirmDialog(this,p,"Crea annualità",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){try{
      int y=Integer.parseInt(year.getText().trim());if(y<1900||y>2200)throw new IllegalArgumentException("Anno non valido.");
      store.addYear(y,note.getText());selectedYear=y;chosen=LocalDate.of(y,1,1);refreshYears();changed();
    }catch(Exception ex){error(ex);}}
  }
  private void reload() throws SQLException {
    categories=store.getCategories();activities=store.getActivities();projectHistoryIds=store.projectHistoryIds();archivedIds=store.getArchivedActivityIds();logs=store.getLogs(selectedYear);missions=store.getMissions();suspensions=store.getSuspensions(selectedYear);annual=store.getAnnual(selectedYear);
    refreshProfileHeader();updateLastSave();
    Map<LocalDate,List<LogEntry>> index=new HashMap<>();for(LogEntry e:logs)index.computeIfAbsent(e.date(), k->new ArrayList<>()).add(e);byDate=index;
  }
  private void changed() {
    RegisterViewport saved = captureRegisterViewport();
    try {reload();refreshSidebar();showPage(page);restoreRegisterViewport(saved);}
    catch(Exception ex){error(ex);}
  }
  private void showPage(String name){
    page=name;
    pageTitle.setText(CategoryTaxonomy.rule(name)!=null?name:switch(name){
      case "Calendario"->"Panoramica annuale";case "Registro"->"Registro mensile";case "Cerca"->"Ricerca registrazioni";
      case "Riepilogo"->"Riepilogo delle ore";case "Attività"->"Anagrafica delle attività";
      case "Archivio attività"->"Archivio delle attività";case "Categorie"->"Le nove categorie";
      case "Guida alle categorie"->"Guida alle categorie";case "Missioni"->"Missioni scientifiche e istituzionali";
      case "Sospensioni"->"Giorni di sospensione";case "Annualità"->"Annualità e mesi bloccati";
      case "Profilo"->"Profilo docente e costo orario";case "Scheda progetto"->"Scheda del progetto";
      case "Backup e ripristino"->"Backup automatici e ripristino";
      case "Storico modifiche"->"Cronologia delle modifiche";default->"Note e promemoria";});
    for(var n:navigation.entrySet()){boolean active=n.getKey().equals(name);n.getValue().setBackground(active?ORANGE:NAVY);n.getValue().setForeground(active?NAVY:WHITE);}
    if(name.equals("Scheda progetto")){Activity activeProject=activities.stream().filter(a->a.id()==selectedProjectId).findFirst().orElse(null);if(activeProject!=null)pageTitle.setText(projectNavLabel(activeProject));refreshSidebar();}
    JPanel current=CategoryTaxonomy.rule(name)!=null?categorySummary(name,CategoryTaxonomy.FUNDED.equals(name)):
      switch(name){case "Calendario"->calendar();case "Registro"->register();case "Cerca"->searchPage();
        case "Riepilogo"->summary();case "Backup e ripristino"->backupsPanel();case "Storico modifiche"->auditPanel();
        case "Attività"->activityPanel();case "Archivio attività"->archivePanel();case "Categorie"->categoryPanel();
        case "Guida alle categorie"->categoryGuidePanel();case "Missioni"->missionsPanel();
        case "Sospensioni"->holidayPanel();case "Annualità"->annualPanel();case "Profilo"->profilePanel();
        case "Scheda progetto"->projectPage();default->notesPanel();};
    body.removeAll();body.add(current,BorderLayout.CENTER);body.revalidate();body.repaint();
  }
  private static JPanel panel(LayoutManager layout) {JPanel p=new JPanel(layout);p.setBackground(BG);return p;}
  private static JLabel label(String text,int size,boolean bold){JLabel l=new JLabel(text);l.setFont(new Font("SansSerif",bold?Font.BOLD:Font.PLAIN,size));l.setForeground(INK);return l;}
  private static JButton action(String text){JButton b=new JButton(text);b.setBackground(ORANGE);b.setForeground(NAVY);b.setOpaque(true);b.setFocusPainted(false);b.setBorder(new CompoundBorder(new LineBorder(new Color(226,137,42),1,true),new EmptyBorder(9,14,9,14)));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;}
  private static JButton subtle(String text){JButton b=new JButton(text);b.setBackground(WHITE);b.setForeground(INK);b.setOpaque(true);b.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(9,13,9,13)));return b;}
  private static Color color(String hex) {try{return Color.decode(hex);}catch(Exception ex){return new Color(220,235,250);}}
  private static String fmt(Double value){if(value==null)return "—";if(value==Math.rint(value))return Long.toString(value.longValue());return String.format(Locale.ITALY,"%.2f",value).replaceAll("0+$","").replaceAll(",$","");}
  private static String fmt(double value){return fmt(Double.valueOf(value));}
  private static boolean weekend(LocalDate d){return d.getDayOfWeek().getValue()>=6;}
  private boolean blocked(LocalDate d){return weekend(d)||suspensions.containsKey(d);}
  private boolean validateSuspension(String category,Collection<LocalDate> dates,String note){
    return validateSuspension(category,dates,note,suspensions);
  }
  private boolean validateSuspension(String category,Collection<LocalDate> dates,String note,
                                     Map<LocalDate,String> suspended){
    var rule=CategoryTaxonomy.rule(category);
    if(rule==null)return true;
    List<LocalDate> blockedDays=dates.stream().filter(d->weekend(d)||suspended.containsKey(d)).sorted().toList();
    if(blockedDays.isEmpty())return true;
    if(rule.suspension()==CategoryTaxonomy.Suspension.ALLOW)return true;
    if(rule.suspension()==CategoryTaxonomy.Suspension.BLOCK){
      if(note.isBlank()){JOptionPane.showMessageDialog(this,
        "Didattica frontale durante sospensione/weekend: per una deroga documentata inserisci una motivazione nelle note.");return false;}
      return ask("Attenzione: "+blockedDays.size()+" giornate senza lezioni (es. "+blockedDays.get(0).format(DISPLAY)+").\n"+
        "La didattica frontale e' normalmente sospesa. Confermi una deroga autorizzata?\n"+
        "Motivazione: "+note+"\nLa motivazione sara' registrata.");
    }
    if(note.isBlank()){JOptionPane.showMessageDialog(this,
      "Per una commissione, didattica integrativa o altra attivita' in sospensione, specifica nelle note la prestazione svolta e perche' era consentita.");return false;}
    return ask("Verifica richiesta: "+blockedDays.size()+" date durante sospensione/weekend.\n"+
      "Confermi che l'attivita' e' stata effettivamente svolta ed era consentita?\n"+note);
  }

  private double totalOf(List<LogEntry> list){return list.stream().filter(e->e.hours()!=null).mapToDouble(e->e.hours()).sum();}
  private static String esc(String text){return text==null?"":text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

  private JPanel calendar() {
    JPanel container=panel(new BorderLayout(0,17));container.setBorder(new EmptyBorder(22,27,24,26));
    JPanel tiles=panel(new GridLayout(1,5,13,0));double total=totalOf(logs);int days=(int)logs.stream().filter(e->e.hours()!=null).map(LogEntry::date).distinct().count();
    double front=sumGroup("DIDATTICA FRONTALE"), integ=sumGroup("DIDATTICA INTEGRATIVA"), projects=sumGroup("PROGETTI");
    tiles.add(kpi("ORE TOTALI",fmt(total),"Anno " + selectedYear));tiles.add(kpi("DISPONIBILI",fmt(Math.max(0,YEAR_LIMIT-total)),"Limite " + fmt(YEAR_LIMIT) + " h"));tiles.add(kpi("DIDATTICA",fmt(front+integ),"Frontale + integrativa"));
    tiles.add(kpi("PROGETTI",fmt(projects),"Ore di ricerca e progetti"));tiles.add(kpi("GIORNI REGISTRATI",Integer.toString(days),"Almeno una voce con ore"));
    JPanel calendarHeader=panel(new BorderLayout(0,9));calendarHeader.add(tiles,BorderLayout.NORTH);
    long overtime=byDate.entrySet().stream().filter(x->totalOf(x.getValue())>8.000001).count();
    JLabel key=label("Giorni oltre 8 ore: "+overtime+"  •  ROSSO INTENSO = oltre 8 h/giorno  •  ROSA = weekend/pausa  •  LUCCHETTO = mese bloccato",12,true);
    key.setForeground(overtime>0?new Color(149,38,38):INK);calendarHeader.add(key,BorderLayout.SOUTH);
    container.add(calendarHeader,BorderLayout.NORTH);
    JPanel all=panel(new GridLayout(0,3,16,16));
    for(int month=1;month<=12;month++)all.add(monthCard(month));
    JScrollPane scroll=new JScrollPane(all);scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(20);scroll.getViewport().setBackground(BG);container.add(scroll,BorderLayout.CENTER);
    return container;
  }
  private JPanel kpi(String head,String value,String subtitle){JPanel card=new JPanel(new BorderLayout(0,9));card.setBackground(WHITE);card.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(17,17,17,13)));JLabel a=label(head,11,true);a.setForeground(new Color(96,113,132));card.add(a,BorderLayout.NORTH);JLabel b=label(value+" h",27,true);if(head.startsWith("GIORNI"))b.setText(value);card.add(b,BorderLayout.CENTER);JLabel c=label(subtitle,11,false);c.setForeground(new Color(118,129,147));card.add(c,BorderLayout.SOUTH);return card;}
  private JPanel monthCard(int m) {
    JPanel card=new JPanel(new BorderLayout(0,9));card.setBackground(WHITE);card.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(13,13,13,13)));
    double total=logs.stream().filter(e->e.date().getMonthValue()==m&&e.hours()!=null).mapToDouble(e->e.hours()).sum();
    boolean monthLocked=false;String lockReason="";
    try {monthLocked=store.isMonthLocked(selectedYear,m);if(monthLocked)for(LockedMonth lm:store.getLockedMonths(selectedYear))if(lm.month()==m)lockReason=lm.reason();}catch(Exception ex){error(ex);}
    JPanel header=new JPanel(new BorderLayout());header.setOpaque(false);JLabel title=label(MONTHS[m-1].toUpperCase(Locale.ITALY),14,true);title.setForeground(NAVY);
    JPanel headingLeft=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));headingLeft.setOpaque(false);headingLeft.add(title);
    if(monthLocked){JLabel lock=label("🔒 CHIUSO",11,true);lock.setForeground(new Color(187,32,44));lock.setToolTipText("Mese bloccato: "+lockReason);headingLeft.add(lock);}
    header.add(headingLeft,BorderLayout.WEST);
    JLabel hoursLabel=label(fmt(total)+" h",12,true);hoursLabel.setForeground(new Color(159,98,23));header.add(hoursLabel,BorderLayout.EAST);card.add(header,BorderLayout.NORTH);
    JPanel grid=new JPanel(new GridLayout(0,7,2,3));grid.setBackground(WHITE);
    for(String day:DAYS){JLabel d=label(day,10,true);d.setHorizontalAlignment(SwingConstants.CENTER);d.setForeground(new Color(109,123,138));grid.add(d);}
    LocalDate first=LocalDate.of(selectedYear,m,1);int off=first.getDayOfWeek().getValue()-1;
    for(int i=0;i<off;i++)grid.add(emptyDay());
    for(int day=1;day<=first.lengthOfMonth();day++){
      LocalDate dt=LocalDate.of(selectedYear,m,day);List<LogEntry> items=byDate.getOrDefault(dt,List.of());double hours=totalOf(items);
      boolean over8=hours>8.000001;
      JButton cell=new JButton("<html><center>"+day+(hours>0?"<br><font size='2'>"+(over8?"! ":"")+fmt(hours)+"h</font>":"<br>&nbsp;")+"</center></html>");
      cell.setFont(new Font("SansSerif",Font.PLAIN,11));cell.setMargin(new Insets(2,0,2,0));cell.setBorder(new LineBorder(dt.equals(chosen)?ORANGE:WHITE,dt.equals(chosen)?2:1,true));
      cell.setBackground(over8?ALERT:blocked(dt)?RED:hours>0?new Color(223,240,234):new Color(248,250,253));cell.setForeground(over8?new Color(146,18,28):INK);if(over8)cell.setFont(new Font("SansSerif",Font.BOLD,11));cell.setOpaque(true);
      String reason=suspensions.getOrDefault(dt,weekend(dt)?"Fine settimana":"");
      cell.setToolTipText(dt.format(DISPLAY)+" | "+fmt(hours)+" ore"+(over8?" | ATTENZIONE: oltre 8 ore!":"")+(reason.isBlank()?"":" | "+reason)+(monthLocked?" | MENSILITÀ BLOCCATA":""));
      cell.addActionListener(e->{chosen=dt;showPage("Registro");});grid.add(cell);
    }
    int used=(off+first.lengthOfMonth())%7;if(used!=0)for(int i=used;i<7;i++)grid.add(emptyDay());
    card.add(grid,BorderLayout.CENTER);return card;
  }
  private JPanel emptyDay(){JPanel p=new JPanel();p.setBackground(WHITE);return p;}

  /** Registro raggruppato: schede giornaliere invece di righe ripetute. */
  private JPanel register() {
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(19,24,22,24));
    JPanel top=panel(new BorderLayout());
    JPanel left=panel(new FlowLayout(FlowLayout.LEFT,9,0));left.add(label("Mensilità",13,true));
    JComboBox<String> months=new JComboBox<>(MONTHS);months.setSelectedIndex(chosen.getMonthValue()-1);months.setPreferredSize(new Dimension(152,36));
    months.addActionListener(e->{int m=months.getSelectedIndex()+1;if(m!=chosen.getMonthValue()){chosen=LocalDate.of(selectedYear,m,1);showPage("Registro");}});
    left.add(months);top.add(left,BorderLayout.WEST);
    JPanel registerButtons=panel(new FlowLayout(FlowLayout.RIGHT,7,0));
    JButton exportButton=subtle("Esporta mese / anno");exportButton.addActionListener(e->exportRegister());registerButtons.add(exportButton);
    JButton add=action("+ Nuova registrazione");add.addActionListener(e->entryEditor(null,chosen));registerButtons.add(add);
    JButton bulk=action("+ Più giorni");bulk.addActionListener(e->batchEntryEditor());registerButtons.add(bulk);
    top.add(registerButtons,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    boolean monthLocked=false;try{monthLocked=store.isMonthLocked(selectedYear,chosen.getMonthValue());}catch(Exception ex){error(ex);}
    final boolean locked=monthLocked;add.setEnabled(!locked); // "Più giorni" può operare su altri mesi aperti.
    JPanel days=new JPanel();days.setLayout(new BoxLayout(days,BoxLayout.Y_AXIS));days.setBackground(BG);
    registerDayCards.clear();
    LocalDate first=chosen.withDayOfMonth(1);
    for(int day=1;day<=first.lengthOfMonth();day++){
      LocalDate date=first.withDayOfMonth(day);List<LogEntry> daily=byDate.getOrDefault(date,List.of());
      JPanel card=new JPanel(new BorderLayout());card.setBackground(WHITE);
      card.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(0,0,4,0)));
      card.setAlignmentX(Component.LEFT_ALIGNMENT);
      boolean suspended=blocked(date);JPanel header=new JPanel(new BorderLayout(6,2));header.setBackground(suspended?RED:new Color(234,241,250));header.setBorder(new EmptyBorder(7,13,7,11));
      String title=date.format(DateTimeFormatter.ofPattern("EEEE dd MMMM",Locale.ITALY));
      JLabel dateName=label(title.substring(0,1).toUpperCase(Locale.ITALY)+title.substring(1),13,true);header.add(dateName,BorderLayout.WEST);
      JPanel headingActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,0));headingActions.setOpaque(false);
      double hoursToday=totalOf(daily);JLabel total=label(fmt(hoursToday)+" h"+(hoursToday>8.000001?"  •  ⚠ OLTRE 8 ORE":"")+(suspended?"  •  sospensione / weekend":"")+(locked?"  •  BLOCCATO":""),12,true);
      if(hoursToday>8.000001){total.setForeground(new Color(170,34,42));header.setBackground(ALERT);}
      headingActions.add(total);JButton plus=subtle("+ Attività");plus.setMargin(new Insets(1,6,1,6));plus.setEnabled(!locked);plus.addActionListener(e->{chosen=date;entryEditor(null,date);});headingActions.add(plus);
      header.add(headingActions,BorderLayout.EAST);card.add(header,BorderLayout.NORTH);
      JPanel entries=new JPanel();entries.setLayout(new BoxLayout(entries,BoxLayout.Y_AXIS));entries.setBackground(WHITE);
      if(daily.isEmpty()){JLabel empty=label("Nessuna attività registrata",12,false);empty.setBorder(new EmptyBorder(9,19,8,8));entries.add(empty);}
      for(LogEntry entry:daily){
        JPanel row=new JPanel(new BorderLayout(10,0));row.setBackground(WHITE);row.setBorder(new EmptyBorder(5,12,5,12));
        JPanel swatch=new JPanel();swatch.setPreferredSize(new Dimension(6,28));swatch.setBackground(color(entry.activity().color()));row.add(swatch,BorderLayout.WEST);
        String activityName=entry.activity().name();
        String restriction="";try{restriction=store.entryRestriction(entry.activity().id(),entry.date(),entry.activity().category());}catch(SQLException ex){restriction="Verifica blocco non riuscita: "+ex.getMessage();}
        final String lockReason=restriction;
        String suffix=lockReason.isBlank()?"":"   🔒 NON MODIFICABILE — "+lockReason;
        String missionTag=entry.missionId()==null?"":missions.stream().filter(m->m.id()==entry.missionId()).findFirst()
            .map(m->"   •   Missione: "+m.title()).orElse("   •   Missione collegata");
        JLabel description=label(activityName+"   •   "+entry.activity().category()+(entry.note().isBlank()?"":"   —   "+entry.note())+missionTag+suffix,12,false);
        description.setToolTipText(lockReason.isBlank()?activityName+" "+entry.note():"Blocco modifica: "+lockReason);
        if(!lockReason.isBlank()){row.setBackground(new Color(255,238,236));description.setForeground(new Color(157,35,48));}
        row.add(description,BorderLayout.CENTER);
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));tools.setOpaque(false);
        JLabel amount=label(fmt(entry.hours())+" h",14,true);amount.setOpaque(true);amount.setBackground(color(entry.activity().color()));amount.setBorder(new EmptyBorder(5,10,5,10));amount.setPreferredSize(new Dimension(64,29));tools.add(amount);
        JButton edit=subtle("Modifica"),delete=subtle("×");edit.setEnabled(!locked && lockReason.isBlank());delete.setEnabled(!locked && lockReason.isBlank());
        edit.setToolTipText(lockReason.isBlank()?"Modifica la registrazione":"Modifica disabilitata: "+lockReason);
        delete.setToolTipText(lockReason.isBlank()?"Elimina la registrazione":"Eliminazione disabilitata: "+lockReason);
        edit.addActionListener(e->{chosen=date;entryEditor(entry,date);});
        delete.addActionListener(e->{if(ask("Eliminare "+entry.activity().name()+" del "+date.format(DISPLAY)+"?")){try{store.deleteEntry(entry.id());chosen=date;changed();}catch(Exception ex){error(ex);}}});
        tools.add(edit);tools.add(delete);row.add(tools,BorderLayout.EAST);
        Runnable highlight=()->{for(Component component:entries.getComponents())if(component instanceof JPanel rr){rr.setBackground(WHITE);rr.setBorder(new EmptyBorder(5,12,5,12));}
          row.setBackground(new Color(216,232,255));row.setBorder(new CompoundBorder(new LineBorder(SELECT_BG,2,true),new EmptyBorder(3,10,3,10)));};
        MouseAdapter selectEntry=new MouseAdapter(){public void mouseClicked(MouseEvent event){highlight.run();}};
        row.addMouseListener(selectEntry);description.addMouseListener(selectEntry);amount.addMouseListener(selectEntry);
        edit.addActionListener(e->highlight.run());delete.addActionListener(e->highlight.run());
        entries.add(row);
      }
      card.add(entries,BorderLayout.CENTER);registerDayCards.put(date,card);days.add(card);days.add(Box.createVerticalStrut(7));
    }
    JScrollPane scroll=new JScrollPane(days);scroll.setBorder(null);scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(24);registerScroll=scroll;p.add(scroll,BorderLayout.CENTER);
    double monthHours=logs.stream().filter(e->e.date().getMonthValue()==chosen.getMonthValue()&&e.hours()!=null).mapToDouble(e->e.hours()).sum();
    p.add(label("Totale "+MONTHS[chosen.getMonthValue()-1]+": "+fmt(monthHours)+" ore"+(locked?"  •  MENSILITÀ BLOCCATA":"  •  Puoi aggiungere più attività in ogni giornata"),13,true),BorderLayout.SOUTH);
    return p;
  }
  private static JTable makeTable(AbstractTableModel model){JTable t=new JTable(model);t.setFont(new Font("SansSerif",Font.PLAIN,12));t.setRowHeight(30);t.setGridColor(new Color(234,239,246));t.setShowVerticalLines(false);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setRowSelectionAllowed(true);t.setSelectionBackground(SELECT_BG);t.setSelectionForeground(WHITE);t.setShowHorizontalLines(true);t.setGridColor(LINE);t.getTableHeader().setBackground(NAVY);t.getTableHeader().setForeground(WHITE);t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,12));t.getTableHeader().setPreferredSize(new Dimension(30,38));t.setFillsViewportHeight(true);return t;}
  private boolean ask(String text){return JOptionPane.showConfirmDialog(this,text,"Conferma",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.YES_OPTION;}
  private void error(Exception e){e.printStackTrace();JOptionPane.showMessageDialog(this,e.getMessage(),"Errore",JOptionPane.ERROR_MESSAGE);}

  private void entryEditor(LogEntry existing,LocalDate date) {
    try{
      if(existing!=null){String reason=store.entryRestriction(existing.activity().id(),existing.date(),existing.activity().category());if(!reason.isBlank()){JOptionPane.showMessageDialog(this,"Registrazione non modificabile:\n"+reason);return;}}
      if(store.isMonthLocked(date.getYear(),date.getMonthValue())){JOptionPane.showMessageDialog(this,"La mensilità è bloccata: nessuna modifica consentita.");return;}
    }catch(Exception ex){error(ex);return;}
    JPanel content=new JPanel(new GridBagLayout());GridBagConstraints gbc=new GridBagConstraints();gbc.gridx=0;gbc.gridy=0;gbc.insets=new Insets(7,8,7,8);gbc.anchor=GridBagConstraints.WEST;
    JTextField day=new JTextField(date.format(DISPLAY),16);
    JTextField a=new JTextField(existing!=null?existing.activity().name():"",24);a.setPreferredSize(new Dimension(335,32));
    JComboBox<String> group=new JComboBox<>(categoryNames());group.setPreferredSize(new Dimension(295,31));
    if(existing!=null)group.setSelectedItem(existing.activity().category());
    JPanel activitySearch=installActivitySuggestions(a,group);
    JTextField hours=new JTextField(existing!=null?fmt(existing.hours()).replace("—",""):"",16);
    JTextField note=new JTextField(existing==null?"":existing.note(),22);
    JComboBox<MissionChoice> mission=new JComboBox<>(missionChoices());
    if(existing!=null&&existing.missionId()!=null)selectMission(mission,existing.missionId());
    mission.setPreferredSize(new Dimension(340,32));
    field(content,gbc,"Data (gg/mm/aaaa)",day);field(content,gbc,"Attività (anche nuova)",activitySearch);
    field(content,gbc,"Categoria per nuova attività",group);field(content,gbc,"Ore (es. 2 o 1,5)",hours);
    field(content,gbc,"Missione (facoltativa)",mission);field(content,gbc,"Note facoltative",note);
    for(;;){int answer=JOptionPane.showConfirmDialog(this,content,existing==null?"Nuova registrazione":"Modifica registrazione",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(answer!=JOptionPane.OK_OPTION)return;
      try {
        LocalDate d=LocalDate.parse(day.getText().trim(),INPUT);if(d.getYear()!=selectedYear)throw new IllegalArgumentException("Usa una data dell’annualità selezionata: "+selectedYear);
        String name=a.getText().trim();if(name.isEmpty())throw new IllegalArgumentException("Inserisci il nome dell'attività.");
        if(name.equalsIgnoreCase("ATTIVITÀ SOSPESE"))throw new IllegalArgumentException("Per sospendere un giorno usa la sezione Sospensioni.");
        Double n=null;String raw=hours.getText().trim().replace(',','.');if(!raw.isEmpty()){n=Double.valueOf(raw);if(!Double.isFinite(n)||n<0||n>24)throw new IllegalArgumentException("Le ore devono essere comprese tra 0 e 24.");}
        Activity known=activities.stream().filter(x->x.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        if(known!=null&&archivedIds.contains(known.id()))throw new IllegalArgumentException("Questa attività è archiviata. Ripristinala da Gestione → Archivio attività prima di inserire nuove ore.");
        String category=known==null?(String)group.getSelectedItem():known.category();
        if(n!=null&&!validateSuspension(category,List.of(d),note.getText().trim()))continue;
        MissionChoice selectedMission=(MissionChoice)mission.getSelectedItem();
        store.saveEntry(existing==null?null:existing.id(),d,name,category,n,note.getText().trim(),
          selectedMission==null?null:selectedMission.id());chosen=d;changed();return;
      }catch(Exception e){error(e);}
    }
  }
  /**
   * Inserimento su più giornate. Finestra non modale: il Registro alle spalle rimane
   * consultabile e scorrevole. Il calendario mostra i dati dell'anno all'apertura,
   * aggiornabili con il pulsante dedicato dopo modifiche eseguite sul Registro.
   */
  private void batchEntryEditor(){
    final int batchYear=selectedYear; // Una finestra aperta mantiene la sua annualità.
    final SortedSet<LocalDate> selected=new TreeSet<>();
    final Map<LocalDate,String> batchSuspensions=new HashMap<>();
    try{batchSuspensions.putAll(store.getSuspensions(batchYear));}catch(SQLException ex){error(ex);return;}
    final Map<LocalDate,JToggleButton> dayButtons=new LinkedHashMap<>();
    final Map<LocalDate,Color> idleColors=new HashMap<>();
    final Map<LocalDate,Boolean> closedMonths=new HashMap<>();
    final Map<LocalDate,List<LogEntry>> previewRows=new HashMap<>();
    final JDialog dialog=new JDialog(this,"Più giorni — "+batchYear,false);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JPanel main=new JPanel(new BorderLayout(10,10));main.setBackground(BG);
    main.setBorder(new EmptyBorder(12,13,12,13));
    JTextField activityName=new JTextField(24);activityName.setPreferredSize(new Dimension(330,31));
    JComboBox<String> category=new JComboBox<>(categoryNames());category.setPreferredSize(new Dimension(285,32));
    JTextField hours=new JTextField("2",9),notes=new JTextField(23);
    JComboBox<MissionChoice> mission=new JComboBox<>(missionChoices());
    JPanel inputs=new JPanel(new GridBagLayout());inputs.setBackground(BG);
    GridBagConstraints gc=new GridBagConstraints();gc.gridx=0;gc.gridy=0;
    gc.insets=new Insets(3,7,3,7);gc.anchor=GridBagConstraints.WEST;
    field(inputs,gc,"Attività",installActivitySuggestions(activityName,category));
    field(inputs,gc,"Categoria (solo nuova attività)",category);
    field(inputs,gc,"Ore da aggiungere per giorno",hours);
    field(inputs,gc,"Note",notes);field(inputs,gc,"Missione (facoltativa)",mission);
    main.add(inputs,BorderLayout.NORTH);
    JPanel months=new JPanel(new GridLayout(0,2,9,9));months.setBackground(BG);
    final Map<Integer,JPanel> monthCards=new HashMap<>();
    JLabel counter=label("Selezionati: 0 giorni",13,true);
    for(int month=1;month<=12;month++){
      YearMonth ym=YearMonth.of(batchYear,month);
      JPanel box=new JPanel(new BorderLayout(0,5));box.setBackground(WHITE);
      monthCards.put(month,box);
      box.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(5,6,6,6)));
      final boolean monthClosed;
      try{monthClosed=store.isMonthLocked(batchYear,month);}catch(SQLException ex){error(ex);return;}
      JPanel title=new JPanel(new BorderLayout());title.setOpaque(false);
      title.add(label(MONTHS[month-1]+(monthClosed?"  🔒":""),12,true),BorderLayout.WEST);
      JButton week=subtle("Lun–Ven");week.setFont(new Font("SansSerif",Font.PLAIN,10));
      week.setMargin(new Insets(1,3,1,3));week.setEnabled(!monthClosed);
      title.add(week,BorderLayout.EAST);box.add(title,BorderLayout.NORTH);
      JPanel grid=new JPanel(new GridLayout(0,7,2,2));grid.setOpaque(false);
      for(String day:DAYS){JLabel head=new JLabel(day,SwingConstants.CENTER);
        head.setFont(new Font("SansSerif",Font.BOLD,10));grid.add(head);}
      int start=ym.atDay(1).getDayOfWeek().getValue()-1;
      for(int i=0;i<start;i++)grid.add(new JLabel(""));
      List<JToggleButton> monthDays=new ArrayList<>();
      for(int day=1;day<=ym.lengthOfMonth();day++){
        LocalDate date=ym.atDay(day);
        JToggleButton cell=new JToggleButton();cell.setFont(new Font("SansSerif",Font.PLAIN,10));
        cell.setMargin(new Insets(0,0,0,0));cell.setPreferredSize(new Dimension(44,35));
        cell.setFocusPainted(false);cell.setOpaque(true);
        cell.setContentAreaFilled(true);
        dayButtons.put(date,cell);idleColors.put(date,(weekend(date)||batchSuspensions.containsKey(date))?RED:WHITE);
        closedMonths.put(date,monthClosed);
        cell.addActionListener(e->{
          if(closedMonths.getOrDefault(date,false)){
            cell.setSelected(false);JOptionPane.showMessageDialog(dialog,"La mensilità è bloccata: non puoi aggiungere ore.");return;
          }
          if(cell.isSelected())selected.add(date);else selected.remove(date);
          counter.setText("Selezionati: "+selected.size()+" giorni");
          cell.setBackground(cell.isSelected()?new Color(176,221,252):idleColors.get(date));
          cell.setText(BatchEntrySupport.dayLabel(date,cell.isSelected(),activityName.getText(),previewRows.getOrDefault(date,List.of())));
        });
        monthDays.add(cell);grid.add(cell);
      }
      week.addActionListener(e->{for(int i=0;i<monthDays.size();i++){
        LocalDate d=ym.atDay(i+1);JToggleButton button=monthDays.get(i);
        if(!weekend(d)&&!batchSuspensions.containsKey(d)&&!button.isSelected())button.doClick();
      }});
      box.add(grid,BorderLayout.CENTER);months.add(box);
    }
    JScrollPane scroll=new JScrollPane(months);scroll.getVerticalScrollBar().setUnitIncrement(24);
    scroll.setPreferredSize(new Dimension(730,335));scroll.setBorder(null);
    main.add(scroll,BorderLayout.CENTER);
    JPanel footer=new JPanel(new BorderLayout(8,7));footer.setOpaque(false);
    JLabel warning=label("Il numero sotto il giorno indica le ore già presenti per l'attività scelta; passa il mouse per il dettaglio.",11,false);
    footer.add(warning,BorderLayout.NORTH);
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));actions.setOpaque(false);
    JButton reloadButton=subtle("Aggiorna calendario");
    JButton clear=subtle("Deseleziona"),cancel=subtle("Chiudi"),save=action("Aggiungi ore");
    final Runnable repaintPreview=()->{
      String requested=activityName.getText().trim();
      Double incoming=null;
      try{double parsed=Double.parseDouble(hours.getText().trim().replace(',','.'));
        if(Double.isFinite(parsed)&&parsed>0)incoming=parsed;
      }catch(NumberFormatException ignored){}
      for(var e:dayButtons.entrySet()){
        LocalDate date=e.getKey();JToggleButton cell=e.getValue();
        List<LogEntry> daily=previewRows.getOrDefault(date,List.of());
        double existing=BatchEntrySupport.activityHours(daily,requested);
        double dailyTotal=BatchEntrySupport.total(daily);
        Color idle=(weekend(date)||batchSuspensions.containsKey(date))?RED:existing>0?new Color(214,234,255):dailyTotal>0?new Color(238,246,255):WHITE;
        if(incoming!=null&&dailyTotal+incoming>24.000001)idle=ALERT;
        else if(incoming!=null&&dailyTotal+incoming>8.000001)idle=new Color(255,236,205);
        idleColors.put(date,idle);
        cell.setBackground(cell.isSelected()?new Color(176,221,252):idle);
        cell.setText(BatchEntrySupport.dayLabel(date,cell.isSelected(),requested,daily));
        cell.setToolTipText(BatchEntrySupport.tooltip(date,daily,requested,incoming,
          closedMonths.getOrDefault(date,false),batchSuspensions.getOrDefault(date,"")));
      }
    };
    final Runnable reloadPreview=()->{
      try{
        batchSuspensions.clear();batchSuspensions.putAll(store.getSuspensions(batchYear));
        previewRows.clear();
        for(LogEntry e:store.getLogs(batchYear))previewRows.computeIfAbsent(e.date(),k->new ArrayList<>()).add(e);
        repaintPreview.run();
      }catch(SQLException ex){error(ex);}
    };
    reloadButton.addActionListener(e->reloadPreview.run());
    DocumentListener livePreview=new DocumentListener(){
      private void update(){SwingUtilities.invokeLater(()->{if(dialog.isDisplayable())repaintPreview.run();});}
      public void insertUpdate(DocumentEvent e){update();}
      public void removeUpdate(DocumentEvent e){update();}
      public void changedUpdate(DocumentEvent e){update();}
    };
    activityName.getDocument().addDocumentListener(livePreview);
    hours.getDocument().addDocumentListener(livePreview);
    clear.addActionListener(e->{for(JToggleButton button:dayButtons.values())if(button.isSelected())button.doClick();});
    cancel.addActionListener(e->dialog.dispose());
    save.addActionListener(e->{try{
      String name=activityName.getText().trim();if(name.isEmpty())throw new IllegalArgumentException("Indica l'attività.");
      if(name.equalsIgnoreCase("ATTIVITÀ SOSPESE"))throw new IllegalArgumentException("Le sospensioni si gestiscono nella sezione Sospensioni.");
      if(selected.isEmpty())throw new IllegalArgumentException("Seleziona almeno un giorno.");
      double quantity=Double.parseDouble(hours.getText().trim().replace(',','.'));
      if(!Double.isFinite(quantity)||quantity<=0||quantity>24)
        throw new IllegalArgumentException("Ore per giorno: inserisci un valore maggiore di 0 e non superiore a 24.");
      Activity known=activities.stream().filter(a->a.name().equalsIgnoreCase(name)).findFirst().orElse(null);
      String group=known==null?(String)category.getSelectedItem():known.category();
      if(!validateSuspension(group,selected,notes.getText().trim(),batchSuspensions))return;
      // Il registro puo' essere modificato mentre la finestra e' aperta: ricaricare
      // i dati per la conferma e lasciare al database la validazione atomica.
      reloadPreview.run();
      long existingDays=selected.stream().filter(d->BatchEntrySupport.activityHours(previewRows.getOrDefault(d,List.of()),name)>0).count();
      long over8=selected.stream().filter(d->BatchEntrySupport.total(previewRows.getOrDefault(d,List.of()))+quantity>8.000001).count();
      String confirmation="Aggiungere "+fmt(quantity)+" h in "+selected.size()+" giorni ("+fmt(quantity*selected.size())+" h in totale)?";
      if(existingDays>0)confirmation+="\nIn "+existingDays+" giorni l'attività esiste già: le ore saranno sommate alle righe compatibili.";
      if(over8>0)confirmation+="\nAttenzione: "+over8+" giornate supereranno 8 ore.";
      confirmation+="\nRegistrazioni con missioni diverse restano separate per conservare la rendicontazione.";
      if(!ask(confirmation))return;
      MissionChoice selectedMission=(MissionChoice)mission.getSelectedItem();
      store.saveEntriesBatch(batchYear,new ArrayList<>(selected),name,group,quantity,notes.getText().trim(),
        selectedMission==null?null:selectedMission.id());
      if(selectedYear==batchYear)chosen=selected.first();
      dialog.dispose();changed();
      JOptionPane.showMessageDialog(this,"Ore aggiunte: "+selected.size()+" giornate, "+fmt(quantity*selected.size())+" h.\nLe registrazioni compatibili già presenti sono state aggiornate.");
    }catch(Exception ex){error(ex);reloadPreview.run();}});
    actions.add(counter);actions.add(reloadButton);actions.add(clear);actions.add(cancel);actions.add(save);
    footer.add(actions,BorderLayout.SOUTH);main.add(footer,BorderLayout.SOUTH);
    dialog.setContentPane(main);
    dialog.setSize(Math.min(800,Toolkit.getDefaultToolkit().getScreenSize().width-40),
      Math.min(740,Toolkit.getDefaultToolkit().getScreenSize().height-75));
    // Aprire a lato del Registro: la finestra principale continua a ricevere lo scroll.
    Rectangle bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    int x=Math.max(bounds.x+8,Math.min(bounds.x+bounds.width-dialog.getWidth()-12,getX()+getWidth()-dialog.getWidth()+55));
    int y=Math.max(bounds.y+8,Math.min(bounds.y+bounds.height-dialog.getHeight()-12,getY()+35));
    dialog.setLocation(x,y);
    reloadPreview.run();
    dialog.setVisible(true);
    SwingUtilities.invokeLater(()->{
      JPanel focusMonth=monthCards.get(chosen.getYear()==batchYear?chosen.getMonthValue():1);
      if(focusMonth!=null)scroll.getVerticalScrollBar().setValue(Math.max(0,focusMonth.getY()-10));
    });
  }

  /** Autocompletamento interno al dialogo: nessun popup, il campo mantiene sempre il focus. */
  private JPanel installActivitySuggestions(JTextField text,JComboBox<String> group){
    List<Activity> searchable=activities.stream().filter(x->!archivedIds.contains(x.id())&&!x.category().equals("SEGNAPOSTO SOSPENSIONE")).toList();
    JPanel box=new JPanel(new BorderLayout(0,5));box.setOpaque(false);
    box.add(text,BorderLayout.NORTH);
    DefaultListModel<String> model=new DefaultListModel<>();
    JList<String> suggestions=new JList<>(model);
    suggestions.setFont(new Font("SansSerif",Font.PLAIN,12));suggestions.setFixedCellHeight(22);
    suggestions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    suggestions.setFocusable(false); // Il clic non ruba il focus all'input del testo.
    JScrollPane scroll=new JScrollPane(suggestions);
    scroll.setPreferredSize(new Dimension(335,112));
    scroll.setBorder(new LineBorder(LINE));
    box.add(scroll,BorderLayout.CENTER);
    JLabel hint=label("Digita per filtrare • ↓ / ↑ per scegliere • Invio per completare",11,false);
    box.add(hint,BorderLayout.SOUTH);
    final boolean[] suppress={false};
    Runnable accept=()->{
      int index=suggestions.getSelectedIndex();
      if(index<0||index>=model.size())return;
      String chosenName=model.get(index);
      suppress[0]=true;
      try{text.setText(chosenName);text.setCaretPosition(chosenName.length());}
      finally{suppress[0]=false;}
      searchable.stream().filter(x->x.name().equalsIgnoreCase(chosenName)).findFirst().ifPresent(x->group.setSelectedItem(x.category()));
      model.clear();
      text.requestFocusInWindow();
    };
    Runnable update=()->{
      if(suppress[0])return;
      String input=text.getText().trim();
      model.clear();
      if(input.isEmpty())return;
      for(String value:filterActivityNames(searchable,input,12))model.addElement(value);
      searchable.stream().filter(x->x.name().equalsIgnoreCase(input)).findFirst().ifPresent(x->group.setSelectedItem(x.category()));
      if(model.size()>0)suggestions.setSelectedIndex(0);
      hint.setText(model.isEmpty()?"Nessuna corrispondenza: sarà creata una nuova attività (scegli il gruppo)":
        "Elenco filtrato • ↓ / ↑ per scegliere • Invio per completare");
    };
    text.getDocument().addDocumentListener(new DocumentListener(){
      public void insertUpdate(DocumentEvent e){SwingUtilities.invokeLater(update);}
      public void removeUpdate(DocumentEvent e){SwingUtilities.invokeLater(update);}
      public void changedUpdate(DocumentEvent e){SwingUtilities.invokeLater(update);}
    });
    suggestions.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
      int i=suggestions.locationToIndex(e.getPoint());
      if(i>=0&&suggestions.getCellBounds(i,i).contains(e.getPoint())){suggestions.setSelectedIndex(i);accept.run();}
    }});
    text.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0),"nextMatch");
    text.getActionMap().put("nextMatch",new AbstractAction(){public void actionPerformed(ActionEvent e){
      if(!model.isEmpty())suggestions.setSelectedIndex(Math.min(model.size()-1,suggestions.getSelectedIndex()+1));
    }});
    text.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,0),"previousMatch");
    text.getActionMap().put("previousMatch",new AbstractAction(){public void actionPerformed(ActionEvent e){
      if(!model.isEmpty())suggestions.setSelectedIndex(Math.max(0,suggestions.getSelectedIndex()-1));
    }});
    text.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"acceptMatch");
    text.getActionMap().put("acceptMatch",new AbstractAction(){public void actionPerformed(ActionEvent e){accept.run();}});
    text.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"clearMatch");
    text.getActionMap().put("clearMatch",new AbstractAction(){public void actionPerformed(ActionEvent e){model.clear();}});
    return box;
  }
  static List<String> filterActivityNames(List<Activity> available,String raw,int limit){
    String needle=raw.strip().toLowerCase(Locale.ITALY);
    if(needle.isEmpty())return List.of();
    return available.stream().filter(a->a.name().toLowerCase(Locale.ITALY).contains(needle))
      .sorted(Comparator.comparingInt((Activity a)->a.name().toLowerCase(Locale.ITALY).startsWith(needle)?0:1)
        .thenComparing(Activity::name,String.CASE_INSENSITIVE_ORDER))
      .limit(limit).map(Activity::name).toList();
  }
  private static void field(JPanel p,GridBagConstraints c,String title,JComponent field){c.gridx=0;c.weightx=0;c.fill=GridBagConstraints.NONE;p.add(label(title,12,true),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;p.add(field,c);c.gridy++;}
  private String categoryKind(String category){return categories.stream().filter(c->c.name().equals(category)).map(Category::kind).findFirst().orElse("ALTRE ATTIVITÀ");}
  private boolean isTeaching(String category){return CategoryTaxonomy.FRONT.equals(category);}
  private boolean isProject(String category){return categoryKind(category).equals(GROUPS[2]);}
  private String[] categoryNames(){return CategoryTaxonomy.names();}
  private double sumGroup(String kind){return logs.stream().filter(e->e.hours()!=null&&categoryKind(e.activity().category()).equals(kind)).mapToDouble(e->e.hours()).sum();}


  private int rateForPerson(Integer personId,LocalDate date){try{return store.hourlyRateAt(personId,date);}catch(Exception ex){return 0;}}
  private String roleForPerson(Integer personId,LocalDate date){try{return store.roleAt(personId,date);}catch(Exception ex){return UNSET_ROLE;}}

  /** Storico ruoli: la decorrenza è una data completa, non soltanto l'annualità. */
  private void roleHistoryDialog(Integer personId,String personName){
    JDialog dialog=new JDialog(this,"Decorrenze dei ruoli — "+personName,Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setSize(690,475);dialog.setLocationRelativeTo(this);
    JPanel surface=new JPanel(new BorderLayout(10,10));surface.setBackground(BG);surface.setBorder(new EmptyBorder(17,18,16,18));
    JLabel message=label("Ruolo valido dalla data indicata fino alla decorrenza successiva. Tariffe: Ordinario 81, Associato 53, Ricercatore 34 €/h.",12,false);
    surface.add(message,BorderLayout.NORTH);
    DefaultTableModel model=new DefaultTableModel(new Object[]{"In servizio dal","Ruolo","Costo €/h"},0){public boolean isCellEditable(int row,int col){return false;}};
    JTable table=makeTable(model);table.setRowHeight(35);
    Runnable refresh=()->{try{model.setRowCount(0);for(RolePeriod rp:store.roleHistory(personId))model.addRow(new Object[]{rp.effective().format(DISPLAY),rp.role(),rate(rp.role())});}catch(Exception ex){error(ex);}};
    refresh.run();surface.add(new JScrollPane(table),BorderLayout.CENTER);
    JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));buttons.setOpaque(false);
    JButton add=action("+ Nuova decorrenza"),edit=subtle("Modifica"),remove=subtle("Elimina"),close=subtle("Chiudi");
    buttons.add(add);buttons.add(edit);buttons.add(remove);buttons.add(close);surface.add(buttons,BorderLayout.SOUTH);
    add.addActionListener(e->{rolePeriodEditor(personId,null);refresh.run();});
    edit.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;try{List<RolePeriod> list=store.roleHistory(personId);rolePeriodEditor(personId,list.get(table.convertRowIndexToModel(r)));refresh.run();}catch(Exception ex){error(ex);}});
    remove.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;try{RolePeriod value=store.roleHistory(personId).get(table.convertRowIndexToModel(r));
      if(ask("Eliminare la decorrenza "+value.effective().format(DISPLAY)+"? Il costo delle ore precedenti potrebbe cambiare.")){store.deleteRolePeriod(personId,value.id());refresh.run();changed();}
    }catch(Exception ex){error(ex);}});
    close.addActionListener(e->dialog.dispose());dialog.add(surface);dialog.setVisible(true);
  }
  private void rolePeriodEditor(Integer personId,RolePeriod old){
    JPanel form=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.gridy=0;g.insets=new Insets(7,8,7,8);
    JTextField start=new JTextField(old==null?LocalDate.of(selectedYear,1,1).format(DISPLAY):old.effective().format(DISPLAY),13);
    JComboBox<String> role=new JComboBox<>(new String[]{"Ordinario","Associato","Ricercatore"});
    if(old!=null)role.setSelectedItem(old.role());
    field(form,g,"Decorrenza (gg/mm/aaaa)",start);field(form,g,"Ruolo",role);
    if(JOptionPane.showConfirmDialog(this,form,old==null?"Nuovo ruolo":"Modifica decorrenza",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
    try{LocalDate date=LocalDate.parse(start.getText().trim(),INPUT);String choice=(String)role.getSelectedItem();
      if(old==null)store.addRolePeriod(personId,date,choice);
      else store.updateRolePeriod(personId,old.id(),date,choice);
      changed();
    }catch(Exception ex){error(ex);}
  }

  /** Ricerca per attività e intervallo date: mostra tutte le annualità già salvate. */
  private JPanel searchPage(){
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(22,26,24,26));
    JPanel filters=panel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.gridy=0;g.insets=new Insets(6,8,6,8);
    JTextField query=new JTextField(25),from=new JTextField(LocalDate.of(selectedYear,1,1).format(DISPLAY),12),
      to=new JTextField(LocalDate.of(selectedYear,12,31).format(DISPLAY),12);
    field(filters,g,"Attività (anche nome parziale)",query);field(filters,g,"Dal (gg/mm/aaaa)",from);field(filters,g,"Al (gg/mm/aaaa)",to);
    JPanel top=panel(new BorderLayout());top.add(filters,BorderLayout.WEST);
    JPanel go=panel(new FlowLayout(FlowLayout.RIGHT,7,4));JButton find=action("Cerca"),reset=subtle("Ripristina");go.add(find);go.add(reset);top.add(go,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    DefaultTableModel model=new DefaultTableModel(new Object[]{"Data","Giorno","Attività","Categoria","Ore","Note","Anno"},0){public boolean isCellEditable(int r,int c){return false;}};
    JTable t=makeTable(model);t.setAutoCreateRowSorter(true);t.getColumnModel().getColumn(2).setPreferredWidth(270);t.getColumnModel().getColumn(3).setPreferredWidth(205);t.getColumnModel().getColumn(5).setPreferredWidth(260);
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    JLabel info=label("",13,true);p.add(info,BorderLayout.SOUTH);
    Runnable search=()->{try{LocalDate begin=LocalDate.parse(from.getText().trim(),INPUT),end=LocalDate.parse(to.getText().trim(),INPUT);
      if(end.isBefore(begin))throw new IllegalArgumentException("La data finale precede la data iniziale.");
      List<LogEntry> results=store.searchEntries(query.getText().trim(),begin,end);model.setRowCount(0);double sum=0;
      for(LogEntry v:results){model.addRow(new Object[]{v.date().format(DISPLAY),DAYS[v.date().getDayOfWeek().getValue()-1],v.activity().name(),v.activity().category(),fmt(v.hours()),v.note(),v.date().getYear()});if(v.hours()!=null)sum+=v.hours();}
      info.setText("Trovate "+results.size()+" registrazioni  •  "+fmt(sum)+" ore  •  periodo "+begin.format(DISPLAY)+" - "+end.format(DISPLAY));
    }catch(Exception ex){error(ex);}};
    find.addActionListener(e->search.run());query.addActionListener(e->search.run());from.addActionListener(e->search.run());to.addActionListener(e->search.run());
    reset.addActionListener(e->{query.setText("");from.setText(LocalDate.of(selectedYear,1,1).format(DISPLAY));to.setText(LocalDate.of(selectedYear,12,31).format(DISPLAY));search.run();});
    search.run();return p;
  }

  private JPanel summary() {
    JPanel p=panel(new BorderLayout(0,14));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel title=new JPanel(new BorderLayout());title.setOpaque(false);title.add(label("Ore per attività e mese • solo voci con ore positive",14,true),BorderLayout.WEST);
    JButton csv=action("Esporta CSV / Excel");csv.addActionListener(e->exportRegister());title.add(csv,BorderLayout.EAST);p.add(title,BorderLayout.NORTH);
    Map<String,Map<Integer,double[]>> totals=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    Map<Integer,String> activityNames=new HashMap<>();
    for(LogEntry e:logs)if(e.hours()!=null){
      String category=e.activity().category();
      double[] vals=totals.computeIfAbsent(category,k->new HashMap<>()).computeIfAbsent(e.activity().id(),k->new double[12]);
      vals[e.date().getMonthValue()-1]+=e.hours();activityNames.put(e.activity().id(),e.activity().name());
    }
    List<SummaryRow> rows=new ArrayList<>();double[] overall=new double[12];
    for(Category category:categories){
      Map<Integer,double[]> matching=totals.get(category.name());
      if(matching==null||matching.isEmpty())continue;
      rows.add(new SummaryRow(category.name(),new double[12],1));
      double[] categoryTotal=new double[12];
      List<Integer> ids=new ArrayList<>(matching.keySet());
      ids.sort(Comparator.comparing(id->activityNames.getOrDefault(id,""),String.CASE_INSENSITIVE_ORDER));
      for(int id:ids){
        double[] vals=matching.get(id);rows.add(new SummaryRow("   "+activityNames.getOrDefault(id,"Attività "+id),vals,0));
        for(int m=0;m<12;m++){categoryTotal[m]+=vals[m];overall[m]+=vals[m];}
      }
      rows.add(new SummaryRow("TOTALE "+category.name(),categoryTotal,2));
    }
    rows.add(new SummaryRow("TOTALE COMPLESSIVO",overall,3));
    String[] cols={"Attività","Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic","Totale"};
    JTable t=makeTable(new AbstractTableModel(){public int getRowCount(){return rows.size();}public int getColumnCount(){return cols.length;}public String getColumnName(int col){return cols[col];}
      public Object getValueAt(int r,int c){SummaryRow row=rows.get(r);if(c==0)return row.label();if(row.kind()==1||row.kind()==4)return "";double v=(c==13)?Arrays.stream(row.months()).sum():row.months()[c-1];return v==0?"":fmt(v);}});
    t.setRowHeight(32);t.getColumnModel().getColumn(0).setPreferredWidth(280);
    for(int i=1;i<14;i++)t.getColumnModel().getColumn(i).setPreferredWidth(i==13?75:60);
    t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    t.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable tb,Object val,boolean sel,boolean focus,int r,int c){super.getTableCellRendererComponent(tb,val,sel,focus,r,c);SummaryRow item=rows.get(r);setBorder(new EmptyBorder(1,9,1,9));
      Color bgColor=item.kind()==1?NAVY:item.kind()==3?new Color(255,203,126):item.kind()==2?new Color(255,231,187):item.kind()==4?new Color(227,239,252):item.kind()==5?new Color(238,247,251):r%2==0?WHITE:new Color(247,250,253);
      setBackground(sel?SELECT_BG:bgColor);setForeground(sel||item.kind()==1?WHITE:INK);setFont(new Font("SansSerif",item.kind()==0?Font.PLAIN:Font.BOLD,12));setHorizontalAlignment(c==0?SwingConstants.LEFT:SwingConstants.RIGHT);return this;}});
    JScrollPane scroll=new JScrollPane(t);scroll.setBorder(new LineBorder(LINE));p.add(scroll,BorderLayout.CENTER);
    p.add(label("Totale registrato: "+fmt(totalOf(logs))+" ore  •  Nove categorie con regole operative integrate.",13,true),BorderLayout.SOUTH);return p;
  }

  /** Scheda analitica della categoria: per progetti mostra anche la valorizzazione economica per anno. */
  private JPanel categorySummary(String category, boolean costs) {
    JPanel p=panel(new BorderLayout(0,13));p.setBorder(new EmptyBorder(22,27,24,27));
    int roleRate=rate(annual.role());
    Map<String,double[]> byName=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(LogEntry l:logs)if(l.hours()!=null&&l.activity().category().equals(category))
      byName.computeIfAbsent(l.activity().name(),k->new double[12])[l.date().getMonthValue()-1]+=l.hours();
    double overall=byName.values().stream().mapToDouble(x->Arrays.stream(x).sum()).sum();
    Map<String,double[]> costByName=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(LogEntry e:logs)if(e.hours()!=null&&isProject(e.activity().category()))
      costByName.computeIfAbsent(e.activity().name(),k->new double[12])[e.date().getMonthValue()-1]+=e.hours()*rateForPerson(null,e.date());
    double totalCost=costByName.values().stream().mapToDouble(x->Arrays.stream(x).sum()).sum();
    boolean hasUnrated=logs.stream().anyMatch(e->e.hours()!=null&&isProject(e.activity().category())&&rateForPerson(null,e.date())==0);
    JPanel banner=panel(new BorderLayout(8,8));
    banner.add(label("ANNO "+selectedYear+"  •  "+fmt(overall)+" ore"+(costs?("  •  "+eur(totalCost)+" valorizzati"+(hasUnrated?" • RUOLI DA COMPLETARE":"")):""),16,true),BorderLayout.WEST);
    JButton csv=action(costs?"Esporta ore di un progetto":"Esporta CSV / Excel");
    csv.addActionListener(e->{if(costs)exportProjectDialog(null);else exportRegister();});banner.add(csv,BorderLayout.EAST);p.add(banner,BorderLayout.NORTH);
    String[] cols=costs?new String[]{"Progetto","Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic","Ore","Costo €"}:
      new String[]{"Attività","Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic","Ore"};
    List<String> names=new ArrayList<>(byName.keySet());names.add("TOTALE");
    JTable table=makeTable(new AbstractTableModel(){public int getColumnCount(){return cols.length;}public int getRowCount(){return names.size();}public String getColumnName(int c){return cols[c];}
      public Object getValueAt(int r,int c){String name=names.get(r);double[] byMonth=names.get(r).equals("TOTALE")?new double[12]:byName.get(name);
        if(name.equals("TOTALE"))for(double[] v:byName.values())for(int i=0;i<12;i++)byMonth[i]+=v[i];
        if(c==0)return name;
        double total=Arrays.stream(byMonth).sum();
        if(costs&&c==14){double cost=0;if(name.equals("TOTALE"))for(double[] v:costByName.values())cost+=Arrays.stream(v).sum();else cost=Arrays.stream(costByName.getOrDefault(name,new double[12])).sum();return eur(cost);}
        if(c==13)return fmt(total);
        return byMonth[c-1]>0?fmt(byMonth[c-1]):"";
      }});
    table.setRowHeight(32);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.getColumnModel().getColumn(0).setPreferredWidth(300);
    for(int c=1;c<table.getColumnCount();c++)table.getColumnModel().getColumn(c).setPreferredWidth(costs&&c==14?115:72);
    table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int r,int c){super.getTableCellRendererComponent(t,v,sel,foc,r,c);setBorder(new EmptyBorder(2,10,2,10));setForeground(INK);setBackground(sel?SELECT_BG:r==names.size()-1?new Color(255,231,187):r%2==0?WHITE:new Color(247,250,253));if(sel)setForeground(WHITE);setFont(new Font("SansSerif",r==names.size()-1?Font.BOLD:Font.PLAIN,12));setHorizontalAlignment(c==0?SwingConstants.LEFT:SwingConstants.RIGHT);return this;}});
    JScrollPane sc=new JScrollPane(table);sc.setBorder(new LineBorder(LINE));
    if(!costs){p.add(sc,BorderLayout.CENTER);p.add(label("Valori mensili e annuali in ore, senza valorizzazione economica.",12,false),BorderLayout.SOUTH);return p;}
    List<ProjectHistory> history;
    try{history=store.getProjectHistory();}catch(Exception ex){error(ex);history=List.of();}
    final List<ProjectHistory> h=history;
    JTable historical=makeTable(new AbstractTableModel(){public int getRowCount(){return h.size();}public int getColumnCount(){return 5;}public String getColumnName(int c){return new String[]{"Progetto (tutte le annualità)","Anno","Ore","Ruolo","Costo €"}[c];}
      public Object getValueAt(int r,int c){ProjectHistory x=h.get(r);return switch(c){case 0->x.project();case 1->x.year();case 2->fmt(x.hours());case 3->x.role();default->eur(x.cost());};}});
    historical.setRowHeight(29);historical.getColumnModel().getColumn(0).setPreferredWidth(350);
    historical.getColumnModel().getColumn(1).setPreferredWidth(70);
    historical.getColumnModel().getColumn(2).setPreferredWidth(105);
    historical.getColumnModel().getColumn(3).setPreferredWidth(140);
    historical.getColumnModel().getColumn(4).setPreferredWidth(145);
    String[] moneyCols={"Progetto","Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic","Totale €"};
    JTable money=makeTable(new AbstractTableModel(){public int getRowCount(){return names.size();}public int getColumnCount(){return moneyCols.length;}public String getColumnName(int c){return moneyCols[c];}
      public Object getValueAt(int r,int c){String name=names.get(r);double[] byMonth=name.equals("TOTALE")?new double[12]:byName.get(name);
        if(name.equals("TOTALE"))for(double[] values:byName.values())for(int i=0;i<12;i++)byMonth[i]+=values[i];
        if(c==0)return name;
        double[] pricing=name.equals("TOTALE")?new double[12]:costByName.getOrDefault(name,new double[12]);
        if(name.equals("TOTALE"))for(double[] values:costByName.values())for(int i=0;i<12;i++)pricing[i]+=values[i];
        double v=c==13?Arrays.stream(pricing).sum():pricing[c-1];return v==0?"":eur(v);}});
    money.setRowHeight(32);money.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);money.getColumnModel().getColumn(0).setPreferredWidth(300);
    for(int c=1;c<money.getColumnCount();c++)money.getColumnModel().getColumn(c).setPreferredWidth(c==13?125:100);
    money.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean f,int r,int c){super.getTableCellRendererComponent(t,v,sel,f,r,c);setBorder(new EmptyBorder(1,9,1,9));setForeground(INK);setBackground(sel?SELECT_BG:r==names.size()-1?new Color(255,231,187):r%2==0?WHITE:new Color(247,250,253));if(sel)setForeground(WHITE);setHorizontalAlignment(c==0?SwingConstants.LEFT:SwingConstants.RIGHT);return this;}});
    JTabbedPane tabs=new JTabbedPane();tabs.addTab("Ore per progetto / mese",sc);tabs.addTab("Costi per progetto / mese",new JScrollPane(money));tabs.addTab("Storico pluriennale",new JScrollPane(historical));p.add(tabs,BorderLayout.CENTER);
    p.add(label("Valorizzazione: ogni giornata usa il ruolo effettivo alla sua data. Ruoli mancanti: costo 0 (da verificare).",12,false),BorderLayout.SOUTH);
    return p;
  }

  /** Scheda individuale di progetto, condivisa tra annualità. */
  private JPanel projectPage() {
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(17,23,21,23));
    Activity project=activities.stream().filter(a->a.id()==selectedProjectId&&(isProject(a.category())||projectHistoryIds.contains(a.id()))).findFirst().orElse(null);
    if(project==null){p.add(label("Seleziona un progetto dall'elenco a sinistra.",16,true));return p;}
    List<ProjectPerson> people;List<PersonHours> colleagueHours;ProjectDetails details;
    try{people=store.projectPeople(project.id());colleagueHours=store.projectHours(project.id(),selectedYear);details=store.projectDetails(project.id());}
    catch(Exception ex){error(ex);return p;}
    double own=logs.stream().filter(e->e.activity().id()==project.id()&&e.hours()!=null&&isProject(e.activity().category())).mapToDouble(e->e.hours()).sum();
    double team=colleagueHours.stream().mapToDouble(PersonHours::hours).sum();
    double personalCost=0,teamCost=0;try {
      for(LogEntry e:logs)if(e.activity().id()==project.id()&&e.hours()!=null&&isProject(e.activity().category()))personalCost+=store.hourlyRateAt(null,e.date())*e.hours();
      for(PersonHours e:colleagueHours)teamCost+=store.hourlyRateAt(e.personId(),e.date())*e.hours();
    }catch(Exception ex){error(ex);}
    JPanel banner=panel(new BorderLayout(10,8));
    JLabel title=label(projectFullTitle(project,storeProjectBrand(project.id())),19,true);title.setOpaque(true);title.setBackground(color(project.color()));title.setBorder(new EmptyBorder(8,13,8,13));banner.add(title,BorderLayout.WEST);
    banner.add(label(selectedYear+"  •  Tue ore: "+fmt(own)+"  •  Colleghi: "+fmt(team)+"  •  Totale squadra: "+fmt(own+team),14,true),BorderLayout.EAST);
    JPanel bannerWithCosts=panel(new BorderLayout(0,4));bannerWithCosts.add(banner,BorderLayout.NORTH);
    bannerWithCosts.add(label("Costo valorizzato: le tue ore "+eur(personalCost)+"  •  colleghi "+eur(teamCost)+"  •  totale squadra "+eur(personalCost+teamCost)+" (solo ruoli noti)",12,true),BorderLayout.SOUTH);
    JButton exportProject=action("Esporta ore progetto (CSV / Excel)");
    exportProject.addActionListener(e->exportProjectDialog(project));
    JPanel projectButtons=panel(new FlowLayout(FlowLayout.RIGHT,7,0));projectButtons.add(exportProject);
    if(!archivedIds.contains(project.id())){
      JButton archiveProject=subtle("Archivia progetto");projectButtons.add(archiveProject);
      archiveProject.addActionListener(e->{if(ask("Archiviare «"+projectNavLabel(project)+"»? Le ore, gli allegati e i colleghi saranno conservati; il progetto sparirà dalla barra laterale."))
        try{store.setActivityArchived(project.id(),true);selectedProjectId=-1;page="Archivio attività";changed();}catch(Exception ex){error(ex);}
      });
    } else {
      JButton restoreProject=subtle("Ripristina progetto");projectButtons.add(restoreProject);
      restoreProject.addActionListener(e->{try{store.setActivityArchived(project.id(),false);changed();}catch(Exception ex){error(ex);}});
    }
    JButton deleteProject=subtle("Elimina progetto");projectButtons.add(deleteProject);
    deleteProject.addActionListener(e->{if(ask("Eliminare definitivamente il progetto? L'operazione sarà rifiutata se ci sono ore, allegati, colleghi o blocchi. Usa Archivia per conservarne lo storico."))
      try{store.deleteActivity(project.id());selectedProjectId=-1;page="Archivio attività";changed();}catch(Exception ex){error(ex);}
    });
    bannerWithCosts.add(projectButtons,BorderLayout.EAST);
    p.add(bannerWithCosts,BorderLayout.NORTH);
    JTabbedPane tabs=new JTabbedPane();tabs.setFont(new Font("SansSerif",Font.BOLD,13));
    tabs.addTab("Informazioni",projectInfoPanel(project,details));
    tabs.addTab("Colleghi e ore",projectPeoplePanel(project,people,colleagueHours,own));
    tabs.addTab("Calendario "+selectedYear,projectCalendar(project,people,colleagueHours));
    tabs.addTab("Documenti allegati",projectFilesPanel(project));
    tabs.addTab("Blocchi mensili",projectLocksPanel(project));
    tabs.setSelectedIndex(Math.min(projectTabIndex,tabs.getTabCount()-1));
    tabs.addChangeListener(e->projectTabIndex=tabs.getSelectedIndex());
    p.add(tabs,BorderLayout.CENTER);return p;
  }
  private static String monthYear(Integer month,Integer year){return month==null||year==null?"":String.format("%02d/%04d",month,year);}
  private static YearMonth parseMonthYear(String raw){String s=raw.trim();if(s.isBlank())return null;try{
    return YearMonth.parse(s,DateTimeFormatter.ofPattern("MM/uuuu",Locale.ITALY).withResolverStyle(ResolverStyle.STRICT));
  }catch(Exception ex){throw new IllegalArgumentException("Periodo non valido: "+raw+". Usa mm/aaaa (es. 02/2026).");}}
  private static LocalDate parseOptionalDate(String value) {
    String v=value.trim();return v.isEmpty()?null:LocalDate.parse(v,INPUT);
  }
  private ProjectBrand storeProjectBrand(int id){
    try{return store.projectBrand(id);}catch(SQLException e){return new ProjectBrand("","","");}
  }
  private JPanel projectInfoPanel(Activity a,ProjectDetails d){
    JPanel outer=panel(new BorderLayout(0,12));outer.setBorder(new EmptyBorder(16,17,12,17));
    JPanel form=panel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.insets=new Insets(8,10,8,10);g.anchor=GridBagConstraints.WEST;
    ProjectBrand brand=storeProjectBrand(a.id());
    JTextField activityName=new JTextField(a.name(),35);
    JTextField funding=new JTextField(brand.funding().isBlank()?d.type():brand.funding(),35);
    JTextField acronym=new JTextField(brand.acronym().isBlank()?defaultAcronym(a.name()):brand.acronym(),35);
    JTextField fullName=new JTextField(brand.fullName().isBlank()?a.name():brand.fullName(),35);
    JTextField type=new JTextField(d.type(),35),funder=new JTextField(d.funder(),35),code=new JTextField(d.code(),35),projectRole=new JTextField(d.projectRole(),35);
    JTextField start=new JTextField(d.startDate()==null?"":d.startDate().format(DISPLAY),15);
    JTextField end=new JTextField(d.endDate()==null?"":d.endDate().format(DISPLAY),15);
    JCheckBox closed=new JCheckBox("Progetto CHIUSO: impedisci nuove ore e modifiche alle ore del progetto",d.closed());
    closed.setOpaque(false);
    JTextArea notes=new JTextArea(d.notes(),9,48);notes.setLineWrap(true);notes.setWrapStyleWord(true);notes.setFont(new Font("SansSerif",Font.PLAIN,13));
    field(form,g,"Nome attività nel registro",activityName);
    field(form,g,"Finanziamento (es. FRA 2022)",funding);
    field(form,g,"Acronimo (es. GATT)",acronym);
    field(form,g,"Nome completo progetto",fullName);
    field(form,g,"Tipologia (PRIN, PNRR, europeo...)",type);field(form,g,"Ente finanziatore / committente",funder);
    field(form,g,"Codice / CUP / identificativo",code);field(form,g,"Tuo ruolo nel progetto",projectRole);
    field(form,g,"Inizio progetto (gg/mm/aaaa)",start);field(form,g,"Fine progetto (gg/mm/aaaa, inclusa)",end);
    field(form,g,"Stato del progetto",closed);
    field(form,g,"Note, obiettivi e altre informazioni",new JScrollPane(notes));
    JButton save=action("Salva informazioni e stato del progetto");g.gridx=1;g.weightx=0;g.fill=GridBagConstraints.NONE;form.add(save,g);
    save.addActionListener(e->{try{
      LocalDate startDay=parseOptionalDate(start.getText()),endDay=parseOptionalDate(end.getText());
      if(startDay!=null&&endDay!=null&&endDay.isBefore(startDay))throw new IllegalArgumentException("La fine del progetto precede l'inizio.");
      if(closed.isSelected()&&!d.closed()&&!ask("Chiudere definitivamente il progetto? Non sarà più possibile inserire, modificare o cancellare ore (anche dei colleghi) finché non lo riapri."))return;
      if(!closed.isSelected()&&d.closed()&&!ask("Riaprire il progetto? Resta comunque il limite delle date e dei mesi bloccati."))return;
      if(!activityName.getText().trim().equals(a.name())&&store.hasLockedEntries(a.id())&&
        !ask("Questa attività compare in timesheet bloccati. La rinomina cambierà il nome mostrato nei riepiloghi storici, ma non le ore. Confermi?"))return;
      store.saveProjectProfile(new ProjectDetails(a.id(),type.getText().trim(),funder.getText().trim(),code.getText().trim(),projectRole.getText().trim(),
        startDay==null?null:startDay.getYear(),startDay==null?null:startDay.getMonthValue(),
        endDay==null?null:endDay.getYear(),endDay==null?null:endDay.getMonthValue(),notes.getText().trim(),startDay,endDay,closed.isSelected()),
        activityName.getText().trim(),funding.getText().trim(),acronym.getText().trim(),fullName.getText().trim());
      projectTabIndex=0;changed();JOptionPane.showMessageDialog(this,"Informazioni e stato del progetto salvati.");
    }catch(Exception ex){error(ex);}});
    JScrollPane sc=new JScrollPane(form);sc.setBorder(null);sc.getViewport().setBackground(BG);outer.add(sc,BorderLayout.CENTER);
    outer.add(label("Le date valgono per tutte le annualità. Dopo la data finale non sono ammessi nuovi timesheet; chiudendo il progetto blocchi tutte le modifiche alle ore.",12,false),BorderLayout.SOUTH);return outer;
  }
  private JPanel projectLocksPanel(Activity project){
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(20,20,16,20));
    p.add(label("Blocchi mensili del progetto «"+project.name()+"» • "+selectedYear+" (indipendenti dai blocchi dell'intero mese)",14,true),BorderLayout.NORTH);
    List<ProjectMonthLock> locked;
    try{locked=store.getProjectMonthLocks(project.id(),selectedYear);}catch(Exception e){error(e);return p;}
    Map<Integer,ProjectMonthLock> items=locked.stream().collect(Collectors.toMap(ProjectMonthLock::month,x->x));
    JTable table=makeTable(new AbstractTableModel(){
      public int getRowCount(){return 12;}public int getColumnCount(){return 3;}
      public String getColumnName(int c){return new String[]{"Mese", "Stato", "Motivo / timesheet"}[c];}
      public Object getValueAt(int r,int c){ProjectMonthLock row=items.get(r+1);return switch(c){case 0->MONTHS[r];case 1->row==null?"Aperto":"BLOCCATO";default->row==null?"":row.reason();};}
    });
    table.setRowHeight(34);table.getColumnModel().getColumn(0).setPreferredWidth(175);table.getColumnModel().getColumn(2).setPreferredWidth(420);
    table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable x,Object v,boolean selected,boolean focus,int row,int column){
      super.getTableCellRendererComponent(x,v,selected,focus,row,column);setBackground(selected?SELECT_BG:items.containsKey(row+1)?RED:row%2==0?WHITE:BG);setForeground(selected?WHITE:INK);return this;}});
    JPanel buttons=panel(new FlowLayout(FlowLayout.RIGHT));JButton lock=action("Blocca il mese del progetto"),unlock=subtle("Sblocca");buttons.add(lock);buttons.add(unlock);
    JPanel body=panel(new BorderLayout(0,8));body.add(buttons,BorderLayout.NORTH);body.add(new JScrollPane(table),BorderLayout.CENTER);p.add(body,BorderLayout.CENTER);
    lock.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;int m=table.convertRowIndexToModel(r)+1;
      if(items.containsKey(m)){JOptionPane.showMessageDialog(this,"Questo mese è già bloccato.");return;}
      String reason=JOptionPane.showInputDialog(this,"Motivo del blocco di "+MONTHS[m-1]+" " + selectedYear + " per "+project.name(),"Timesheet progetto inviato");
      if(reason==null)return;try{store.setProjectMonthLocked(project.id(),selectedYear,m,true,reason);projectTabIndex=4;changed();}catch(Exception ex){error(ex);}});
    unlock.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;int m=table.convertRowIndexToModel(r)+1;
      if(!items.containsKey(m))return;
      if(ask("Sbloccare "+MONTHS[m-1]+" "+selectedYear+" per il solo progetto «"+project.name()+"»?"))try{store.setProjectMonthLocked(project.id(),selectedYear,m,false,"");projectTabIndex=4;changed();}catch(Exception ex){error(ex);}});
    p.add(label("Un blocco protegge le tue ore e quelle dei colleghi per questo progetto. Le altre attività del mese restano modificabili, salvo blocchi generali.",12,false),BorderLayout.SOUTH);
    return p;
  }
  private JPanel projectPeoplePanel(Activity project,List<ProjectPerson> people,List<PersonHours> assigned,double own){
    JPanel rootPanel=panel(new BorderLayout(0,10));rootPanel.setBorder(new EmptyBorder(11,12,10,12));
    JTabbedPane sub=new JTabbedPane();
    JPanel team=panel(new BorderLayout(0,8));
    JPanel actions=panel(new FlowLayout(FlowLayout.RIGHT,8,0));JButton add=action("+ Collega"),edit=subtle("Modifica collega"),remove=subtle("Rimuovi collega");
    actions.add(add);actions.add(edit);actions.add(remove);team.add(actions,BorderLayout.NORTH);
    JTable peopleTable=makeTable(new AbstractTableModel(){public int getRowCount(){return people.size();}public int getColumnCount(){return 6;}
      public String getColumnName(int c){return new String[]{"Collega","Ruolo al 31/12","Ruolo nel progetto","Ore anno","Costo anno €","Note"}[c];}
      public Object getValueAt(int r,int c){ProjectPerson m=people.get(r);return switch(c){case 0->m.name();case 1->roleForPerson(m.id(),LocalDate.of(selectedYear,12,31));case 2->m.projectRole();case 3->fmt(assigned.stream().filter(x->x.personId()==m.id()).mapToDouble(PersonHours::hours).sum());case 4->eur(assigned.stream().filter(x->x.personId()==m.id()).mapToDouble(x->rateForPerson(m.id(),x.date())*x.hours()).sum());default->m.notes();};}});
    peopleTable.getColumnModel().getColumn(0).setPreferredWidth(195);peopleTable.getColumnModel().getColumn(1).setPreferredWidth(125);
    peopleTable.getColumnModel().getColumn(2).setPreferredWidth(190);peopleTable.getColumnModel().getColumn(3).setPreferredWidth(90);peopleTable.getColumnModel().getColumn(4).setPreferredWidth(130);
    team.add(new JScrollPane(peopleTable),BorderLayout.CENTER);
    add.addActionListener(e->projectPersonEditor(project.id(),null));
    edit.addActionListener(e->{int r=peopleTable.getSelectedRow();if(r>=0)projectPersonEditor(project.id(),people.get(peopleTable.convertRowIndexToModel(r)));});
    remove.addActionListener(e->{int r=peopleTable.getSelectedRow();if(r>=0){ProjectPerson person=people.get(peopleTable.convertRowIndexToModel(r));
      if(ask("Eliminare "+person.name()+"? È possibile soltanto se non ha registrazioni di ore.")){try{store.deleteProjectPerson(person.id());changed();}catch(Exception ex){error(ex);}}}});
    JButton history=subtle("Storico ruoli e decorrenze");actions.add(history);
    history.addActionListener(e->{int r=peopleTable.getSelectedRow();if(r<0){JOptionPane.showMessageDialog(this,"Seleziona prima un collega.");return;}ProjectPerson member=people.get(peopleTable.convertRowIndexToModel(r));roleHistoryDialog(member.id(),member.name());});
    sub.addTab("Anagrafica colleghi",team);
    JPanel hours=panel(new BorderLayout(0,9));JPanel buttons=panel(new FlowLayout(FlowLayout.RIGHT,7,0));
    JButton addHours=action("+ Ore collega"),editHours=subtle("Modifica ore"),deleteHours=subtle("Elimina ore");buttons.add(addHours);buttons.add(editHours);buttons.add(deleteHours);hours.add(buttons,BorderLayout.NORTH);
    List<PersonHours> entries=new ArrayList<>(assigned);entries.sort(Comparator.comparing(PersonHours::date).thenComparing(PersonHours::name));
    JTable table=makeTable(new AbstractTableModel(){public int getRowCount(){return entries.size();}public int getColumnCount(){return 6;}
      public String getColumnName(int c){return new String[]{"Data","Collega","Ore","Ruolo alla data","Costo €","Note"}[c];}
      public Object getValueAt(int r,int c){PersonHours h=entries.get(r);return switch(c){case 0->h.date().format(DISPLAY);case 1->h.name();case 2->fmt(h.hours());case 3->roleForPerson(h.personId(),h.date());case 4->eur(rateForPerson(h.personId(),h.date())*h.hours());default->h.note();};}});
    table.getColumnModel().getColumn(0).setPreferredWidth(115);table.getColumnModel().getColumn(1).setPreferredWidth(250);
    table.getColumnModel().getColumn(2).setPreferredWidth(85);table.getColumnModel().getColumn(3).setPreferredWidth(140);table.getColumnModel().getColumn(4).setPreferredWidth(110);table.getColumnModel().getColumn(5).setPreferredWidth(270);
    hours.add(new JScrollPane(table),BorderLayout.CENTER);
    addHours.setEnabled(!people.isEmpty());
    addHours.addActionListener(e->projectHoursEditor(project.id(),people,null,chosen));
    editHours.addActionListener(e->{int r=table.getSelectedRow();if(r>=0)projectHoursEditor(project.id(),people,entries.get(table.convertRowIndexToModel(r)),null);});
    deleteHours.addActionListener(e->{int r=table.getSelectedRow();if(r>=0){PersonHours val=entries.get(table.convertRowIndexToModel(r));
      if(ask("Eliminare le "+fmt(val.hours())+" ore di "+val.name()+" del "+val.date().format(DISPLAY)+"?")){try{store.deleteProjectHours(val.id());changed();}catch(Exception ex){error(ex);}}}});
    JPanel totals=panel(new BorderLayout());totals.add(label("Le ore dei colleghi NON riducono il tuo tetto di 1.500 ore e NON sono incluse nella tua valorizzazione.",12,true),BorderLayout.SOUTH);
    hours.add(totals,BorderLayout.SOUTH);sub.addTab("Registrazioni giornaliere",hours);
    sub.setSelectedIndex(Math.min(teamTabIndex,sub.getTabCount()-1));
    sub.addChangeListener(e->teamTabIndex=sub.getSelectedIndex());
    rootPanel.add(sub,BorderLayout.CENTER);
    String[] cols={"Persona","Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic","Totale","Costo €"};
    List<String> names=new ArrayList<>();names.add("Tu (registro ore)");for(ProjectPerson person:people)names.add(person.name());
    double[][] matrix=new double[names.size()][12];for(LogEntry e:logs)if(e.activity().id()==project.id()&&e.hours()!=null&&isProject(e.activity().category()))matrix[0][e.date().getMonthValue()-1]+=e.hours();
    Map<Integer,Integer> indexes=new HashMap<>();for(int i=0;i<people.size();i++)indexes.put(people.get(i).id(),i+1);
    for(PersonHours e:assigned){Integer ix=indexes.get(e.personId());if(ix!=null)matrix[ix][e.date().getMonthValue()-1]+=e.hours();}
    JTable summary=makeTable(new AbstractTableModel(){public int getRowCount(){return names.size();}public int getColumnCount(){return cols.length;}
      public String getColumnName(int c){return cols[c];}public Object getValueAt(int r,int c){if(c==0)return names.get(r);if(c==14){double money=0;
        if(r==0)for(LogEntry e:logs)if(e.activity().id()==project.id()&&e.hours()!=null&&isProject(e.activity().category()))money+=rateForPerson(null,e.date())*e.hours();
        else for(PersonHours h:assigned)if(h.personId()==people.get(r-1).id())money+=rateForPerson(h.personId(),h.date())*h.hours();
        return eur(money);}
        double n=c==13?Arrays.stream(matrix[r]).sum():matrix[r][c-1];return n==0?"":fmt(n);}});
    summary.setRowHeight(28);summary.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);summary.getColumnModel().getColumn(0).setPreferredWidth(195);
    for(int i=1;i<14;i++)summary.getColumnModel().getColumn(i).setPreferredWidth(63);summary.getColumnModel().getColumn(14).setPreferredWidth(120);
    JScrollPane summaryScroll=new JScrollPane(summary);summaryScroll.setPreferredSize(new Dimension(760,Math.min(190,70+names.size()*28)));summaryScroll.setBorder(new TitledBorder("Ripartizione mensile delle ore — anno "+selectedYear));
    rootPanel.add(summaryScroll,BorderLayout.SOUTH);return rootPanel;
  }
  private void projectPersonEditor(int projectId,ProjectPerson old){
    JPanel form=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.gridy=0;g.insets=new Insets(7,8,7,8);
    JTextField name=new JTextField(old==null?"":old.name(),25),projRole=new JTextField(old==null?"":old.projectRole(),25),notes=new JTextField(old==null?"":old.notes(),25);
    JComboBox<String> qual=new JComboBox<>(new String[]{"Ordinario","Associato","Ricercatore","Dottorando","Tecnologo","Assegnista","Altro"});
    if(old!=null)qual.setSelectedItem(old.qualification());
    JTextField serviceStart=new JTextField(LocalDate.of(selectedYear,1,1).format(DISPLAY),16);
    field(form,g,"Nome e cognome",name);field(form,g,"Qualifica",qual);
    if(old==null)field(form,g,"In servizio nel ruolo dal (gg/mm/aaaa)",serviceStart);
    field(form,g,"Ruolo nel progetto",projRole);field(form,g,"Note",notes);
    if(JOptionPane.showConfirmDialog(this,form,old==null?"Aggiungi collega":"Modifica collega",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
    try{if(name.getText().trim().isBlank())throw new IllegalArgumentException("Inserisci il nome del collega.");
      if(old==null){LocalDate start=LocalDate.parse(serviceStart.getText().trim(),INPUT);int id=store.saveProjectPerson(null,projectId,name.getText().trim(),(String)qual.getSelectedItem(),projRole.getText().trim(),notes.getText().trim());
        if(rate((String)qual.getSelectedItem())>0)store.addRolePeriod(id,start,(String)qual.getSelectedItem());
      }else store.saveProjectPerson(old.id(),projectId,name.getText().trim(),(String)qual.getSelectedItem(),projRole.getText().trim(),notes.getText().trim());changed();
    }catch(Exception ex){error(ex);}
  }
  private void projectHoursEditor(int projectId,List<ProjectPerson> people,PersonHours old,LocalDate fallback){
    if(people.isEmpty()){JOptionPane.showMessageDialog(this,"Prima aggiungi almeno un collega.");return;}
    JPanel form=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.gridy=0;g.insets=new Insets(7,8,7,8);
    JComboBox<ProjectPerson> person=new JComboBox<>(people.toArray(new ProjectPerson[0]));
    person.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean selected,boolean focus){super.getListCellRendererComponent(l,v,i,selected,focus);if(v instanceof ProjectPerson pp)setText(pp.name());return this;}});
    if(old!=null)for(ProjectPerson member:people)if(member.id()==old.personId()){person.setSelectedItem(member);break;}
    LocalDate date=old!=null?old.date():fallback==null?LocalDate.of(selectedYear,1,1):fallback;
    JTextField day=new JTextField(date.format(DISPLAY),15),hours=new JTextField(old==null?"":fmt(old.hours()),15),notes=new JTextField(old==null?"":old.note(),22);
    field(form,g,"Collega",person);field(form,g,"Data (gg/mm/aaaa)",day);field(form,g,"Ore",hours);field(form,g,"Nota",notes);
    for(;;){if(JOptionPane.showConfirmDialog(this,form,old==null?"Registra ore collega":"Modifica ore collega",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
      try{LocalDate dateValue=LocalDate.parse(day.getText().trim(),INPUT);if(dateValue.getYear()!=selectedYear)throw new IllegalArgumentException("La data deve appartenere al "+selectedYear);
        double val=Double.parseDouble(hours.getText().trim().replace(',','.'));if(!Double.isFinite(val)||val<=0||val>24)throw new IllegalArgumentException("Ore tra 0 (escluso) e 24.");
        ProjectPerson member=(ProjectPerson)person.getSelectedItem();if(member==null)throw new IllegalArgumentException("Seleziona il collega.");
        store.saveProjectHours(old==null?null:old.id(),member.id(),dateValue,val,notes.getText().trim());changed();return;
      }catch(Exception ex){error(ex);}
    }
  }
  private JPanel projectCalendar(Activity project,List<ProjectPerson> people,List<PersonHours> colleagues){
    JPanel outer=panel(new BorderLayout(0,9));outer.setBorder(new EmptyBorder(10,8,5,8));
    JLabel legend=label("Clicca un giorno per vedere chi ha lavorato e registrare ore dei colleghi. In blu i giorni con ore, in rosa i giorni non didattici.",12,false);
    outer.add(legend,BorderLayout.NORTH);
    Map<LocalDate,Double> own=new HashMap<>();for(LogEntry entry:logs)if(entry.activity().id()==project.id()&&entry.hours()!=null&&isProject(entry.activity().category()))own.merge(entry.date(),entry.hours(),Double::sum);
    Map<LocalDate,List<PersonHours>> byDay=new HashMap<>();for(PersonHours h:colleagues)byDay.computeIfAbsent(h.date(),k->new ArrayList<>()).add(h);
    JPanel year=panel(new GridLayout(3,4,9,9));year.setBorder(new EmptyBorder(2,2,3,8));
    for(int month=1;month<=12;month++){
      LocalDate first=LocalDate.of(selectedYear,month,1);JPanel monthCard=new JPanel(new BorderLayout(3,5));monthCard.setBackground(WHITE);
      monthCard.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(8,9,9,9)));
      JLabel heading=label(MONTHS[month-1],13,true);heading.setHorizontalAlignment(SwingConstants.CENTER);monthCard.add(heading,BorderLayout.NORTH);
      JPanel cells=new JPanel(new GridLayout(0,7,2,3));cells.setBackground(WHITE);
      for(String day:new String[]{"L","M","M","G","V","S","D"}){JLabel h=label(day,10,true);h.setHorizontalAlignment(SwingConstants.CENTER);cells.add(h);}
      for(int n=0;n<first.getDayOfWeek().getValue()-1;n++)cells.add(new JLabel(""));
      for(int i=1;i<=first.lengthOfMonth();i++){
        LocalDate d=first.withDayOfMonth(i);double mine=own.getOrDefault(d,0.0);List<PersonHours> others=byDay.getOrDefault(d,List.of());double their=others.stream().mapToDouble(PersonHours::hours).sum();
        JButton day=new JButton("<html><center>"+i+(mine+their>0?"<br><b>"+fmt(mine+their)+"h</b>":"")+"</center></html>");
        day.setFont(new Font("SansSerif",Font.PLAIN,10));day.setHorizontalAlignment(SwingConstants.CENTER);day.setMargin(new Insets(0,0,0,0));day.setOpaque(true);
        Color bg=mine+their>0?new Color(203,229,250):blocked(d)?RED:WHITE;day.setBackground(bg);
        day.setBorder(new LineBorder(new Color(228,235,241),1,true));day.setPreferredSize(new Dimension(31,36));
        StringBuilder tip=new StringBuilder("<html><b>"+d.format(DISPLAY)+"</b><br>Tu: "+fmt(mine)+" ore");
        for(PersonHours h:others)tip.append("<br>").append(esc(h.name())).append(": ").append(fmt(h.hours())).append(" ore");tip.append("</html>");
        day.setToolTipText(tip.toString());day.addActionListener(e->showProjectDay(project,d,people,own.getOrDefault(d,0.0),byDay.getOrDefault(d,List.of())));
        cells.add(day);
      }
      monthCard.add(cells,BorderLayout.CENTER);year.add(monthCard);
    }
    JScrollPane scroll=new JScrollPane(year);scroll.setBorder(null);scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(25);outer.add(scroll,BorderLayout.CENTER);return outer;
  }
  private void showProjectDay(Activity project,LocalDate day,List<ProjectPerson> people,double mine,List<PersonHours> others){
    JPanel panel=new JPanel(new BorderLayout(7,9));
    JTextArea text=new JTextArea();text.setEditable(false);text.setFont(new Font("SansSerif",Font.PLAIN,14));
    StringBuilder b=new StringBuilder("TU — "+fmt(mine)+" ore (dal registro personale)\n");
    for(PersonHours h:others)b.append(h.name()).append(" — ").append(fmt(h.hours())).append(" ore").append(h.note().isBlank()?"":" — "+h.note()).append("\n");
    b.append("\nTOTALE SQUADRA: ").append(fmt(mine+others.stream().mapToDouble(PersonHours::hours).sum())).append(" ore");text.setText(b.toString());
    panel.add(new JScrollPane(text),BorderLayout.CENTER);panel.setPreferredSize(new Dimension(530,210));
    Object[] options={"+ Ore collega","Apri registro","Chiudi"};
    int choice=JOptionPane.showOptionDialog(this,panel,project.name()+" • "+day.format(DISPLAY),JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[2]);
    if(choice==0)projectHoursEditor(project.id(),people,null,day);else if(choice==1){chosen=day;showPage("Registro");}
  }
  /** Ultime cartelle per il selettore degli allegati: preferenze locali dell'utente, non DB. */
  private static List<Path> recentDocumentFolders(){
    List<Path> values=new ArrayList<>();for(String line:FILE_PREFERENCES.get("recentFolders","").split("\n")){
      if(line.isBlank())continue;try{Path path=Paths.get(line);if(Files.isDirectory(path))values.add(path);}catch(Exception ignored){} }
    return values;
  }
  private static void rememberDocumentFolder(Path path){
    if(path==null||!Files.isDirectory(path))return;
    LinkedHashSet<String> all=new LinkedHashSet<>();all.add(path.toAbsolutePath().normalize().toString());
    for(Path previous:recentDocumentFolders())all.add(previous.toString());
    FILE_PREFERENCES.put("recentFolders",all.stream().limit(10).collect(Collectors.joining("\n")));
  }
  private JFileChooser documentChooser(String title,boolean multi){
    JFileChooser picker=new JFileChooser();picker.setDialogTitle(title);picker.setMultiSelectionEnabled(multi);
    List<Path> recent=recentDocumentFolders();if(!recent.isEmpty())picker.setCurrentDirectory(recent.get(0).toFile());
    JPanel accessory=new JPanel(new BorderLayout(4,5));accessory.setBorder(new EmptyBorder(12,8,12,8));
    accessory.add(new JLabel("Ultime cartelle visitate:"),BorderLayout.NORTH);
    JComboBox<String> recentPicker=new JComboBox<>(recent.stream().map(Path::toString).toArray(String[]::new));recentPicker.setPreferredSize(new Dimension(235,30));
    recentPicker.addActionListener(e->{String path=(String)recentPicker.getSelectedItem();if(path!=null&&Files.isDirectory(Paths.get(path)))picker.setCurrentDirectory(new File(path));});
    accessory.add(recentPicker,BorderLayout.CENTER);picker.setAccessory(accessory);
    picker.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY,e->{File file=picker.getCurrentDirectory();if(file!=null)rememberDocumentFolder(file.toPath());});
    return picker;
  }

  private JPanel projectFilesPanel(Activity project){
    JPanel p=panel(new BorderLayout(0,10));p.setBorder(new EmptyBorder(14,14,12,14));
    List<ProjectDocument> docs;try{docs=store.projectDocuments(project.id());}catch(Exception ex){error(ex);return p;}
    JPanel bar=panel(new BorderLayout(0,6));bar.add(label("Documenti del progetto: seleziona una riga e premi Visualizza per aprire l'anteprima interna.",12,true),BorderLayout.NORTH);
    JPanel tools=panel(new FlowLayout(FlowLayout.RIGHT,7,0));
    JButton add=action("+ Carica file"),preview=action("Visualizza"),save=subtle("Scarica file"),note=subtle("Modifica nota"),remove=subtle("Elimina");
    tools.add(add);tools.add(preview);tools.add(save);tools.add(note);tools.add(remove);bar.add(tools,BorderLayout.SOUTH);p.add(bar,BorderLayout.NORTH);
    JTable table=makeTable(new AbstractTableModel(){public int getRowCount(){return docs.size();}public int getColumnCount(){return 5;}
      public String getColumnName(int c){return new String[]{"Documento","Nota","Periodo","Dimensione","Caricato il"}[c];}
      public Object getValueAt(int r,int c){ProjectDocument d=docs.get(r);return switch(c){case 0->d.filename();case 1->d.description();case 2->monthYear(d.month(),d.year());case 3->String.format(Locale.ITALY,"%.1f KB",d.size()/1024.0);default->d.createdAt();};}});
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    TableRowSorter<TableModel> sorter=new TableRowSorter<>(table.getModel());table.setRowSorter(sorter);
    sorter.setComparator(0,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a),String.valueOf(b)));
    sorter.setComparator(2,(a,b)->{
      String x=String.valueOf(a),y=String.valueOf(b);
      int ax=x.matches("\\d{2}/\\d{4}")?Integer.parseInt(x.substring(3))*12+Integer.parseInt(x.substring(0,2)):0;
      int by=y.matches("\\d{2}/\\d{4}")?Integer.parseInt(y.substring(3))*12+Integer.parseInt(y.substring(0,2)):0;
      return Integer.compare(ax,by);
    });
    sorter.setComparator(4,(a,b)->String.valueOf(a).compareTo(String.valueOf(b)));
    JComboBox<String> order=new JComboBox<>(new String[]{"Data caricamento","Documento","Periodo"});
    JCheckBox descending=new JCheckBox("Decrescente",true);
    Runnable applySort=()->{int c=switch(order.getSelectedIndex()){case 1->0;case 2->2;default->4;};
      sorter.setSortKeys(List.of(new RowSorter.SortKey(c,descending.isSelected()?SortOrder.DESCENDING:SortOrder.ASCENDING)));sorter.sort();};
    order.addActionListener(e->applySort.run());descending.addActionListener(e->applySort.run());
    JPanel sortControls=panel(new FlowLayout(FlowLayout.LEFT,9,3));sortControls.add(label("Ordina per:",12,true));sortControls.add(order);sortControls.add(descending);
    JPanel tableArea=panel(new BorderLayout(3,6));tableArea.add(sortControls,BorderLayout.NORTH);applySort.run();
    table.getColumnModel().getColumn(0).setPreferredWidth(240);table.getColumnModel().getColumn(1).setPreferredWidth(230);
    table.getColumnModel().getColumn(2).setPreferredWidth(110);table.getColumnModel().getColumn(3).setPreferredWidth(95);
    tableArea.add(new JScrollPane(table),BorderLayout.CENTER);p.add(tableArea,BorderLayout.CENTER);
    add.addActionListener(e->{
      JFileChooser picker=documentChooser("Seleziona uno o più documenti del progetto",true);
      if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
      File[] chosenFiles=picker.getSelectedFiles();if(chosenFiles.length==0&&picker.getSelectedFile()!=null)chosenFiles=new File[]{picker.getSelectedFile()};
      if(chosenFiles.length==0)return;
      List<Path> paths=Arrays.stream(chosenFiles).map(File::toPath).toList();
      if(picker.getCurrentDirectory()!=null)rememberDocumentFolder(picker.getCurrentDirectory().toPath());
      JTextArea comment=new JTextArea(3,40);comment.setLineWrap(true);comment.setWrapStyleWord(true);
      JTextField period=new JTextField(12);
      JPanel form=new JPanel(new BorderLayout(5,8));
      JPanel info=new JPanel(new GridLayout(0,1,4,4));info.add(new JLabel("File selezionati: "+paths.size()));
      JTextArea names=new JTextArea(paths.stream().map(x->x.getFileName().toString()).collect(Collectors.joining("\n")),Math.min(6,paths.size()),38);
      names.setEditable(false);info.add(new JScrollPane(names));info.add(new JLabel("Periodo comune (mm/aaaa, facoltativo):"));info.add(period);
      form.add(info,BorderLayout.NORTH);form.add(new JScrollPane(comment),BorderLayout.CENTER);
      form.add(new JLabel("Nota comune ai file (modificabile singolarmente in seguito)"),BorderLayout.SOUTH);
      if(JOptionPane.showConfirmDialog(this,form,"Carica "+paths.size()+" documenti",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
      try{YearMonth ym=parseMonthYear(period.getText());
        store.addProjectDocuments(project.id(),paths,comment.getText().trim(),ym==null?null:ym.getYear(),ym==null?null:ym.getMonthValue());
        projectTabIndex=3;changed();JOptionPane.showMessageDialog(this,paths.size()+" documento/i caricato/i e verificato/i nel database.");
      }catch(Exception ex){error(ex);}
    });
    preview.addActionListener(e->{int r=table.getSelectedRow();if(r<0){JOptionPane.showMessageDialog(this,"Seleziona il documento da visualizzare.");return;}
      ProjectDocument doc=docs.get(table.convertRowIndexToModel(r));
      try{DocumentViewer.open(this,doc.filename(),doc.description(),store.readProjectDocument(doc.id()));}catch(Exception ex){error(ex);}
    });
    save.addActionListener(e->{int r=table.getSelectedRow();if(r<0){JOptionPane.showMessageDialog(this,"Seleziona un documento da scaricare.");return;}
      ProjectDocument doc=docs.get(table.convertRowIndexToModel(r));JFileChooser picker=documentChooser("Scarica il documento in una cartella",false);
      picker.setSelectedFile(new File(picker.getCurrentDirectory(),doc.filename()));
      if(picker.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
      Path path=picker.getSelectedFile().toPath();rememberDocumentFolder(path.toAbsolutePath().getParent());if(Files.exists(path)&&!ask("Il file esiste già. Sovrascriverlo?"))return;
      try{store.exportProjectDocument(doc.id(),path);JOptionPane.showMessageDialog(this,"File scaricato:\n"+path.toAbsolutePath());}catch(Exception ex){error(ex);}
    });
    note.addActionListener(e->{int r=table.getSelectedRow();if(r<0){JOptionPane.showMessageDialog(this,"Seleziona prima un documento.");return;}
      ProjectDocument doc=docs.get(table.convertRowIndexToModel(r));JTextArea field=new JTextArea(doc.description(),6,45);
      field.setLineWrap(true);field.setWrapStyleWord(true);
      if(JOptionPane.showConfirmDialog(this,new JScrollPane(field),"Modifica nota: "+doc.filename(),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)
        try{store.updateProjectDocumentNote(doc.id(),field.getText().trim());projectTabIndex=3;changed();}catch(Exception ex){error(ex);}
    });
    remove.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;ProjectDocument doc=docs.get(table.convertRowIndexToModel(r));
      if(ask("Eliminare definitivamente il documento '"+doc.filename()+"' dal database?"))try{store.deleteProjectDocument(doc.id());projectTabIndex=3;changed();}catch(Exception ex){error(ex);}});
    p.add(label("File fino a 25 MB. Prima di effettuare aggiornamenti, conserva anche una copia di sicurezza del database.",12,false),BorderLayout.SOUTH);
    table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)preview.doClick();}});return p;
  }
  private JPanel annualPanel() {
    JPanel p=panel(new BorderLayout(0,14));p.setBorder(new EmptyBorder(22,27,24,27));
    double total=totalOf(logs);
    JPanel top=panel(new BorderLayout(0,10));
    top.add(label("ANNUALITÀ "+selectedYear+"     |     "+fmt(total)+" / "+fmt(YEAR_LIMIT)+" ore     |     Disponibili: "+fmt(Math.max(0,YEAR_LIMIT-total))+" ore",17,true),BorderLayout.NORTH);
    JPanel noteRow=panel(new BorderLayout(8,5));noteRow.add(label("Note dell’annualità (puoi modificare e salvare):",13,true),BorderLayout.NORTH);
    JTextArea note=new JTextArea(annual.note(),4,45);note.setLineWrap(true);note.setWrapStyleWord(true);note.setFont(new Font("SansSerif",Font.PLAIN,13));noteRow.add(new JScrollPane(note),BorderLayout.CENTER);
    JButton save=action("Salva note annualità");save.addActionListener(e->{try{store.saveAnnualNote(selectedYear,note.getText());changed();}catch(Exception ex){error(ex);}});
    JPanel row=panel(new FlowLayout(FlowLayout.RIGHT,0,0));row.add(save);noteRow.add(row,BorderLayout.SOUTH);top.add(noteRow,BorderLayout.CENTER);p.add(top,BorderLayout.NORTH);
    List<LockedMonth> locks;try{locks=store.getLockedMonths(selectedYear);}catch(Exception ex){error(ex);locks=List.of();}
    Set<Integer> blockedMonths=locks.stream().map(LockedMonth::month).collect(Collectors.toSet());
    Map<Integer,LockedMonth> lockByMonth=locks.stream().collect(Collectors.toMap(LockedMonth::month, x->x));
    JTable table=makeTable(new AbstractTableModel(){public int getRowCount(){return 12;}public int getColumnCount(){return 4;}public String getColumnName(int c){return new String[]{"Mensilità","Stato","Motivo / timesheet","Ore registrate"}[c];}
      public Object getValueAt(int r,int c){int m=r+1;LockedMonth lock=lockByMonth.get(m);return switch(c){case 0->MONTHS[r];case 1->lock==null?"Aperto":"BLOCCATO";case 2->lock==null?"":lock.reason();default->fmt(logs.stream().filter(x->x.date().getMonthValue()==m&&x.hours()!=null).mapToDouble(x->x.hours()).sum());};}});
    table.setRowHeight(34);table.getColumnModel().getColumn(0).setPreferredWidth(180);table.getColumnModel().getColumn(1).setPreferredWidth(125);table.getColumnModel().getColumn(2).setPreferredWidth(400);table.getColumnModel().getColumn(3).setPreferredWidth(135);
    table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean f,int r,int c){super.getTableCellRendererComponent(t,v,sel,f,r,c);setBorder(new EmptyBorder(2,12,2,10));setBackground(sel?SELECT_BG:blockedMonths.contains(r+1)?new Color(255,225,225):r%2==0?WHITE:new Color(247,250,253));setForeground(sel?WHITE:INK);return this;}});
    JPanel center=panel(new BorderLayout(0,8));JPanel actions=panel(new FlowLayout(FlowLayout.RIGHT,8,0));JButton lock=action("Blocca mensilità"),unlock=subtle("Sblocca mensilità");actions.add(lock);actions.add(unlock);center.add(actions,BorderLayout.NORTH);center.add(new JScrollPane(table),BorderLayout.CENTER);p.add(center,BorderLayout.CENTER);
    lock.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;int m=table.convertRowIndexToModel(r)+1;if(blockedMonths.contains(m)){JOptionPane.showMessageDialog(this,"Mese già bloccato.");return;}
      String reason=JOptionPane.showInputDialog(this,"Motivo del blocco (es. timesheet già inviato):","Timesheet inviato");if(reason==null)return;
      if(ask("Bloccare "+MONTHS[m-1]+" "+selectedYear+"? Non potrai più modificare le ore, le attività registrate o le sospensioni del mese.")){try{store.setMonthLocked(selectedYear,m,true,reason);changed();}catch(Exception ex){error(ex);}}});
    unlock.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;int m=table.convertRowIndexToModel(r)+1;if(!blockedMonths.contains(m))return;
      if(ask("Sbloccare "+MONTHS[m-1]+" "+selectedYear+"? Consentirai di nuovo la modifica dei dati.")){try{store.setMonthLocked(selectedYear,m,false,"");changed();}catch(Exception ex){error(ex);}}});
    p.add(label("Il blocco impedisce aggiunta/modifica/eliminazione di ore e sospensioni del mese, anche a livello database.",12,true),BorderLayout.SOUTH);
    return p;
  }

  private JPanel profilePanel() {
    JPanel p=panel(new BorderLayout(0,15));p.setBorder(new EmptyBorder(28,35,26,30));
    ProfileInfo profile;try{profile=store.getProfile();}catch(Exception ex){error(ex);return p;}
    JPanel form=panel(new GridBagLayout());GridBagConstraints gbc=new GridBagConstraints();gbc.gridx=0;gbc.gridy=0;gbc.insets=new Insets(12,10,12,10);gbc.anchor=GridBagConstraints.WEST;
    JTextField name=new JTextField(profile.name(),30);JComboBox<String> role=new JComboBox<>(ROLES);role.setSelectedItem(annual.role());
    field(form,gbc,"Nome docente (facoltativo)",name);field(form,gbc,"Ruolo per l’anno "+selectedYear,role);
    JLabel rateLabel=label("Costo orario: "+(rate(annual.role())==0?"da impostare":rate(annual.role())+" €/ora"),16,true);
    role.addActionListener(e->{String choice=(String)role.getSelectedItem();rateLabel.setText("Costo orario: "+(rate(choice)==0?"da impostare":rate(choice)+" €/ora"));});
    gbc.gridx=0;gbc.gridwidth=2;form.add(rateLabel,gbc);gbc.gridy++;
    JLabel description=label("Ordinario 81 €/h   •   Associato 53 €/h   •   Ricercatore 34 €/h",14,false);form.add(description,gbc);gbc.gridy++;
    JLabel explanation=label("Il ruolo viene registrato per annualità: i costi degli anni passati non cambiano se cambia il tuo ruolo.",12,false);form.add(explanation,gbc);gbc.gridy++;
    JButton save=action("Salva profilo e ruolo");save.addActionListener(e->{try{store.saveProfile(name.getText().trim(),(String)role.getSelectedItem(),selectedYear);changed();}catch(Exception ex){error(ex);}});form.add(save,gbc);
    JButton history=action("Storico ruoli (date di decorrenza)");gbc.gridy++;form.add(history,gbc);
    history.addActionListener(e->roleHistoryDialog(null,"Il tuo profilo"));
    JLabel hint=label("Imposta le decorrenze effettive, anche a metà anno. I costi dei progetti sono calcolati giorno per giorno.",12,false);
    gbc.gridy++;form.add(hint,gbc);
    JTabbedPane tabs=new JTabbedPane();tabs.addTab("Profilo docente",new JScrollPane(form));
    tabs.addTab("Colleghi e progetti",colleagueOverviewPanel());
    tabs.setSelectedIndex(Math.min(profileTabIndex,tabs.getTabCount()-1));
    tabs.addChangeListener(e->profileTabIndex=tabs.getSelectedIndex());
    p.add(tabs,BorderLayout.CENTER);return p;
  }

  /** Vista unica dei colleghi, raggruppati per nominativo e articolati per progetto. */
  private JPanel colleagueOverviewPanel(){
    JPanel p=panel(new BorderLayout(0,10));p.setBorder(new EmptyBorder(14,12,12,12));
    List<ColleagueAssignment> rows;
    try{rows=store.colleagueAssignments(selectedYear);}catch(Exception ex){error(ex);return p;}
    JPanel header=panel(new BorderLayout());
    header.add(label("Collaboratori nei progetti - anno "+selectedYear+" (ruolo alla data e storico decorrenze)",13,true),BorderLayout.WEST);
    JButton history=action("Storico ruoli del collega selezionato");header.add(history,BorderLayout.EAST);p.add(header,BorderLayout.NORTH);
    JTable t=makeTable(new AbstractTableModel(){
      public int getRowCount(){return rows.size();}public int getColumnCount(){return 6;}
      public String getColumnName(int c){return new String[]{"Collega","Ruolo al 31/12","Progetto","Ruolo nel progetto","Ore anno","Costo anno"}[c];}
      public Object getValueAt(int r,int c){ColleagueAssignment a=rows.get(r);return switch(c){case 0->a.name();case 1->a.roleAtYearEnd();case 2->a.project();case 3->a.projectRole();case 4->fmt(a.hours());default->eur(a.cost());};}
    });
    t.setAutoCreateRowSorter(true);t.setRowHeight(30);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    t.getColumnModel().getColumn(0).setPreferredWidth(200);
    t.getColumnModel().getColumn(2).setPreferredWidth(250);
    t.getColumnModel().getColumn(3).setPreferredWidth(180);
    history.addActionListener(e->{int selected=t.getSelectedRow();if(selected<0){JOptionPane.showMessageDialog(this,"Seleziona una riga del collega.");return;}
      ColleagueAssignment a=rows.get(t.convertRowIndexToModel(selected));
      roleHistoryDialog(a.personId(),a.name()+" - "+a.project());
    });
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    TreeMap<String,double[]> totals=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(ColleagueAssignment a:rows){double[] v=totals.computeIfAbsent(a.name(),k->new double[2]);v[0]+=a.hours();v[1]+=a.cost();}
    StringBuilder text=new StringBuilder("Totali per collega: ");
    for(var e:totals.entrySet()){if(text.length()>22)text.append("    |    ");text.append(e.getKey()).append(": ").append(fmt(e.getValue()[0])).append(" h");}
    JTextArea foot=new JTextArea(text.toString()+"\nIl costo dipende dal ruolo in vigore in ogni giornata. Lo storico delle decorrenze si modifica sulla riga del progetto selezionato.");
    foot.setLineWrap(true);foot.setWrapStyleWord(true);foot.setEditable(false);foot.setBackground(BG);
    foot.setFont(new Font("SansSerif",Font.PLAIN,12));foot.setBorder(new EmptyBorder(8,4,5,4));p.add(foot,BorderLayout.SOUTH);
    return p;
  }

  private MissionChoice[] missionChoices(){
    List<MissionChoice> options=new ArrayList<>();options.add(new MissionChoice(null,"— Nessuna missione —"));
    for(Mission m:missions)options.add(new MissionChoice(m.id(),m.toString()));
    return options.toArray(new MissionChoice[0]);
  }
  private static void selectMission(JComboBox<MissionChoice> box,Integer id){
    for(int i=0;i<box.getItemCount();i++)if(Objects.equals(box.getItemAt(i).id(),id)){box.setSelectedIndex(i);return;}
  }
  private JPanel missionsPanel(){
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel top=panel(new BorderLayout());
    top.add(label("Le missioni descrivono il contesto delle attività, senza duplicare ore di lavoro.",13,false),BorderLayout.WEST);
    JPanel commands=panel(new FlowLayout(FlowLayout.RIGHT,6,0));
    JButton add=action("+ Missione"),edit=subtle("Modifica"),docs=subtle("Documenti"),remove=subtle("Elimina");
    for(JButton b:List.of(add,edit,docs,remove))commands.add(b);
    top.add(commands,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    JTable t=makeTable(new AbstractTableModel(){
      public int getRowCount(){return missions.size();}public int getColumnCount(){return 6;}
      public String getColumnName(int c){return new String[]{"Missione","Tipo","Ruolo","Luogo","Dal","Al"}[c];}
      public Object getValueAt(int r,int c){Mission m=missions.get(r);return switch(c){
        case 0->m.title();case 1->m.type();case 2->m.role();case 3->m.location();
        case 4->m.from().format(DISPLAY);default->m.to().format(DISPLAY);};}
    });t.setRowHeight(31);t.setAutoCreateRowSorter(true);
    t.getColumnModel().getColumn(0).setPreferredWidth(290);
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    java.util.function.Supplier<Mission> selected=()->{
      int row=t.getSelectedRow();return row<0?null:missions.get(t.convertRowIndexToModel(row));};
    add.addActionListener(e->missionEditor(null));
    edit.addActionListener(e->{Mission m=selected.get();if(m!=null)missionEditor(m);});
    docs.addActionListener(e->{Mission m=selected.get();if(m!=null)missionDocumentsDialog(m);});
    remove.addActionListener(e->{Mission m=selected.get();if(m!=null&&ask("Eliminare la missione «"+m.title()+"»? I suoi documenti saranno eliminati.\nNon è possibile se ci sono ore collegate."))try{
      store.deleteMission(m.id());changed();
    }catch(Exception ex){error(ex);}});
    JPanel footer=panel(new BorderLayout());
    JTextArea hint=new JTextArea("Partecipazione a conferenze e presentazione di paper sono motivi di missione. " +
      "La categoria delle ore dipende dalla natura del lavoro svolto (ricerca, progetti, formazione, commissioni, ecc.). " +
      "Viaggio, vitto e alloggio non producono automaticamente ore rendicontabili.");
    hint.setEditable(false);hint.setLineWrap(true);hint.setWrapStyleWord(true);hint.setBackground(BG);hint.setRows(3);
    footer.add(hint,BorderLayout.CENTER);p.add(footer,BorderLayout.SOUTH);
    return p;
  }
  private void missionEditor(Mission old){
    JTextField title=new JTextField(old==null?"":old.title());
    String[] kinds={"Conferenza scientifica","Presentazione paper","Keynote","Riunione di progetto",
      "Visita scientifica","Commissione esterna","Formazione","Altra missione"};
    JComboBox<String> type=new JComboBox<>(kinds);type.setEditable(true);
    JComboBox<String> role=new JComboBox<>(new String[]{"Partecipante","Relatore","Chair","Organizzatore","Commissario","Altro"});
    JTextField location=new JTextField(old==null?"":old.location());
    JTextField from=new JTextField(old==null?chosen.format(DISPLAY):old.from().format(DISPLAY));
    JTextField to=new JTextField(old==null?chosen.format(DISPLAY):old.to().format(DISPLAY));
    JTextField conference=new JTextField(old==null?"":old.conference());
    JTextField paper=new JTextField(old==null?"":old.paper());
    JTextField doi=new JTextField(old==null?"":old.doi());
    JTextField auth=new JTextField(old==null?"":old.authorization());
    JTextArea note=new JTextArea(old==null?"":old.notes(),3,28);note.setLineWrap(true);note.setWrapStyleWord(true);
    List<MissionChoice> projects=new ArrayList<>();projects.add(new MissionChoice(null,"— Nessun progetto —"));
    for(Activity a:availableProjects())projects.add(new MissionChoice(a.id(),projectNavLabel(a)));
    JComboBox<MissionChoice> project=new JComboBox<>(projects.toArray(new MissionChoice[0]));
    if(old!=null){type.setSelectedItem(old.type());role.setSelectedItem(old.role());selectMission(project,old.projectId());}
    JPanel form=new JPanel(new GridLayout(0,2,8,7));form.setBorder(new EmptyBorder(8,12,8,12));
    form.add(new JLabel("Titolo missione"));form.add(title);
    form.add(new JLabel("Tipologia"));form.add(type);
    form.add(new JLabel("Ruolo"));form.add(role);
    form.add(new JLabel("Località"));form.add(location);
    form.add(new JLabel("Dal (gg/mm/aaaa)"));form.add(from);
    form.add(new JLabel("Al (gg/mm/aaaa)"));form.add(to);
    form.add(new JLabel("Conferenza / evento"));form.add(conference);
    form.add(new JLabel("Titolo paper"));form.add(paper);
    form.add(new JLabel("DOI"));form.add(doi);
    form.add(new JLabel("Progetto finanziatore (facoltativo)"));form.add(project);
    form.add(new JLabel("Autorizzazione / protocollo"));form.add(auth);
    form.add(new JLabel("Note"));form.add(new JScrollPane(note));
    if(JOptionPane.showConfirmDialog(this,new JScrollPane(form),old==null?"Nuova missione":"Modifica missione",
        JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
    try{
      MissionChoice chosenProject=(MissionChoice)project.getSelectedItem();
      store.saveMission(new Mission(old==null?0:old.id(),title.getText().trim(),type.getSelectedItem().toString(),
        role.getSelectedItem().toString(),location.getText().trim(),LocalDate.parse(from.getText().trim(),INPUT),
        LocalDate.parse(to.getText().trim(),INPUT),conference.getText().trim(),paper.getText().trim(),
        doi.getText().trim(),chosenProject==null?null:chosenProject.id(),auth.getText().trim(),note.getText().trim()));
      changed();
    }catch(Exception e){error(e);}
  }
  private void missionDocumentsDialog(Mission mission){
    try{
      List<MissionDocument> files=store.getMissionDocuments(mission.id());
      JPanel p=panel(new BorderLayout(8,8));
      String[] names=files.stream().map(d->d.filename()+" ("+d.size()+" byte)").toArray(String[]::new);
      JList<String> listing=new JList<>(names);listing.setVisibleRowCount(8);
      p.add(new JScrollPane(listing),BorderLayout.CENTER);
      JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
      JButton attach=action("Allega"),export=subtle("Esporta copia"),delete=subtle("Rimuovi documento");
      buttons.add(attach);buttons.add(export);buttons.add(delete);p.add(buttons,BorderLayout.SOUTH);
      JDialog dialog=new JDialog(this,"Documenti missione: "+mission.title(),true);
      attach.addActionListener(e->{FileDialog chooser=new FileDialog(this,"Seleziona documento missione",FileDialog.LOAD);
        chooser.setVisible(true);if(chooser.getFile()==null)return;try{
          store.addMissionDocument(mission.id(),Paths.get(chooser.getDirectory(),chooser.getFile()));dialog.dispose();changed();
          missionDocumentsDialog(mission);
        }catch(Exception ex){error(ex);}});
      export.addActionListener(e->{int i=listing.getSelectedIndex();if(i<0)return;
        MissionDocument file=files.get(i);FileDialog chooser=new FileDialog(this,"Esporta documento",FileDialog.SAVE);
        chooser.setFile(file.filename());chooser.setVisible(true);if(chooser.getFile()==null)return;
        try{store.exportMissionDocument(file.id(),Paths.get(chooser.getDirectory(),chooser.getFile()));}
        catch(Exception ex){error(ex);}});
      delete.addActionListener(e->{int i=listing.getSelectedIndex();if(i<0)return;
        if(!ask("Rimuovere il documento dalla missione?"))return;
        try{store.deleteMissionDocument(files.get(i).id());dialog.dispose();changed();missionDocumentsDialog(mission);}
        catch(Exception ex){error(ex);}});
      dialog.setContentPane(p);dialog.setSize(660,350);dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }catch(Exception ex){error(ex);}
  }

  private JPanel categoryPanel(){
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel top=panel(new BorderLayout(5,8));
    top.add(label("Le nove categorie sono predefinite; non occorre scegliere una seconda regola operativa.",13,true),BorderLayout.NORTH);
    JButton help=action("Apri guida alle categorie");help.addActionListener(e->showPage("Guida alle categorie"));
    top.add(help,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    String[] headers={"Categoria","Sospensione lezioni","Attività associate"};
    JTable t=makeTable(new AbstractTableModel(){
      public int getColumnCount(){return headers.length;}public int getRowCount(){return CategoryTaxonomy.RULES.size();}
      public String getColumnName(int c){return headers[c];}
      public Object getValueAt(int r,int c){var item=CategoryTaxonomy.RULES.get(r);return switch(c){
        case 0->item.name();case 1->suspensionLabel(item.suspension());
        default->activities.stream().filter(a->a.category().equals(item.name())).count();};}
    });t.setRowHeight(33);t.getColumnModel().getColumn(0).setPreferredWidth(440);
    t.getColumnModel().getColumn(1).setPreferredWidth(200);
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    try{
      long review=store.getLegacyMappings().stream().filter(LegacyMapping::review).count();
      if(review>0){JButton reviewButton=subtle("Controlla "+review+" classificazioni storiche da verificare");
        reviewButton.addActionListener(e->showLegacyMappingReview());
        p.add(reviewButton,BorderLayout.SOUTH);}
    }catch(SQLException e){error(e);}
    return p;
  }
  private static String suspensionLabel(CategoryTaxonomy.Suspension s){return switch(s){
    case BLOCK->"Blocco con deroga";case VERIFY->"Verifica richiesta";case ALLOW->"Consentita";};}
  private JPanel categoryGuidePanel(){
    JPanel outer=panel(new BorderLayout(0,11));outer.setBorder(new EmptyBorder(20,27,20,27));
    JPanel sections=panel(new BorderLayout());sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
    for(var r:CategoryTaxonomy.RULES){
      JPanel item=new JPanel(new BorderLayout(6,6));item.setBackground(WHITE);
      item.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(12,15,12,15)));
      item.setMaximumSize(new Dimension(Integer.MAX_VALUE,175));
      JLabel title=label(r.name()+"  •  "+suspensionLabel(r.suspension()),14,true);
      item.add(title,BorderLayout.NORTH);
      JTextArea description=new JTextArea("Comprende: "+r.includes()+"\nNon comprende: "+r.excludes()+"\nSospensione: "+r.note());
      description.setLineWrap(true);description.setWrapStyleWord(true);description.setEditable(false);description.setBackground(WHITE);
      description.setForeground(INK);description.setFont(new Font("SansSerif",Font.PLAIN,12));item.add(description,BorderLayout.CENTER);
      sections.add(item);sections.add(Box.createVerticalStrut(9));
    }
    JTextArea intro=new JTextArea("Le categorie classificano la natura del lavoro; la missione e' una proprieta' trasversale. " +
      "Un periodo di sospensione delle lezioni non equivale alla chiusura dell'Ateneo. " +
      "I controlli sono supporto alla registrazione, NON attestazione di ammissibilita' per un finanziamento o per U-Web.");
    intro.setEditable(false);intro.setLineWrap(true);intro.setWrapStyleWord(true);intro.setBackground(BG);
    intro.setFont(new Font("SansSerif",Font.PLAIN,12));intro.setRows(3);outer.add(intro,BorderLayout.NORTH);
    JScrollPane scroll=new JScrollPane(sections);scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(24);
    outer.add(scroll,BorderLayout.CENTER);return outer;
  }
  private void showLegacyMappingReview(){
    try{
      List<LegacyMapping> rows=store.getLegacyMappings().stream().filter(LegacyMapping::review).toList();
      StringBuilder content=new StringBuilder("Classificazioni precedenti non univoche.\n\n");
      for(LegacyMapping row:rows)content.append("• ").append(row.oldCategory()).append(" → ").append(row.newCategory()).append(" (provvisoria)\n");
      content.append("\nLe etichette originali sono conservate nel database. Per correggere i nuovi inserimenti, modifica la categoria dell'attivita'. " +
        "Per lo storico seleziona la riclassificazione esplicita; i mesi bloccati rimangono protetti.");
      JTextArea a=new JTextArea(content.toString(),Math.min(16,rows.size()+6),63);
      a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);
      JOptionPane.showMessageDialog(this,new JScrollPane(a),"Revisione categorie storiche",JOptionPane.INFORMATION_MESSAGE);
    }catch(SQLException ex){error(ex);}
  }

  private JPanel activityPanel() {
    JPanel p=panel(new BorderLayout(0,13));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);top.add(label("Ogni attività ha una categoria corrente; le registrazioni conservano anche la categoria storica.",13,false),BorderLayout.WEST);
    JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));buttons.setOpaque(false);
    JButton add=action("+ Aggiungi"),edit=subtle("Modifica"),archive=subtle("Archivia"),remove=subtle("Elimina");buttons.add(add);buttons.add(edit);buttons.add(archive);buttons.add(remove);top.add(buttons,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    List<Activity> sorted=new ArrayList<>(activities);sorted.removeIf(x->x.category().equals("SEGNAPOSTO SOSPENSIONE")||archivedIds.contains(x.id()));sorted.sort(Comparator.comparing(Activity::category).thenComparing(Activity::name));
    String[] names={"Attività","Categoria","Colore","Ore totali"};
    JTable t=makeTable(new AbstractTableModel(){public int getColumnCount(){return 4;}public int getRowCount(){return sorted.size();}public String getColumnName(int c){return names[c];}
      public Object getValueAt(int r,int c){Activity a=sorted.get(r);return switch(c){case 0->a.name();case 1->a.category();case 2->a.color();default->fmt(logs.stream().filter(e->e.activity().id()==a.id()&&e.hours()!=null).mapToDouble(e->e.hours()).sum());};}});
    t.setRowHeight(31);t.getColumnModel().getColumn(0).setPreferredWidth(400);t.getColumnModel().getColumn(1).setPreferredWidth(300);t.getColumnModel().getColumn(2).setPreferredWidth(130);t.getColumnModel().getColumn(3).setPreferredWidth(115);
    t.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable tab,Object v,boolean sel,boolean foc,int r,int c){super.getTableCellRendererComponent(tab,v,sel,foc,r,c);setBorder(new EmptyBorder(1,10,1,10));setForeground(INK);setBackground(sel?SELECT_BG:(c==0||c==2)?color(sorted.get(r).color()):r%2==0?WHITE:new Color(248,250,253));setForeground(sel?WHITE:INK);return this;}});
    JScrollPane sc=new JScrollPane(t);sc.setBorder(new LineBorder(LINE));p.add(sc,BorderLayout.CENTER);
    add.addActionListener(e->activityEditor(null));
    edit.addActionListener(e->{int r=t.getSelectedRow();if(r>=0)activityEditor(sorted.get(t.convertRowIndexToModel(r)));});
    archive.addActionListener(e->{int r=t.getSelectedRow();if(r<0)return;Activity a=sorted.get(t.convertRowIndexToModel(r));
      if(ask("Archiviare «"+a.name()+"»? Le ore e i documenti resteranno consultabili, ma non potrai inserire nuove ore finché non ripristini l'attività.")){
        try{store.setActivityArchived(a.id(),true);changed();}catch(Exception ex){error(ex);}
      }
    });
    remove.addActionListener(e->{int r=t.getSelectedRow();if(r<0)return;Activity a=sorted.get(t.convertRowIndexToModel(r));if(ask("Eliminare l'attività '"+a.name()+"'? È possibile solo se non ci sono registrazioni.")){try{store.deleteActivity(a.id());changed();}catch(Exception x){error(x);}}});
    t.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2){int r=t.getSelectedRow();if(r>=0)activityEditor(sorted.get(t.convertRowIndexToModel(r)));}}});
    return p;
  }
  private JPanel archivePanel(){
    JPanel panel=panel(new BorderLayout(0,12));panel.setBorder(new EmptyBorder(22,27,24,27));
    List<Activity> archived=activities.stream().filter(a->archivedIds.contains(a.id())).sorted(Comparator.comparing(Activity::name,String.CASE_INSENSITIVE_ORDER)).toList();
    JLabel intro=label("Attività archiviate esplicitamente: lo storico resta disponibile; non è possibile rendicontare nuove ore.",13,false);
    panel.add(intro,BorderLayout.NORTH);
    JTable table=makeTable(new AbstractTableModel(){public int getRowCount(){return archived.size();}public int getColumnCount(){return 3;}
      public String getColumnName(int c){return new String[]{"Attività", "Categoria", "Tipo"}[c];}
      public Object getValueAt(int r,int c){Activity a=archived.get(r);return switch(c){case 0->a.name();case 1->a.category();default->(isProject(a.category())||projectHistoryIds.contains(a.id()))?"Progetto":"Attività";};}
    });table.setRowHeight(31);
    JPanel footer=panel(new FlowLayout(FlowLayout.RIGHT));
    JButton restore=action("Ripristina attività"),details=subtle("Apri / Modifica"),remove=subtle("Elimina definitivamente");
    footer.add(details);footer.add(restore);footer.add(remove);
    restore.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;Activity a=archived.get(table.convertRowIndexToModel(r));
      if(ask("Ripristinare «"+a.name()+"» tra le attività attive?"))try{store.setActivityArchived(a.id(),false);changed();}catch(Exception ex){error(ex);}
    });
    details.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;Activity a=archived.get(table.convertRowIndexToModel(r));
      if(isProject(a.category())||projectHistoryIds.contains(a.id()))openProject(a.id());else activityEditor(a);
    });
    remove.addActionListener(e->{int r=table.getSelectedRow();if(r<0)return;Activity a=archived.get(table.convertRowIndexToModel(r));
      if(ask("Eliminare definitivamente «"+a.name()+"»? L'eliminazione è possibile soltanto se non ci sono registrazioni, colleghi, blocchi o allegati.")){
        try{store.deleteActivity(a.id());changed();}catch(Exception ex){error(ex);}
      }
    });
    panel.add(new JScrollPane(table),BorderLayout.CENTER);panel.add(footer,BorderLayout.SOUTH);return panel;
  }
  /** La modifica della categoria permette di conservare lo storico o riclassificare in modo esplicito. */
  private void activityEditor(Activity old){
    JTextField name=new JTextField(old==null?"":old.name(),27);
    JComboBox<String> cat=new JComboBox<>(categoryNames());if(old!=null)cat.setSelectedItem(old.category());
    Color initial=old==null?new Color(224,234,250):color(old.color());final Color[] chosenColor={initial};
    JButton pick=subtle("Scegli colore");JPanel swatch=new JPanel();swatch.setBackground(initial);swatch.setPreferredSize(new Dimension(45,25));
    pick.addActionListener(e->{Color c=JColorChooser.showDialog(this,"Colore attività",chosenColor[0]);if(c!=null){chosenColor[0]=c;swatch.setBackground(c);}});
    JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT));row.add(pick);row.add(swatch);
    JPanel content=new JPanel(new GridBagLayout());GridBagConstraints gbc=new GridBagConstraints();gbc.gridx=0;gbc.gridy=0;gbc.insets=new Insets(6,8,6,8);
    field(content,gbc,"Nome attività",name);field(content,gbc,"Categoria",cat);field(content,gbc,"Colore",row);
    if(JOptionPane.showConfirmDialog(this,content,old==null?"Nuova attività":"Modifica attività",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
    try{
      String activityName=name.getText().trim(),newCategory=(String)cat.getSelectedItem();
      if(activityName.isEmpty()||activityName.equalsIgnoreCase("ATTIVITÀ SOSPESE"))throw new IllegalArgumentException("Nome attività non valido.");
      boolean retroactive=false;
      if(old!=null&&!old.category().equalsIgnoreCase(newCategory)){
        long count=store.entryCount(old.id());
        if(count>0){
          String message="L'attività '"+old.name()+"' ha "+count+" registrazioni.\n\n"
            +"Come vuoi applicare la categoria '"+newCategory+"'?\n\n"
            +"• Solo nuove: le registrazioni esistenti restano nella vecchia categoria.\n"
            +"• Retroattiva: tutte le registrazioni vengono riclassificate,\n"
            +"  solo se non interessano mesi o progetti bloccati.";
          Object[] choices={"Solo nuove registrazioni","Anche quelle precedenti","Annulla"};
          int option=JOptionPane.showOptionDialog(this,message,"Cambio categoria: scegli la decorrenza",
            JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,choices,choices[0]);
          if(option!=0&&option!=1)return;
          retroactive=option==1;
        }
      }
      store.saveActivity(old==null?null:old.id(),activityName,newCategory,toHex(chosenColor[0]),retroactive);
      changed();
    }catch(Exception ex){error(ex);}
  }
  private static String toHex(Color c){return String.format("#%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue());}

  private JPanel holidayPanel() {
    JPanel p=panel(new BorderLayout(0,13));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
    top.add(label("Sospensioni per l’anno " + selectedYear + ". Weekend sempre non didattici.",13,false),BorderLayout.WEST);
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);JButton add=action("+ Sospendi un giorno"),range=subtle("+ Intervallo"),del=subtle("Rimuovi sospensione");actions.add(add);actions.add(range);actions.add(del);top.add(actions,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    List<Map.Entry<LocalDate,String>> dates=new ArrayList<>(suspensions.entrySet());dates.sort(Map.Entry.comparingByKey());
    JTable t=makeTable(new AbstractTableModel(){public int getColumnCount(){return 3;}public int getRowCount(){return dates.size();}public String getColumnName(int c){return new String[]{"Data","Giorno","Motivo"}[c];}
      public Object getValueAt(int r,int c){var item=dates.get(r);return switch(c){case 0->item.getKey().format(DISPLAY);case 1->DAYS[item.getKey().getDayOfWeek().getValue()-1];default->item.getValue();};}});
    t.getColumnModel().getColumn(0).setPreferredWidth(100);t.getColumnModel().getColumn(1).setPreferredWidth(90);t.getColumnModel().getColumn(2).setPreferredWidth(620);
    t.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable x,Object v,boolean s,boolean f,int r,int c){super.getTableCellRendererComponent(x,v,s,f,r,c);setBackground(s?SELECT_BG:r%2==0?new Color(255,238,239):WHITE);setForeground(s?WHITE:INK);setBorder(new EmptyBorder(1,9,1,9));return this;}});
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    add.addActionListener(e->{JTextField date=new JTextField(chosen.format(DISPLAY));JTextField reason=new JTextField("Sospensione attività didattiche");JPanel form=new JPanel(new GridLayout(2,2,8,8));form.add(new JLabel("Data gg/mm/aaaa"));form.add(date);form.add(new JLabel("Motivo"));form.add(reason);
      if(JOptionPane.showConfirmDialog(this,form,"Nuova sospensione",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){try{LocalDate d=LocalDate.parse(date.getText(),INPUT);if(d.getYear()!=selectedYear)throw new IllegalArgumentException("Usa una data dell’anno "+selectedYear);store.addSuspension(d,reason.getText());changed();}catch(Exception x){error(x);}}});
    range.addActionListener(e->{JTextField start=new JTextField(chosen.format(DISPLAY)),end=new JTextField(chosen.format(DISPLAY));JTextField why=new JTextField("Pausa didattica");
      JPanel form=new JPanel(new GridLayout(3,2,8,8));form.add(new JLabel("Dal gg/mm/aaaa"));form.add(start);form.add(new JLabel("Al gg/mm/aaaa"));form.add(end);form.add(new JLabel("Motivo"));form.add(why);
      if(JOptionPane.showConfirmDialog(this,form,"Sospensione su intervallo",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{
        LocalDate a=LocalDate.parse(start.getText().trim(),INPUT),b=LocalDate.parse(end.getText().trim(),INPUT);
        if(a.getYear()!=selectedYear||b.getYear()!=selectedYear||b.isBefore(a))throw new IllegalArgumentException("Inserisci un intervallo ordinato entro l’anno "+selectedYear);
        store.addSuspensionRange(a,b,why.getText());changed();
      }catch(Exception ex){error(ex);}});
    del.addActionListener(e->{int r=t.getSelectedRow();if(r>=0){var entry=dates.get(t.convertRowIndexToModel(r));if(ask("Rimuovere la sospensione del "+entry.getKey().format(DISPLAY)+"?")){try{store.removeSuspension(entry.getKey());changed();}catch(Exception x){error(x);}}}});
    p.add(label("Nei giorni sospesi e nei weekend puoi lavorare ai PROGETTI, ma non registrare didattica frontale o integrativa. I mesi bloccati non sono modificabili.",13,true),BorderLayout.SOUTH);
    return p;
  }
  private JPanel notesPanel() {
    JPanel p=panel(new BorderLayout(0,13));p.setBorder(new EmptyBorder(22,27,24,27));
    JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);top.add(label("Promemoria originali dall'Excel, con possibilità di aggiungere e modificare note.",13,false),BorderLayout.WEST);
    JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));buttons.setOpaque(false);JButton add=action("+ Nuova nota"),edit=subtle("Modifica"),del=subtle("Elimina");
    buttons.add(add);buttons.add(edit);buttons.add(del);top.add(buttons,BorderLayout.EAST);p.add(top,BorderLayout.NORTH);
    List<NoteRow> notes;
    try{notes=store.getNotes();}catch(Exception e){error(e);return p;}
    JTable t=makeTable(new AbstractTableModel(){public int getRowCount(){return notes.size();}public int getColumnCount(){return 1;}public String getColumnName(int c){return "Nota / promemoria";}
      public Object getValueAt(int r,int c){return notes.get(r).text();}});
    t.getColumnModel().getColumn(0).setPreferredWidth(800);t.setRowHeight(39);
    t.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable x,Object v,boolean sel,boolean f,int r,int c){super.getTableCellRendererComponent(x,v,sel,f,r,c);setBackground(sel?SELECT_BG:r%2==0?WHITE:new Color(249,251,254));setForeground(sel?WHITE:INK);setBorder(new EmptyBorder(2,14,2,10));return this;}});
    p.add(new JScrollPane(t),BorderLayout.CENTER);
    add.addActionListener(e->noteEditor(null));
    edit.addActionListener(e->{int r=t.getSelectedRow();if(r>=0)noteEditor(notes.get(t.convertRowIndexToModel(r)));});
    del.addActionListener(e->{int r=t.getSelectedRow();if(r>=0){NoteRow n=notes.get(t.convertRowIndexToModel(r));if(ask("Eliminare definitivamente questa nota?")){try{store.deleteNote(n.id());changed();}catch(Exception x){error(x);}}}});
    t.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2){int r=t.getSelectedRow();if(r>=0)noteEditor(notes.get(t.convertRowIndexToModel(r)));}}});
    return p;
  }
  private void noteEditor(NoteRow old){JTextArea text=new JTextArea(old==null?"":old.text(),8,52);text.setLineWrap(true);text.setWrapStyleWord(true);
    if(JOptionPane.showConfirmDialog(this,new JScrollPane(text),old==null?"Nuova nota":"Modifica nota",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
      try{if(text.getText().trim().isEmpty())throw new IllegalArgumentException("La nota non può essere vuota.");store.saveNote(old==null?null:old.id(),text.getText().trim());changed();}catch(Exception e){error(e);}
    }
  }
  /** Cronologia trasparente e in sola lettura di tutte le modifiche importanti. */
  private JPanel auditPanel(){
    JPanel base=panel(new BorderLayout(0,11));base.setBorder(new EmptyBorder(19,22,20,22));
    JPanel head=panel(new BorderLayout(6,5));
    JLabel expl=label("Chi ha cambiato cosa e quando. Seleziona una riga per vedere valori precedenti e nuovi.",12,false);
    head.add(expl,BorderLayout.NORTH);
    JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,5));controls.setOpaque(false);
    JTextField filter=new JTextField(22);
    JComboBox<String> type=new JComboBox<>(new String[]{"Tutte le modifiche","Ore personali","Ore colleghi","Attività e categorie","Progetti","Documenti","Altro"});
    JButton update=subtle("Aggiorna"),export=subtle("Esporta CSV");
    controls.add(label("Cerca",12,true));controls.add(filter);controls.add(type);controls.add(update);controls.add(export);
    head.add(controls,BorderLayout.SOUTH);base.add(head,BorderLayout.NORTH);
    DefaultTableModel model=new DefaultTableModel(new Object[]{"Data e ora","Utente","Operazione","Elemento","ID"},0){public boolean isCellEditable(int r,int c){return false;}};
    JTable table=makeTable(model);table.setAutoCreateRowSorter(true);table.setRowHeight(27);
    table.getColumnModel().getColumn(0).setPreferredWidth(155);
    table.getColumnModel().getColumn(1).setPreferredWidth(145);
    table.getColumnModel().getColumn(3).setPreferredWidth(180);
    JTextArea detail=new JTextArea(9,50);detail.setFont(new Font("Monospaced",Font.PLAIN,12));detail.setEditable(false);
    detail.setBorder(new EmptyBorder(9,9,9,9));detail.setLineWrap(true);detail.setWrapStyleWord(true);
    JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(detail));
    split.setResizeWeight(0.72);split.setDividerLocation(440);base.add(split,BorderLayout.CENTER);
    List<AuditRecord> entries;
    try{entries=store.auditEntries(3000);}catch(Exception ex){error(ex);entries=List.of();}
    final List<AuditRecord> all=entries;
    List<AuditRecord> showing=new ArrayList<>();
    Runnable refresh=()->{
      showing.clear();model.setRowCount(0);
      String q=filter.getText().strip().toLowerCase(Locale.ROOT);int group=type.getSelectedIndex();
      for(AuditRecord e:all){
        boolean match=switch(group){
          case 1 -> e.entity().equals("entries");
          case 2 -> e.entity().equals("project_person_hours");
          case 3 -> e.entity().equals("activities")||e.entity().equals("categories");
          case 4 -> e.entity().startsWith("project_")&&!e.entity().equals("project_person_hours")&&!e.entity().equals("project_documents");
          case 5 -> e.entity().equals("project_documents");
          case 6 -> !List.of("entries","project_person_hours","activities","categories").contains(e.entity())&&!e.entity().startsWith("project_");
          default -> true;
        };
        if(!match)continue;
        String hay=(e.when()+" "+e.who()+" "+e.event()+" "+e.entity()+" "+e.rowKey()+" "+e.before()+" "+e.after()).toLowerCase(Locale.ROOT);
        if(!q.isBlank()&&!hay.contains(q))continue;
        showing.add(e);model.addRow(new Object[]{e.when(),e.who(),e.event(),friendlyAuditEntity(e.entity()),e.rowKey()});
      }
      detail.setText("Registrazioni mostrate: "+showing.size()+" (max 3.000 eventi recenti).\n");
    };
    type.addActionListener(e->refresh.run());update.addActionListener(e->showPage("Storico modifiche"));
    filter.getDocument().addDocumentListener(new DocumentListener(){
      public void insertUpdate(DocumentEvent e){refresh.run();}
      public void removeUpdate(DocumentEvent e){refresh.run();}
      public void changedUpdate(DocumentEvent e){refresh.run();}
    });
    table.getSelectionModel().addListSelectionListener(e->{if(e.getValueIsAdjusting())return;
      int row=table.getSelectedRow();if(row<0)return;int i=table.convertRowIndexToModel(row);if(i<0||i>=showing.size())return;
      AuditRecord a=showing.get(i);
      detail.setText("EVENTO #"+a.id()+" — "+a.event()+" / "+friendlyAuditEntity(a.entity())+
        "\nQuando: "+a.when()+" | Utente: "+a.who()+" | Record: "+a.rowKey()+
        "\n\nPRIMA\n"+(a.before()==null?"—":a.before())+"\n\nDOPO\n"+(a.after()==null?"—":a.after()));
      detail.setCaretPosition(0);
    });
    export.addActionListener(e->{JFileChooser picker=new JFileChooser();picker.setSelectedFile(new File("registro_ore_storico_modifiche.csv"));
      if(picker.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
      try(BufferedWriter writer=Files.newBufferedWriter(picker.getSelectedFile().toPath(),StandardCharsets.UTF_8)){
        writer.write("ID;Data UTC;Utente;Operazione;Tabella;Record;Prima;Dopo\n");
        for(AuditRecord a:showing){writer.write(a.id()+";"+csv(a.when())+";"+csv(a.who())+";"+csv(a.event())+";"+csv(a.entity())+";"+csv(a.rowKey())+";"+csv(a.before())+";"+csv(a.after())+"\n");}
        JOptionPane.showMessageDialog(this,"Storico esportato correttamente.");
      }catch(Exception x){error(x);}
    });
    refresh.run();return base;
  }
  private static String friendlyAuditEntity(String s){return switch(s){
    case "entries" -> "Ore personali";case "project_person_hours" -> "Ore colleghi";
    case "activities"->"Attività";case "categories"->"Categorie";
    case "project_documents"->"Documenti";case "project_details"->"Dati progetto";
    case "project_month_locks"->"Blocchi progetto";case "month_locks"->"Blocchi mensili";
    case "profile"->"Profilo";case "annual_settings"->"Annualità";
    case "person_role_history"->"Ruoli colleghi";case "own_role_history"->"Ruoli personali";
    default -> s.replace('_',' ');
  };}

  /** Lists the 30 daily snapshots of the database that is ACTUALLY opened. */
  private JPanel backupsPanel(){
    JPanel p=panel(new BorderLayout(0,12));p.setBorder(new EmptyBorder(22,26,24,26));
    Path dbFile=store.databasePath;
    JLabel info=label("Database attivo: "+dbFile.toAbsolutePath(),12,false);
    info.setToolTipText(dbFile.toString());
    JPanel header=panel(new BorderLayout(0,8));header.add(label("Copie giornaliere SQLite - ultime 30 giornate",17,true),BorderLayout.NORTH);
    header.add(info,BorderLayout.SOUTH);p.add(header,BorderLayout.NORTH);
    DefaultTableModel model=new DefaultTableModel(new Object[]{"Data","File di backup","Dimensione"},0){public boolean isCellEditable(int r,int c){return false;}};
    JTable table=makeTable(model);table.setRowHeight(29);table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(1).setPreferredWidth(490);
    List<Path> snapshots=new ArrayList<>();
    Runnable refresh=()->{try{snapshots.clear();snapshots.addAll(DatabaseBackups.files(dbFile));model.setRowCount(0);
      for(Path f:snapshots)model.addRow(new Object[]{f.getFileName().toString().replace("registro_ore_","").replace(".sqlite",""),f.getFileName().toString(),String.format(Locale.ITALY,"%.2f MB",Files.size(f)/1048576.0)});
    }catch(Exception ex){error(ex);}};
    p.add(new JScrollPane(table),BorderLayout.CENTER);
    JPanel buttons=panel(new FlowLayout(FlowLayout.LEFT,9,4));
    JButton now=action("Crea copia adesso"),restore=subtle("Ripristina copia selezionata"),open=subtle("Apri cartella backup"),again=subtle("Aggiorna elenco");
    buttons.add(now);buttons.add(restore);buttons.add(open);buttons.add(again);
    now.addActionListener(e->{try{DatabaseBackups.daily(dbFile,true);refresh.run();JOptionPane.showMessageDialog(this,"Backup SQLite creato e verificato.");}catch(Exception ex){error(ex);}});
    restore.addActionListener(e->{int row=table.getSelectedRow();if(row<0)return;
      Path selected=snapshots.get(table.convertRowIndexToModel(row));
      if(!ask("Ripristinare la copia del "+selected.getFileName()+"?\nQuesto sostituira il database attivo. Verra creata prima una copia di sicurezza dell'attuale database."))return;
      if(!ask("Confermi il ripristino? Le modifiche successive alla copia scelta andranno perse nel database attivo, ma resteranno nel backup di sicurezza."))return;
      try{
        DatabaseBackups.verify(selected);
        Path before=DatabaseBackups.preRestore(dbFile);
        restoringBackup.set(true);
        if(dailyBackupExecutor!=null)dailyBackupExecutor.shutdownNow();
        if(clockTimer!=null)clockTimer.stop();
        store.close();
        DatabaseBackups.replaceAfterClose(dbFile,selected);
        JOptionPane.showMessageDialog(this,"Ripristino completato.\nCopia di sicurezza precedente: "+before+"\nChiudi e riapri Registro Ore.");
        System.exit(0);
      }catch(Exception ex){JOptionPane.showMessageDialog(this,"Ripristino non completato: "+ex.getMessage()+"\nRiavvia Registro Ore. Nessun backup e stato eliminato.","Errore",JOptionPane.ERROR_MESSAGE);System.exit(2);}
    });
    open.addActionListener(e->{try{Files.createDirectories(DatabaseBackups.folder(dbFile));Desktop.getDesktop().open(DatabaseBackups.folder(dbFile).toFile());}catch(Exception ex){error(ex);}});
    again.addActionListener(e->refresh.run());
    JPanel bottom=panel(new BorderLayout());bottom.add(buttons,BorderLayout.NORTH);
    bottom.add(label("Le copie vengono aggiornate automaticamente ogni 10 minuti durante l'uso, e alla chiusura. Non sostituiscono il database attivo.",12,false),BorderLayout.SOUTH);
    p.add(bottom,BorderLayout.SOUTH);refresh.run();return p;
  }

  private void backup(){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("backup_registro_ore_tutte_le_annualita.sqlite"));if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){try{store.backup(chooser.getSelectedFile().toPath());JOptionPane.showMessageDialog(this,"Backup salvato correttamente.");}catch(Exception ex){error(ex);}}}
  /** Include le tue ore e quelle dei collaboratori per un singolo progetto, anche su piu annualita. */
  private void exportProjectDialog(Activity selected){
    List<Activity> projects=activities.stream().filter(a->isProject(a.category())||projectHistoryIds.contains(a.id())).toList();
    if(projects.isEmpty()){JOptionPane.showMessageDialog(this,"Non ci sono progetti da esportare.");return;}
    JComboBox<String> picker=new JComboBox<>(projects.stream().map(Activity::name).toArray(String[]::new));
    if(selected!=null)picker.setSelectedItem(selected.name());
    JTextField from=new JTextField(LocalDate.of(selectedYear,1,1).format(DISPLAY),12);
    JTextField until=new JTextField(LocalDate.of(selectedYear,12,31).format(DISPLAY),12);
    JComboBox<String> format=new JComboBox<>(new String[]{"Excel (.xlsx)","CSV (.csv)"});
    JPanel settings=panel(new GridLayout(0,2,9,9));
    settings.add(new JLabel("Progetto"));settings.add(picker);
    settings.add(new JLabel("Dal (gg/mm/aaaa)"));settings.add(from);
    settings.add(new JLabel("Al (gg/mm/aaaa)"));settings.add(until);
    settings.add(new JLabel("Formato"));settings.add(format);
    if(JOptionPane.showConfirmDialog(this,settings,"Esporta rendicontazione progetto",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
    try{
      LocalDate begin=LocalDate.parse(from.getText().trim(),INPUT),end=LocalDate.parse(until.getText().trim(),INPUT);
      if(end.isBefore(begin))throw new IllegalArgumentException("La data finale precede quella iniziale.");
      Activity project=projects.get(picker.getSelectedIndex());
      List<ProjectExportRow> rows=store.projectExportRows(project.id(),begin,end);
      boolean excel=format.getSelectedIndex()==0;String ext=excel?".xlsx":".csv";
      FileDialog choose=new FileDialog(this,"Esporta ore - "+project.name(),FileDialog.SAVE);
      choose.setFile("rendicontazione_"+project.name().replaceAll("[^A-Za-z0-9_-]+","_")+"_"+begin+"_"+end+ext);
      choose.setVisible(true);
      if(choose.getFile()==null)return;
      Path output=Paths.get(choose.getDirectory(),choose.getFile());
      if(!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext))output=output.resolveSibling(output.getFileName()+ext);
      if(Files.exists(output)&&!ask("Sovrascrivere "+output.getFileName()+"?"))return;
      if(excel)ProjectReportXlsx.write(output,project.name(),begin,end,rows);
      else ProjectReportXlsx.writeCsv(output,project.name(),begin,end,rows);
      double total=rows.stream().mapToDouble(ProjectExportRow::hours).sum();
      JOptionPane.showMessageDialog(this,"Esportate "+rows.size()+" registrazioni - "+fmt(total)+" ore.\n"+output.toAbsolutePath(),
        "Esportazione progetto completata",JOptionPane.INFORMATION_MESSAGE);
    }catch(Exception ex){error(ex);}
  }
  /** Esporta TUTTO il registro (incluse le registrazioni senza ore), per mese o anno. */
  private void exportRegister(){
    JComboBox<String> period=new JComboBox<>();
    period.addItem("Intero anno "+selectedYear);
    for(String month:MONTHS)period.addItem(month+" "+selectedYear);
    if(page.equals("Registro"))period.setSelectedIndex(chosen.getMonthValue());
    JComboBox<String> format=new JComboBox<>(new String[]{"Excel (.xlsx)","CSV (.csv)"});
    JPanel options=new JPanel(new GridLayout(0,1,4,5));
    options.add(new JLabel("Quali registrazioni esportare?"));options.add(period);
    options.add(new JLabel("Formato del file"));options.add(format);
    if(JOptionPane.showConfirmDialog(this,options,"Esporta registro",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
    int selectedMonth=period.getSelectedIndex();
    boolean excel=format.getSelectedIndex()==0;
    List<LogEntry> selected=logs.stream().filter(e->selectedMonth==0||e.date().getMonthValue()==selectedMonth).toList();
    String slug=selectedMonth==0?"anno_"+selectedYear:SHORT_MONTHS[selectedMonth-1].toLowerCase(Locale.ITALY)+"_"+selectedYear;
    String suffix=excel?".xlsx":".csv";
    // Anche il salvataggio sfrutta la finestra nativa del sistema operativo.
    FileDialog chooser=new FileDialog(this,"Salva registro "+(selectedMonth==0?"annuale":"mensile"),FileDialog.SAVE);
    chooser.setFile("registro_ore_"+slug+suffix);
    chooser.setVisible(true);
    if(chooser.getFile()==null)return;
    Path destination=Paths.get(chooser.getDirectory(),chooser.getFile());
    if(!destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
      destination=destination.resolveSibling(destination.getFileName()+suffix);
    if(Files.exists(destination)&&!ask("Il file esiste già: sovrascrivere "+destination.getFileName()+"?"))return;
    try{
      if(excel)SimpleXlsx.write(destination,selectedYear,selectedMonth,selected);
      else writeCsv(destination,selected);
      JOptionPane.showMessageDialog(this,"Esportate "+selected.size()+" registrazioni in:\n"+destination.toAbsolutePath(),
        "Esportazione completata",JOptionPane.INFORMATION_MESSAGE);
    }catch(Exception ex){error(ex);}
  }
  static void writeCsv(Path destination,List<LogEntry> selected) throws IOException{
    try(Writer writer=new OutputStreamWriter(Files.newOutputStream(destination),StandardCharsets.UTF_8)){
      writer.write('\ufeff');writer.write("Data;Giorno;Attività;Categoria;Ore;Note;Origine;ID missione\n");
      for(LogEntry log:selected){String[] parts={log.date().format(DISPLAY), DAYS[log.date().getDayOfWeek().getValue()-1],
        log.activity().name(),log.activity().category(),log.hours()==null?"":fmt(log.hours()),log.note(),log.source()==null?"":log.source(),
        log.missionId()==null?"":log.missionId().toString()};
        writer.write(Arrays.stream(parts).map(App::csv).collect(Collectors.joining(";"))+"\n");
      }
    }
  }
  private static String csv(String s){return "\""+(s==null?"":s).replace("\"","\"\"")+"\"";}

  private static final class Store implements AutoCloseable {
    private static final List<String> TRACKED_TABLES = List.of(
      "activities", "categories", "profile", "annual_settings", "month_locks", "entries",
      "suspensions", "notes", "project_details", "project_people", "project_person_hours",
      "project_documents", "project_month_locks", "own_role_history", "person_role_history",
      "missions", "mission_documents");
    private final Connection db;
    private final Path databasePath;
    Store(Path path) throws Exception {
      databasePath=path.toAbsolutePath();
      try{Class.forName("org.sqlite.JDBC");}catch(ClassNotFoundException e){throw new IllegalStateException("Manca la libreria SQLite JDBC. Avvia con AVVIA_MAC.command per scaricare le librerie automaticamente.",e);}
      db=DriverManager.getConnection("jdbc:sqlite:"+path);try(Statement s=db.createStatement()){s.execute("PRAGMA foreign_keys = ON");s.execute("PRAGMA busy_timeout = 5000");}
    }
    /**
     * Timestamp persistente aggiornato automaticamente, in una stessa transazione delle modifiche.
     * TEMP TRIGGER: riguarda solo la connessione di questa app e NON il backup SQLite esterno
     * né le migrazioni avvenute prima dell'installazione dei trigger.
     * Un rollback annulla anche il timestamp. Il valore è UTC e viene mostrato nell'ora locale.
     */
    void installSaveTracking() throws SQLException {
      try(Statement st=db.createStatement()) {
        st.execute("CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        for(String table:TRACKED_TABLES) {
          boolean exists;
          try(PreparedStatement q=db.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            q.setString(1,table);try(ResultSet r=q.executeQuery()){exists=r.next();}
          }
          if(!exists)continue;
          for(String event:new String[]{"INSERT","UPDATE","DELETE"}) {
            String trigger="temp_last_save_"+table+"_"+event.toLowerCase(Locale.ROOT);
            st.execute("CREATE TEMP TRIGGER IF NOT EXISTS " + trigger + " AFTER " + event + " ON main." + table + " BEGIN " +
              "INSERT INTO app_metadata(key,value) VALUES('last_user_save_at',strftime('%Y-%m-%dT%H:%M:%fZ','now')) " +
              "ON CONFLICT(key) DO UPDATE SET value=excluded.value; END");
          }
        }
      }
    }
    String lastSavedAt() throws SQLException {
      try(PreparedStatement q=db.prepareStatement("SELECT value FROM app_metadata WHERE key='last_user_save_at'");ResultSet r=q.executeQuery()) {
        return r.next()?r.getString(1):null;
      }
    }
    /** Audit persistente nella stessa transazione dei dati (e quindi incluso nei backup). */
    void installAuditTracking() throws SQLException {
      try(Statement st=db.createStatement()) {
        st.execute("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
          "changed_at TEXT NOT NULL, actor TEXT NOT NULL, operation TEXT NOT NULL,"+
          "entity TEXT NOT NULL, record_key TEXT NOT NULL, before_json TEXT, after_json TEXT)");
        st.execute("CREATE INDEX IF NOT EXISTS ix_audit_changed_at ON audit_log(id DESC)");
        for(String table:TRACKED_TABLES){
          boolean exists=false;
          try(PreparedStatement q=db.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")){
            q.setString(1,table);try(ResultSet r=q.executeQuery()){exists=r.next();}
          }
          if(!exists)continue;
          List<String> cols=new ArrayList<>();
          try(ResultSet r=st.executeQuery("PRAGMA table_info(\""+table+"\")")){
            while(r.next()){
              String col=r.getString("name");String datatype=Objects.toString(r.getString("type"),"").toUpperCase(Locale.ROOT);
              if(!datatype.contains("BLOB")&&!col.equalsIgnoreCase("content")&&!col.equalsIgnoreCase("file_data"))cols.add(col);
            }
          }
          for(String op:new String[]{"INSERT","UPDATE","DELETE"}){
            String name="audit_v310_"+table+"_"+op.toLowerCase(Locale.ROOT);
            if(table.equals("activities")||table.equals("project_details"))st.execute("DROP TRIGGER IF EXISTS "+name);
            String oldJson=columnsJson(cols,"OLD",table),newJson=columnsJson(cols,"NEW",table);
            String before=op.equals("INSERT")?"NULL":oldJson;
            String after=op.equals("DELETE")?"NULL":newJson;
            String recordId=(op.equals("DELETE")?"OLD":"NEW")+".rowid";
            String extra=op.equals("UPDATE")?" WHEN "+oldJson+" IS NOT "+newJson:"";
            st.execute("CREATE TRIGGER IF NOT EXISTS "+name+" AFTER "+op+" ON "+table+extra+
              " BEGIN INSERT INTO audit_log(changed_at,actor,operation,entity,record_key,before_json,after_json) VALUES("+
              "strftime('%Y-%m-%dT%H:%M:%fZ','now'),"+
              "COALESCE((SELECT NULLIF(TRIM(full_name),'') FROM profile WHERE id=1),'Utente locale'),"+
              "'"+op+"','"+table+"',CAST("+recordId+" AS TEXT),"+before+","+after+"); END");
          }
        }
      }
    }
    private static String columnsJson(List<String> columns,String prefix,String table){
      StringJoiner j=new StringJoiner(",","json_object(",")");
      for(String c:columns){j.add("'"+c.replace("'","''")+"'");j.add(prefix+".\""+c.replace("\"","\"\"")+"\"");}
      if("entries".equals(table)){
        j.add("'nome_attivita'");j.add("(SELECT name FROM activities WHERE id="+prefix+".activity_id)");
      } else if("project_person_hours".equals(table)) {
        j.add("'collega'");j.add("(SELECT name FROM project_people WHERE id="+prefix+".person_id)");
      }
      return j.toString();
    }
    List<AuditRecord> auditEntries(int maximum) throws SQLException {
      List<AuditRecord> result=new ArrayList<>();
      try(PreparedStatement q=db.prepareStatement("SELECT id,changed_at,actor,operation,entity,record_key,before_json,after_json FROM audit_log ORDER BY id DESC LIMIT ?")){
        q.setInt(1,Math.min(Math.max(maximum,1),10000));
        try(ResultSet r=q.executeQuery()){while(r.next())result.add(new AuditRecord(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)));}
      }return result;
    }
    /** Migrazione non distruttiva: aggiunge solo tabelle/trigger necessari. */
    void migrate() throws SQLException {
      boolean fresh;
      try(ResultSet r=db.getMetaData().getTables(null,null,"annual_settings",null)){fresh=!r.next();}
      boolean roleHistoryMissing;
      try(ResultSet r=db.getMetaData().getTables(null,null,"own_role_history",null)){roleHistoryMissing=!r.next();}
      if(roleHistoryMissing){try(Statement safety=db.createStatement()){
        Path backup=Paths.get(databasePath.toString()+".backup-prima-storico-ruoli.sqlite");
        if(!Files.exists(backup))safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
      }catch(Exception ex){throw new SQLException("Impossibile salvare backup pre-migrazione ruoli: "+ex.getMessage(),ex);}}
      // Prima della prima migrazione 3.7, salvare una copia consistente indipendente dai dati personali.
      Path backupCategories=Paths.get(databasePath.toString()+".backup-prima-categorie-v37.sqlite");
      if(!Files.exists(backupCategories)){
        try(Statement safety=db.createStatement()){
          safety.execute("VACUUM INTO '"+backupCategories.toAbsolutePath().toString().replace("'","''")+"'");
        }catch(Exception ex){throw new SQLException("Backup prima della semplificazione categorie non riuscito: "+ex.getMessage(),ex);}
      }
      // Backup prima della migrazione dei gruppi di rendicontazione.
      if(!hasColumn("activities","account_group")){
        try(Statement safety=db.createStatement()){
          Path backup=Paths.get(databasePath.toString()+".backup-prima-gruppi-v36.sqlite");
          if(!Files.exists(backup))safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
        }catch(Exception ex){throw new SQLException("Backup prima della nuova gestione gruppi non riuscito: "+ex.getMessage(),ex);}
      }
      // Prima di aggiungere i sottogruppi, conserviamo una copia consistente del database.
      if(!hasColumn("activities","subgroup")){
        try(Statement safety=db.createStatement()){
          Path backup=Paths.get(databasePath.toString()+".backup-prima-sottogruppi-v35.sqlite");
          if(!Files.exists(backup))safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
        }catch(Exception ex){throw new SQLException("Backup prima dei sottogruppi non riuscito: "+ex.getMessage(),ex);}
      }
      boolean firstProjectMigration;
      try(ResultSet r=db.getMetaData().getTables(null,null,"project_details",null)){firstProjectMigration=!r.next();}
      boolean projectV34Missing;
      try(ResultSet r=db.getMetaData().getTables(null,null,"project_month_locks",null)){projectV34Missing=!r.next();}
      if(projectV34Missing){try(Statement safety=db.createStatement()){
        Path backup=Paths.get(databasePath.toString()+".backup-prima-progetti-v34.sqlite");
        if(!Files.exists(backup))safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
      }catch(Exception ex){throw new SQLException("Impossibile effettuare il backup prima dell'aggiornamento progetti: "+ex.getMessage(),ex);}}
      // Copia di sicurezza prima della prima migrazione alle schede progetto.
      if(firstProjectMigration){try(Statement safety=db.createStatement()){
        Path backup=Paths.get(databasePath.toString()+".backup-prima-schede-progetto.sqlite");
        if(!Files.exists(backup)){String safe=backup.toAbsolutePath().toString().replace("'","''");safety.execute("VACUUM INTO '"+safe+"'");}
      }catch(Exception ex){throw new SQLException("Impossibile creare backup prima delle schede progetto: "+ex.getMessage(),ex);}}
      if(fresh){try(Statement s=db.createStatement()){
        Path backup=Paths.get(databasePath.toString() + ".backup-prima-multianno.sqlite");
        if(!Files.exists(backup)){String safe=backup.toAbsolutePath().toString().replace("'","''");s.execute("VACUUM INTO '"+safe+"'");}
      }catch(Exception ex){throw new SQLException("Impossibile creare il backup automatico prima della migrazione: " + ex.getMessage(),ex);}}
      // Backup consistente, una sola volta, prima di associare la categoria a ogni registrazione.
      if(!hasColumn("entries","category_snapshot")){
        Path backup=Paths.get(databasePath.toString()+".backup-prima-categorie-storiche-v38.sqlite");
        if(!Files.exists(backup))try(Statement safety=db.createStatement()){
          safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
        }catch(Exception ex){throw new SQLException("Backup prima della migrazione storica non riuscito: "+ex.getMessage(),ex);}
      }
      if(!hasColumn("activities","archived_at")){
        Path backup=Paths.get(databasePath.toString()+".backup-prima-archivio-v3103.sqlite");
        if(!Files.exists(backup))try(Statement safety=db.createStatement()){
          safety.execute("VACUUM INTO '"+backup.toAbsolutePath().toString().replace("'","''")+"'");
        }catch(Exception ex){throw new SQLException("Backup prima dell'archiviazione non riuscito: "+ex.getMessage(),ex);}
      }
      try(Statement st=db.createStatement()){
        if(!hasColumn("activities","archived_at"))st.execute("ALTER TABLE activities ADD COLUMN archived_at TEXT");
        // Non cambiare le tabelle storiche: solo attributi aggiuntivi del progetto.
        // Le migrazioni delle etichette storiche non toccano ore, date o note.
        // I trigger legacy bloccavano qualsiasi UPDATE, anche la sola etichetta.
        // Vengono ricreati subito sotto per proteggere soltanto i campi rendicontabili.
        st.execute("DROP TRIGGER IF EXISTS entries_lock_update");
        st.execute("DROP TRIGGER IF EXISTS project_entries_update");
        if(!hasColumn("entries","category_snapshot"))st.execute("ALTER TABLE entries ADD COLUMN category_snapshot TEXT");
        st.execute("UPDATE entries SET category_snapshot=(SELECT category FROM activities WHERE activities.id=entries.activity_id) WHERE category_snapshot IS NULL");
        st.execute("CREATE INDEX IF NOT EXISTS idx_entries_snapshot ON entries(category_snapshot)");
        st.execute("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY COLLATE NOCASE,kind TEXT NOT NULL CHECK(kind IN ('DIDATTICA FRONTALE','DIDATTICA INTEGRATIVA','PROGETTI','ALTRE ATTIVITÀ')),system INTEGER NOT NULL DEFAULT 0 CHECK(system IN (0,1)))");
        // Da 3.7 in poi nessuna categoria deve essere ricreata automaticamente:
        // anche le quattro etichette storiche sono eliminabili dall'utente.
        st.execute("INSERT OR IGNORE INTO categories(name,kind,system) SELECT DISTINCT category,CASE WHEN category IN ('DIDATTICA FRONTALE','DIDATTICA INTEGRATIVA','PROGETTI','ALTRE ATTIVITÀ') THEN category ELSE 'ALTRE ATTIVITÀ' END,0 FROM activities WHERE category NOT IN (SELECT name FROM categories)");
        // I campi legacy account_group e subgroup possono rimanere nel vecchio database
        // esclusivamente per compatibilita storica: nessuna logica li legge o li scrive.
        st.execute("CREATE TABLE IF NOT EXISTS profile (id INTEGER PRIMARY KEY CHECK(id=1), full_name TEXT NOT NULL DEFAULT '', default_role TEXT NOT NULL DEFAULT 'Da impostare')");
        st.execute("INSERT OR IGNORE INTO profile(id,full_name,default_role) VALUES (1,'','Da impostare')");
        st.execute("CREATE TABLE IF NOT EXISTS annual_settings (year INTEGER PRIMARY KEY, max_hours REAL NOT NULL DEFAULT 1500 CHECK(max_hours = 1500), annual_note TEXT NOT NULL DEFAULT '', role TEXT NOT NULL DEFAULT 'Da impostare')");
        st.execute("INSERT OR IGNORE INTO annual_settings(year) VALUES(2026)");
        st.execute("INSERT OR IGNORE INTO annual_settings(year) SELECT DISTINCT CAST(substr(work_date,1,4) AS INTEGER) FROM entries");
        st.execute("INSERT OR IGNORE INTO annual_settings(year) SELECT DISTINCT CAST(substr(work_date,1,4) AS INTEGER) FROM suspensions");
        st.execute("CREATE TABLE IF NOT EXISTS own_role_history (id INTEGER PRIMARY KEY AUTOINCREMENT,start_date TEXT NOT NULL UNIQUE, role TEXT NOT NULL CHECK(role IN ('Ordinario','Associato','Ricercatore')))");
        st.execute("CREATE TABLE IF NOT EXISTS person_role_history (id INTEGER PRIMARY KEY AUTOINCREMENT,person_id INTEGER NOT NULL REFERENCES project_people(id) ON DELETE CASCADE,start_date TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('Ordinario','Associato','Ricercatore')),UNIQUE(person_id,start_date))");
        st.execute("CREATE INDEX IF NOT EXISTS idx_person_role_date ON person_role_history(person_id,start_date)");
        st.execute("CREATE TABLE IF NOT EXISTS month_locks (year INTEGER NOT NULL REFERENCES annual_settings(year), month INTEGER NOT NULL CHECK(month BETWEEN 1 AND 12), reason TEXT NOT NULL DEFAULT '', locked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(year,month))");
        st.execute("CREATE TABLE IF NOT EXISTS project_details (activity_id INTEGER PRIMARY KEY REFERENCES activities(id) ON DELETE CASCADE, type TEXT NOT NULL DEFAULT '',funder TEXT NOT NULL DEFAULT '',project_code TEXT NOT NULL DEFAULT '',project_role TEXT NOT NULL DEFAULT '',start_year INTEGER,start_month INTEGER CHECK(start_month BETWEEN 1 AND 12),end_year INTEGER,end_month INTEGER CHECK(end_month BETWEEN 1 AND 12),notes TEXT NOT NULL DEFAULT '')");
        if(!hasColumn("project_details","funding_label"))st.execute("ALTER TABLE project_details ADD COLUMN funding_label TEXT NOT NULL DEFAULT ''");
        if(!hasColumn("project_details","acronym"))st.execute("ALTER TABLE project_details ADD COLUMN acronym TEXT NOT NULL DEFAULT ''");
        if(!hasColumn("project_details","full_name"))st.execute("ALTER TABLE project_details ADD COLUMN full_name TEXT NOT NULL DEFAULT ''");
        st.execute("CREATE TABLE IF NOT EXISTS project_people (id INTEGER PRIMARY KEY AUTOINCREMENT,activity_id INTEGER NOT NULL REFERENCES activities(id) ON DELETE CASCADE,full_name TEXT NOT NULL,qualification TEXT NOT NULL DEFAULT '',project_role TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',UNIQUE(activity_id,full_name COLLATE NOCASE))");
        st.execute("CREATE TABLE IF NOT EXISTS project_person_hours (id INTEGER PRIMARY KEY AUTOINCREMENT,person_id INTEGER NOT NULL REFERENCES project_people(id) ON DELETE RESTRICT,work_date TEXT NOT NULL,hours REAL NOT NULL CHECK(hours>0 AND hours<=24),note TEXT NOT NULL DEFAULT '')");
        st.execute("CREATE INDEX IF NOT EXISTS idx_project_hours_date ON project_person_hours(work_date,person_id)");
        st.execute("CREATE TABLE IF NOT EXISTS project_documents (id INTEGER PRIMARY KEY AUTOINCREMENT,activity_id INTEGER NOT NULL REFERENCES activities(id) ON DELETE CASCADE,filename TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',period_year INTEGER,period_month INTEGER CHECK(period_month BETWEEN 1 AND 12),file_size INTEGER NOT NULL DEFAULT 0,contents BLOB NOT NULL,uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS project_month_locks (activity_id INTEGER NOT NULL REFERENCES activities(id) ON DELETE CASCADE,year INTEGER NOT NULL REFERENCES annual_settings(year),month INTEGER NOT NULL CHECK(month BETWEEN 1 AND 12),reason TEXT NOT NULL DEFAULT '',locked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(activity_id,year,month))");
        // Schema 3.4: aggiunge colonne senza ricreare la tabella e senza modificare le ore già inserite.
        if(!hasColumn("project_details","start_date"))st.execute("ALTER TABLE project_details ADD COLUMN start_date TEXT");
        if(!hasColumn("project_details","end_date"))st.execute("ALTER TABLE project_details ADD COLUMN end_date TEXT");
        if(!hasColumn("project_details","closed"))st.execute("ALTER TABLE project_details ADD COLUMN closed INTEGER NOT NULL DEFAULT 0 CHECK(closed IN (0,1))");
        if(!hasColumn("project_details","closed_at"))st.execute("ALTER TABLE project_details ADD COLUMN closed_at TEXT");
        // Le vecchie date, definite solo per mese, sono mantenute come intervalli del mese completo.
        st.execute("UPDATE project_details SET start_date=printf('%04d-%02d-01',start_year,start_month) WHERE start_date IS NULL AND start_year IS NOT NULL AND start_month IS NOT NULL");
        st.execute("UPDATE project_details SET end_date=date(printf('%04d-%02d-01',end_year,end_month),'+1 month','-1 day') WHERE end_date IS NULL AND end_year IS NOT NULL AND end_month IS NOT NULL");
        // Seed existing annual roles at 1 January and historic colleague qualifications.
        // INSERT OR IGNORE ensures the migration is safe to run repeatedly.
        if(roleHistoryMissing)st.execute("INSERT OR IGNORE INTO own_role_history(start_date,role) SELECT printf('%04d-01-01',year),role FROM annual_settings WHERE role IN ('Ordinario','Associato','Ricercatore')");
        if(roleHistoryMissing)st.execute("INSERT OR IGNORE INTO person_role_history(person_id,start_date,role) SELECT id,'1900-01-01',qualification FROM project_people WHERE qualification IN ('Ordinario','Associato','Ricercatore')");
        // Ricrea i trigger parametrizzati dal tipo categoria (3.3), non dal suo nome visibile.
        for(String triggerName:new String[]{"own_roles_locked_insert","own_roles_locked_update","own_roles_locked_delete","annual_role_lock_update"}){
          st.execute("DROP TRIGGER IF EXISTS " + triggerName);
        }
        st.execute("CREATE TRIGGER IF NOT EXISTS own_roles_locked_insert BEFORE INSERT ON own_role_history WHEN EXISTS(SELECT 1 FROM entries e JOIN activities a ON a.id=e.activity_id JOIN month_locks l ON l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER) WHERE COALESCE(e.category_snapshot,a.category) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND e.hours IS NOT NULL AND e.work_date>=NEW.start_date) BEGIN SELECT RAISE(ABORT,'Ruolo: sblocca prima i timesheet di progetto interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS own_roles_locked_update BEFORE UPDATE ON own_role_history WHEN EXISTS(SELECT 1 FROM entries e JOIN activities a ON a.id=e.activity_id JOIN month_locks l ON l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER) WHERE COALESCE(e.category_snapshot,a.category) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND e.hours IS NOT NULL AND e.work_date>=MIN(OLD.start_date,NEW.start_date)) BEGIN SELECT RAISE(ABORT,'Ruolo: sblocca prima i timesheet di progetto interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS own_roles_locked_delete BEFORE DELETE ON own_role_history WHEN EXISTS(SELECT 1 FROM entries e JOIN activities a ON a.id=e.activity_id JOIN month_locks l ON l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER) WHERE COALESCE(e.category_snapshot,a.category) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND e.hours IS NOT NULL AND e.work_date>=OLD.start_date) BEGIN SELECT RAISE(ABORT,'Ruolo: sblocca prima i timesheet di progetto interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS colleague_roles_locked_insert BEFORE INSERT ON person_role_history WHEN EXISTS(SELECT 1 FROM project_person_hours h JOIN month_locks l ON l.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(h.work_date,6,2) AS INTEGER) WHERE h.person_id=NEW.person_id AND h.work_date>=NEW.start_date) BEGIN SELECT RAISE(ABORT,'Collega: sblocca prima i timesheet interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS colleague_roles_locked_update BEFORE UPDATE ON person_role_history WHEN EXISTS(SELECT 1 FROM project_person_hours h JOIN month_locks l ON l.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(h.work_date,6,2) AS INTEGER) WHERE h.person_id=OLD.person_id AND h.work_date>=MIN(OLD.start_date,NEW.start_date)) BEGIN SELECT RAISE(ABORT,'Collega: sblocca prima i timesheet interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS colleague_roles_locked_delete BEFORE DELETE ON person_role_history WHEN EXISTS(SELECT 1 FROM project_person_hours h JOIN month_locks l ON l.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(h.work_date,6,2) AS INTEGER) WHERE h.person_id=OLD.person_id AND h.work_date>=OLD.start_date) BEGIN SELECT RAISE(ABORT,'Collega: sblocca prima i timesheet interessati'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_person_hours_lock_insert BEFORE INSERT ON project_person_hours WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: ore collega non modificabili'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_person_hours_lock_update BEFORE UPDATE ON project_person_hours WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) OR EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: ore collega non modificabili'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_person_hours_lock_delete BEFORE DELETE ON project_person_hours WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: ore collega non modificabili'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_people_lock_update BEFORE UPDATE OF full_name,qualification,project_role ON project_people WHEN EXISTS(SELECT 1 FROM project_person_hours h JOIN month_locks m ON m.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND m.month=CAST(substr(h.work_date,6,2) AS INTEGER) WHERE h.person_id=OLD.id) BEGIN SELECT RAISE(ABORT,'Collega con ore su timesheet bloccati: anagrafica non modificabile'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_person_hours_daily_insert BEFORE INSERT ON project_person_hours WHEN COALESCE((SELECT SUM(hours) FROM project_person_hours WHERE person_id=NEW.person_id AND work_date=NEW.work_date),0) + NEW.hours > 24.000001 BEGIN SELECT RAISE(ABORT,'Massimo 24 ore per collega e giorno'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_person_hours_daily_update BEFORE UPDATE ON project_person_hours WHEN COALESCE((SELECT SUM(hours) FROM project_person_hours WHERE person_id=NEW.person_id AND work_date=NEW.work_date AND id<>OLD.id),0) + NEW.hours > 24.000001 BEGIN SELECT RAISE(ABORT,'Massimo 24 ore per collega e giorno'); END");
        // I trigger sono parte del database: impediscono modifiche anche quando eseguite da altri client SQLite.
        st.execute("CREATE TRIGGER IF NOT EXISTS entries_lock_insert BEFORE INSERT ON entries WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: impossibile aggiungere ore'); END");
        st.execute("DROP TRIGGER IF EXISTS entries_lock_update");
        st.execute("CREATE TRIGGER entries_lock_update BEFORE UPDATE OF work_date,activity_id,hours,note,source_ref ON entries WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) OR EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: impossibile modificare ore'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS entries_lock_delete BEFORE DELETE ON entries WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: impossibile eliminare ore'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS suspensions_lock_insert BEFORE INSERT ON suspensions WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: sospensione non modificabile'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS suspensions_lock_update BEFORE UPDATE ON suspensions WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) OR EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: sospensione non modificabile'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS suspensions_lock_delete BEFORE DELETE ON suspensions WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Mensilità bloccata: sospensione non modificabile'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS annual_limit_insert BEFORE INSERT ON entries WHEN (SELECT COALESCE(SUM(hours),0) FROM entries WHERE substr(work_date,1,4)=substr(NEW.work_date,1,4)) + COALESCE(NEW.hours,0) > 1500.000001 BEGIN SELECT RAISE(ABORT,'Superato limite annuale 1500 ore'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS annual_limit_update BEFORE UPDATE ON entries WHEN (SELECT COALESCE(SUM(hours),0) FROM entries WHERE substr(work_date,1,4)=substr(NEW.work_date,1,4) AND id != OLD.id) + COALESCE(NEW.hours,0) > 1500.000001 BEGIN SELECT RAISE(ABORT,'Superato limite annuale 1500 ore'); END");
        // Le categorie originarie non sono più di sistema. Il cambiamento è idempotente.
        // Le nove categorie canoniche sono di sistema e non vanno declassate ai riavvii.
        st.execute("UPDATE categories SET system=0 WHERE system<>0 AND name NOT IN ("+
          String.join(",",Arrays.stream(CategoryTaxonomy.names()).map(n->"'"+n.replace("'","''")+"'").toList())+")");
        // I vecchi vincoli su gruppi e sottogruppi non sono più utilizzati.
        for(String oldTrigger:new String[]{"category_system_rename","category_protect_delete","activity_subgroup_insert","activity_subgroup_update","activity_subgroup_locked_update","activity_lock_update"})
          st.execute("DROP TRIGGER IF EXISTS "+oldTrigger);
        st.execute("CREATE TRIGGER category_protect_delete BEFORE DELETE ON categories WHEN EXISTS(SELECT 1 FROM activities WHERE category=OLD.name) BEGIN SELECT RAISE(ABORT,'Categoria ancora utilizzata: trasferisci prima le attività'); END");
        // Non consentire di cambiare il nome di un'attività in un timesheet chiuso.
        // La rinomina di una categoria è invece consentita e non cambia il tipo funzionale.
        /* Il nome anagrafico e il titolo possono essere corretti anche dopo la rendicontazione: nessun vincolo sulle ore viene rimosso. */
        st.execute("CREATE TRIGGER IF NOT EXISTS annual_role_lock_update BEFORE UPDATE OF role ON annual_settings WHEN NEW.role!=OLD.role AND EXISTS (SELECT 1 FROM month_locks l JOIN entries e ON l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER) JOIN activities a ON a.id=e.activity_id WHERE l.year=OLD.year AND COALESCE(e.category_snapshot,a.category) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND e.hours IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Ruolo non modificabile: timesheet di progetto già bloccati nell’anno'); END");
        // Regole universali di progetto: applicate dal motore SQLite, anche fuori dall'interfaccia.
        st.execute("DROP TRIGGER IF EXISTS project_entries_insert");
        st.execute("DROP TRIGGER IF EXISTS archived_entry_insert");
        st.execute("CREATE TRIGGER archived_entry_insert BEFORE INSERT ON entries WHEN EXISTS(SELECT 1 FROM activities WHERE id=NEW.activity_id AND archived_at IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Attività archiviata: ripristinala prima di rendicontare'); END");
        st.execute("DROP TRIGGER IF EXISTS archived_entry_reassign");
        st.execute("CREATE TRIGGER archived_entry_reassign BEFORE UPDATE OF activity_id ON entries WHEN NEW.activity_id<>OLD.activity_id AND EXISTS(SELECT 1 FROM activities WHERE id=NEW.activity_id AND archived_at IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Attività archiviata: ripristinala prima di rendicontare'); END");
        st.execute("DROP TRIGGER IF EXISTS archived_team_insert");
        st.execute("CREATE TRIGGER archived_team_insert BEFORE INSERT ON project_person_hours WHEN EXISTS(SELECT 1 FROM project_people pp JOIN activities a ON a.id=pp.activity_id WHERE pp.id=NEW.person_id AND a.archived_at IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Progetto archiviato: ripristinalo prima di rendicontare'); END");
        st.execute("CREATE TRIGGER project_entries_insert BEFORE INSERT ON entries WHEN COALESCE(NEW.category_snapshot,(SELECT category FROM activities WHERE id=NEW.activity_id)) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND (EXISTS(SELECT 1 FROM project_details p WHERE p.activity_id=NEW.activity_id AND (p.closed=1 OR (p.start_date IS NOT NULL AND NEW.work_date<p.start_date) OR (p.end_date IS NOT NULL AND NEW.work_date>p.end_date))) OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=NEW.activity_id AND l.year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(NEW.work_date,6,2) AS INTEGER))) BEGIN SELECT RAISE(ABORT,'Progetto chiuso, fuori periodo o mese del progetto bloccato'); END");
        st.execute("DROP TRIGGER IF EXISTS project_entries_update");
        st.execute("CREATE TRIGGER project_entries_update BEFORE UPDATE OF work_date,activity_id,hours,note,source_ref ON entries WHEN (COALESCE(OLD.category_snapshot,(SELECT category FROM activities WHERE id=OLD.activity_id)) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND (EXISTS(SELECT 1 FROM project_details p WHERE p.activity_id=OLD.activity_id AND p.closed=1) OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=OLD.activity_id AND l.year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(OLD.work_date,6,2) AS INTEGER)))) OR (COALESCE(NEW.category_snapshot,(SELECT category FROM activities WHERE id=NEW.activity_id)) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND (EXISTS(SELECT 1 FROM project_details p WHERE p.activity_id=NEW.activity_id AND (p.closed=1 OR (p.start_date IS NOT NULL AND NEW.work_date<p.start_date) OR (p.end_date IS NOT NULL AND NEW.work_date>p.end_date))) OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=NEW.activity_id AND l.year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(NEW.work_date,6,2) AS INTEGER)))) BEGIN SELECT RAISE(ABORT,'Progetto chiuso o mese del progetto bloccato'); END");
        st.execute("DROP TRIGGER IF EXISTS project_entries_delete");
        st.execute("CREATE TRIGGER project_entries_delete BEFORE DELETE ON entries WHEN COALESCE(OLD.category_snapshot,(SELECT category FROM activities WHERE id=OLD.activity_id)) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND (EXISTS(SELECT 1 FROM project_details p WHERE p.activity_id=OLD.activity_id AND p.closed=1) OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=OLD.activity_id AND l.year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(OLD.work_date,6,2) AS INTEGER))) BEGIN SELECT RAISE(ABORT,'Progetto chiuso o mese del progetto bloccato'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_team_insert BEFORE INSERT ON project_person_hours WHEN EXISTS(SELECT 1 FROM project_details p JOIN project_people m ON m.activity_id=p.activity_id WHERE m.id=NEW.person_id AND (p.closed=1 OR (p.start_date IS NOT NULL AND NEW.work_date<p.start_date) OR (p.end_date IS NOT NULL AND NEW.work_date>p.end_date))) OR EXISTS(SELECT 1 FROM project_month_locks l JOIN project_people m ON m.activity_id=l.activity_id WHERE m.id=NEW.person_id AND l.year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(NEW.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Progetto chiuso, fuori periodo o mese bloccato: ore collega'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_team_update BEFORE UPDATE ON project_person_hours WHEN EXISTS(SELECT 1 FROM project_details p JOIN project_people m ON m.activity_id=p.activity_id WHERE (m.id=OLD.person_id AND p.closed=1) OR (m.id=NEW.person_id AND (p.closed=1 OR (p.start_date IS NOT NULL AND NEW.work_date<p.start_date) OR (p.end_date IS NOT NULL AND NEW.work_date>p.end_date)))) OR EXISTS(SELECT 1 FROM project_month_locks l JOIN project_people m ON m.activity_id=l.activity_id WHERE (m.id=OLD.person_id AND l.year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) OR (m.id=NEW.person_id AND l.year=CAST(substr(NEW.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(NEW.work_date,6,2) AS INTEGER))) BEGIN SELECT RAISE(ABORT,'Progetto chiuso o mese bloccato: ore collega'); END");
        st.execute("CREATE TRIGGER IF NOT EXISTS project_team_delete BEFORE DELETE ON project_person_hours WHEN EXISTS(SELECT 1 FROM project_details p JOIN project_people m ON m.activity_id=p.activity_id WHERE m.id=OLD.person_id AND p.closed=1) OR EXISTS(SELECT 1 FROM project_month_locks l JOIN project_people m ON m.activity_id=l.activity_id WHERE m.id=OLD.person_id AND l.year=CAST(substr(OLD.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) BEGIN SELECT RAISE(ABORT,'Progetto chiuso o mese bloccato: ore collega'); END");
      }
    }
    private boolean hasColumn(String table,String column) throws SQLException{
      try(Statement s=db.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info("+table+")")){
        while(r.next())if(column.equalsIgnoreCase(r.getString("name")))return true;
      }return false;
    }
    List<ProjectMonthLock> getProjectMonthLocks(int id,int year) throws SQLException{
      List<ProjectMonthLock> result=new ArrayList<>();
      try(PreparedStatement p=db.prepareStatement("SELECT activity_id,year,month,reason FROM project_month_locks WHERE activity_id=? AND year=? ORDER BY month")){
        p.setInt(1,id);p.setInt(2,year);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new ProjectMonthLock(r.getInt(1),r.getInt(2),r.getInt(3),r.getString(4)));}
      }return result;
    }
    void setProjectMonthLocked(int id,int year,int month,boolean locked,String reason) throws SQLException{
      if(month<1||month>12)throw new IllegalArgumentException("Mese non valido");
      if(locked){try(PreparedStatement p=db.prepareStatement("INSERT OR REPLACE INTO project_month_locks(activity_id,year,month,reason) VALUES(?,?,?,?)")){
        p.setInt(1,id);p.setInt(2,year);p.setInt(3,month);p.setString(4,reason==null?"":reason);p.executeUpdate();}}
      else{try(PreparedStatement p=db.prepareStatement("DELETE FROM project_month_locks WHERE activity_id=? AND year=? AND month=?")){
        p.setInt(1,id);p.setInt(2,year);p.setInt(3,month);p.executeUpdate();}}
    }
    List<Integer> getYears() throws SQLException {List<Integer> years=new ArrayList<>();try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("SELECT year FROM annual_settings ORDER BY year")){while(rs.next())years.add(rs.getInt(1));}return years;}
    void addYear(int year,String note) throws SQLException {
      if(getYears().contains(year))throw new IllegalArgumentException("Annualità già presente: "+year);
      try(PreparedStatement p=db.prepareStatement("INSERT INTO annual_settings(year,annual_note,role) VALUES(?,?,(SELECT default_role FROM profile WHERE id=1))")){p.setInt(1,year);p.setString(2,note);p.executeUpdate();}
    }
    AnnualInfo getAnnual(int year) throws SQLException {try(PreparedStatement p=db.prepareStatement("SELECT annual_note,role FROM annual_settings WHERE year=?")){p.setInt(1,year);try(ResultSet r=p.executeQuery()){if(r.next())return new AnnualInfo(year,r.getString(1),r.getString(2));}}throw new SQLException("Annualità inesistente: "+year);}
    void saveAnnualNote(int year,String note) throws SQLException {try(PreparedStatement p=db.prepareStatement("UPDATE annual_settings SET annual_note=? WHERE year=?")){p.setString(1,note);p.setInt(2,year);p.executeUpdate();}}
    boolean isMonthLocked(int year,int month) throws SQLException {try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM month_locks WHERE year=? AND month=?")){p.setInt(1,year);p.setInt(2,month);try(ResultSet r=p.executeQuery()){return r.next();}}}
    List<LockedMonth> getLockedMonths(int year) throws SQLException {List<LockedMonth> items=new ArrayList<>();try(PreparedStatement p=db.prepareStatement("SELECT year,month,reason,locked_at FROM month_locks WHERE year=? ORDER BY month")){p.setInt(1,year);try(ResultSet r=p.executeQuery()){while(r.next())items.add(new LockedMonth(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4)));}}return items;}
    void setMonthLocked(int year,int month,boolean locked,String reason) throws SQLException {
      if(locked){try(PreparedStatement p=db.prepareStatement("INSERT INTO month_locks(year,month,reason) VALUES(?,?,?)")){p.setInt(1,year);p.setInt(2,month);p.setString(3,reason);p.executeUpdate();}}
      else {try(PreparedStatement p=db.prepareStatement("DELETE FROM month_locks WHERE year=? AND month=?")){p.setInt(1,year);p.setInt(2,month);p.executeUpdate();}}
    }
    ProfileInfo getProfile() throws SQLException {try(Statement st=db.createStatement();ResultSet r=st.executeQuery("SELECT full_name,default_role FROM profile WHERE id=1")){if(r.next())return new ProfileInfo(r.getString(1),r.getString(2));}throw new SQLException("Profilo non trovato");}
    void saveProfile(String name,String role,int year) throws SQLException {
      if(!Arrays.asList(ROLES).contains(role))throw new IllegalArgumentException("Ruolo non valido");
      db.setAutoCommit(false);try{
        try(PreparedStatement p=db.prepareStatement("UPDATE profile SET full_name=?,default_role=? WHERE id=1")){p.setString(1,name);p.setString(2,role);p.executeUpdate();}
        try(PreparedStatement p=db.prepareStatement("UPDATE annual_settings SET role=? WHERE year=?")){p.setString(1,role);p.setInt(2,year);p.executeUpdate();}
        // La selezione annuale equivale ad una decorrenza del 1 gennaio (la puoi precisare nello storico).
        String from=String.format(Locale.ROOT,"%04d-01-01",year);
        if(rate(role)>0){try(PreparedStatement p=db.prepareStatement("INSERT INTO own_role_history(start_date,role) VALUES(?,?) ON CONFLICT(start_date) DO UPDATE SET role=excluded.role WHERE role<>excluded.role")){p.setString(1,from);p.setString(2,role);p.executeUpdate();}}
        else {try(PreparedStatement p=db.prepareStatement("DELETE FROM own_role_history WHERE start_date=?")){p.setString(1,from);p.executeUpdate();}}
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    List<ProjectHistory> getProjectHistory() throws SQLException {
      Map<String,double[]> totals=new LinkedHashMap<>();
      Map<String,String> names=new HashMap<>();
      String sql="SELECT a.name,e.work_date,e.hours FROM entries e JOIN activities a ON a.id=e.activity_id WHERE COALESCE(e.category_snapshot,a.category) IN (SELECT name FROM categories WHERE kind='PROGETTI') AND e.hours IS NOT NULL ORDER BY a.name COLLATE NOCASE,e.work_date";
      try(Statement st=db.createStatement();ResultSet r=st.executeQuery(sql)){while(r.next()){
        String name=r.getString(1);LocalDate day=LocalDate.parse(r.getString(2));double hours=r.getDouble(3);String key=name+"\u0001"+day.getYear();
        double[] stat=totals.computeIfAbsent(key,k->new double[2]);stat[0]+=hours;stat[1]+=hours*hourlyRateAt(null,day);names.put(key,name);
      }}
      List<ProjectHistory> history=new ArrayList<>();for(var item:totals.entrySet()){
        String[] parts=item.getKey().split("\u0001");int year=Integer.parseInt(parts[1]);
        history.add(new ProjectHistory(parts[0],year,item.getValue()[0],"Per data",item.getValue()[1]));
      }return history;
    }
    /** Motivo leggibile della protezione per una registrazione individuale. */
    String entryRestriction(int activityId,LocalDate date) throws SQLException{
      return entryRestriction(activityId,date,null);
    }
    String entryRestriction(int activityId,LocalDate date,String historicCategory) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("SELECT reason FROM month_locks WHERE year=? AND month=?")){
        p.setInt(1,date.getYear());p.setInt(2,date.getMonthValue());try(ResultSet r=p.executeQuery()){if(r.next())return "Mensilità bloccata"+(r.getString(1).isBlank()?"":": "+r.getString(1));}}
      // Un'attività può aver cambiato categoria: i blocchi di progetto continuano
      // a valere per le vecchie registrazioni di progetto, non per le nuove di didattica.
      String effective=historicCategory;
      if(effective==null)try(PreparedStatement p=db.prepareStatement("SELECT category FROM activities WHERE id=?")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){effective=r.next()?r.getString(1):"";}
      }
      if(!categoryKindDb(effective).equals(GROUPS[2]))return "";
      try(PreparedStatement p=db.prepareStatement("SELECT reason FROM project_month_locks WHERE activity_id=? AND year=? AND month=?")){
        p.setInt(1,activityId);p.setInt(2,date.getYear());p.setInt(3,date.getMonthValue());try(ResultSet r=p.executeQuery()){if(r.next())return "Timesheet del progetto bloccato"+(r.getString(1).isBlank()?"":": "+r.getString(1));}}
      try(PreparedStatement p=db.prepareStatement("SELECT closed,start_date,end_date FROM project_details WHERE activity_id=?")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){if(r.next()){
          if(r.getInt(1)!=0)return "Progetto chiuso";
          String start=r.getString(2),end=r.getString(3);
          if(start!=null&&date.isBefore(LocalDate.parse(start)))return "Data precedente all'inizio del progetto ("+LocalDate.parse(start).format(DISPLAY)+")";
          if(end!=null&&date.isAfter(LocalDate.parse(end)))return "Data successiva alla fine del progetto ("+LocalDate.parse(end).format(DISPLAY)+")";
        }} }
      return "";
    }
    /** A safe additive migration: no tables or triggers are removed or rewritten. */
    void enableOrdinaryKind() throws SQLException {
      if(hasColumn("categories","ordinary_flag"))return;
      Path safety=databasePath.resolveSibling(databasePath.getFileName()+".backup-prima-regola-ordinaria-v39.sqlite");
      if(!Files.exists(safety))backup(safety);
      try(Statement st=db.createStatement()){
        st.execute("ALTER TABLE categories ADD COLUMN ordinary_flag INTEGER NOT NULL DEFAULT 0 CHECK(ordinary_flag IN (0,1))");
      }
    }
    /** Migrazione una-tantum. Le denominazioni originali rimangono nei campi legacy_*.
     * Un mapping incerto diventa Altre attività, con segnalazione per revisione umana. */
    void migrateCategoriesV311() throws SQLException {
      try(Statement st=db.createStatement()){
        st.execute("CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
      }
      try(Statement st=db.createStatement();ResultSet r=st.executeQuery(
          "SELECT value FROM app_metadata WHERE key='category_taxonomy_v311'")){
        if(r.next()&&"complete".equals(r.getString(1)))return;
      }
      Path safety=databasePath.resolveSibling(databasePath.getFileName()+".backup-prima-categorie-v311.sqlite");
      if(!Files.exists(safety))backup(safety);
      db.setAutoCommit(false);
      try{
        try(Statement st=db.createStatement()){
          if(!hasColumn("activities","legacy_category_v311"))st.execute("ALTER TABLE activities ADD COLUMN legacy_category_v311 TEXT");
          if(!hasColumn("entries","legacy_category_v311"))st.execute("ALTER TABLE entries ADD COLUMN legacy_category_v311 TEXT");
          st.execute("CREATE TABLE IF NOT EXISTS legacy_category_mapping (old_name TEXT PRIMARY KEY, new_name TEXT NOT NULL, review INTEGER NOT NULL)");
        }
        record Original(String old,String kind){}
        List<Original> originals=new ArrayList<>();
        try(Statement st=db.createStatement();ResultSet r=st.executeQuery(
          "SELECT name,CASE WHEN ordinary_flag=1 THEN 'ATTIVITÀ ORDINARIA' ELSE kind END FROM categories WHERE name<>'SEGNAPOSTO SOSPENSIONE'")){
          while(r.next())originals.add(new Original(r.getString(1),r.getString(2)));
        }
        for(var rule:CategoryTaxonomy.RULES){
          try(PreparedStatement insert=db.prepareStatement("INSERT OR IGNORE INTO categories(name,kind,system,ordinary_flag) VALUES(?,?,1,0)")){
            insert.setString(1,rule.name());insert.setString(2,rule.technicalKind());insert.executeUpdate();
          }
          // Anche su installazioni con categorie custom omonime, forza kind canonico.
          try(PreparedStatement update=db.prepareStatement("UPDATE categories SET name=?,kind=?,system=1,ordinary_flag=0 WHERE name=? COLLATE NOCASE")){
            update.setString(1,rule.name());update.setString(2,rule.technicalKind());update.setString(3,rule.name());update.executeUpdate();
          }
        }
        for(Original o:originals){
          var guess=CategoryTaxonomy.mapLegacy(o.old(),o.kind());
          if(!o.old().equals(guess.target())){
            try(PreparedStatement hist=db.prepareStatement("UPDATE entries SET legacy_category_v311=COALESCE(legacy_category_v311,category_snapshot),category_snapshot=? WHERE category_snapshot=? COLLATE NOCASE")){
              hist.setString(1,guess.target());hist.setString(2,o.old());hist.executeUpdate();
            }
            try(PreparedStatement current=db.prepareStatement("UPDATE activities SET legacy_category_v311=COALESCE(legacy_category_v311,category),category=? WHERE category=? COLLATE NOCASE")){
              current.setString(1,guess.target());current.setString(2,o.old());current.executeUpdate();
            }
          }
          if(guess.requiresReview())try(PreparedStatement alert=db.prepareStatement("INSERT OR IGNORE INTO legacy_category_mapping(old_name,new_name,review) VALUES(?,?,1)")){
            alert.setString(1,o.old());alert.setString(2,guess.target());alert.executeUpdate();
          }
        }
        // Una scheda progetto è una prova più forte di qualsiasi denominazione custom.
        try(Statement st=db.createStatement()){
          st.execute("UPDATE activities SET legacy_category_v311=COALESCE(legacy_category_v311,category),category='"+CategoryTaxonomy.FUNDED+"' " +
            "WHERE id IN (SELECT activity_id FROM project_details) AND category<>'"+CategoryTaxonomy.FUNDED+"'");
          st.execute("DELETE FROM categories WHERE system=0 AND name<>'SEGNAPOSTO SOSPENSIONE' " +
            "AND name NOT IN (SELECT category FROM activities) AND name NOT IN (SELECT category_snapshot FROM entries)");
          st.execute("INSERT INTO app_metadata(key,value) VALUES('category_taxonomy_v311','complete') " +
            "ON CONFLICT(key) DO UPDATE SET value='complete'");
        }
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    /** Gestione trasversale missioni. Non crea automaticamente ore. */
    void migrateMissionsV311() throws SQLException {
      try(Statement st=db.createStatement()){
        st.execute("CREATE TABLE IF NOT EXISTS missions ("+
          "id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,type TEXT NOT NULL,role TEXT NOT NULL,"+
          "location TEXT NOT NULL,start_date TEXT NOT NULL,end_date TEXT NOT NULL,"+
          "conference TEXT NOT NULL DEFAULT '',paper_title TEXT NOT NULL DEFAULT '',doi TEXT NOT NULL DEFAULT '',"+
          "project_activity_id INTEGER REFERENCES activities(id) ON DELETE SET NULL,"+
          "authorization TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',"+
          "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,CHECK(start_date<=end_date))");
        st.execute("CREATE TABLE IF NOT EXISTS mission_documents (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
          "mission_id INTEGER NOT NULL REFERENCES missions(id) ON DELETE CASCADE,"+
          "filename TEXT NOT NULL,bytes BLOB NOT NULL,description TEXT NOT NULL DEFAULT '',"+
          "uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        if(!hasColumn("entries","mission_id"))st.execute("ALTER TABLE entries ADD COLUMN mission_id INTEGER REFERENCES missions(id) ON DELETE SET NULL");
        st.execute("CREATE INDEX IF NOT EXISTS idx_entries_mission ON entries(mission_id)");
        st.execute("DROP TRIGGER IF EXISTS entries_lock_mission_update");
        st.execute("CREATE TRIGGER entries_lock_mission_update BEFORE UPDATE OF mission_id ON entries " +
          "WHEN EXISTS(SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER) " +
          "AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER)) " +
          "BEGIN SELECT RAISE(ABORT,'Mensilita bloccata: non puoi modificare la missione associata'); END");
        st.execute("DROP TRIGGER IF EXISTS entries_mission_date_insert");
        st.execute("CREATE TRIGGER entries_mission_date_insert BEFORE INSERT ON entries WHEN NEW.mission_id IS NOT NULL " +
          "AND NOT EXISTS(SELECT 1 FROM missions WHERE id=NEW.mission_id AND NEW.work_date BETWEEN start_date AND end_date) " +
          "BEGIN SELECT RAISE(ABORT,'La registrazione e fuori dal periodo della missione'); END");
        st.execute("DROP TRIGGER IF EXISTS entries_mission_date_update");
        st.execute("CREATE TRIGGER entries_mission_date_update BEFORE UPDATE OF mission_id,work_date ON entries WHEN NEW.mission_id IS NOT NULL " +
          "AND NOT EXISTS(SELECT 1 FROM missions WHERE id=NEW.mission_id AND NEW.work_date BETWEEN start_date AND end_date) " +
          "BEGIN SELECT RAISE(ABORT,'La registrazione e fuori dal periodo della missione'); END");
        st.execute("DROP TRIGGER IF EXISTS missions_dates_update");
        st.execute("CREATE TRIGGER missions_dates_update BEFORE UPDATE OF start_date,end_date ON missions WHEN " +
          "EXISTS(SELECT 1 FROM entries WHERE mission_id=OLD.id AND work_date NOT BETWEEN NEW.start_date AND NEW.end_date) " +
          "BEGIN SELECT RAISE(ABORT,'La modifica del periodo escluderebbe registrazioni collegate'); END");
      }
    }
    List<Mission> getMissions() throws SQLException {
      List<Mission> list=new ArrayList<>();
      try(Statement st=db.createStatement();ResultSet r=st.executeQuery(
          "SELECT id,title,type,role,location,start_date,end_date,conference,paper_title,doi,project_activity_id,authorization,notes FROM missions ORDER BY start_date DESC,title COLLATE NOCASE")){
        while(r.next())list.add(new Mission(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),
          LocalDate.parse(r.getString(6)),LocalDate.parse(r.getString(7)),r.getString(8),r.getString(9),r.getString(10),
          r.getObject(11)==null?null:r.getInt(11),r.getString(12),r.getString(13)));
      }return list;
    }
    void saveMission(Mission m) throws SQLException {
      if(m.title().isBlank()||m.type().isBlank()||m.location().isBlank()||m.from().isAfter(m.to()))
        throw new IllegalArgumentException("Titolo, tipo, localita e periodo valido sono obbligatori.");
      String sql=m.id()==0?
        "INSERT INTO missions(title,type,role,location,start_date,end_date,conference,paper_title,doi,project_activity_id,authorization,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)":
        "UPDATE missions SET title=?,type=?,role=?,location=?,start_date=?,end_date=?,conference=?,paper_title=?,doi=?,project_activity_id=?,authorization=?,notes=? WHERE id=?";
      try(PreparedStatement p=db.prepareStatement(sql)){
        p.setString(1,m.title().trim());p.setString(2,m.type());p.setString(3,m.role());p.setString(4,m.location().trim());
        p.setString(5,m.from().toString());p.setString(6,m.to().toString());p.setString(7,m.conference());
        p.setString(8,m.paper());p.setString(9,m.doi());
        if(m.projectId()==null)p.setNull(10,Types.INTEGER);else p.setInt(10,m.projectId());
        p.setString(11,m.authorization());p.setString(12,m.notes());if(m.id()!=0)p.setInt(13,m.id());
        p.executeUpdate();
      }
    }
    void deleteMission(int id) throws SQLException {
      try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM entries WHERE mission_id=? LIMIT 1")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){
          if(r.next())throw new IllegalStateException("Missione collegata a registrazioni. Scollega prima le ore, senza eliminarle.");
        }
      }
      try(PreparedStatement p=db.prepareStatement("DELETE FROM missions WHERE id=?")){
        p.setInt(1,id);p.executeUpdate();
      }
    }
    void validateMissionDate(Integer id,LocalDate date) throws SQLException {
      if(id==null)return;
      try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM missions WHERE id=? AND ? BETWEEN start_date AND end_date")){
        p.setInt(1,id);p.setString(2,date.toString());
        try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Data fuori dal periodo della missione selezionata.");}
      }
    }
    List<MissionDocument> getMissionDocuments(int id) throws SQLException{
      List<MissionDocument> docs=new ArrayList<>();
      try(PreparedStatement p=db.prepareStatement("SELECT id,filename,length(bytes) FROM mission_documents WHERE mission_id=? ORDER BY id")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){while(r.next())docs.add(new MissionDocument(r.getInt(1),r.getString(2),r.getLong(3)));}
      }return docs;
    }
    void addMissionDocument(int missionId,Path file) throws Exception {
      if(Files.size(file)>30L*1024L*1024L)throw new IllegalArgumentException("Documento troppo grande: massimo 30 MB.");
      try(PreparedStatement p=db.prepareStatement("INSERT INTO mission_documents(mission_id,filename,bytes) VALUES(?,?,?)")){
        p.setInt(1,missionId);p.setString(2,file.getFileName().toString());p.setBytes(3,Files.readAllBytes(file));p.executeUpdate();
      }
    }
    void exportMissionDocument(int docId,Path output) throws Exception {
      try(PreparedStatement p=db.prepareStatement("SELECT bytes FROM mission_documents WHERE id=?")){
        p.setInt(1,docId);try(ResultSet r=p.executeQuery()){
          if(!r.next())throw new IllegalArgumentException("Documento non disponibile.");
          Files.write(output,r.getBytes(1));
        }
      }
    }
    void deleteMissionDocument(int id) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("DELETE FROM mission_documents WHERE id=?")){
        p.setInt(1,id);p.executeUpdate();
      }
    }

    List<LegacyMapping> getLegacyMappings() throws SQLException {
      List<LegacyMapping> mappings=new ArrayList<>();
      try(Statement st=db.createStatement();ResultSet r=st.executeQuery(
          "SELECT old_name,new_name,review FROM legacy_category_mapping ORDER BY old_name COLLATE NOCASE")){
        while(r.next())mappings.add(new LegacyMapping(r.getString(1),r.getString(2),r.getInt(3)!=0));
      }return mappings;
    }

    List<Category> getCategories() throws SQLException{
      List<Category> list=new ArrayList<>();
      for(String canonical:CategoryTaxonomy.names()){
        try(PreparedStatement p=db.prepareStatement("SELECT name,kind,system FROM categories WHERE name=? COLLATE NOCASE")){
          p.setString(1,canonical);try(ResultSet r=p.executeQuery()){
            if(r.next())list.add(new Category(canonical,r.getString(2),true));
          }
        }
      }return list;
    }
    boolean categoryExists(String name) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM categories WHERE name=? COLLATE NOCASE")){p.setString(1,name);try(ResultSet r=p.executeQuery()){return r.next();}}
    }
    void saveCategory(String old,String name,String kind) throws SQLException{
      if(true)throw new IllegalStateException("Le nove categorie sono predefinite nella versione 3.11.");
      name=name==null?"":name.trim();
      if(name.isBlank()||name.equalsIgnoreCase("SEGNAPOSTO SOSPENSIONE"))throw new IllegalArgumentException("Inserisci un nome di categoria valido.");
      if(!Arrays.asList(GROUPS).contains(kind))throw new IllegalArgumentException("Regola operativa non valida.");
      db.setAutoCommit(false);
      try{
        if(old==null){
          try(PreparedStatement p=db.prepareStatement("INSERT INTO categories(name,kind,system,ordinary_flag) VALUES(?,?,0,?)")){
            p.setString(1,name);p.setString(2,kind.equals(GROUPS[4])?GROUPS[0]:kind);p.setInt(3,kind.equals(GROUPS[4])?1:0);p.executeUpdate();}
        }else{
          String previous=categoryKindDb(old);
          if(!categoryExists(old))throw new IllegalArgumentException("Categoria inesistente.");
          if(!kind.equals(previous)){
            // I costi e le regole di sospensione dipendono dal tipo tecnico: non riclassificare
            // retroattivamente una categoria che ha ore già registrate.
            try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM entries WHERE category_snapshot=? COLLATE NOCASE LIMIT 1")){
              p.setString(1,old);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("La categoria ha registrazioni storiche. Puoi rinominarla, ma non cambiarne la regola operativa senza riclassificazione esplicita.");}}
            try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM project_details pd JOIN activities a ON a.id=pd.activity_id WHERE a.category=? LIMIT 1")){
              p.setString(1,old);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Categoria associata a schede progetto: non puoi cambiarne la regola operativa.");}}
          }
          try(PreparedStatement p=db.prepareStatement("UPDATE categories SET name=?,kind=?,ordinary_flag=?,system=0 WHERE name=?")){
            p.setString(1,name);p.setString(2,kind.equals(GROUPS[4])?GROUPS[0]:kind);p.setInt(3,kind.equals(GROUPS[4])?1:0);p.setString(4,old);p.executeUpdate();}
          if(!old.equals(name))try(PreparedStatement p=db.prepareStatement("UPDATE activities SET category=? WHERE category=?")){
            p.setString(1,name);p.setString(2,old);p.executeUpdate();}
          if(!old.equals(name))try(PreparedStatement p=db.prepareStatement("UPDATE entries SET category_snapshot=? WHERE category_snapshot=? COLLATE NOCASE")){
            p.setString(1,name);p.setString(2,old);p.executeUpdate();}
        }
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    private String categoryKindDb(String name) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("SELECT CASE WHEN ordinary_flag=1 THEN 'ATTIVIT\u00c0 ORDINARIA' ELSE kind END FROM categories WHERE name=?")){p.setString(1,name);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):"ALTRE ATTIVITÀ";}}
    }
    void removeCategory(String name,String destination) throws SQLException{
      if(true)throw new IllegalStateException("Le nove categorie sono predefinite nella versione 3.11.");
      db.setAutoCommit(false);
      try{
        if(destination!=null){
          String sourceType=categoryKindDb(name),targetType=categoryKindDb(destination);
          if(!categoryExists(destination)||!sourceType.equals(targetType))throw new IllegalArgumentException("Per conservare il trattamento delle ore, seleziona una categoria con la stessa regola operativa.");
          if(name.equalsIgnoreCase(destination))throw new IllegalArgumentException("Seleziona una categoria differente.");
          try(PreparedStatement p=db.prepareStatement("UPDATE activities SET category=? WHERE category=?")){
            p.setString(1,destination);p.setString(2,name);p.executeUpdate();}
          // La categoria storica viene trasferita solo a una categoria dello stesso tipo.
          try(PreparedStatement p=db.prepareStatement("UPDATE entries SET category_snapshot=? WHERE category_snapshot=? COLLATE NOCASE")){
            p.setString(1,destination);p.setString(2,name);p.executeUpdate();}
        }
        try(PreparedStatement p=db.prepareStatement("DELETE FROM categories WHERE name=?")){
          p.setString(1,name);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Categoria inesistente o ancora assegnata ad attività.");}
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    Set<Integer> getArchivedActivityIds() throws SQLException{
      Set<Integer> ids=new HashSet<>();
      try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id FROM activities WHERE archived_at IS NOT NULL")){
        while(r.next())ids.add(r.getInt(1));
      }return ids;
    }
    void setActivityArchived(int id,boolean archived) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("UPDATE activities SET archived_at="+(archived?"CURRENT_TIMESTAMP":"NULL")+" WHERE id=?")){
        p.setInt(1,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Attività non trovata.");
      }
    }
    List<Activity> getActivities() throws SQLException{List<Activity> result=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,name,category,color FROM activities ORDER BY name COLLATE NOCASE")){while(r.next())result.add(new Activity(r.getInt(1),r.getString(2),r.getString(3),r.getString(4)));}return result;}
    List<LogEntry> getLogs(int year) throws SQLException {
      List<LogEntry> list=new ArrayList<>();String sql="SELECT e.id,e.work_date,a.id,a.name,COALESCE(e.category_snapshot,a.category),a.color,e.hours,e.note,e.source_ref,e.mission_id FROM entries e JOIN activities a ON e.activity_id=a.id WHERE e.work_date >= ? AND e.work_date < ? ORDER BY e.work_date,e.id";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,year+"-01-01");p.setString(2,(year+1)+"-01-01");try(ResultSet r=p.executeQuery()){while(r.next()){
        double hours=r.getDouble(7);Double val=r.wasNull()?null:hours;
        list.add(new LogEntry(r.getInt(1),LocalDate.parse(r.getString(2)),new Activity(r.getInt(3),r.getString(4),r.getString(5),r.getString(6)),val,r.getString(8),r.getString(9),r.getObject(10)==null?null:r.getInt(10)));
      }}}return list;
    }
    Map<LocalDate,String> getSuspensions(int year) throws SQLException{Map<LocalDate,String> map=new HashMap<>();try(PreparedStatement p=db.prepareStatement("SELECT work_date,reason FROM suspensions WHERE work_date >= ? AND work_date < ?")){p.setString(1,year+"-01-01");p.setString(2,(year+1)+"-01-01");try(ResultSet r=p.executeQuery()){while(r.next())map.put(LocalDate.parse(r.getString(1)),r.getString(2));}}return map;}
    private int findActivity(String name) throws SQLException{try(PreparedStatement p=db.prepareStatement("SELECT id FROM activities WHERE name=? COLLATE NOCASE")){p.setString(1,name);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):-1;}}}
    private String nextColor() throws SQLException {
      int n=0;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM activities")){if(r.next())n=r.getInt(1);}
      float hue=(float)((n*0.61803398875)%1.0);Color c=Color.getHSBColor(hue,0.25f,0.99f);return toHex(c);
    }
    /**
     * Inserimento multiplo additivo. Se la stessa attivita' e' presente nello stesso
     * giorno, riunisce le righe compatibili (stessa missione e categoria storica,
     * nessuna provenienza esterna), preservando tutte le note.
     * Missioni diverse o provenienze esterne restano righe distinte per non
     * falsificare le informazioni rendicontabili. Operazione integralmente atomica.
     */
    void saveEntriesBatch(int year,List<LocalDate> dates,String name,String category,double hours,String note,Integer missionId) throws SQLException{
      if(dates.isEmpty()||dates.stream().distinct().count()!=dates.size())throw new IllegalArgumentException("Selezione giorni non valida.");
      if(!Double.isFinite(hours)||hours<=0||hours>24)throw new IllegalArgumentException("Ore per giorno non valide.");
      if(!categoryExists(category))throw new IllegalArgumentException("Categoria non riconosciuta.");
      if(name.isBlank())throw new IllegalArgumentException("Attività obbligatoria.");
      int existingId=findActivity(name);
      if(existingId>=0&&getArchivedActivityIds().contains(existingId))throw new IllegalArgumentException("Attività archiviata: ripristinala da Archivio attività.");
      if(existingId>=0){
        try(PreparedStatement p=db.prepareStatement("SELECT category FROM activities WHERE id=?")){
          p.setInt(1,existingId);try(ResultSet r=p.executeQuery()){if(r.next())category=r.getString(1);}
        }
      }
      for(LocalDate d:dates){
        if(d.getYear()!=year)throw new IllegalArgumentException("Data fuori annualità: "+d);
        if(isMonthLocked(d.getYear(),d.getMonthValue()))throw new IllegalArgumentException("Mese bloccato: "+d.format(DISPLAY));
        validateMissionDate(missionId,d);
        try(PreparedStatement daily=db.prepareStatement("SELECT COALESCE(SUM(hours),0) FROM entries WHERE work_date=?")){
          daily.setString(1,d.toString());try(ResultSet r=daily.executeQuery()){
            if(r.next()&&r.getDouble(1)+hours>24.000001)
              throw new IllegalArgumentException("Il "+d.format(DISPLAY)+" supererebbe 24 ore (già presenti "+fmt(r.getDouble(1))+" h).");
          }
        }
      }
      double total=0;
      try(PreparedStatement p=db.prepareStatement("SELECT COALESCE(SUM(hours),0) FROM entries WHERE substr(work_date,1,4)=?")){
        p.setString(1,Integer.toString(year));try(ResultSet r=p.executeQuery()){if(r.next())total=r.getDouble(1);}
      }
      if(total+hours*dates.size()>1500.000001)
        throw new IllegalArgumentException("Superato limite di 1.500 ore annue: già "+fmt(total)+" h, nuove "+fmt(hours*dates.size())+" h.");
      db.setAutoCommit(false);
      try{
        int id=existingId;
        if(id<0){
          try(PreparedStatement p=db.prepareStatement("INSERT INTO activities(name,category,color) VALUES(?,?,?)")){
            p.setString(1,name.trim());p.setString(2,category);p.setString(3,nextColor());p.executeUpdate();
          }
          id=findActivity(name);
          if(id<0)throw new SQLException("Attività non creata.");
        }
        try(PreparedStatement find=db.prepareStatement(
             "SELECT id,hours,note FROM entries WHERE work_date=? AND activity_id=? " +
             "AND (mission_id=? OR (mission_id IS NULL AND ? IS NULL)) " +
             "AND category_snapshot=? COLLATE NOCASE " +
             "AND (source_ref IS NULL OR TRIM(source_ref)='') ORDER BY id" );
            PreparedStatement update=db.prepareStatement("UPDATE entries SET hours=?,note=? WHERE id=?");
            PreparedStatement remove=db.prepareStatement("DELETE FROM entries WHERE id=?");
            PreparedStatement insert=db.prepareStatement(
              "INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot,mission_id) " +
              "VALUES(?,?,?,?,(SELECT category FROM activities WHERE id=?),?)")){
          for(LocalDate d:dates){
            find.setString(1,d.toString());find.setInt(2,id);
            if(missionId==null){find.setNull(3,Types.INTEGER);find.setNull(4,Types.INTEGER);}
            else{find.setInt(3,missionId);find.setInt(4,missionId);}
            find.setString(5,category);
            List<Integer> matches=new ArrayList<>();List<String> previousNotes=new ArrayList<>();
            double existingHours=0;
            try(ResultSet r=find.executeQuery()){
              while(r.next()){
                // Le righe con ore nulle potrebbero essere segnaposto e non si fondono.
                if(r.getObject(2)==null)continue;
                matches.add(r.getInt(1));existingHours+=r.getDouble(2);
                previousNotes.add(r.getString(3));
              }
            }
            if(!matches.isEmpty()){
              // Eliminare prima i doppioni evita di violare temporaneamente il limite
              // delle 1.500 ore nei trigger durante il consolidamento.
              for(int i=1;i<matches.size();i++){
                remove.setInt(1,matches.get(i));remove.executeUpdate();
              }
              update.setDouble(1,existingHours+hours);
              update.setString(2,BatchEntrySupport.mergeNotes(previousNotes,note));
              update.setInt(3,matches.get(0));
              if(update.executeUpdate()!=1)throw new SQLException("Errore aggiornamento delle ore esistenti.");
            }else{
              insert.setString(1,d.toString());insert.setInt(2,id);insert.setDouble(3,hours);
              insert.setString(4,note);insert.setInt(5,id);
              if(missionId==null)insert.setNull(6,Types.INTEGER);else insert.setInt(6,missionId);
              insert.executeUpdate();
            }
          }
        }
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}
      finally{db.setAutoCommit(true);}
    }
    void saveEntry(Integer id,LocalDate date,String name,String category,Double hours,String note,Integer missionId) throws SQLException{
      validateMissionDate(missionId,date);
      if(!categoryExists(category))throw new IllegalArgumentException("Categoria non riconosciuta.");
      db.setAutoCommit(false);try{
        int act=findActivity(name);
        if(act>=0&&getArchivedActivityIds().contains(act)&&id==null)throw new IllegalArgumentException("Attività archiviata: ripristinala prima di inserire nuove ore.");
        if(act<0){try(PreparedStatement p=db.prepareStatement("INSERT INTO activities(name,category,color) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){
          p.setString(1,name);p.setString(2,category);p.setString(3,nextColor());p.executeUpdate();act=findActivity(name);if(act<0)throw new SQLException("Impossibile creare l'attività.");
}
        }
        if(id==null){try(PreparedStatement p=db.prepareStatement("INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot,mission_id) VALUES(?,?,?,?,(SELECT category FROM activities WHERE id=?),?)")){
          p.setString(1,date.toString());p.setInt(2,act);if(hours==null)p.setNull(3,Types.DOUBLE);else p.setDouble(3,hours);p.setString(4,note);p.setInt(5,act);
          if(missionId==null)p.setNull(6,Types.INTEGER);else p.setInt(6,missionId);p.executeUpdate();}}
        else{try(PreparedStatement p=db.prepareStatement("UPDATE entries SET work_date=?,activity_id=?,hours=?,note=?,category_snapshot=CASE WHEN activity_id=? THEN category_snapshot ELSE (SELECT category FROM activities WHERE id=?) END,mission_id=? WHERE id=?")){
          p.setString(1,date.toString());p.setInt(2,act);if(hours==null)p.setNull(3,Types.DOUBLE);else p.setDouble(3,hours);p.setString(4,note);p.setInt(5,act);p.setInt(6,act);
          if(missionId==null)p.setNull(7,Types.INTEGER);else p.setInt(7,missionId);p.setInt(8,id);p.executeUpdate();}}
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    void deleteEntry(int id) throws SQLException{try(PreparedStatement p=db.prepareStatement("DELETE FROM entries WHERE id=?")){p.setInt(1,id);p.executeUpdate();}}
    Set<Integer> projectHistoryIds() throws SQLException{
      Set<Integer> ids=new HashSet<>();
      String sql="SELECT activity_id FROM project_details UNION "
        +"SELECT DISTINCT e.activity_id FROM entries e JOIN categories c ON c.name=COALESCE(e.category_snapshot, "
        +"(SELECT category FROM activities WHERE id=e.activity_id)) WHERE c.kind='PROGETTI'";
      try(Statement st=db.createStatement();ResultSet r=st.executeQuery(sql)){while(r.next())ids.add(r.getInt(1));}
      return ids;
    }
    long entryCount(int activityId) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("SELECT COUNT(*) FROM entries WHERE activity_id=?")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):0;}
      }
    }
    boolean categoryUsedInHistory(String category) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM entries WHERE category_snapshot=? COLLATE NOCASE LIMIT 1")){
        p.setString(1,category);try(ResultSet r=p.executeQuery()){return r.next();}
      }
    }
    /** Per compatibilita, il metodo privo di scelta preserva sempre le vecchie categorie. */
    void saveActivity(Integer id,String name,String category,String color) throws SQLException{
      saveActivity(id,name,category,color,false);
    }
    /** Cambia la categoria futura o riclassifica lo storico, con validazione atomica. */
    void saveActivity(Integer id,String name,String category,String color,boolean retroactive) throws SQLException{
      if(!categoryExists(category))throw new IllegalArgumentException("Categoria non riconosciuta.");
      if(name==null||name.isBlank())throw new IllegalArgumentException("Nome dell'attività obbligatorio.");
      db.setAutoCommit(false);
      try{
        if(id==null){
          try(PreparedStatement p=db.prepareStatement("INSERT INTO activities(name,category,color) VALUES (?,?,?)")){
            p.setString(1,name.trim());p.setString(2,category);p.setString(3,color);p.executeUpdate();
          }
        }else{
          String prior;
          try(PreparedStatement p=db.prepareStatement("SELECT category FROM activities WHERE id=?")){
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){
              if(!r.next())throw new IllegalArgumentException("Attività inesistente.");
              prior=r.getString(1);
            }
          }
          if(!prior.equalsIgnoreCase(category)&&retroactive){
            validateRetroactiveCategoryChange(id,category);
            try(PreparedStatement p=db.prepareStatement("UPDATE entries SET category_snapshot=? WHERE activity_id=?")){
              p.setString(1,category);p.setInt(2,id);p.executeUpdate();
            }
          }
          // Se retroactive=false: nessuna scrittura su entries; tutte le vecchie
          // registrazioni conservano la categoria che avevano al momento del salvataggio.
          try(PreparedStatement p=db.prepareStatement("UPDATE activities SET name=?,category=?,color=? WHERE id=?")){
            p.setString(1,name.trim());p.setString(2,category);p.setString(3,color);p.setInt(4,id);p.executeUpdate();
          }
        }
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}
      finally{db.setAutoCommit(true);}
    }
    private void validateRetroactiveCategoryChange(int activityId,String newCategory) throws SQLException{
      // I timesheet gia inviati non possono cambiare di classificazione.
      String sql="SELECT e.work_date FROM entries e WHERE e.activity_id=? AND ("
        +"EXISTS(SELECT 1 FROM month_locks m WHERE m.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND m.month=CAST(substr(e.work_date,6,2) AS INTEGER)) "
        +"OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=e.activity_id AND l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER)) "
        +"OR EXISTS(SELECT 1 FROM project_details p WHERE p.activity_id=e.activity_id AND p.closed=1)) LIMIT 1";
      try(PreparedStatement p=db.prepareStatement(sql)){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){
          if(r.next())throw new IllegalArgumentException("Riclassificazione retroattiva non consentita: sono presenti registrazioni in mesi o progetti bloccati (es. "+r.getString(1)+"). Sblocca esplicitamente il periodo oppure scegli 'Solo nuove registrazioni'.");
        }
      }
      String kind=categoryKindDb(newCategory);
      if(CategoryTaxonomy.FRONT.equals(newCategory)){
        String invalid="SELECT e.work_date FROM entries e WHERE e.activity_id=? AND "
          +"(CAST(strftime('%w',e.work_date) AS INTEGER) IN (0,6) "
          +"OR EXISTS(SELECT 1 FROM suspensions s WHERE s.work_date=e.work_date)) LIMIT 1";
        try(PreparedStatement p=db.prepareStatement(invalid)){
          p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){
            if(r.next())throw new IllegalArgumentException("Impossibile riclassificare come didattica: la giornata "+r.getString(1)+" è sospesa o cade nel fine settimana. Puoi invece applicare la categoria soltanto alle nuove registrazioni.");
          }
        }
      }
      if(kind.equals(GROUPS[2])){
        String invalid="SELECT e.work_date FROM entries e JOIN project_details p ON p.activity_id=e.activity_id WHERE e.activity_id=? AND "
          +"((p.start_date IS NOT NULL AND e.work_date<p.start_date) OR (p.end_date IS NOT NULL AND e.work_date>p.end_date)) LIMIT 1";
        try(PreparedStatement p=db.prepareStatement(invalid)){
          p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){
            if(r.next())throw new IllegalArgumentException("Data fuori dalla validità del progetto: "+r.getString(1));
          }
        }
      }
    }
    List<NoteRow> getNotes() throws SQLException{List<NoteRow> notes=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,note FROM notes ORDER BY id")){while(r.next())notes.add(new NoteRow(r.getInt(1),r.getString(2)));}return notes;}
    void saveNote(Integer id,String text) throws SQLException{if(id==null){try(PreparedStatement p=db.prepareStatement("INSERT INTO notes(note) VALUES(?)")){p.setString(1,text);p.executeUpdate();}}else{try(PreparedStatement p=db.prepareStatement("UPDATE notes SET note=? WHERE id=?")){p.setString(1,text);p.setInt(2,id);p.executeUpdate();}}}
    void deleteNote(int id) throws SQLException{try(PreparedStatement p=db.prepareStatement("DELETE FROM notes WHERE id=?")){p.setInt(1,id);p.executeUpdate();}}
    void deleteActivity(int id) throws SQLException{
      // Non eliminare mai per cascata ore, timesheet, persone o allegati.
      for(String query:new String[]{
        "SELECT 1 FROM entries WHERE activity_id=? LIMIT 1",
        "SELECT 1 FROM project_people WHERE activity_id=? LIMIT 1",
        "SELECT 1 FROM project_documents WHERE activity_id=? LIMIT 1",
        "SELECT 1 FROM project_month_locks WHERE activity_id=? LIMIT 1"
      })try(PreparedStatement p=db.prepareStatement(query)){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){
          if(r.next())throw new IllegalArgumentException("L'attività contiene dati storici o collegati: non può essere eliminata. Utilizza Archivia.");
        }
      }
      // Se non vi sono ore/allegati/colleghi, i soli dati anagrafici sono eliminabili.
      db.setAutoCommit(false);
      try{
        try(PreparedStatement p=db.prepareStatement("DELETE FROM project_details WHERE activity_id=?")){p.setInt(1,id);p.executeUpdate();}
        try(PreparedStatement p=db.prepareStatement("DELETE FROM activities WHERE id=?")){
          p.setInt(1,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Attività inesistente.");
        }
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}
      finally{db.setAutoCommit(true);}
    }
    void addSuspension(LocalDate date,String reason) throws SQLException{try(PreparedStatement p=db.prepareStatement("INSERT INTO suspensions(work_date,reason) VALUES(?,?) ON CONFLICT(work_date) DO UPDATE SET reason=excluded.reason")){p.setString(1,date.toString());p.setString(2,reason.trim().isEmpty()?"Sospensione didattica":reason.trim());p.executeUpdate();}}
    void addSuspensionRange(LocalDate from,LocalDate to,String reason) throws SQLException {
      db.setAutoCommit(false);try {
        LocalDate d=from;while(!d.isAfter(to)){
          if(isMonthLocked(d.getYear(),d.getMonthValue()))throw new IllegalArgumentException("Intervallo comprende un mese bloccato: "+d.getMonthValue()+"/"+d.getYear());
          try(PreparedStatement p=db.prepareStatement("INSERT INTO suspensions(work_date,reason) VALUES(?,?) ON CONFLICT(work_date) DO UPDATE SET reason=excluded.reason")){
            p.setString(1,d.toString());p.setString(2,reason.trim().isEmpty()?"Pausa didattica":reason.trim());p.executeUpdate();}
          d=d.plusDays(1);
        }
        db.commit();
      }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    void removeSuspension(LocalDate date) throws SQLException{try(PreparedStatement p=db.prepareStatement("DELETE FROM suspensions WHERE work_date=?")){p.setString(1,date.toString());p.executeUpdate();}}
    void backup(Path path) throws SQLException{
      String escaped=path.toAbsolutePath().toString().replace("'","''");
      try(Statement s=db.createStatement()){s.execute("VACUUM INTO '"+escaped+"'");}
    }
    ProjectDetails projectDetails(int activityId) throws SQLException {
      String sql="SELECT activity_id,type,funder,project_code,project_role,start_year,start_month,end_year,end_month,notes,start_date,end_date,closed FROM project_details WHERE activity_id=?";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){if(r.next())return new ProjectDetails(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),getNullableInt(r,6),getNullableInt(r,7),getNullableInt(r,8),getNullableInt(r,9),r.getString(10),parseNullableIso(r.getString(11)),parseNullableIso(r.getString(12)),r.getInt(13)!=0);}}
      return new ProjectDetails(activityId,"","","","",null,null,null,null,"",null,null,false);
    }
    boolean hasLockedEntries(int id) throws SQLException {
      try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM entries e WHERE e.activity_id=? AND ("+
        "EXISTS(SELECT 1 FROM month_locks m WHERE m.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND m.month=CAST(substr(e.work_date,6,2) AS INTEGER)) " +
        "OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=e.activity_id AND l.year=CAST(substr(e.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(e.work_date,6,2) AS INTEGER)) " +
        "OR EXISTS(SELECT 1 FROM project_details d WHERE d.activity_id=e.activity_id AND d.closed=1)) LIMIT 1")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){return r.next();}
      }
    }
    ProjectBrand projectBrand(int activityId) throws SQLException {
      try(PreparedStatement p=db.prepareStatement("SELECT funding_label,acronym,full_name FROM project_details WHERE activity_id=?")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){
          if(r.next())return new ProjectBrand(r.getString(1),r.getString(2),r.getString(3));
        }
      }
      return new ProjectBrand("","","");
    }
    void saveProjectProfile(ProjectDetails details,String activityName,String funding,String acronym,String fullName) throws SQLException {
      if(activityName==null||activityName.isBlank()||acronym==null||acronym.isBlank()||fullName==null||fullName.isBlank())
        throw new IllegalArgumentException("Nome attività, acronimo e denominazione completa sono obbligatori.");
      db.setAutoCommit(false);
      try{
        saveProjectDetails(details);
        try(PreparedStatement p=db.prepareStatement("UPDATE activities SET name=? WHERE id=?")){
          p.setString(1,activityName.trim());p.setInt(2,details.activityId());
          if(p.executeUpdate()!=1)throw new SQLException("Attività progetto inesistente.");
        }
        try(PreparedStatement p=db.prepareStatement("UPDATE project_details SET funding_label=?,acronym=?,full_name=? WHERE activity_id=?")){
          p.setString(1,funding==null?"":funding.trim());p.setString(2,acronym.trim());p.setString(3,fullName.trim());p.setInt(4,details.activityId());
          if(p.executeUpdate()!=1)throw new SQLException("Salvataggio denominazione progetto non riuscito.");
        }
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}
      finally{db.setAutoCommit(true);}
    }
    private static LocalDate parseNullableIso(String s){return s==null||s.isBlank()?null:LocalDate.parse(s);}
    private static Integer getNullableInt(ResultSet r,int idx) throws SQLException{int v=r.getInt(idx);return r.wasNull()?null:v;}
    void saveProjectDetails(ProjectDetails d) throws SQLException {
      if(d.startDate()!=null&&d.endDate()!=null&&d.endDate().isBefore(d.startDate()))throw new IllegalArgumentException("Periodo non valido.");
      // Non spostare i confini sopra ore storiche, proprie o dei colleghi.
      String check="SELECT COUNT(*) FROM (SELECT work_date FROM entries WHERE activity_id=? UNION ALL SELECT h.work_date FROM project_person_hours h JOIN project_people p ON p.id=h.person_id WHERE p.activity_id=?) t WHERE (? IS NOT NULL AND work_date<?) OR (? IS NOT NULL AND work_date>?)";
      try(PreparedStatement p=db.prepareStatement(check)){
        p.setInt(1,d.activityId());p.setInt(2,d.activityId());
        String st=d.startDate()==null?null:d.startDate().toString(),ed=d.endDate()==null?null:d.endDate().toString();
        p.setString(3,st);p.setString(4,st);p.setString(5,ed);p.setString(6,ed);
        try(ResultSet r=p.executeQuery()){if(r.next()&&r.getInt(1)>0)throw new IllegalArgumentException("Il nuovo periodo escluderebbe ore già registrate. Correggi il periodo o le registrazioni prima di salvare.");}
      }
      String sql="INSERT INTO project_details(activity_id,type,funder,project_code,project_role,start_year,start_month,end_year,end_month,notes,start_date,end_date,closed,closed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ?=1 THEN CURRENT_TIMESTAMP ELSE NULL END) " +
        "ON CONFLICT(activity_id) DO UPDATE SET type=excluded.type,funder=excluded.funder,project_code=excluded.project_code,project_role=excluded.project_role,start_year=excluded.start_year,start_month=excluded.start_month,end_year=excluded.end_year,end_month=excluded.end_month,notes=excluded.notes,start_date=excluded.start_date,end_date=excluded.end_date,closed=excluded.closed,closed_at=CASE WHEN excluded.closed=1 THEN COALESCE(project_details.closed_at,CURRENT_TIMESTAMP) ELSE NULL END";
      try(PreparedStatement p=db.prepareStatement(sql)){
        p.setInt(1,d.activityId());p.setString(2,d.type());p.setString(3,d.funder());p.setString(4,d.code());p.setString(5,d.projectRole());
        setNullable(p,6,d.startYear());setNullable(p,7,d.startMonth());setNullable(p,8,d.endYear());setNullable(p,9,d.endMonth());p.setString(10,d.notes());
        p.setString(11,d.startDate()==null?null:d.startDate().toString());p.setString(12,d.endDate()==null?null:d.endDate().toString());
        p.setInt(13,d.closed()?1:0);p.setInt(14,d.closed()?1:0);
        if(p.executeUpdate()!=1)throw new SQLException("Salvataggio progetto non confermato.");
      }
    }
    private static void setNullable(PreparedStatement p,int idx,Integer value) throws SQLException{if(value==null)p.setNull(idx,Types.INTEGER);else p.setInt(idx,value);}
    List<ProjectPerson> projectPeople(int activityId) throws SQLException {
      List<ProjectPerson> result=new ArrayList<>();
      try(PreparedStatement p=db.prepareStatement("SELECT id,activity_id,full_name,qualification,project_role,notes FROM project_people WHERE activity_id=? ORDER BY full_name COLLATE NOCASE")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new ProjectPerson(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)));}}
      return result;
    }
    int saveProjectPerson(Integer id,int activityId,String name,String qualification,String role,String note) throws SQLException{
      String sql=id==null?"INSERT INTO project_people(activity_id,full_name,qualification,project_role,notes) VALUES(?,?,?,?,?)":"UPDATE project_people SET activity_id=?,full_name=?,qualification=?,project_role=?,notes=? WHERE id=?";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setInt(1,activityId);p.setString(2,name);p.setString(3,qualification);p.setString(4,role);p.setString(5,note);if(id!=null)p.setInt(6,id);p.executeUpdate();}
      if(id!=null)return id;
      try(Statement st=db.createStatement();ResultSet keys=st.executeQuery("SELECT last_insert_rowid()")){if(keys.next())return keys.getInt(1);}
      throw new SQLException("Identificativo collega non generato.");
    }
    void deleteProjectPerson(int id) throws SQLException{try(PreparedStatement p=db.prepareStatement("DELETE FROM project_people WHERE id=?")){p.setInt(1,id);p.executeUpdate();}}
    List<PersonHours> projectHours(int activityId,int year) throws SQLException{
      List<PersonHours> result=new ArrayList<>();
      try(PreparedStatement p=db.prepareStatement("SELECT h.id,h.person_id,p.full_name,h.work_date,h.hours,h.note FROM project_person_hours h JOIN project_people p ON p.id=h.person_id WHERE p.activity_id=? AND h.work_date >= ? AND h.work_date < ? ORDER BY h.work_date,h.id")){
        p.setInt(1,activityId);p.setString(2,year+"-01-01");p.setString(3,(year+1)+"-01-01");
        try(ResultSet r=p.executeQuery()){while(r.next())result.add(new PersonHours(r.getInt(1),r.getInt(2),r.getString(3),LocalDate.parse(r.getString(4)),r.getDouble(5),r.getString(6)));}}
      return result;
    }
    void saveProjectHours(Integer id,int personId,LocalDate date,double hours,String notes) throws SQLException{
      if(!Double.isFinite(hours)||hours<=0||hours>24)throw new IllegalArgumentException("Ore non valide.");
      String sql=id==null?"INSERT INTO project_person_hours(person_id,work_date,hours,note) VALUES(?,?,?,?)":"UPDATE project_person_hours SET person_id=?,work_date=?,hours=?,note=? WHERE id=?";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setInt(1,personId);p.setString(2,date.toString());p.setDouble(3,hours);p.setString(4,notes);if(id!=null)p.setInt(5,id);p.executeUpdate();}
    }
    void deleteProjectHours(int id) throws SQLException{try(PreparedStatement p=db.prepareStatement("DELETE FROM project_person_hours WHERE id=?")){p.setInt(1,id);p.executeUpdate();}}
    List<ProjectDocument> projectDocuments(int activityId) throws SQLException{
      List<ProjectDocument> docs=new ArrayList<>();try(PreparedStatement p=db.prepareStatement("SELECT id,activity_id,filename,description,period_year,period_month,file_size,uploaded_at FROM project_documents WHERE activity_id=? ORDER BY uploaded_at DESC,id DESC")){
        p.setInt(1,activityId);try(ResultSet r=p.executeQuery()){while(r.next())docs.add(new ProjectDocument(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),getNullableInt(r,5),getNullableInt(r,6),r.getLong(7),r.getString(8)));}}
      return docs;
    }
    void addProjectDocument(int activityId,Path file,String description,Integer year,Integer month) throws Exception{
      if(!Files.isRegularFile(file)||!Files.isReadable(file))throw new IOException("Il documento non esiste o non è leggibile: "+file);
      long size=Files.size(file);if(size>25L*1024*1024)throw new IOException("Allegato troppo grande (max 25 MB).");
      byte[] contents=Files.readAllBytes(file);
      if(contents.length!=size)throw new IOException("Dimensione del documento variata durante la lettura. Riprova.");
      try(PreparedStatement p=db.prepareStatement("INSERT INTO project_documents(activity_id,filename,description,period_year,period_month,file_size,contents) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
        p.setInt(1,activityId);p.setString(2,file.getFileName().toString());p.setString(3,description);setNullable(p,4,year);setNullable(p,5,month);p.setLong(6,size);p.setBytes(7,contents);
        if(p.executeUpdate()!=1)throw new SQLException("Salvataggio allegato non riuscito.");
      }
      try(PreparedStatement check=db.prepareStatement("SELECT LENGTH(contents),file_size FROM project_documents WHERE activity_id=? ORDER BY id DESC LIMIT 1")){
        check.setInt(1,activityId);try(ResultSet r=check.executeQuery()){
          if(!r.next()||r.getLong(1)!=size||r.getLong(2)!=size)throw new SQLException("Dimensione dell'allegato nel database non corrispondente al file originale.");
        }
      }
    }
    /** Importazione atomica: un errore di uno degli allegati annulla l'intero gruppo. */
    void addProjectDocuments(int activityId,List<Path> paths,String note,Integer year,Integer month) throws Exception{
      if(paths==null||paths.isEmpty())throw new IllegalArgumentException("Nessun file selezionato.");
      for(Path file:paths){if(!Files.isRegularFile(file)||!Files.isReadable(file)||Files.size(file)>25L*1024*1024)
        throw new IOException("File non valido, illeggibile o maggiore di 25 MB: "+file.getFileName());}
      db.setAutoCommit(false);
      try{
        for(Path file:paths)addProjectDocument(activityId,file,note,year,month);
        db.commit();
      }catch(Exception ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    void updateProjectDocumentNote(int id,String note) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("UPDATE project_documents SET description=? WHERE id=?")){
        p.setString(1,note==null?"":note);p.setInt(2,id);
        if(p.executeUpdate()!=1)throw new SQLException("Allegato non trovato.");
      }
    }
    byte[] readProjectDocument(int id) throws SQLException,IOException{
      try(PreparedStatement p=db.prepareStatement("SELECT contents,file_size FROM project_documents WHERE id=?")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){
          if(!r.next())throw new IOException("Documento non trovato nel database.");
          byte[] data=r.getBytes(1);if(data==null||data.length!=r.getLong(2))throw new IOException("Il documento memorizzato non supera il controllo di integrità.");
          return data;
        }
      }
    }
    void exportProjectDocument(int id,Path destination) throws Exception{
      byte[] data;String filename;
      try(PreparedStatement p=db.prepareStatement("SELECT contents,filename,file_size FROM project_documents WHERE id=?")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){
          if(!r.next())throw new IOException("Documento non trovato nel database.");
          filename=r.getString(2);data=r.getBytes(1);
          if(data==null||data.length!=r.getLong(3))throw new IOException("Documento danneggiato o dimensione non coerente: "+filename);
        }
      }
      Path dest=destination.toAbsolutePath().normalize(),directory=dest.getParent();
      if(directory==null||!Files.isDirectory(directory))throw new IOException("Cartella di destinazione non valida: "+destination);
      Path tmp=Files.createTempFile(directory,".registro-ore-allegato-",".tmp");
      try{
        Files.write(tmp,data);
        if(Files.size(tmp)!=data.length)throw new IOException("Scrittura incompleta.");
        try{Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
      }finally{Files.deleteIfExists(tmp);}
    }
    void deleteProjectDocument(int id) throws SQLException{
      try(PreparedStatement p=db.prepareStatement("DELETE FROM project_documents WHERE id=? AND NOT EXISTS (SELECT 1 FROM month_locks l WHERE l.year=project_documents.period_year AND l.month=project_documents.period_month)")){
        p.setInt(1,id);if(p.executeUpdate()!=1)throw new SQLException("Il documento appartiene a un mese bloccato o non esiste.");}
    }


    /** A colleague has one role history shared by all project memberships with the same name. */
    List<Integer> colleagueMemberships(int personId) throws SQLException {
      List<Integer> ids=new ArrayList<>();
      String sql="SELECT id FROM project_people WHERE full_name=(SELECT full_name FROM project_people WHERE id=?) COLLATE NOCASE ORDER BY id";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setInt(1,personId);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getInt(1));}}
      if(ids.isEmpty())throw new IllegalArgumentException("Collega inesistente.");
      return ids;
    }
    List<RolePeriod> roleHistory(Integer personId) throws SQLException {
      List<RolePeriod> periods=new ArrayList<>();
      if(personId==null){
        try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT id,start_date,role FROM own_role_history ORDER BY start_date")){
          while(r.next())periods.add(new RolePeriod(r.getInt(1),null,LocalDate.parse(r.getString(2)),r.getString(3)));
        }
      }else{
        String sql="SELECT h.id,h.person_id,h.start_date,h.role FROM person_role_history h JOIN project_people p ON p.id=h.person_id WHERE p.full_name=(SELECT full_name FROM project_people WHERE id=?) COLLATE NOCASE ORDER BY h.start_date,h.person_id";
        Set<LocalDate> seen=new HashSet<>();
        try(PreparedStatement p=db.prepareStatement(sql)){p.setInt(1,personId);try(ResultSet r=p.executeQuery()){
          while(r.next()){LocalDate date=LocalDate.parse(r.getString(3));if(seen.add(date))periods.add(new RolePeriod(r.getInt(1),r.getInt(2),date,r.getString(4)));}
        }}
      }
      return periods;
    }
    String roleAt(Integer personId,LocalDate date) throws SQLException {
      String sql=personId==null?
        "SELECT role FROM own_role_history WHERE start_date<=? ORDER BY start_date DESC LIMIT 1":
        "SELECT h.role FROM person_role_history h JOIN project_people p ON p.id=h.person_id WHERE p.full_name=(SELECT full_name FROM project_people WHERE id=?) COLLATE NOCASE AND h.start_date<=? ORDER BY h.start_date DESC,h.person_id LIMIT 1";
      try(PreparedStatement p=db.prepareStatement(sql)){
        int ix=1;if(personId!=null)p.setInt(ix++,personId);p.setString(ix,date.toString());
        try(ResultSet r=p.executeQuery()){if(r.next())return r.getString(1);}
      }
      if(personId==null){try(PreparedStatement p=db.prepareStatement("SELECT role FROM annual_settings WHERE year=?")){
        p.setInt(1,date.getYear());try(ResultSet r=p.executeQuery()){if(r.next()&&rate(r.getString(1))>0)return r.getString(1);}
      }}
      return UNSET_ROLE;
    }
    int hourlyRateAt(Integer personId,LocalDate day) throws SQLException{return rate(roleAt(personId,day));}
    /** Blocks retroactive role changes that could modify costs in closed project timesheets. */
    private void ensureColleagueRoleEditable(List<Integer> ids,LocalDate affected) throws SQLException {
      String sql="SELECT h.work_date FROM project_person_hours h JOIN project_people p ON p.id=h.person_id " +
        "LEFT JOIN project_details d ON d.activity_id=p.activity_id " +
        "WHERE h.person_id=? AND h.work_date>=? AND (COALESCE(d.closed,0)=1 " +
        "OR EXISTS(SELECT 1 FROM month_locks m WHERE m.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND m.month=CAST(substr(h.work_date,6,2) AS INTEGER)) " +
        "OR EXISTS(SELECT 1 FROM project_month_locks l WHERE l.activity_id=p.activity_id AND l.year=CAST(substr(h.work_date,1,4) AS INTEGER) AND l.month=CAST(substr(h.work_date,6,2) AS INTEGER))) LIMIT 1";
      try(PreparedStatement p=db.prepareStatement(sql)){for(Integer id:ids){p.setInt(1,id);p.setString(2,affected.toString());try(ResultSet r=p.executeQuery()){
        if(r.next())throw new IllegalArgumentException("Storico ruolo non modificabile: il collega ha ore su progetto o timesheet bloccato dal "+r.getString(1)+". Sbloccare prima.");
      }}}
    }
    void addRolePeriod(Integer personId,LocalDate date,String role) throws SQLException {
      if(rate(role)==0)throw new IllegalArgumentException("Qualifica non valida per costo orario.");
      if(personId==null){try(PreparedStatement p=db.prepareStatement("INSERT INTO own_role_history(start_date,role) VALUES(?,?)")){
        p.setString(1,date.toString());p.setString(2,role);p.executeUpdate();}return;}
      List<Integer> ids=colleagueMemberships(personId);ensureColleagueRoleEditable(ids,date);
      db.setAutoCommit(false);
      try(PreparedStatement p=db.prepareStatement("INSERT INTO person_role_history(person_id,start_date,role) VALUES(?,?,?) ON CONFLICT(person_id,start_date) DO UPDATE SET role=excluded.role")){
        for(int id:ids){p.setInt(1,id);p.setString(2,date.toString());p.setString(3,role);p.executeUpdate();}
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    private LocalDate rolePeriodDate(int id) throws SQLException {
      try(PreparedStatement p=db.prepareStatement("SELECT start_date FROM person_role_history WHERE id=?")){
        p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(r.next())return LocalDate.parse(r.getString(1));}
      }throw new SQLException("Decorrenza del collega non trovata.");
    }
    void updateRolePeriod(Integer personId,int id,LocalDate date,String role) throws SQLException {
      if(rate(role)==0)throw new IllegalArgumentException("Qualifica non valida.");
      if(personId==null){try(PreparedStatement p=db.prepareStatement("UPDATE own_role_history SET start_date=?,role=? WHERE id=?")){
        p.setString(1,date.toString());p.setString(2,role);p.setInt(3,id);if(p.executeUpdate()!=1)throw new SQLException("Decorrenza non trovata.");}return;}
      LocalDate previous=rolePeriodDate(id);List<Integer> ids=colleagueMemberships(personId);
      ensureColleagueRoleEditable(ids,previous.isBefore(date)?previous:date);
      db.setAutoCommit(false);
      try(PreparedStatement update=db.prepareStatement("UPDATE person_role_history SET start_date=?,role=? WHERE person_id=? AND start_date=?");
          PreparedStatement insert=db.prepareStatement("INSERT INTO person_role_history(person_id,start_date,role) VALUES(?,?,?)")){
        for(int membership:ids){
          update.setString(1,date.toString());update.setString(2,role);update.setInt(3,membership);update.setString(4,previous.toString());
          if(update.executeUpdate()==0){insert.setInt(1,membership);insert.setString(2,date.toString());insert.setString(3,role);insert.executeUpdate();}
        }db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    void deleteRolePeriod(Integer personId,int id) throws SQLException {
      if(personId==null){try(PreparedStatement p=db.prepareStatement("DELETE FROM own_role_history WHERE id=?")){
        p.setInt(1,id);if(p.executeUpdate()!=1)throw new SQLException("Decorrenza non trovata.");}return;}
      LocalDate previous=rolePeriodDate(id);List<Integer> ids=colleagueMemberships(personId);
      ensureColleagueRoleEditable(ids,previous);
      db.setAutoCommit(false);
      try(PreparedStatement p=db.prepareStatement("DELETE FROM person_role_history WHERE person_id=? AND start_date=?")){
        for(int membership:ids){p.setInt(1,membership);p.setString(2,previous.toString());p.executeUpdate();}
        db.commit();
      }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    List<ColleagueAssignment> colleagueAssignments(int year) throws SQLException{
      List<ColleagueAssignment> result=new ArrayList<>();
      String sql="SELECT p.id,p.full_name,a.name,p.project_role FROM project_people p JOIN activities a ON a.id=p.activity_id ORDER BY p.full_name COLLATE NOCASE,a.name COLLATE NOCASE";
      try(PreparedStatement q=db.prepareStatement(sql);ResultSet r=q.executeQuery()){
        try(PreparedStatement hours=db.prepareStatement("SELECT work_date,hours FROM project_person_hours WHERE person_id=? AND work_date BETWEEN ? AND ?")){
          while(r.next()){
            int id=r.getInt(1);hours.setInt(1,id);hours.setString(2,year+"-01-01");hours.setString(3,year+"-12-31");
            double total=0,cost=0;
            try(ResultSet h=hours.executeQuery()){while(h.next()){
              LocalDate day=LocalDate.parse(h.getString(1));double value=h.getDouble(2);total+=value;
              cost+=value*hourlyRateAt(id,day);
            }}
            result.add(new ColleagueAssignment(id,r.getString(2),r.getString(3),r.getString(4),
              roleAt(id,LocalDate.of(year,12,31)),total,cost));
          }
        }
      }
      return result;
    }
    List<ProjectExportRow> projectExportRows(int projectId,LocalDate from,LocalDate to) throws SQLException{
      List<ProjectExportRow> out=new ArrayList<>();
      String own="SELECT e.work_date,e.hours,e.note FROM entries e JOIN activities a ON a.id=e.activity_id JOIN categories c ON c.name=COALESCE(e.category_snapshot,a.category) WHERE e.activity_id=? AND e.work_date BETWEEN ? AND ? AND e.hours IS NOT NULL AND c.kind='PROGETTI' ORDER BY e.work_date,e.id";
      String person="SELECT h.work_date,h.hours,h.note,p.id,p.full_name,p.project_role FROM project_person_hours h JOIN project_people p ON p.id=h.person_id WHERE p.activity_id=? AND h.work_date BETWEEN ? AND ? ORDER BY h.work_date,h.id";
      try(PreparedStatement stmt=db.prepareStatement(own)){
        stmt.setInt(1,projectId);stmt.setString(2,from.toString());stmt.setString(3,to.toString());
        try(ResultSet r=stmt.executeQuery()){while(r.next()){
          LocalDate day=LocalDate.parse(r.getString(1));out.add(new ProjectExportRow(day,"Docente","Tu",roleAt(null,day),"",r.getDouble(2),hourlyRateAt(null,day),r.getString(3)));
        }}
      }
      try(PreparedStatement stmt=db.prepareStatement(person)){
        stmt.setInt(1,projectId);stmt.setString(2,from.toString());stmt.setString(3,to.toString());
        try(ResultSet r=stmt.executeQuery()){while(r.next()){
          LocalDate day=LocalDate.parse(r.getString(1));int personId=r.getInt(4);
          out.add(new ProjectExportRow(day,"Collega",r.getString(5),roleAt(personId,day),r.getString(6),r.getDouble(2),hourlyRateAt(personId,day),r.getString(3)));
        }}
      }
      out.sort(Comparator.comparing(ProjectExportRow::day).thenComparing(ProjectExportRow::colleague,String.CASE_INSENSITIVE_ORDER));
      return out;
    }
    List<LogEntry> searchEntries(String needle,LocalDate from,LocalDate to) throws SQLException{
      List<LogEntry> result=new ArrayList<>();
      String sql="SELECT e.id,e.work_date,a.id,a.name,COALESCE(e.category_snapshot,a.category),a.color,e.hours,e.note,e.source_ref,e.mission_id FROM entries e JOIN activities a ON a.id=e.activity_id WHERE e.work_date BETWEEN ? AND ? AND a.name LIKE ? ESCAPE '~' COLLATE NOCASE ORDER BY e.work_date,e.id";
      try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,from.toString());p.setString(2,to.toString());p.setString(3,"%"+needle.replace("~","~~").replace("%","~%").replace("_","~_")+"%");
        try(ResultSet r=p.executeQuery()){while(r.next()){
          Activity activity=new Activity(r.getInt(3),r.getString(4),r.getString(5),r.getString(6));Object h=r.getObject(7);
          result.add(new LogEntry(r.getInt(1),LocalDate.parse(r.getString(2)),activity,h==null?null:r.getDouble(7),r.getString(8),r.getString(9),r.getObject(10)==null?null:r.getInt(10)));
        }}
      }
      return result;
    }

    @Override public void close() throws SQLException{db.close();}
  }
}
