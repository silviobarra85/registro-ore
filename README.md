# Registro Ore · v3.11.1

<p align="center"><img src="assets/registro-ore-icon.png" alt="Icona Registro Ore" width="128"></p>

**Applicazione desktop per registrare, pianificare e rendicontare le ore di didattica, attività istituzionali e progetti di ricerca.**

Disponibile per **macOS, Windows 10/11 (64 bit)** e **Linux Mint (64 bit)**. È realizzata in Java Swing e usa un **database SQLite locale**, senza server. Questa distribuzione è la versione **3.11.1**.

**Dati personali:** i pacchetti distribuiti includono un database iniziale privo di registrazioni, progetti, colleghi e allegati. Le registrazioni di ciascun utente rimangono nel database locale e non vengono trasmesse al repository.

## Scarica e installa

| Sistema | Prima installazione | Aggiornamento / alternativa |
|---|---|---|
| **macOS** | [Installazione vergine (.zip)](installers/macos/RegistroOre_3_11_1_MAC_VERGINE.zip) | [Aggiornamento con doppio clic (.zip)](installers/macos/RegistroOre_3_11_1_MAC_AGGIORNAMENTO.zip) |
| **Windows 10/11 x64** | [Applicazione (.zip)](installers/windows/RegistroOre_3_11_1_Windows_x64.zip) | Sostituire l'eseguibile solo dopo un backup |
| **Linux Mint x64** | [Pacchetto (.deb)](installers/linux/RegistroOre_3_11_1_LinuxMint_amd64.deb) | [Versione portabile (.zip)](installers/linux/RegistroOre_3_11_1_LinuxMint_Portabile.zip) |

Le versioni distribuite sono disponibili anche nella **[sezione Releases](../../releases)**.

**Requisiti:** JRE/JDK **Java 17+** per Mac e Linux. Su Windows il launcher usa una Java Runtime compatibile, se presente, o può predisporla alla prima esecuzione (serve Internet). Gli eseguibili non sono firmati digitalmente: macOS e Windows possono richiedere un'autorizzazione di sicurezza.

**Novità 3.11.1:** inserimento su più giorni con somma delle ore già presenti, calendario informativo con tooltip giornaliero e finestra indipendente dal Registro. La versione 3.11.0 ha introdotto nove **Categorie** predefinite al posto delle vecchie categorie personalizzabili e delle regole operative separate; rendicontazione per categoria; guida alle categorie nell’app; archivio delle missioni e collegamento facoltativo alle registrazioni (senza duplicare le ore). Le attività e i progetti esistenti rimangono disponibili.

## Funzioni principali

- Calendario e registro **multiannuale**, con più attività nello stesso giorno e inserimenti ripetuti su più giornate, con **somma automatica delle ore compatibili**, anteprima delle ore già presenti e dettaglio al passaggio del mouse.
- Monte ore di **1.500 ore per anno**, avvisi per giornate oltre **8 ore** e sospensioni didattiche personalizzabili.
- **Nove Categorie predefinite**, una per attività, con controlli integrati su sospensioni didattiche e rendicontazione per categoria. Le singole attività restano modificabili e archiviabili.
- **Guida alle categorie** accessibile dall’app, con esempi, esclusioni e comportamento durante le sospensioni.
- **Missioni trasversali** per conferenze, presentazioni di paper, commissioni esterne, visite e riunioni: dati dell’evento, DOI, ruolo, progetto e allegati; le ore restano registrazioni uniche.
- Schede **progetto** con nome dell’attività, finanziamento, acronimo e denominazione completa modificabili, intervallo di validità, collaboratori, documenti, blocchi mensili, chiusura ed esportazioni.
- **Archivio attività** esplicito e reversibile: le attività archiviate non accettano nuove ore, ma conservano lo storico e i documenti. Eliminazione definitiva consentita solo se l’attività non ha dati collegati.
- Storico dei **ruoli** di docenti e collaboratori, con tariffe configurate: Ordinario 81 €/h, Associato 53 €/h, Ricercatore 34 €/h.
- Riepiloghi mensili, annuali e per progetto, esportabili in **CSV ed Excel**.
- **Backup giornalieri** SQLite, ripristino, storico modifiche e controllo del database all'avvio.
- Barra laterale compatta/espandibile, con menu a sezioni e icone Java2D.

