# Sicurezza, privacy e segnalazione di problemi

Registro Ore archivia localmente dati che possono contenere informazioni personali, registrazioni lavorative, costi e documenti allegati.

## Non condividere dati personali pubblicamente

Non caricare nel repository, nelle GitHub Issues o nelle Pull Request:

- `registro_ore_2026.sqlite` personali (il nome del file non cambia necessariamente con l'anno);
- backup SQLite, esportazioni CSV/XLSX dei timesheet, contratti e documenti di progetto;
- log contenenti percorsi utente, informazioni su colleghi o descrizioni riservate;
- credenziali, password, token e file di configurazione locali.

Il **solo file `database_vuoto.sqlite`** può essere presente tra i sorgenti dopo averne verificato il contenuto nullo.

## Rischi e limitazioni

- L'app usa un database locale: chi ha accesso al file può potenzialmente leggerne il contenuto. Applicare le protezioni del sistema operativo e backup protetti.
- I backup automatici locali **non sostituiscono** una copia esterna protetta.
- I file in `installers/` sono binari non firmati; verificare checksum e provenienza.
- Prima di aggiornare dipendenze o distribuire a larga scala, eseguire test su macOS, Windows e Mint e valutare una verifica di sicurezza delle librerie Java.

Per segnalazioni sensibili, non allegare file riservati in una issue pubblica. Contattare il manutentore con un canale privato concordato.
