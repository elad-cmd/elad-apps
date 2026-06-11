# -*- coding: utf-8 -*-
import re, json, glob, os

SRC = {
 'שחזור ידע מועד א מאי 1.5.2026':'מועד א מאי 2026',
 'שחזור מבחן ידע מועד ב מאי 2025 ללא תשובות':'מועד ב מאי 2025',
 'שחזור מבחן ידע מועד ג יולי 2025 מילואימניקים ללא תשובות':'מועד ג יולי 2025',
 'שחזור מבחן ידע מועד יוני 2024 ללא תשובות':'מועד יוני 2024',
 'שחזור מבחן ידע מועד מאי 2024 ללא תשובות':'מועד מאי 2024',
}
ORDER = ['מועד א מאי 2026','מועד ב מאי 2025','מועד ג יולי 2025','מועד יוני 2024','מועד מאי 2024']

BULLETS = '•▪●*-'
def strip_bul(s):
    return s.lstrip(BULLETS + ' \t').strip()

import re as _re
_latin_run = _re.compile(r"[A-Za-z0-9][A-Za-z0-9 .,\-/_()'\u2019\u201c\u201d\"]*[A-Za-z0-9]|[A-Za-z0-9]")
def fix_latin(s):
    # after full-line reverse Hebrew is correct but Latin/number runs are reversed; restore them
    return _latin_run.sub(lambda m: m.group(0)[::-1], s)

def header_subject(line):
    s = strip_bul(line).rstrip(':').strip()
    if s.startswith('פרק'):  # 'פרק'
        s = s.split(':',1)[-1].strip()
    if len(s) > 24: return None
    if re.match(r'^[א-ד][\.\) ]', s): return None
    if 'ביוכימ' in s: return 'biochem'
    if 'מולקול' in s: return 'mol'
    if 'פיזיולוג' in s: return 'physio'
    if s in ('ביותא','ביולוגיה של התא','ביולוגיה של תא','ביו תא') or ('ביולוגיה' in s and 'התא' in s):
        return 'cell'
    return None

opt_re = re.compile(r'^([אבגד])[\.\)]\s*(.*)$')
q_prefix_re = re.compile(r'^שאלה\s*(\d{1,3})[\.\)\s:]+(.*)$')
q_bare_re = re.compile(r'^(\d{1,3})[\.\)]\s*(.*)$')

def qmatch(body):
    m=q_prefix_re.match(body)
    if m and len(m.group(2).strip())>=4: return m
    m=q_bare_re.match(body)
    if m and len(m.group(2).strip())>=4: return m
    return None

def recover_num(d):
    v=int(d)
    if v>130:
        r=d[::-1].lstrip('0') or '0'
        if 1<=int(r)<=130: return int(r)
    return v

def parse_file(path):
    name = os.path.splitext(os.path.basename(path))[0]
    src = SRC.get(name, name)
    raw = open(path, encoding='utf-8').read().split('\n')
    lines = [fix_latin(ln[::-1]) for ln in raw]
    questions = []
    subject = None
    cur = None
    last_opt = None
    started = False

    def flush():
        nonlocal cur
        if cur and cur.get('q') and cur['opts']:
            questions.append(cur)
        cur = None

    for rln in lines:
        ln = rln.strip()
        if not ln:
            continue
        hs = header_subject(ln)
        if hs:
            flush(); subject = hs; started = True; last_opt = None
            continue
        if not started:
            continue
        body = strip_bul(ln)
        mo = opt_re.match(body)
        if mo:
            if cur is not None:
                cur['opts'][mo.group(1)] = mo.group(2).strip()
                last_opt = mo.group(1)
            continue
        mq = qmatch(body)
        if mq:
            flush()
            cur = {'n': recover_num(mq.group(1)), 'subject': subject, 'source': src,
                   'q': mq.group(2).strip(), 'opts': {}, 'flags': []}
            last_opt = None
            continue
        if ln.startswith('*') and cur is not None:
            cur['flags'].append(strip_bul(ln)); continue
        if cur is not None and last_opt:
            cur['opts'][last_opt] = (cur['opts'][last_opt] + ' ' + body).strip()
        elif cur is not None:
            cur['q'] = (cur['q'] + ' ' + body).strip()
    flush()
    return src, questions

allq = []
report = []
files = {SRC[os.path.splitext(os.path.basename(p))[0]]: p for p in glob.glob('*.txt') if os.path.splitext(os.path.basename(p))[0] in SRC}
for src in ORDER:
    if src not in files: continue
    s2, qs = parse_file(files[src])
    for q in qs:
        q['o'] = [q['opts'].get(l,'') for l in 'אבגד']
        q['n_opts'] = sum(1 for x in q['o'] if x and 'לא קיים' not in x and 'אין מידע' not in x and x != 'חסר')
        del q['opts']
    bys = dict()