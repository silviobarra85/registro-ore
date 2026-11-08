"""Smoke test SQL statements used by additive batch entry consolidation.

Uses Python's built-in SQLite and deliberately does NOT require Java/JDBC.
An additional end-to-end JDBC test on a real installation is still recommended.
"""
import sqlite3

FIND = ("SELECT id,hours,note FROM entries WHERE work_date=? AND activity_id=? "
        "AND (mission_id=? OR (mission_id IS NULL AND ? IS NULL)) "
        "AND category_snapshot=? COLLATE NOCASE "
        "AND (source_ref IS NULL OR TRIM(source_ref)='') ORDER BY id")


def add(conn, day, act_id, category, hours, note, mission=None):
    matches = conn.execute(FIND, (day, act_id, mission, mission, category)).fetchall()
    positive = [row for row in matches if row[1] is not None]
    if positive:
        notes = list(dict.fromkeys([row[2] for row in positive if row[2]] + ([note] if note else [])))
        for old in positive[1:]:
            conn.execute("DELETE FROM entries WHERE id=?", (old[0],))
        conn.execute("UPDATE entries SET hours=?,note=? WHERE id=?",
                     (sum(row[1] for row in positive) + hours, ' | '.join(notes), positive[0][0]))
    else:
        conn.execute("INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot,mission_id) "
                     "VALUES(?,?,?,?,?,?)", (day, act_id, hours, note, category, mission))


def test():
    conn=sqlite3.connect(':memory:')
    conn.executescript('''
        PRAGMA foreign_keys=ON;
        CREATE TABLE entries (id INTEGER PRIMARY KEY,work_date TEXT,activity_id INTEGER,
          hours REAL,note TEXT,category_snapshot TEXT,mission_id INTEGER,source_ref TEXT);
        CREATE TABLE month_locks (year INTEGER, month INTEGER, PRIMARY KEY(year,month));
        CREATE TRIGGER entries_lock_update BEFORE UPDATE OF hours,note ON entries
          WHEN EXISTS (SELECT 1 FROM month_locks WHERE year=CAST(substr(OLD.work_date,1,4) AS INTEGER)
             AND month=CAST(substr(OLD.work_date,6,2) AS INTEGER))
          BEGIN SELECT RAISE(ABORT,'locked'); END;
        CREATE TRIGGER entries_lock_insert BEFORE INSERT ON entries
          WHEN EXISTS (SELECT 1 FROM month_locks WHERE year=CAST(substr(NEW.work_date,1,4) AS INTEGER)
             AND month=CAST(substr(NEW.work_date,6,2) AS INTEGER))
          BEGIN SELECT RAISE(ABORT,'locked'); END;
    ''')
    category='Ricerca su progetti finanziati'
    day='2026-11-12'
    conn.execute('INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot) VALUES(?,?,?,?,?)',
                 (day,1,2.0,'Prototipo',category))
    conn.execute('INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot) VALUES(?,?,?,?,?)',
                 (day,1,1.5,'Verifica',category))
    conn.commit()
    with conn:
        add(conn,day,1,category,2.0,'Sperimentazione')
    result=conn.execute('SELECT hours,note FROM entries WHERE work_date=? AND activity_id=1',(day,)).fetchall()
    assert result == [(5.5, 'Prototipo | Verifica | Sperimentazione')],result
    # A different mission must not be merged with the original line.
    with conn:
        add(conn,day,1,category,1.0,'Conferenza',mission=9)
    lines=conn.execute('SELECT hours,mission_id FROM entries WHERE work_date=? ORDER BY id',(day,)).fetchall()
    assert lines == [(5.5,None),(1.0,9)],lines
    # Different old category and external source remain separate as well.
    conn.execute('INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot) VALUES(?,?,?,?,?)',
                 (day,1,2.0,'Vecchia categoria','Ricerca istituzionale'))
    conn.execute('INSERT INTO entries(work_date,activity_id,hours,note,category_snapshot,source_ref) VALUES(?,?,?,?,?,?)',
                 (day,1,1.0,'Import esterno',category,'external-import'))
    with conn:
        add(conn,day,1,category,0.5,'Aggiornamento')
    lines=conn.execute('SELECT hours,category_snapshot,source_ref FROM entries WHERE work_date=? ORDER BY id',(day,)).fetchall()
    assert len(lines)==4 and abs(lines[0][0]-6)<1e-8,lines
    # A locked month must reject an UPDATE without affecting existing rows.
    conn.execute('INSERT INTO month_locks(year,month) VALUES(2026,11)')
    conn.commit()
    previous=list(conn.execute('SELECT id,hours,note FROM entries ORDER BY id'))
    try:
        with conn:
            add(conn,day,1,category,1.0,'bloccato')
    except sqlite3.IntegrityError:
        pass
    else:
        raise AssertionError('locked month accepted new hours')
    assert previous==list(conn.execute('SELECT id,hours,note FROM entries ORDER BY id'))
    print('OK: SQL SQLite - somma, note, missioni diverse, categorie storiche e blocchi')

if __name__ == '__main__':
    test()
