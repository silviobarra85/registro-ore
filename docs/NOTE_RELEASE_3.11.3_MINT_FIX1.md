# Registro Ore 3.11.3 — Linux Mint FIX1

Data: 8 novembre 2026. Revisione del pacchetto Debian: **3.11.3-2**.
La versione Java mostrata dall'app resta **3.11.3**. La correzione riguarda
soltanto Linux Mint; gli installer Mac e Windows non cambiano.

## Problema e correzione

Il bootstrap controllava `org/slf4j/nop/NOPLogger.class` dentro
`slf4j-nop-2.0.17.jar`. Quella classe non appartiene al provider: il controllo
corretto usa `org/slf4j/nop/NOPServiceProvider.class`. Il vecchio controllo
rifiutava anche il JAR corretto dopo il download, impedendo il primo avvio.
La correzione mantiene la verifica di dimensione, struttura ZIP e CRC.

I launcher Linux erano stati mantenuti dalla 3.3.2 ai pacchetti successivi,
quindi il problema riguarda anche la distribuzione Mint 3.11.3 precedente.
I nuovi pacchetti contengono il programma 3.11.3 completo e il launcher corretto.

## Aggiornamento consigliato, anche da 3.3.2

1. Chiudere Registro Ore.
2. Estrarre `RegistroOre_3_11_3_LinuxMint_FIX1_AGGIORNAMENTO.zip`.
3. Aprire nel Terminale la cartella estratta e lanciare:

```bash
bash AGGIORNA_REGISTRO_ORE.sh
```

Eseguire come utente normale: lo script richiede `sudo` soltanto per `apt`.
Crea prima un backup verificato del database esistente nella sottocartella
`backup_aggiornamenti`, installa il pacchetto 3.11.3-2 e prepara le librerie.
Poi aprire Registro Ore dal menu di Mint. Non disinstallare la versione precedente.

Il file `.deb` singolo si può installare anche con doppio clic o con:

```bash
sudo apt install ./RegistroOre_3_11_3_LinuxMint_FIX1_amd64.deb
registro-ore --prepara
registro-ore
```

Lo ZIP di aggiornamento è preferibile per chi ha dati, perché aggiunge il backup
prima dell'installazione. Non copiare mai `database_vuoto.sqlite` sui dati personali.
Il bootstrap conserva il database esistente; all'apertura le eventuali migrazioni
dalle versioni precedenti sono quelle già previste dalla 3.11.3.

## Versione portabile

Estrarre `RegistroOre_3_11_3_LinuxMint_FIX1_Portabile.zip` in una nuova cartella.
Da quella cartella, con Java 17+ e Python 3 installati:

```bash
bash AVVIA_REGISTRO_ORE.sh --prepara
bash AVVIA_REGISTRO_ORE.sh
```

La versione portabile usa il database dello stesso utente come il `.deb`.
Non avviare contemporaneamente più copie dell'applicazione.

## Verifica della correzione

```bash
dpkg-query -W -f='${Version}\n' registro-ore
registro-ore --prepara
registro-ore --diagnostica
```

Risultati attesi: pacchetto `3.11.3-2`, messaggio `Componenti pronti.` e tre
librerie con esito `OK`. Per la versione portabile usare il proprio launcher
`bash AVVIA_REGISTRO_ORE.sh --diagnostica`.

Serve Internet per scaricare le dipendenze mancanti. Se la rete non è disponibile,
il pacchetto può essere installato mentre la preparazione rimane incompleta:
ripetere `registro-ore --prepara` quando si è connessi. Il log è in
`~/.local/share/RegistroOre/logs/primo_avvio.log` (oppure sotto `XDG_DATA_HOME`).

## Sorgenti e controlli di questa revisione

- `packaging/linux/bootstrap.py`, `registro-ore.sh` e `build_mint.py` sono ora
  disponibili come sorgenti autonomi nel repository.
- Eseguire `python3 -m unittest discover -s tests -p test_mint_bootstrap.py -v`.
- I sette test verificano il percorso corretto della classe, scaricamento simulato,
  cache offline, rifiuto di JAR sbagliati, conservazione byte per byte di un database
  con ore/note/allegato, protezione dei database vuoti non validi, comando senza GUI
  e backup prima dell'installazione (comandi amministrativi simulati).
- I JAR dei test sono fixture sintetiche; non sostituiscono il collaudo di Java/JDBC.
- Verificati contenuti e versione del `.deb`, permessi dei launcher, confronto dei
  JAR e del database vergine con la 3.11.3 e checksum degli archivi distribuiti.
- Non effettuato collaudo grafico su un PC Mint reale. Il download HTTPS reale da
  Maven Central non è verificabile in questo ambiente, che lo rifiuta con HTTP 403;
  la logica di download e validazione è verificata con risposte simulate.

Per ricostruire i pacchetti usare la ricetta documentata in
`packaging/linux/build_mint.py`. Un aggiornamento dell'app Java può fornire
un nuovo JAR tramite `--jar`; questa revisione riutilizza quello della 3.11.3.
