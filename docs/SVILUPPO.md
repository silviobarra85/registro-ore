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
java -Dregistro.db="$(pwd)/data/registro_ore_2026.sqlite" -jar target/registro-ore-3.10.2.jar
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
