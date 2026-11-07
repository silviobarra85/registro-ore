# Changelog

## v3.10.3 — 2026-11-07

### Nuove funzionalità
- Archivio esplicito e reversibile delle attività inattive, separato dalla chiusura dei progetti.
- Le attività archiviate non sono proposte per nuove registrazioni e non possono ricevere nuove ore; i dati storici restano consultabili.
- Gestione completa dell'anagrafica del progetto: nome attività, acronimo, denominazione completa, finanziamento.
- Barra laterale e titolo alto mostrano `[Finanziamento] Acronimo`; il titolo interno mostra acronimo e nome completo.
- Eliminazione sicura: vietata quando esistono registrazioni, collaboratori, documenti o blocchi.

### Compatibilità
- Migrazione SQLite non distruttiva, con backup prima dell'aggiunta delle colonne.
- Nessuna modifica alle ore esistenti; Java 17 e database precedenti supportati.

## v3.10.2 — 2026-11-07

- Uniformati i crediti pubblici a Silvio Barra (DIETI-UNINA) e chiarita la documentazione utente.
- Pubblicato il codice originale con licenza MIT.

- **Registro mensile:** corretto il ritorno automatico al primo giorno del mese dopo aver modificato un'attività, variato le ore o eliminato una registrazione.
- La vista conserva il giorno visibile e la posizione verticale originale, compensando le variazioni di altezza delle righe.
- Nessuna migrazione del database richiesta; formule, ore, allegati, progetti e backup invariati.
- Aggiunto test automatico della logica di ripristino dello scorrimento (`RegistroViewportTest`).
- Aggiornati gli installer per macOS, Windows 10/11 e Linux Mint, la documentazione e i checksum.

## v3.10.1 — 2026-11-06

- Correzione delle icone della barra laterale: sostituite con grafica vettoriale Java2D, indipendente dal font del sistema.
- Conservazione delle funzioni e dei dati della serie 3.10.

## v3.10.0 — 2026-11-06

- Controllo del database all'avvio, con verifica del percorso, integrità e registrazioni.
- Storico modifiche (audit log) dalla prima attivazione.
- Barra laterale a sezioni espandibili e modalità compatta.

## Serie v3.9

- Backup giornalieri e ripristino, regola Attività Ordinaria, percorso database e ultimo salvataggio visibili.
- Esportazione delle ore di progetto, panoramica collaboratori, intestazione profilo e footer di versione.

## Serie v3.8 e precedenti

- Categoria storica per registrazione, gestione multianno, progetti, documenti, ruoli, blocchi mensili e CSV/XLSX.

Le voci precedenti sono riassuntive: per il comportamento dettagliato consultare il manuale utente.
