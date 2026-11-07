package it.registroore;

import java.util.*;

/** Nove categorie ufficiali dell'app, distinte dalle classificazioni di altri enti. */
public final class CategoryTaxonomy {
  private CategoryTaxonomy() {}
  public enum Suspension { BLOCK, VERIFY, ALLOW }
  public record Rule(String name, String technicalKind, Suspension suspension,
                     String includes, String excludes, String note) {}

  public static final String FRONT = "Didattica frontale";
  public static final String INTEGRATIVE = "Didattica integrativa e servizio agli studenti";
  public static final String FUNDED = "Ricerca su progetti finanziati";
  public static final String RESEARCH = "Ricerca istituzionale";
  public static final String ADMIN = "Attività istituzionali e gestionali";
  public static final String THIRD = "Terza missione e trasferimento tecnologico";
  public static final String TRAINING = "Formazione e aggiornamento professionale";
  public static final String BOARDS = "Commissioni";
  public static final String OTHER = "Altre attività";

  public static final List<Rule> RULES = List.of(
    new Rule(FRONT,"DIDATTICA FRONTALE",Suspension.BLOCK,
      "Lezioni curricolari, laboratori previsti dagli insegnamenti, esercitazioni frontali, lezioni di master e dottorato.",
      "Esami, commissioni, ricevimento e preparazione dei materiali.",
      "Nelle sospensioni delle lezioni o nel weekend: blocco ordinario, con deroga motivata e registrata. Il provvedimento specifico prevale."),
    new Rule(INTEGRATIVE,"DIDATTICA INTEGRATIVA",Suspension.VERIFY,
      "Ricevimento, tutorato, assistenza tesi, preparazione esami, seminari integrativi e assistenza studenti.",
      "Lezioni frontali e commissioni formalmente costituite (categoria Commissioni).",
      "Nelle sospensioni: verifica che la specifica prestazione si sia svolta e non sia sospesa."),
    new Rule(FUNDED,"PROGETTI",Suspension.ALLOW,
      "Ricerca, sviluppo, riunioni, deliverable, analisi dati e disseminazione direttamente riconducibili a un finanziamento.",
      "Attività generiche non imputabili al progetto o spese di missione assimilate automaticamente a ore.",
      "Durante le sospensioni didattiche è generalmente possibile lavorare; vigono date, blocchi, regolamenti e ammissibilità del finanziamento."),
    new Rule(RESEARCH,"ALTRE ATTIVITÀ",Suspension.ALLOW,
      "Ricerca non imputata a progetti, scrittura e revisione di paper, presentazione scientifica di risultati, conferenze scientifiche di ricerca, ideazione proposte.",
      "Presentazioni divulgative al pubblico (Terza missione); ricerca rendicontata su finanziamenti.",
      "La pausa delle lezioni non vieta di per sé la ricerca. La presentazione di un paper può essere collegata a una missione."),
    new Rule(ADMIN,"ALTRE ATTIVITÀ",Suspension.ALLOW,
      "Consigli di Dipartimento/CdS, Collegio di Dottorato, programmazione, incarichi organizzativi e gestionali.",
      "Commissioni di valutazione formalmente costituite (Commissioni); lezioni.",
      "Le sospensioni delle lezioni non annullano automaticamente gli impegni istituzionali."),
    new Rule(THIRD,"ALTRE ATTIVITÀ",Suspension.ALLOW,
      "Public engagement, divulgazione verso cittadini/scuole/imprese, trasferimento tecnologico e valorizzazione della ricerca.",
      "Conferenze rivolte principalmente alla comunità scientifica; dissemination già rendicontata su un progetto.",
      "Registrabile durante una sospensione didattica se l'attività è autorizzata e realmente svolta."),
    new Rule(TRAINING,"ALTRE ATTIVITÀ",Suspension.ALLOW,
      "Corsi di aggiornamento, formazione professionale, partecipazione a conferenze con finalità prevalente di apprendimento.",
      "Conferenze con presentazione di risultati originali (Ricerca); formazione già imputata a un progetto.",
      "Registrabile se svolta e riconosciuta nel contesto del proprio timesheet."),
    new Rule(BOARDS,"ALTRE ATTIVITÀ",Suspension.VERIFY,
      "Commissioni di laurea, esami, ammissione e valutazione dottorandi, concorsi, selezioni e commissioni scientifiche.",
      "Collegio di Dottorato ordinario e riunioni di gestione (Attività istituzionali e gestionali).",
      "Durante la sospensione: verifica convocazione e specifico provvedimento; i singoli esami possono essere sospesi oppure proseguire."),
    new Rule(OTHER,"ALTRE ATTIVITÀ",Suspension.VERIFY,
      "Prestazioni lavorative reali non riconducibili alle otto categorie precedenti, con descrizione esplicita.",
      "Ferie, malattia, festività o ore duplicate; non sono ore di lavoro svolto.",
      "Motivazione necessaria durante la sospensione per classificare la prestazione e verificarne lo svolgimento.")
  );
  private static final Map<String,Rule> INDEX;
  static { Map<String,Rule> m=new LinkedHashMap<>();for(Rule r:RULES)m.put(r.name().toLowerCase(Locale.ROOT),r);INDEX=Collections.unmodifiableMap(m); }
  public static Rule rule(String name) {return name==null?null:INDEX.get(name.trim().toLowerCase(Locale.ROOT));}
  public static String[] names() {return RULES.stream().map(Rule::name).toArray(String[]::new);}

  /** Una mappatura prudente e documentata: ambiguità => Altre attività, da rivedere. */
  public record Guess(String target, boolean requiresReview) {}
  public static Guess mapLegacy(String name,String technicalKind) {
    if(rule(name)!=null)return new Guess(rule(name).name(),false);
    String n=name==null?"":name.toLowerCase(Locale.ROOT).trim();
    String k=technicalKind==null?"":technicalKind.toUpperCase(Locale.ROOT);
    if(k.equals("PROGETTI"))return new Guess(FUNDED,false);
    // La denominazione di una commissione formalmente costituita prevale sulla
    // vecchia regola tecnica integrativa eventualmente associata.
    if(n.contains("commission")||n.contains("valutazione dottor"))return new Guess(BOARDS,false);
    if(k.equals("DIDATTICA FRONTALE"))return new Guess(FRONT,false);
    if(k.equals("DIDATTICA INTEGRATIVA"))return new Guess(INTEGRATIVE,false);
    if(k.equals("ATTIVITÀ ORDINARIA"))return new Guess(OTHER,true);
    if(n.contains("esam"))return new Guess(BOARDS,false);
    if(n.contains("ricerca")&& !n.contains("progett"))return new Guess(RESEARCH,false);
    if(n.contains("istituzion")||n.contains("gestional")||n.contains("consigl"))return new Guess(ADMIN,false);
    if(n.contains("terza mission")||n.contains("trasferimento")||n.contains("public engagement"))return new Guess(THIRD,false);
    if(n.contains("formazione")||n.contains("aggiornamento"))return new Guess(TRAINING,false);
    if(n.contains("integrativ")||n.contains("tutor")||n.contains("ricevimento"))return new Guess(INTEGRATIVE,false);
    if(n.contains("frontale")||n.contains("lezion"))return new Guess(FRONT,false);
    return new Guess(OTHER,true);
  }
}
