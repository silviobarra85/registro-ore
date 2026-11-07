# Manuale utente — Registro Ore 3.10.3

**Destinatari:** docenti, ricercatori e collaboratori che devono registrare attività e rendicontare ore su progetti, didattica e altre attività.

> Il manuale descrive la versione Java 3.10.3. Le etichette potrebbero variare leggermente secondo il sistema operativo. Conservare sempre una copia di sicurezza prima di importazioni e aggiornamenti.

## Indice

1. [Installazione](#1-installazione)
2. [Primo avvio e Profilo](#2-primo-avvio-e-profilo)
3. [Navigazione](#3-navigazione)
4. [Annualità, calendario e sospensioni](#4-annualità-calendario-e-sospensioni)
5. [Inserire e modificare ore](#5-inserire-e-modificare-ore)
6. [Attività e categorie](#6-attività-e-categorie)
7. [Gestione dei progetti](#7-gestione-dei-progetti)
8. [Colleghi, ruolo e costo](#8-colleghi-ruolo-e-costo)
9. [Allegati ai progetti](#9-allegati-ai-progetti)
10. [Riepiloghi e ricerche](#10-riepiloghi-e-ricerche)
11. [Esportazioni CSV ed Excel](#11-esportazioni-csv-ed-excel)
12. [Backup, ripristino e storico modifiche](#12-backup-ripristino-e-storico-modifiche)
13. [Database e sicurezza](#13-database-e-sicurezza)
14. [Risoluzione dei problemi](#14-risoluzione-dei-problemi)

## 1. Installazione

### macOS

**Prima installazione:** scaricare `installers/macos/RegistroOre_3_10_3_MAC_VERGINE.zip`, decomprimere e fare doppio clic su `INSTALLA_REGISTRO_ORE.command`. La nuova applicazione verrà installata nella cartella applicativa prevista dallo script. Dopo l'installazione avviarla dal Finder.

**Aggiornamento:** chiudere il programma, scaricare `RegistroOre_3_10_3_MAC_AGGIORNAMENTO.zip` e avviare con doppio clic `AGGIORNA_REGISTRO_ORE.command`. Questo pacchetto **non va usato per una prima installazione** e non deve sostituire i file SQLite personali.

È richiesto Java 17+. Per un pacchetto non firmato macOS potrebbe richiedere un'apertura esplicita tramite le impostazioni di sicurezza: controllare la provenienza prima di autorizzare.

### Windows

Scaricare `installers/windows/RegistroOre_3_10_3_Windows_x64.zip`, decomprimere in una cartella locale e fare doppio clic su **RegistroOre.exe**. Al primo avvio il launcher può richiedere Internet per il runtime Java. In presenza di SmartScreen verificare provenienza e checksum prima di procedere.

### Linux Mint

Scaricare `installers/linux/RegistroOre_3_10_3_LinuxMint_amd64.deb`, aprire con il gestore pacchetti e installare. In alternativa estrarre lo ZIP portabile ed eseguire `AVVIA_REGISTRO_ORE.sh`. Java 17+ è richiesto.

## 2. Primo avvio e Profilo

- Aprire **Gestione → Profilo**.
- Inserire il nominativo che deve comparire nell'intestazione e nei resoconti.
- Impostare il ruolo, scegliendo **Ordinario, Associato o Ricercatore**.
- Registrare **le decorrenze** dello storico ruoli se la qualifica è cambiata durante un anno.
- Verificare nel footer il percorso del database e la data dell'ultimo salvataggio.

Tariffe attualmente configurate nel programma:

| Ruolo | Tariffa oraria |
|---|---:|
| Ordinario | 81 €/h |
| Associato | 53 €/h |
| Ricercatore | 34 €/h |

Le tariffe sono parametri del programma e **non costituiscono una certificazione dell'ente**. La valorizzazione dei costi dei progetti usa il ruolo valido alla data di registrazione.

## 3. Navigazione

La barra laterale contiene menu espandibili: **Calendario e Registro**, **Rendicontazione**, **Gestione**, **Schede Progetto** e **Sistema**.

Il pulsante in alto comprime la barra in una colonna di icone. Passare il puntatore sulle icone o selezionarle per visualizzare le pagine del gruppo.

## 4. Annualità, calendario e sospensioni

Per aggiungere un anno usare **Gestione → Annualità**: indicare l'anno, una nota e configurare le giornate di sospensione da **Gestione → Sospensioni**.

- Il limite configurato è di **1.500 ore complessive per anno**.
- Le giornate con **più di 8 ore** sono evidenziate nel calendario: è un avviso, non un blocco automatico.
- Nei giorni sospesi le registrazioni didattiche seguono la regola operativa della rispettiva categoria; le ore sui progetti possono essere ammesse secondo i blocchi.
- Un **lucchetto** sul mese segnala una mensilità chiusa, per esempio perché il timesheet è già stato inviato.

Controllare sempre il totale annuale e la distribuzione giornaliera prima di inviare la rendicontazione.

## 5. Inserire e modificare ore

1. Aprire **Calendario e Registro → Registro** e selezionare mese e anno.
2. Usare **Nuova registrazione** e indicare data, attività, ore e nota (se prevista).
3. Iniziare a digitare il nome dell'attività: i suggerimenti si filtrano direttamente nella finestra.
4. Confermare la registrazione e controllare il totale giornaliero.

### Registrazioni su più giornate

Usare **+ Più giorni** per selezionare una stessa attività, definire le ore per data e scegliere più giornate dal calendario. L'operazione è soggetta a verifica preventiva di mesi bloccati, sospensioni e limite annuo. Se una delle date non è consentita, il salvataggio in blocco viene respinto.

### Posizione conservata dopo il salvataggio (novità 3.10.3)

Nel **Registro mensile**, quando modifichi il nome di un'attività, le ore o una nota, oppure elimini una registrazione, il calendario **rimane sullo stesso giorno e nella stessa posizione dello scorrimento**. Non torna più automaticamente al primo giorno del mese. Anche se la giornata cambia altezza per l'aggiunta o la rimozione di una voce, la posizione visiva viene compensata.

### Modifiche, eliminazioni e blocchi

Le righe selezionate vengono evidenziate. Se un elemento è bloccato, il programma indica il motivo (mese chiuso, blocco progetto, progetto chiuso o data fuori validità). Quando un mese è già rendicontato, evitarne lo sblocco senza una rettifica autorizzata.

## 6. Attività e categorie

Ogni attività ha **una sola categoria corrente**, modificabile da **Gestione → Attività**. Le categorie sono gestibili da **Gestione → Categorie**; non esiste una gerarchia obbligatoria di gruppi principali e sottogruppi.

La categoria possiede una **regola operativa** tecnica: fra gli esempi, didattica frontale, integrativa, progetti, altre attività e attività ordinaria. Questa regola governa i controlli e i riepiloghi.

Se cambi categoria a un'attività con ore già registrate, puoi scegliere fra:

- **Solo nuove registrazioni:** le vecchie ore mantengono la categoria storica.
- **Anche registrazioni precedenti:** la riclassificazione è retroattiva, ma può essere negata per periodi/progetti bloccati.

La cancellazione di una categoria utilizzata richiede il trasferimento delle attività in un'altra categoria, quando consentito.

### Archivio delle attività (3.10.3)

In **Gestione → Attività** selezionare un'attività e premere **Archivia**. L'attività scompare dalle proposte per le nuove registrazioni e, se è un progetto, dall'elenco dei progetti attivi nella barra laterale. Tutti i timesheet, i collaboratori e gli allegati restano nel database e nei riepiloghi storici.

Da **Gestione → Archivio attività** si possono consultare e **ripristinare** le attività archiviate. Se si apre un progetto archiviato, i dati storici restano consultabili. Per rendicontare di nuovo bisogna ripristinarlo esplicitamente e rispettarne date e blocchi.

**Archiviare non significa chiudere il progetto.** Lo stato *Progetto chiuso* blocca la rendicontazione, mentre l’archivio riguarda la visibilità dell'attività; le due operazioni sono distinte.

**Eliminazione definitiva:** possibile soltanto se l'attività non ha registrazioni, collaboratori, documenti o blocchi collegati. Per progetti rendicontati utilizzare sempre l'archivio, non l'eliminazione.

## 7. Gestione dei progetti

Dalla barra laterale aprire la **scheda del singolo progetto**. Le sezioni comprendono:

- **Informazioni:** nome attività registrato nei timesheet, **finanziamento** (es. FRA 2022), **acronimo** (es. GATT), **nome completo** (es. GAmification in Testing Teaching), tipologia, finanziatore, codice progetto/CUP, ruolo nel progetto, date di inizio/fine e note.
- Nella barra laterale e nell’intestazione principale si legge `[FRA 2022] GATT`. Nel riquadro della scheda appare invece `GATT — GAmification in Testing Teaching` (esempio).
- Il nome attività può essere corretto senza cambiare ore o date; se compare in timesheet già bloccati, il programma mostra un’avvertenza perché l’etichetta visibile nello storico cambierà.
- **Colleghi e ore:** partecipanti, qualifiche, ruolo nel progetto e registrazioni giornaliere.
- **Calendario annuale:** totale per giorno e dettaglio per persona.
- **Documenti allegati:** archivio dei file di progetto.
- **Blocchi mensili:** mesi già rendicontati per il singolo progetto.

Un progetto può proseguire per più annualità. Registrare il periodo di validità e chiuderlo esplicitamente quando terminato: **un progetto chiuso non accetta nuove ore e richiede riapertura per rettifiche**. I blocchi per progetto e mese agiscono sulle ore del progetto senza bloccare gli altri progetti nello stesso mese.

## 8. Colleghi, ruolo e costo

Nella sezione **Profilo → Colleghi e progetti** è disponibile una vista trasversale di colleghi, progetti e ore. Le qualifiche possono avere decorrenze differenti: se un collega passa da Ricercatore ad Associato, le ore vengono valorizzate con la tariffa valida nel giorno del lavoro.

Le ore dei colleghi sono distinte dalle ore personali e **non consumano le 1.500 ore del monte ore del titolare**. Verificare il ruolo e la sua decorrenza prima di produrre un prospetto economico.

## 9. Allegati ai progetti

Nella scheda progetto aprire **Documenti allegati**. È possibile:

- Caricare uno o **più file insieme**, indicando una nota e un periodo comuni.
- Modificare la nota di un file già caricato.
- Ordinare i documenti per nome, periodo e data di caricamento.
- Scaricare il file originale o aprire l'anteprima interna.

Il visualizzatore offre anteprime per PDF, immagini, testo e contenuto testuale dei documenti Office moderni. **L'impaginazione dei file Office potrebbe differire dall'originale**; se occorre l'aspetto esatto, usare l'applicazione associata sul computer.

I documenti archiviati nel database sono inclusi nelle copie SQLite.

## 10. Riepiloghi e ricerche

Usare **Riepilogo** per i totali per categoria e mese, **Rendicontazione** per ore/costi dei progetti e didattica, e **Cerca** per individuare le registrazioni di un'attività in un intervallo di date. Il periodo iniziale della ricerca è l'anno selezionato.

Per i progetti, i report possono distinguere ore proprie, ore dei colleghi e costi basati sul ruolo alla data.

## 11. Esportazioni CSV ed Excel

- **Registro** → **Esporta mese/anno**: specificare periodo e scegliere CSV o XLSX.
- **Rendicontazione / Progetti** → esportazione del progetto: scegliere un progetto, le date iniziale/finale e il formato.

CSV: righe di registrazioni, con note e dati utili per analisi esterne. Excel: registrazioni e, quando previsto, un riepilogo di ore o costi. Le esportazioni possono essere prodotte anche da mesi bloccati in sola lettura.

**Controllare le cifre e le qualifiche prima di inviare un timesheet ufficiale.**

## 12. Backup, ripristino e storico modifiche

Aprire **Sistema → Backup e ripristino**. Il programma conserva fino a **30 copie giornaliere** e permette backup manuale e ripristino dopo conferma. Prima del ripristino viene creata un'ulteriore copia dello stato corrente.

Aprire **Sistema → Storico modifiche** per vedere inserimenti, modifiche ed eliminazioni successivi all'attivazione dell'audit. Le operazioni precedenti all'introduzione dell'audit **non sono ricostruibili automaticamente**. La cronologia può essere consultata e, per le funzionalità disponibili, esportata in CSV.

Un backup di sicurezza esterno al computer (supporto protetto o cartella aziendale autorizzata) rimane consigliato: le copie locali non proteggono dal guasto completo del disco.

## 13. Database e sicurezza

Database locale SQLite, **non un server**. Posizioni usuali:

| Piattaforma | Directory |
|---|---|
| macOS | `~/Library/Application Support/RegistroOre/data/` |
| Windows | `%LOCALAPPDATA%\RegistroOre\data\` |
| Linux Mint | `~/.local/share/RegistroOre/data/` |

Il nome del file è normalmente `registro_ore_2026.sqlite`, anche quando contiene più annualità.

**IMPORTANTE su Mac:** l'app può utilizzare un percorso personalizzato letto da `~/Library/Application Support/RegistroOre/config/db_path.txt`. Il percorso realmente aperto è visibile in basso a sinistra. Usare **Sistema → Stato database** per controllare, prima di ogni ripristino, che non si tratti di una copia più vecchia.

Non pubblicare mai su GitHub database personali, allegati, file di log, timesheet, backup o informazioni riservate su colleghi/progetti.

## 14. Risoluzione dei problemi

- **L'app non si avvia e appare `UnsupportedClassVersionError`:** verificare Java **17+**; la versione 3.10.3 è compilata per Java 17.
- **Non compaiono ore o un progetto:** controllare il **percorso del database effettivamente usato**, non ripristinare subito una copia.
- **Un giorno o un mese non accetta ore:** controllare stato di sospensione, blocchi mensili, chiusura progetto e limite di 1.500 ore.
- **Non riesci ad aprire un allegato:** provarne lo scaricamento e l'apertura esterna; l'anteprima Office è in parte testuale.
- **Mac o Windows mostra avvisi di sicurezza:** controllare provenienza e checksum del file. Gli installer non sono firmati digitalmente.

Per i report di bug, allegare soltanto messaggi d'errore **anonimizzati** e versione del sistema, non i database reali. Vedi [FAQ](FAQ.md).
