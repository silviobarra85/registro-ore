# Registro Ore · v3.10.2

<p align="center"><img src="assets/registro-ore-icon.png" alt="Icona Registro Ore" width="128"></p>

**Applicazione desktop per registrare, pianificare e rendicontare le ore di didattica, attività istituzionali e progetti di ricerca.**

Disponibile per **macOS, Windows 10/11 (64 bit)** e **Linux Mint (64 bit)**. È realizzata in Java Swing e usa un **database SQLite locale**, senza server. Questa distribuzione è la versione **3.10.2**.

**Dati personali:** i pacchetti distribuiti includono un database iniziale privo di registrazioni, progetti, colleghi e allegati. Le registrazioni di ciascun utente rimangono nel database locale e non vengono trasmesse al repository.

## Scarica e installa

| Sistema | Prima installazione | Aggiornamento / alternativa |
|---|---|---|
| **macOS** | [Installazione vergine (.zip)](installers/macos/RegistroOre_3_10_2_MAC_VERGINE.zip) | [Aggiornamento con doppio clic (.zip)](installers/macos/RegistroOre_3_10_2_MAC_AGGIORNAMENTO.zip) |
| **Windows 10/11 x64** | [Applicazione (.zip)](installers/windows/RegistroOre_3_10_2_Windows_x64.zip) | Sostituire l'eseguibile solo dopo un backup |
| **Linux Mint x64** | [Pacchetto (.deb)](installers/linux/RegistroOre_3_10_2_LinuxMint_amd64.deb) | [Versione portabile (.zip)](installers/linux/RegistroOre_3_10_2_LinuxMint_Portabile.zip) |

Le versioni distribuite sono disponibili anche nella **[sezione Releases](../../releases)**.

**Requisiti:** JRE/JDK **Java 17+** per Mac e Linux. Su Windows il launcher usa una Java Runtime compatibile, se presente, o può predisporla alla prima esecuzione (serve Internet). Gli eseguibili non sono firmati digitalmente: macOS e Windows possono richiedere un'autorizzazione di sicurezza.

**Novità 3.10.2:** nel Registro, dopo una modifica o cancellazione, resti sullo stesso giorno senza perdere la posizione nello scorrimento.

## Funzioni principali

- Calendario e registro **multiannuale**, con più attività nello stesso giorno e inserimenti ripetuti su più giornate.
- Monte ore di **1.500 ore per anno**, avvisi per giornate oltre **8 ore** e sospensioni didattiche personalizzabili.
- **Categorie modificabili**, una per attività, con regole operative che distinguono ore didattiche, progetti e altre attività.
- Schede **progetto** con intervallo di validità, collaboratori, documenti, blocchi mensili, chiusura ed esportazioni.
- Storico dei **ruoli** di docenti e collaboratori, con tariffe configurate: Ordinario 81 €/h, Associato 53 €/h, Ricercatore 34 €/h.
- Riepiloghi mensili, annuali e per progetto, esportabili in **CSV ed Excel**.
- **Backup giornalieri** SQLite, ripristino, storico modifiche e controllo del database all'avvio.
- Barra laterale compatta/espandibile, con menu a sezioni e icone Java2D.

Queste funzioni sono strumenti di gestione: **controllare sempre la conformità dei timesheet ai regolamenti dell'ente e del progetto**.

## Manuale e documentazione

- **[MANUALE UTENTE (Italiano)](docs/MANUALE_UTENTE.md)** — installazione, uso quotidiano, progetti, allegati, esportazioni e backup.
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

**© 2026 Silvio Barra (DIETI-UNINA) & ChatGPT · Registro Ore v3.10.2**

Autore: **Silvio Barra (DIETI-UNINA)**. Progetto sviluppato con il supporto di ChatGPT. La dicitura non implica un patrocinio ufficiale da parte delle organizzazioni citate.

**Licenza:** il codice originale di Registro Ore è distribuito con **licenza MIT**. Il testo completo è disponibile nel file [LICENSE](LICENSE). La licenza permette utilizzo, modifica e redistribuzione, mantenendo la nota di copyright e il testo della licenza. Le librerie di terze parti conservano le rispettive licenze.

> Software distribuito senza garanzie di conformità amministrativa, fiscale o legale. Verificare le esportazioni prima di inviarle agli uffici competenti.
