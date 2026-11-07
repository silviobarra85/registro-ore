# FAQ — Registro Ore 3.10.2

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