Queste funzioni sono strumenti di gestione: **controllare sempre la conformità dei timesheet ai regolamenti dell'ente e del progetto**.

## Le nove categorie

Le categorie **sono tipologie gestionali dell’app**, non voci ufficiali preapprovate della rendicontazione di un Ateneo o di un bando. Sono: didattica frontale; didattica integrativa e servizio agli studenti; ricerca su progetti finanziati; ricerca istituzionale; attività istituzionali e gestionali; terza missione e trasferimento tecnologico; formazione e aggiornamento professionale; commissioni; altre attività.

Le sospensioni delle lezioni **non equivalgono** a chiusure di Ateneo o assenze personali: le categorie prevedono attività consentite, verifica con motivazione oppure blocco normalmente derogabile soltanto con una motivazione esplicita. Consultare [Guida alle categorie](docs/CATEGORIE_E_SOSPENSIONI.md).

Le **missioni non sono una decima categoria**: una missione può comprendere una presentazione di paper, un convegno scientifico, una commissione o una riunione su progetto; le ore sono registrate una sola volta con collegamento facoltativo alla missione.

## Manuale e documentazione

- **[MANUALE UTENTE (Italiano)](docs/MANUALE_UTENTE.md)** — installazione, uso quotidiano, progetti, allegati, esportazioni e backup.
- [Guida alle nove categorie e sospensioni](docs/CATEGORIE_E_SOSPENSIONI.md)
- [Guida all'installazione e aggiornamento](docs/INSTALLAZIONE_E_AGGIORNAMENTO.md)
- [Domande frequenti e risoluzione problemi](docs/FAQ.md)
- [Struttura del progetto e sviluppo](docs/SVILUPPO.md)
- [Privacy e sicurezza dei dati](SECURITY.md)
- [Note di versione](CHANGELOG.md)
- [Installer e checksum](installers/README.md)

## Dove vengono salvati i dati?

| Sistema | Posizione tipica del database |
|---|---|
| macOS | `~/Library/Application Support/RegistroOre/data/registro_ore_2026.sqlite` |
| Windows | `%LOCALAPPDATA%\RegistroOre\data\registro_ore_2026.sqlite` |
| Linux Mint | `~/.local/share/RegistroOre/data/registro_ore_2026.sqlite` |

**Attenzione, soprattutto su macOS:** se è configurato `config/db_path.txt`, l'app potrebbe usare un percorso personalizzato. Controllare il **percorso effettivo mostrato in basso a sinistra** e aprire **Sistema → Stato database** prima di sostituire o ripristinare alcun file.

## Compilazione dai sorgenti

Richiede Java **JDK 17 o successivo** e Apache Maven:

```bash
mvn clean package
```

L'applicazione Java compilata usa le dipendenze dichiarate in `pom.xml`. L'avvio completo richiede un database inizializzato e il percorso fornito con `-Dregistro.db=...`; vedi [guida sviluppo](docs/SVILUPPO.md). Il launcher Windows è descritto in `WindowsLauncher/`.

## Licenza, autore e attribuzione

**© 2026 Silvio Barra (DIETI-UNINA) & ChatGPT · Registro Ore v3.11.1**

Autore: **Silvio Barra (DIETI-UNINA)**. Progetto sviluppato con il supporto di ChatGPT. La dicitura non implica un patrocinio ufficiale da parte delle organizzazioni citate.

**Licenza:** il codice originale di Registro Ore è distribuito con **licenza MIT**. Il testo completo è disponibile nel file [LICENSE](LICENSE). La licenza permette utilizzo, modifica e redistribuzione, mantenendo la nota di copyright e il testo della licenza. Le librerie di terze parti conservano le rispettive licenze.

> Software distribuito senza garanzie di conformità amministrativa, fiscale o legale. Verificare le esportazioni prima di inviarle agli uffici competenti.
