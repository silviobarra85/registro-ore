#!/usr/bin/env python3
"""Bootstrap locale Registro Ore: database separato, dipendenze HTTPS e integrita."""
import argparse, os, sqlite3, sys, tempfile, urllib.request, zipfile
from pathlib import Path

LIBRARIES = (
    ("sqlite-jdbc-3.49.1.0.jar", "https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar", "org/sqlite/JDBC.class", 2000000, 22000000),
    ("slf4j-api-2.0.17.jar", "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar", "org/slf4j/Logger.class", 30000, 1000000),
    # Il provider NOP contiene NOPServiceProvider; NOPLogger si trova invece
    # nel package helpers di slf4j-api. Il vecchio controllo rifiutava JAR validi.
    ("slf4j-nop-2.0.17.jar", "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar", "org/slf4j/nop/NOPServiceProvider.class", 2000, 1000000),
)

def valid_jar(path, cls, min_size, max_size):
    if not path.exists() or not (min_size <= path.stat().st_size <= max_size):
        return False
    try:
        with zipfile.ZipFile(path) as z:
            return cls in z.namelist() and z.testzip() is None
    except (OSError, zipfile.BadZipFile):
        return False

def seed_db(seed, dest):
    dest.parent.mkdir(parents=True,exist_ok=True)
    if dest.exists():
        if not dest.is_file() or dest.stat().st_size == 0:
            raise RuntimeError("Il database esistente e vuoto o non e un file: " + str(dest) + ". Non viene sovrascritto.")
        return False
    if not seed.is_file():
        raise RuntimeError("Manca il database vuoto distribuito con il programma.")
    # Create exclusively; never overwrite a user's DB, even with concurrent launches.
    try:
        with open(dest, 'xb') as fd:
            fd.write(seed.read_bytes())
    except FileExistsError:
        return False
    os.chmod(dest, 0o600)
    return True

def verify_db(dest):
    if not dest.exists():
        raise RuntimeError("Database assente: "+str(dest))
    con=sqlite3.connect(f"file:{dest}?mode=ro",uri=True)
    try:
        status=con.execute("PRAGMA quick_check").fetchone()[0]
        if status!='ok':
            raise RuntimeError("Database non integro: "+str(status))
        return con.execute("select count(*) from entries").fetchone()[0]
    finally:
        con.close()

def fetch(url, path, cls, minimum, maximum):
    if valid_jar(path,cls,minimum,maximum):
        return
    if path.exists():
        raise RuntimeError("Libreria locale non valida: "+str(path)+". Spostala altrove prima di riprovare.")
    print("Scaricamento componente: "+path.name,flush=True)
    req=urllib.request.Request(url,headers={'User-Agent':'RegistroOre-Mint/3.11.3-fix1'})
    tmp=None
    try:
        with urllib.request.urlopen(req,timeout=40) as src, tempfile.NamedTemporaryFile(dir=path.parent,prefix='.download-',suffix='.jar',delete=False) as dst:
            tmp=Path(dst.name)
            length=0
            while True:
                chunk=src.read(256*1024)
                if not chunk: break
                length+=len(chunk)
                if length>maximum: raise RuntimeError("Download piu grande del previsto: "+path.name)
                dst.write(chunk)
        if not valid_jar(tmp,cls,minimum,maximum):
            raise RuntimeError("Libreria scaricata non valida: "+path.name)
        os.replace(tmp,path)
        os.chmod(path,0o600)
    finally:
        if tmp and tmp.exists(): tmp.unlink()

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--app-dir',required=True)
    p.add_argument('--data-dir',required=True)
    p.add_argument('--mode',choices=('start','prepare','init','check'),default='start')
    args=p.parse_args()
    root=Path(args.data_dir).expanduser()
    app=Path(args.app_dir)
    lib=root/'lib'
    db=root/'data'/'registro_ore_2026.sqlite'
    for d in [lib,root/'logs',root/'data']:
        d.mkdir(parents=True,exist_ok=True)
    created=seed_db(app/'database_vuoto.sqlite',db)
    entries=verify_db(db)
    print(("Creato database vergine" if created else "Database esistente mantenuto")+f": {db} ({entries} registrazioni)",flush=True)
    if args.mode=='check':
        for filename,_,cls,mn,mx in LIBRARIES:
            print(f"  {filename}: "+('OK' if valid_jar(lib/filename,cls,mn,mx) else 'non ancora scaricato'))
        return
    if args.mode=='init': return
    for name,url,cls,mn,mx in LIBRARIES:
        fetch(url,lib/name,cls,mn,mx)
    print("Componenti pronti.",flush=True)
if __name__=='__main__':
    try: main()
    except Exception as exc:
        print("ERRORE: "+str(exc),file=sys.stderr,flush=True)
        sys.exit(1)
