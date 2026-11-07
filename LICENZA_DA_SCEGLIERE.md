# Licenza del software — decisione necessaria prima della pubblicazione

Il repository è predisposto per essere pubblico, **ma non include deliberatamente un file `LICENSE`**: non è corretto scegliere in automatico la licenza al posto del titolare.

Se vuoi che altri possano leggere, utilizzare, modificare e redistribuire il codice come software open source, le opzioni comuni comprendono:

- **MIT** — permissiva, breve, consente anche modifiche e riutilizzi commerciali mantenendo le condizioni di attribuzione e l'avviso di copyright.
- **Apache License 2.0** — permissiva, con condizioni più dettagliate, inclusa una concessione esplicita di brevetti da parte dei contributori.
- **GPLv3** — copyleft: chi redistribuisce versioni derivate deve rispettare gli obblighi della stessa licenza.

La scelta dipende dai diritti effettivamente detenuti sul codice, dalle eventuali policy universitarie e dalle licenze delle dipendenze. Consulta <https://choosealicense.com/> e <https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository>.

Una volta decisa la licenza:

1. Aggiungi il suo testo integrale nella radice del repository come `LICENSE`.
2. Aggiorna il paragrafo **Licenza** in `README.md`.
3. Verifica i file e gli obblighi di attribuzione delle librerie incluse negli installer.

**Nota:** un repository pubblico **senza licenza** non equivale a software con riuso illimitato. Non usare la dicitura “open source” prima di avere chiarito i permessi.
