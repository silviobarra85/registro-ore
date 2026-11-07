# Installer — Registro Ore 3.10.2

Questa cartella contiene **i cinque pacchetti binari della versione 3.10.2**:

| Sistema | File | Uso |
|---|---|---|
| macOS | `macos/RegistroOre_3_10_2_MAC_VERGINE.zip` | Nuova installazione, senza dati personali |
| macOS | `macos/RegistroOre_3_10_2_MAC_AGGIORNAMENTO.zip` | Aggiornamento di un'installazione esistente |
| Windows | `windows/RegistroOre_3_10_2_Windows_x64.zip` | Windows 10/11 x64, avvio `.exe` |
| Linux Mint | `linux/RegistroOre_3_10_2_LinuxMint_amd64.deb` | Installazione `.deb` |
| Linux Mint | `linux/RegistroOre_3_10_2_LinuxMint_Portabile.zip` | Esecuzione portabile, Java 17+ |

**Verifica file:** confrontare il checksum SHA-256 con `SHA256SUMS.txt` prima di diffondere i pacchetti. Gli installer e i JAR sono **non firmati digitalmente**.

**Non inserire qui** copie personali di `registro_ore_2026.sqlite`, timesheet, log o allegati.

Per gli aggiornamenti futuri: pubblicare i pacchetti su **GitHub Releases** e mantenere la cartella `installers/` ordinata, con i soli file della versione corrente.

## Licenze

Il codice originale è disponibile con [licenza MIT](../LICENSE). Le librerie incluse negli installer mantengono le proprie condizioni di licenza: fare riferimento ai metadati dei componenti di terze parti.
