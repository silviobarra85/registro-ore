# Pubblicazione su GitHub — guida per il manutentore

La cartella consegnata è già strutturata come repository, con README, manuale, sorgenti e `installers/`.

## 1. Preparazione

1. Estrarre lo ZIP **`RegistroOre_GitHub_v3.10.1.zip`** in una cartella del Mac, ad esempio `~/Documents/RegistroOre-GitHub`.
2. **Non copiare** database, timesheet, backup o documenti personali all'interno.
3. Decidere la licenza: vedi `LICENZA_DA_SCEGLIERE.md`. Se il codice deve essere open source, aggiungere il file `LICENSE` prima della pubblicazione.
4. Leggere `README.md`, `docs/MANUALE_UTENTE.md`, `CHANGELOG.md` e la [checklist pre-pubblicazione](CHECKLIST_PUBBLICAZIONE.md).

## 2. Creare il repository su GitHub

Su <https://github.com/new> creare un repository chiamato, per esempio, **`registro-ore`**, impostato come **Public**. **Non** spuntare le opzioni per generare automaticamente README, LICENSE o `.gitignore`, perché sono già presenti (o da decidere) nella cartella locale.

## 3. Primo caricamento dal Terminale Mac

Aprire il Terminale **dentro la cartella principale** del progetto, quella contenente `README.md`. Sostituire `TUO_USERNAME` con il proprio account GitHub:

```bash
git init -b main
git add .
git status
git commit -m "Registro Ore 3.10.1 - prima pubblicazione"
git remote add origin https://github.com/TUO_USERNAME/registro-ore.git
git push -u origin main
```

Il comando `git status` è un controllo utile: non devono comparire database personali, backup o file di lavoro. Se si usa GitHub Desktop, aprire la cartella come repository locale e usare **Publish repository**, scegliendo **Public** solo dopo la verifica.

## 4. Creare una Release per il download

Aprire il repository online → **Releases** → **Draft a new release** (o **Create a new release**). Creare il tag **`v3.10.1`**, titolo **`Registro Ore 3.10.1`**, inserire le note presenti in `CHANGELOG.md` e allegare i cinque file in `installers/`.

La pagina ufficiale dei download sarà:

```text
https://github.com/TUO_USERNAME/registro-ore/releases/latest
```

Esempio di download diretto, una volta pubblicata la Release:

```text
https://github.com/TUO_USERNAME/registro-ore/releases/latest/download/RegistroOre_3_10_1_Windows_x64.zip
```

Puoi anche usare la CLI GitHub `gh` dopo aver effettuato il login:

```bash
gh release create v3.10.1   installers/macos/*.zip installers/windows/*.zip installers/linux/*.deb installers/linux/*.zip   --title "Registro Ore 3.10.1"   --notes-file docs/NOTE_RELEASE_3.10.1.md
```

## 5. Per gli aggiornamenti futuri

- Aggiornare il codice e la versione; aggiornare README/manuale/changelog.
- Sostituire i file in `installers/` con la versione corrente e rigenerare gli SHA-256.
- Conservare le vecchie versioni **nelle Releases**, non in cartelle sempre più grandi della cronologia Git.
- Creare un nuovo tag (`v3.10.2`, `v3.11.0`, ...), poi una nuova Release.
- Prima di ogni aggiornamento, copiare il database personale e verificarne l'integrità.

**Consiglio:** GitHub consente file binari nelle Releases, ma per la salute del repository a lungo termine è preferibile spostare progressivamente i pacchetti binari **solo nelle Releases**. `installers/` può allora contenere un README con i link alle versioni pubblicate. La prima pubblicazione qui include comunque tutti i file come richiesto.

## 6. Gestione della licenza e dei crediti

Il testo di copyright in app non è una licenza software. Prima della pubblicazione aperta scegliere esplicitamente i diritti da concedere. Per progetti universitari verificare eventuali policy dell'ateneo/diritti di terzi. Non usare loghi o denominazioni in modo da suggerire approvazioni ufficiali non dichiarate.
