# Architettura e sviluppo

## Tecnologia

- Interfaccia: **Java Swing / Java2D** (`App.java`, `NavigationIcons.java`).
- Persistenza: **SQLite** su file, con migrazioni e vincoli nel codice Java.
- Backup/controllo DB: `DatabaseBackups.java`, `DatabaseStartup.java`.
- Anteprime: `DocumentViewer.java`, con dipendenza PDFBox.
- Export: `SimpleXlsx.java`, `ProjectReportXlsx.java`.
- Launcher Windows: `WindowsLauncher/main.go` (sorgente del wrapper).

## Requisiti per compilare

Java Development Kit **17+**, **Apache Maven**, accesso Internet alla prima compilazione per le dipendenze del `pom.xml`.

```bash
mvn -B -ntp clean package
```

Il risultato standard è sotto `target/` e include il JAR eseguibile prodotto da Maven Shade. Non effettuare commit di `target/`, `.jar` compilati, database personali o log.

Per avviare localmente il JAR serve un database inizializzato, per esempio una **copia privata** del file vergine fornito:

```bash
mkdir -p data
cp database_vuoto.sqlite data/registro_ore_2026.sqlite
java -Dregistro.db="$(pwd)/data/registro_ore_2026.sqlite" -jar target/registro-ore-3.11.3.jar
```

> L'app può aggiornare lo schema SQL al primo avvio: non puntare mai un esperimento al database personale.

## Test e release

Prima di pubblicare una nuova versione:

1. Compilare con Java 17 e verificare che la versione in `pom.xml` e `App.java` coincidano.
2. Verificare l'integrità di un database vergine, uno storico e uno con mesi/progetti bloccati.
3. Testare le migrazioni su **copie**: confrontare registrazioni e totale ore prima/dopo.
4. Testare l'installazione e l'avvio su Mac, Windows 11 e Linux Mint reali.
5. Compilare i pacchetti platform-specific e controllare gli SHA-256.
6. Pubblicare `vX.Y.Z` e gli installer nella sezione **Releases**.

Il workflow `.github/workflows/java-ci.yml` effettua soltanto la compilazione Maven su Java 17: **non sostituisce i test grafici o di migrazione SQLite**.

## Dipendenze

Si trovano in `pom.xml`. Prima della distribuzione estesa valutare aggiornamenti di sicurezza e le licenze delle dipendenze: in particolare PDFBox **1.8.16** è una versione storica, da rivedere prima di una futura major release.

## Licenza del progetto

Il codice originale di Registro Ore è distribuito con licenza MIT (vedere [`LICENSE`](../LICENSE)). Le dipendenze esterne elencate in `pom.xml` e i relativi componenti redistribuiti hanno licenze proprie, che devono essere rispettate in fase di packaging.

## Migrazione anagrafica 3.11.1

La migrazione aggiunge `activities.archived_at` e le colonne `project_details.funding_label`, `project_details.acronym`, `project_details.full_name` con `ALTER TABLE` (senza ricreare le tabelle). Viene creato un backup prima dell'introduzione dell'archivio. I trigger `archived_entry_insert` e `archived_team_insert` impediscono nuove registrazioni sui progetti archiviati. L'eliminazione passa da un controllo esplicito sulle tabelle collegate.

Per la compatibilità con i timesheet già bloccati, modificare il nome anagrafico è permesso con avviso; non viene alterato nessun valore numerico delle registrazioni.

## Migrazione 3.11.1

Il codice `CategoryTaxonomy.java` contiene le nove categorie, i tre livelli di controllo durante le sospensioni e la mappatura prudente delle vecchie denominazioni. `Store.migrateCategoriesV311()` crea il backup iniziale, conserva la precedente classificazione in `activities.legacy_category_v311` e `entries.legacy_category_v311`, salva le mappature in `legacy_category_mapping`, poi applica le categorie canoniche senza alterare i valori numerici delle ore. La migrazione è marcata in `app_metadata` e non deve essere riapplicata ai dati già convertiti. `Store.migrateMissionsV311()` aggiunge `missions`, `mission_documents` e `entries.mission_id` con controlli sul periodo e sui mesi bloccati.

Sono presenti `CategoryTaxonomyTest`, `RegistroViewportTest` e `ProjectIdentityTest` come test Java semplici. Oltre alla compilazione, prima di una Release stabile occorre eseguire test di integrazione JDBC con le dipendenze reali e collaudo grafico su tutti i sistemi operativi.

## Inserimento su più giorni (3.11.1)

`App.batchEntryEditor()` usa un `JDialog` non modale, il cui anno viene fissato all'apertura. La vista legge le registrazioni correnti dal database, presenta il riepilogo per attività e il dettaglio in tooltip (`BatchEntrySupport`) e consente di ricaricare il calendario.

`Store.saveEntriesBatch()` usa una transazione SQLite unica: ricerca per data, activity ID, missione, categoria storica e provenienza locale; conserva e combina le note, somma le ore alle righe compatibili e consolida eventuali doppioni. Le righe incompatibili rimangono separate. Prima di scrivere controlla 24 ore/giorno, 1.500 ore/anno, sospensioni e mesi bloccati; i trigger DB proteggono anche periodi e blocchi del progetto. Il nuovo `BatchEntrySupportTest` verifica i riepiloghi e il testo dei tooltip.

Per Windows il file `RegistroOre.exe` contiene il JAR aggiornato, e viene ricompilato dal sorgente Go. Gli aggiornamenti Windows e Linux hanno pacchetti dedicati che effettuano backup prima di sostituire i binari; i dati rimangono nella directory utente.

## Interfaccia 3.11.3

`QuickHoursSupport` isola la validazione matematica della modifica rapida di 30 minuti; il metodo `Store.adjustEntryHours` esegue in transazione SQLite soltanto l'UPDATE del campo `hours`, ricontrollando la protezione del registro, i limiti giorno/anno e lasciando invariati note, missione e categoria storica. `QuickHoursSupportTest` verifica i casi limite.

Per il calendario multi-giorno Swing, il `JDialog` usa fino a 1260 pixel in larghezza (adattati allo schermo) e il `GridLayout` passa da due a una colonna sotto 1080 pixel. `BatchEntrySupport.dayLabel` rende gli spazi del valore delle ore non separabili, impedendo righe verticali spezzate.
