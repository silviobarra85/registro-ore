# Installer — Registro Ore 3.11.0

- `macos/`: prima installazione e aggiornamento, con backup del database preesistente.
- `windows/`: applicazione `.exe` 64 bit avviabile con doppio clic.
- `linux/`: `.deb` per Mint e versione portabile ZIP.

Il database vergine contiene **zero registrazioni**, zero progetti e zero allegati. I database personali non vengono distribuiti.

Verifica SHA-256: dalla cartella `installers`, eseguire `sha256sum -c SHA256SUMS.txt` (su macOS usare `shasum -a 256` per controlli manuali).

Gli installer non sono firmati digitalmente. Prima di un rilascio pubblico esteso, verificare l'avvio su ogni sistema operativo.
