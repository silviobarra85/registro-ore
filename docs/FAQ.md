# FAQ — Registro Ore 3.11.2

**Dove si trovano le ore?** Nel database SQLite locale. Il percorso effettivo è visualizzato in basso a sinistra e in **Sistema → Stato database**.

**Posso aggiungere il 2027 e gli anni successivi?** Sì, da **Gestione → Annualità**, con sospensioni e note per anno.

**Se supero otto ore in un giorno?** Il programma segnala la giornata nel calendario. Il limite annuale di 1.500 ore è invece un vincolo del registro.

**Come si bloccano timesheet già inviati?** Con il blocco mensile dell'anno o il blocco per mese del singolo progetto. Il motivo è visibile nel registro.

**Se un collega cambia ruolo a metà anno?** Inserire le decorrenze nello storico dei ruoli. Il costo usa la qualifica valida alla data di ciascuna registrazione.

**Come si esporta un progetto?** Aprire la rendicontazione dei progetti, selezionare progetto, intervallo di date e formato CSV o Excel.

**I dati sono condivisi automaticamente con altri?** No, sono locali. Condividere un installer non condivide il database, che è separato. Per la gestione multiutente simultanea servirebbe un'architettura differente.

**Come ripristino una copia?** Da **Sistema → Backup e ripristino**, con doppia conferma. Prima verificare il percorso del database e creare un ulteriore backup su supporto separato.

**Non vedo un vecchio progetto: è perso?** Non necessariamente: controllare il percorso del database e confrontare le copie prima di ripristinare.

**Posso usare il codice per creare una versione mia?** Sì. Il codice originale è distribuito con [licenza MIT](../LICENSE): puoi usarlo, modificarlo e redistribuirlo, mantenendo la nota di copyright e il testo della licenza. Le dipendenze di terze parti hanno licenze proprie.

**Segnalazione bug:** usare le GitHub Issues senza inserire dati personali o documenti dei progetti.

### Posso nascondere un progetto concluso senza perdere documenti e rendicontazioni?

Sì: usare **Gestione → Attività → Archivia**. L'archiviazione è esplicita e reversibile; le ore storiche rimangono nei riepiloghi e gli allegati nel database. Per consultare o ripristinare l'attività usare **Gestione → Archivio attività**. La chiusura del progetto è un blocco contabile separato.

### Posso eliminare un progetto che contiene ore o allegati?

No. Per proteggere timesheet e documenti, l'eliminazione definitiva è consentita soltanto quando non esistono dati collegati. Utilizzare l'archiviazione negli altri casi.

### Perché non posso creare altre Categorie?

A partire dalla 3.11.1 le **nove Categorie** definiscono direttamente il comportamento gestionale e i filtri di rendicontazione. Si possono creare liberamente le singole **Attività** usando una delle nove Categorie, ma non introdurre una decima categoria personalizzata. La sezione **Gestione → Guida alle categorie** ne chiarisce usi e limiti.

### Durante una sospensione didattica posso registrare una commissione o un convegno?

La sospensione delle lezioni non vieta automaticamente le altre prestazioni. Per le **Commissioni** e alcune attività integrative l'app chiede una verifica e una motivazione; la ricerca, incluse conferenze scientifiche, è ordinariamente consentita, salvo il provvedimento specifico e le regole amministrative. Le lezioni frontali richiedono una deroga motivata. Un avviso dell'app non garantisce l'ammissibilità di una spesa o di ore finanziate.

### «Missioni» è una decima Categoria?

No. Si registra una missione con sede, periodo e scopo, poi si collegano a essa le ore lavorate tramite le rispettive categorie. Partecipare a una conferenza per aggiornarsi, presentare un paper scientifico e presentare un lavoro di progetto possono appartenere a categorie diverse. Le ore non vengono conteggiate due volte.

### Che cosa succede alle vecchie categorie dopo l'aggiornamento?

Alla prima apertura la 3.11.1 crea una copia di sicurezza e converte la classificazione nelle nove categorie. I nomi originari restano tracciati; i casi ambigui vengono evidenziati per revisione. Le ore, le note, i progetti e i blocchi contabili rimangono invariati.

### Quando aggiungo ore su più giorni a un progetto già presente, che cosa succede?
Le ore vengono sommate a quelle già registrate per la stessa attività/data se la missione, la categoria storica e la provenienza sono compatibili. Le note vengono conservate. Per missioni diverse, o righe provenienti da altre fonti, il programma crea una riga separata per non perdere informazioni rendicontabili.

### Come vedo le ore già registrate nel calendario «Più giorni»?
Scegli un'attività: sotto ogni data compare il totale di ore già registrate per quell'attività. Passa il mouse sul giorno per vedere tutte le registrazioni e il totale previsto. Senza attività selezionata viene mostrato il totale giornaliero.

### Posso leggere il Registro mentre scelgo più giorni?
Sì, il selettore è una finestra non modale. Puoi spostarla e scorrere il Registro sottostante. Se modifichi le ore nel frattempo, premi «Aggiorna calendario» per sincronizzare l'anteprima prima di salvare.

### Posso aggiungere o togliere mezz'ora senza aprire la finestra Modifica?

Sì. Nel Registro il pulsante `+` aggiunge 0,5 ore e il pulsante `−` sottrae 0,5 ore alla singola registrazione. I pulsanti sono disabilitati quando la registrazione non è modificabile. `−` non elimina una riga a zero; per eliminarla usare il comando dedicato. I dati di missione e le note non vengono modificati.

### Se nel calendario «Più giorni» le celle sono ancora piccole?

Allarga la finestra (nei limiti del monitor); se la finestra viene ristretta, i mesi diventano una sola colonna. Ogni cella visualizza il numero del giorno e le ore su due righe distinte; passando il mouse si apre il dettaglio delle ore giornaliere.
