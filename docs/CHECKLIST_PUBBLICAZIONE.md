# Checklist prima di rendere pubblico Registro Ore

Questa checklist è una verifica di qualità, non una certificazione di sicurezza.

- [ ] Confermare che la pubblicazione dei sorgenti è autorizzata e scegliere una **licenza software** appropriata (MIT, Apache-2.0, GPL, ...).
- [ ] Verificare che nel repository non siano presenti **database personali, copie di backup, timesheet, documenti di progetto, credenziali o log**.
- [ ] Testare la versione **3.10.1 su un Mac**, specialmente installazione, aggiornamento, configurazione del percorso database e ritorno ai dati già presenti.
- [ ] Testare l'**eseguibile Windows 11 x64** da una macchina reale, inclusi primo avvio e librerie Java.
- [ ] Testare il pacchetto **Linux Mint .deb** e lo ZIP portabile da un ambiente reale.
- [ ] Verificare **anteprima documenti, upload/download allegati, esportazione CSV/XLSX e ripristino da backup**.
- [ ] Valutare sicurezza e licenze delle dipendenze in `pom.xml`, soprattutto la versione storica di **PDFBox 1.8.16**.
- [ ] Sostituire nelle **prossime build del pacchetto Linux Mint** i metadati dimostrativi del file `.deb` (Maintainer `noreply@example.invalid` e Homepage `openjdk.org`) con quelli effettivi del progetto, una volta deciso l'URL GitHub.
- [ ] Valutare la firma digitale di eseguibili e installer. Quelli attuali **non sono firmati né notarizzati**.
- [ ] Creare una **GitHub Release `v3.10.1`** e allegare i cinque installer; per il primo rilascio pubblico valutare l'etichetta **Pre-release** finché manca il collaudo completo sui tre sistemi.
- [ ] Conservare nel tempo solo gli installer correnti nella cartella `installers/`, usando le Releases per archiviare le versioni precedenti.
