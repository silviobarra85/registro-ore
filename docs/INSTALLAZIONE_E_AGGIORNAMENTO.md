# Installazione e aggiornamento — Registro Ore 3.11.0

## Scelta del pacchetto

**Prima installazione:**
- macOS: `installers/macos/RegistroOre_3_11_0_MAC_VERGINE.zip`
- Windows 10/11 x64: `installers/windows/RegistroOre_3_11_0_Windows_x64.zip`
- Linux Mint x64: `installers/linux/RegistroOre_3_11_0_LinuxMint_amd64.deb`

**Installazione esistente:**
- macOS: `installers/macos/RegistroOre_3_11_0_MAC_AGGIORNAMENTO.zip` (doppio clic sul file `.command` dopo l'estrazione).
- Windows: conservare una copia del database prima di sostituire l'eseguibile.
- Linux Mint: installare il nuovo `.deb` tramite il gestore pacchetti; il database personale rimane sotto la home.

## Come verificare il download

Il file `installers/SHA256SUMS.txt` contiene l'impronta SHA-256 di ogni installer. Su Mac/Linux, dalla radice del repository:

```bash
shasum -a 256 installers/macos/RegistroOre_3_11_0_MAC_VERGINE.zip
```

Confrontare l'hash stampato con la voce corrispondente in `installers/SHA256SUMS.txt`. Su Windows usare PowerShell:

```powershell
Get-FileHash .\installers\windows\RegistroOre_3_11_0_Windows_x64.zip -Algorithm SHA256
```

## Prima di aggiornare un database esistente

1. Chiudere Registro Ore.
2. Leggere il percorso del database indicato nel footer oppure in **Sistema → Stato database**.
3. Fare una copia manuale del file SQLite in un luogo protetto.
4. Aggiornare **solo i file dell'applicazione**, non il database.
5. Riaprire e verificare che il percorso, il numero di registrazioni e i progetti attesi coincidano.

**Mac:** in caso di precedente migrazione tra `~/Documents/.../data` e `~/Library/Application Support/RegistroOre/data`, controllare `~/Library/Application Support/RegistroOre/config/db_path.txt`. Un collegamento personalizzato può far apparire un database differente dall'ultimo aggiornato: non sovrascrivere nessuno dei due senza un confronto.

## Java e firma digitale

Java 17+ è richiesto su macOS e Linux; su Windows il launcher può acquisire un runtime in modo guidato alla prima apertura. I pacchetti non sono firmati/notarizzati. Per una distribuzione universitaria estesa è preferibile predisporre firma e verifica su sistemi reali prima di promuoverla a installazione ufficiale.

## Database iniziale

`database_vuoto.sqlite` incluso nei pacchetti è inizializzato ma **senza registrazioni personali**. Per una nuova installazione si usa solo se il database utente non esiste. Non va usato per sostituire un file con ore già salvate.

## Aggiornamento da 3.10.x a 3.11.0

La prima apertura esegue una migrazione delle nove categorie e aggiunge le tabelle delle missioni. Prima di aggiornare, chiudere l'applicazione e fare **un backup verificabile del database attivo**. Su macOS l'aggiornamento crea già una copia; dopo l'installazione il programma crea anche un backup specifico prima della conversione delle categorie. Non copiare mai il database vergine sopra quello personale.

Aprire **Gestione → Guida alle categorie** per controllare le nuove definizioni e verificare, nel registro, le attività che prima avevano categorie personalizzate. La migrazione conserva i nomi originali e segnala le associazioni incerte. La conservazione delle ore non implica che la classificazione scelta automaticamente sia sempre quella amministrativamente corretta.
