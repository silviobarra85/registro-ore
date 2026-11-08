"""Regressioni Mint: validazione JAR, primo avvio e dati già esistenti.

I JAR fixture sono archivi sintetici per testare il bootstrap, non librerie
Java eseguibili. Nessun accesso Internet e nessuna installazione di sistema.
"""
import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'packaging/linux'
def module(name):
    spec=importlib.util.spec_from_file_location(name,PACK/(name+'.py'))
    m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m
bootstrap=module('bootstrap')
builder=module('build_mint')

def jar_data(cls, minimum=2000):
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w',zipfile.ZIP_STORED) as z:
        z.writestr(cls,b'fixture, not executable bytecode')
        z.writestr('fixture-padding',b'x'*minimum)
    return data.getvalue()

class MintBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.base=Path(self.temp.name); self.app=self.base/'app'; self.app.mkdir()
        shutil.copy2(ROOT/'database_vuoto.sqlite',self.app/'database_vuoto.sqlite')
        self.data=self.base/'xdg/RegistroOre'

    def prepare(self):
        args=['bootstrap.py','--app-dir',str(self.app),'--data-dir',str(self.data),'--mode','prepare']
        with mock.patch.object(sys,'argv',args), contextlib.redirect_stdout(io.StringIO()):
            bootstrap.main()

    def cached(self):
        folder=self.data/'lib'; folder.mkdir(parents=True,exist_ok=True)
        for name,url,cls,mn,mx in bootstrap.LIBRARIES:
            (folder/name).write_bytes(jar_data(cls,mn))

    def test_nop_provider_is_accepted_old_probe_is_rejected(self):
        name,url,cls,mn,mx=bootstrap.LIBRARIES[-1]
        self.assertEqual(cls,'org/slf4j/nop/NOPServiceProvider.class')
        path=self.base/name; path.write_bytes(jar_data(cls,mn))
        self.assertTrue(bootstrap.valid_jar(path,cls,mn,mx))
        self.assertFalse(bootstrap.valid_jar(path,'org/slf4j/nop/NOPLogger.class',mn,mx))

    def test_fresh_prepare_downloads_then_reuses_cache_offline(self):
        bodies={url:jar_data(cls,mn) for name,url,cls,mn,mx in bootstrap.LIBRARIES}
        def response(req,**kwargs): return io.BytesIO(bodies[req.full_url])
        with mock.patch.object(bootstrap.urllib.request,'urlopen',side_effect=response) as network:
            self.prepare(); self.assertEqual(network.call_count,3)
        with mock.patch.object(bootstrap.urllib.request,'urlopen',side_effect=AssertionError('network used')) as network:
            self.prepare(); self.assertEqual(network.call_count,0)
        db=self.data/'data/registro_ore_2026.sqlite'
        with sqlite3.connect(db) as c:
            self.assertEqual(c.execute('PRAGMA integrity_check').fetchone()[0],'ok')
            self.assertEqual(c.execute('SELECT COUNT(*) FROM entries').fetchone()[0],0)

    def test_existing_db_hours_notes_and_document_are_unchanged(self):
        target=self.data/'data/registro_ore_2026.sqlite'; target.parent.mkdir(parents=True)
        shutil.copy2(self.app/'database_vuoto.sqlite',target)
        with sqlite3.connect(target) as c:
            c.execute("INSERT INTO activities(id,name,category,color) VALUES(100,'Test','PROGETTI','#abcabc')")
            c.execute("INSERT INTO entries(work_date,activity_id,hours,note) VALUES('2026-11-06',100,3.5,'nota da preservare')")
            c.execute("INSERT INTO project_documents(activity_id,filename,contents,file_size) VALUES(100,'test.txt',?,4)",(b'dati',))
        before=hashlib.sha256(target.read_bytes()).digest(); self.cached()
        with mock.patch.object(bootstrap.urllib.request,'urlopen',side_effect=AssertionError('network used')):
            self.prepare()
        self.assertEqual(before,hashlib.sha256(target.read_bytes()).digest())

    def test_html_corrupt_or_wrong_jar_is_not_installed(self):
        path=self.base/'bad.jar'; cls=bootstrap.LIBRARIES[-1][2]
        for payload in [b'<html>errore</html>'*250,jar_data('org/slf4j/nop/Other.class')]:
            with mock.patch.object(bootstrap.urllib.request,'urlopen',return_value=io.BytesIO(payload)):
                with self.assertRaisesRegex(RuntimeError,'Libreria scaricata non valida'):
                    bootstrap.fetch('https://example.invalid/test.jar',path,cls,2000,1000000)
            self.assertFalse(path.exists())
            self.assertEqual(list(self.base.glob('.download-*')),[])

    def test_invalid_existing_db_is_never_reseeded(self):
        path=self.data/'data/registro_ore_2026.sqlite';path.parent.mkdir(parents=True)
        path.write_bytes(b'')
        with self.assertRaisesRegex(RuntimeError,'non viene sovrascritto|Non viene sovrascritto'):
            self.prepare()
        self.assertEqual(path.read_bytes(),b'')

    def test_launcher_prepare_and_diagnostics_do_not_open_java_app(self):
        for n in ['bootstrap.py','registro-ore.sh']: shutil.copy2(PACK/n,self.app/n)
        self.cached(); bins=self.base/'bin';bins.mkdir()
        java=bins/'java';java.write_text('#!/bin/sh\nif [ "$1" = "-version" ]; then echo \'openjdk version "17.0.1"\' >&2; exit 0; fi\nexit 88\n');java.chmod(0o755)
        env=dict(os.environ,PATH=str(bins)+os.pathsep+os.environ['PATH'],XDG_DATA_HOME=str(self.base/'xdg'))
        for arg in ['--prepara','--diagnostica']:
            r=subprocess.run(['bash',str(self.app/'registro-ore.sh'),arg],env=env,text=True,capture_output=True)
            self.assertEqual(r.returncode,0,r.stderr+r.stdout)
            self.assertIn('Componenti pronti.' if arg=='--prepara' else 'slf4j-nop-2.0.17.jar: OK',r.stdout)

    def test_updater_backs_up_before_install_without_writing_original(self):
        db=self.data/'data/registro_ore_2026.sqlite';db.parent.mkdir(parents=True)
        shutil.copy2(self.app/'database_vuoto.sqlite',db);before=db.read_bytes()
        script=self.base/'AGGIORNA_REGISTRO_ORE.sh';script.write_text(builder.UPDATER)
        bins=self.base/'bin';bins.mkdir()
        commands={'id':'echo 1000','pgrep':'exit 1',
            'sudo':'test -n "$(find "$XDG_DATA_HOME/RegistroOre/backup_aggiornamenti" -name "*.sqlite" -print -quit)"',
            'registro-ore':'echo "Componenti pronti."'}
        for name,body in commands.items():
            p=bins/name;p.write_text('#!/bin/sh\n'+body+'\n');p.chmod(0o755)
        env=dict(os.environ,PATH=str(bins)+os.pathsep+os.environ['PATH'],XDG_DATA_HOME=str(self.base/'xdg'))
        r=subprocess.run(['sh',str(script)],env=env,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr+r.stdout)
        backups=list((self.data/'backup_aggiornamenti').glob('*.sqlite'))
        self.assertEqual(len(backups),1);self.assertEqual(db.read_bytes(),before)
        with sqlite3.connect(backups[0]) as c:self.assertEqual(c.execute('PRAGMA integrity_check').fetchone()[0],'ok')

if __name__=='__main__': unittest.main()
