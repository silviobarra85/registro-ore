#!/usr/bin/env python3
"""Ricostruisce i tre pacchetti Mint usando la base .deb e i launcher versionati.

Esempio dalla radice del repository:
python3 packaging/linux/build_mint.py --base-deb installers/linux/RegistroOre_3_11_3_LinuxMint_FIX1_amd64.deb --output /tmp/mint-build
Per una futura build Java è possibile aggiungere --jar target/registro-ore-3.11.3.jar.
Non legge dati personali e non accede alla rete.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

STEM = 'RegistroOre_3_11_3_LinuxMint_FIX1'
DEB_VERSION = '3.11.3-2'
HERE = Path(__file__).resolve().parent

GUIDE = '''REGISTRO ORE 3.11.3 — LINUX MINT FIX1

Corregge il controllo errato della libreria slf4j-nop-2.0.17.jar.
Il programma Java resta alla versione 3.11.3. Pacchetto Debian: 3.11.3-2.
Compatibile come aggiornamento con le precedenti installazioni Mint, anche 3.3.2.
L'aggiornamento dalla 3.3.2 comprende le funzioni e le migrazioni della 3.11.3.

METODO CONSIGLIATO: ZIP AGGIORNAMENTO
1. Chiudere Registro Ore.
2. Estrarre completamente lo ZIP AGGIORNAMENTO, ad esempio in Scaricati.
3. Aprire la cartella estratta nel Terminale (clic destro: Apri nel terminale).
4. Eseguire come utente normale, NON anteponendo sudo:

   bash AGGIORNA_REGISTRO_ORE.sh

Lo script crea prima un backup SQLite verificato, poi richiede la password
amministrativa per installare il .deb con apt. Infine prepara le librerie.
Serve Internet per le dipendenze mancanti. Non disinstallare la versione vecchia.
Al termine aprire Registro Ore dal menu di Mint. Verificare il proprio profilo,
le registrazioni e il percorso del database indicato nell'app.

ALTERNATIVA: SOLO FILE .deb
Doppio clic su RegistroOre_3_11_3_LinuxMint_FIX1_amd64.deb e Installa/Aggiorna.
Per un'installazione già usata è preferibile lo ZIP sopra: effettua anche
il backup prima dell'installazione. In alternativa, dal terminale nella cartella:

   sudo apt install ./RegistroOre_3_11_3_LinuxMint_FIX1_amd64.deb
   registro-ore --prepara
   registro-ore

VERSIONE PORTABILE
Estrarre lo ZIP Portabile in una nuova cartella. Aprire lì il Terminale:

   bash AVVIA_REGISTRO_ORE.sh --prepara
   bash AVVIA_REGISTRO_ORE.sh

Richiede Java 17+ e Python 3 già installati. Usa i dati dello stesso utente
come il .deb; non eseguire due copie contemporaneamente. Non sostituire il
database personale con database_vuoto.sqlite presente nella cartella portabile.

DATI
Percorso standard: ~/.local/share/RegistroOre/data/registro_ore_2026.sqlite
Con XDG_DATA_HOME personalizzato: $XDG_DATA_HOME/RegistroOre/data/...
Backup dell'aggiornamento: RegistroOre/backup_aggiornamenti/ accanto a data/.
Lo script non sostituisce né cancella il database esistente o gli allegati.
Le migrazioni della 3.11.3, se necessarie, avvengono all'apertura dell'app
e creano i relativi backup; i valori numerici delle ore vengono conservati.

CONTROLLO
   dpkg-query -W -f='${Version}\\n' registro-ore
Risultato atteso del pacchetto: 3.11.3-2 (nell'app compare 3.11.3).
   registro-ore --prepara
Deve terminare con: Componenti pronti.
   registro-ore --diagnostica
I tre componenti devono risultare OK.

Se una vecchia libreria locale viene segnalata non valida, il comando
mostra il suo percorso: conservarne una copia e spostare solo quel file
fuori dalla cartella lib, poi ripetere --prepara. Non cancellare data/.
I log sono in RegistroOre/logs/. Se la rete non è disponibile, il .deb può
risultare installato anche se la preparazione delle librerie non è completata:
riconnettersi e ripetere registro-ore --prepara senza reinstallare il pacchetto.

REQUISITI
Linux Mint/Ubuntu compatibile, amd64, Java 17+, Python 3 e ambiente grafico.
Mac e Windows non richiedono questo aggiornamento.
'''

UPDATER = r'''#!/bin/sh
set -eu
umask 077
HERE="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ "$(id -u)" -eq 0 ]; then
  echo "Esegui questo script come utente normale: bash AGGIORNA_REGISTRO_ORE.sh" >&2
  echo "Lo script richiederà sudo soltanto per installare il pacchetto." >&2
  exit 1
fi
command -v python3 >/dev/null 2>&1 || { echo "Serve Python 3." >&2; exit 1; }
command -v apt >/dev/null 2>&1 || { echo "Serve apt (Linux Mint/Ubuntu)." >&2; exit 1; }
if command -v pgrep >/dev/null 2>&1 && pgrep -u "$(id -u)" -f '(^|/)java([[:space:]]|$).*it[.]registroore[.]App' >/dev/null 2>&1; then
  echo "Chiudi Registro Ore prima di aggiornare, poi ripeti il comando." >&2
  exit 1
fi
ROOT="${XDG_DATA_HOME:-$HOME/.local/share}/RegistroOre"
DB="$ROOT/data/registro_ore_2026.sqlite"
if [ -e "$DB" ]; then
  python3 - "$DB" "$ROOT" <<'PYSQL'
import sqlite3, sys, pathlib, datetime
source=pathlib.Path(sys.argv[1]); root=pathlib.Path(sys.argv[2])
if not source.is_file() or source.stat().st_size == 0:
    raise SystemExit('Database esistente non valido: aggiornamento annullato senza sostituirlo.')
folder=root/'backup_aggiornamenti'; folder.mkdir(parents=True,exist_ok=True)
backup=folder/('prima_3_11_3_Mint_FIX1_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S_%f')+'.sqlite')
original=sqlite3.connect(source.resolve().as_uri()+'?mode=ro',uri=True)
copy=sqlite3.connect(str(backup))
try:
    if original.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
        raise RuntimeError('Il database originale non supera la verifica di integrità.')
    original.backup(copy)
    if copy.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
        raise RuntimeError('Backup non integro: aggiornamento annullato.')
    print('Backup verificato:',backup)
finally:
    copy.close(); original.close()
PYSQL
else
  echo "Nessun database precedente: verrà inizializzato al primo avvio."
fi
echo "Installazione Registro Ore 3.11.3 Mint FIX1 (pacchetto 3.11.3-2)..."
sudo apt install "$HERE/RegistroOre_3_11_3_LinuxMint_FIX1_amd64.deb"
echo "Pacchetto installato. Preparazione librerie con il tuo utente..."
if registro-ore --prepara; then
  echo "Aggiornamento completato. Apri Registro Ore dal menu di Mint."
else
  echo "Pacchetto installato; preparazione librerie non completata." >&2
  echo "Con Internet disponibile ripeti: registro-ore --prepara" >&2
  exit 1
fi
'''

def zip_tree(source, output):
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(source.rglob('*')):
            if p.is_file():
                z.write(p,source.name+'/'+p.relative_to(source).as_posix())

def build(base, output, jar=None):
    output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='registro-mint-') as temp:
        temp=Path(temp); tree=temp/'deb'
        subprocess.run(['dpkg-deb','-R',str(base),str(tree)],check=True)
        app=tree/'opt/registro-ore'
        for name in ['bootstrap.py','registro-ore.sh']:
            shutil.copy2(HERE/name,app/name)
            (app/name).chmod(0o755)
        if jar is not None:
            shutil.copy2(jar,app/'RegistroOre.jar')
        # Rimuove vecchi testi di release dalla cartella di programma.
        for p in app.glob('NOVITA*'):
            p.unlink()
        (app/'LEGGIMI_LINUX_MINT.md').write_text(GUIDE,encoding='utf-8')
        (app/'NOVITA_3_11_3_MINT_FIX1.txt').write_text(
            'Registro Ore 3.11.3 — Mint FIX1\nControllo SLF4J corretto: NOPServiceProvider.class.\n'
            'Comando --prepara per verificare il primo avvio senza aprire la GUI.\n'
            'Database esistente conservato. Pacchetto Debian 3.11.3-2.\n',encoding='utf-8')
        control=tree/'DEBIAN/control'; text=control.read_text()
        text=re.sub(r'^Version:.*$', 'Version: '+DEB_VERSION,text,flags=re.M)
        text=re.sub(r'^Installed-Size:.*\n','',text,flags=re.M)
        control.write_text(text)
        md5=tree/'DEBIAN/md5sums'
        if md5.exists(): md5.unlink()
        # Copie dell'installer non devono ereditare mode 0600 dal download.
        for p in tree.rglob('*'):
            if p.is_dir(): p.chmod(0o755)
            elif p.is_file() and not p.is_symlink():
                p.chmod(0o755 if p.suffix in ('.sh','.py') or p.name=='postinst' else 0o644)
        deb=output/(STEM+'_amd64.deb')
        subprocess.run(['dpkg-deb','--root-owner-group','--build',str(tree),str(deb)],check=True)
        portable=temp/(STEM+'_Portabile'); shutil.copytree(app,portable)
        wrapper=portable/'AVVIA_REGISTRO_ORE.sh'
        wrapper.write_text('#!/usr/bin/env bash\nset -e\ncd "$(dirname "$(readlink -f "$0")")"\nexec bash ./registro-ore.sh "$@"\n')
        wrapper.chmod(0o755)
        zip_tree(portable,output/(STEM+'_Portabile.zip'))
        update=temp/(STEM+'_AGGIORNAMENTO'); update.mkdir()
        shutil.copy2(deb,update/deb.name)
        script=update/'AGGIORNA_REGISTRO_ORE.sh'; script.write_text(UPDATER); script.chmod(0o755)
        (update/'LEGGIMI_AGGIORNAMENTO.txt').write_text(GUIDE,encoding='utf-8')
        zip_tree(update,output/(STEM+'_AGGIORNAMENTO.zip'))
    return [output/(STEM+s) for s in ('_amd64.deb','_Portabile.zip','_AGGIORNAMENTO.zip')]

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-deb',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--jar',type=Path)
    args=parser.parse_args()
    for p in build(args.base_deb.resolve(),args.output.resolve(),args.jar): print(p)
