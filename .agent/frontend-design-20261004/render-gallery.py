"""Package actual CUA screenshots; never synthesize application pixels."""
from pathlib import Path
import base64
import html
import json
import math
import re
import textwrap

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
OUT = ROOT / 'presentation'
OUT.mkdir(exist_ok=True)
base = json.loads((ROOT / 'capture-evidence.json').read_text(encoding='utf-8'))
extras = json.loads((ROOT / 'extra-capture-evidence.json').read_text(encoding='utf-8'))['captures']
captures = base['captures'] + extras
for capture in captures:
    if capture['id'] == 'interaction-reason-open':
        capture['label'] = 'Причина · фокус (нативный список вне снимка браузера)'

font_root = REPO / 'frontends/node_modules/@fontsource-variable/onest/files'
font_path = font_root / 'onest-cyrillic-wght-normal.woff2'
title_font = ImageFont.truetype(str(font_path), 25)
meta_font = ImageFont.truetype(str(font_path), 22)
title_latin = ImageFont.truetype(str(font_root / 'onest-latin-wght-normal.woff2'), 25)
meta_latin = ImageFont.truetype(str(font_root / 'onest-latin-wght-normal.woff2'), 22)

def font_runs(value, cyrillic_font, latin_font):
    runs = []
    for character in value:
        selected = cyrillic_font if '\u0400' <= character <= '\u052f' or character == '\u2116' else latin_font
        if runs and runs[-1][1] is selected:
            runs[-1] = (runs[-1][0] + character, selected)
        else:
            runs.append((character, selected))
    return runs

def measure(draw, value):
    return sum(draw.textlength(run, font=selected) for run, selected in font_runs(value, title_font, title_latin))

def draw_onest(draw, position, value, color, small=False):
    x, y = position
    for run, selected in font_runs(value, meta_font if small else title_font, meta_latin if small else title_latin):
        draw.text((x,y), run, font=selected, fill=color)
        x += draw.textlength(run, font=selected)

def uri(path):
    return 'data:image/jpeg;base64,' + base64.b64encode(Path(path).read_bytes()).decode('ascii')

def group_for(capture):
    identifier = capture['id']
    if identifier.startswith('stress-320-'):
        return '320 px'
    if identifier.startswith('interaction-'):
        return 'Взаимодействия'
    if identifier in {x['id'] for x in base['states'][:6]}:
        return 'Макеты'
    if identifier.startswith('excuse-'):
        return 'Уважительная причина'
    if identifier.startswith('late-'):
        return 'Забыл отметиться'
    return 'Сегодня'

entries = {}
for capture in captures:
    entry = entries.setdefault(capture['id'], {'id': capture['id'], 'label': capture['label'], 'group': group_for(capture), 'images': {}})
    entry['images'][capture['shell']] = capture
    assert not capture['outside'], capture['id']
    assert capture['scrollWidth'] <= capture['clientWidth'], capture['id']
    assert all('Onest' in value for value in capture['fonts']), capture['id']
    assert Path(capture['path']).is_file(), capture['id']

entries = list(entries.values())
companions = [entry for entry in entries if any(c.get('scrollPath') for c in entry['images'].values())]
image_count = len(captures) + sum(bool(c.get('scrollPath')) for c in captures)

def escaped(value):
    return html.escape(value, quote=True)

def image_html(capture, bottom=False):
    path = capture.get('scrollPath') if bottom else capture['path']
    if not path:
        return ''
    label = capture['shell'].upper() + (' · низ после прокрутки' if bottom else '')
    return f'<figure><figcaption>{label}</figcaption><img loading="lazy" src="{uri(path)}" alt="{escaped(capture["label"] + " · " + label)}" tabindex="0"></figure>'

cards = []
for i, entry in enumerate(entries, 1):
    pictures = ''.join(image_html(entry['images'][shell]) for shell in ('pwa', 'tma'))
    bottoms = ''.join(image_html(entry['images'][shell], True) for shell in ('pwa', 'tma'))
    bottom_markup = f'<details><summary>Нижняя часть после прокрутки</summary><div class="pair">{bottoms}</div></details>' if bottoms else ''
    cards.append(f'<article data-group="{escaped(entry["group"])}" data-search="{escaped(entry["label"] + " " + entry["id"])}"><h2>{i:02} · {escaped(entry["label"])}</h2><small>{escaped(entry["id"])}</small><div class="pair">{pictures}</div>{bottom_markup}</article>')

live_path = ROOT / 'screenshots/pwa-live-empty.jpg'
live_capture = {'shell': 'pwa', 'path': str(live_path), 'label': 'Реальный сервер · Сегодня без пар'}
cards.append(f'<article data-group="Реальный сервер" data-search="Реальный сервер Сегодня без пар pwa-live-empty"><h2>Реальный сервер · Сегодня без пар</h2><small>PWA · фактическая Student-сессия, воскресенье 4 октября</small><div class="pair">{image_html(live_capture)}</div></article>')
groups = ['Все', 'Макеты', 'Сегодня', 'Уважительная причина', 'Забыл отметиться', 'Взаимодействия', '320 px', 'Реальный сервер']
font_faces = []
for subset, unicode_range in [('cyrillic', 'U+0400-045F,U+0490-0491,U+04B0-04B1,U+2116'), ('latin', 'U+0000-00FF,U+2000-206F,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF')]:
    encoded = base64.b64encode((font_root / f'onest-{subset}-wght-normal.woff2').read_bytes()).decode('ascii')
    font_faces.append(f"@font-face{{font-family:Onest;src:url(data:font/woff2;base64,{encoded}) format('woff2');font-style:normal;font-weight:100 900;unicode-range:{unicode_range};font-display:swap}}")

document = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Student Today · итоговые скриншоты</title><style>__FONTS__
:root{color-scheme:dark;--base:#0b0b0c;--raised:#1c1922;--text:#edeaf3;--secondary:#c8c2d6;--accent:#b79cfa;font-family:Onest,sans-serif;background:var(--base);color:var(--text)}*{box-sizing:border-box}body{margin:0;padding:24px}header{max-width:1200px;margin:auto auto 32px}h1{font-size:26px}p{line-height:1.5;color:var(--secondary)}a{color:var(--accent)}.tools{display:flex;gap:12px;flex-wrap:wrap}input,select,button{font:inherit;color:var(--text);background:var(--raised);border:1px solid var(--secondary);border-radius:12px;padding:12px}input{flex:1;min-width:200px}button{cursor:pointer}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,790px),1fr));gap:24px}article{background:var(--raised);border-radius:20px;padding:16px;min-width:0}article[hidden]{display:none}h2{font-size:18px;line-height:1.4;margin:0 0 4px}small{color:var(--secondary);overflow-wrap:anywhere}.pair{display:flex;gap:12px;margin-top:12px;align-items:flex-start}figure{margin:0;flex:1;min-width:0;text-align:center}figcaption{font-size:13px;margin:0 0 8px}img{display:block;width:100%;max-width:390px;margin:auto;cursor:zoom-in;border-radius:8px}summary{padding:16px 0;cursor:pointer}dialog{border:0;background:var(--base);color:var(--text);padding:16px;max-height:96vh;max-width:98vw}dialog::backdrop{background:#000b}dialog img{width:auto;max-width:min(100%,780px);max-height:84vh;object-fit:contain;cursor:default}dialog button{display:block;margin:12px auto 0}@media(max-width:600px){body{padding:12px}.pair{gap:8px}article{padding:12px}}
</style><header><h1>Студент · Сегодня · тёмная PWA и TMA</h1><p>__COUNTS__. Слева PWA, справа локальная TMA. Нажми на кадр, чтобы открыть оригинал. Поиск и фильтр позволяют проверить каждое состояние отдельно.</p><p>Это визуальные симуляции настоящих Vue-компонентов: API не вызывается, нативный Telegram не подключён. Настоящее восстановление Student-сессии сейчас останавливает ошибка Schedule Service; этот API-путь ещё не принят. Системный popup нативного выбора причины не попадает в снимок браузера; фокус и expanded-состояние проверены отдельно.</p><div class="tools"><input type="search" id="search" aria-label="Поиск состояния" placeholder="Поиск состояния"><select id="group" aria-label="Группа состояний">__OPTIONS__</select><button id="bottoms">Раскрыть нижние кадры</button></div><p id="count"></p></header><main>__CARDS__</main><dialog><img alt=""><button>Закрыть</button></dialog><script>
const cards=[...document.querySelectorAll('article')],search=document.querySelector('#search'),group=document.querySelector('#group'),count=document.querySelector('#count');function filter(){let visible=0;for(const card of cards){card.hidden=!(group.value==='Все'||card.dataset.group===group.value)||!card.dataset.search.toLocaleLowerCase('ru').includes(search.value.toLocaleLowerCase('ru'));if(!card.hidden)visible++}count.textContent=`Показано состояний: ${visible} / ${cards.length}`}search.addEventListener('input',filter);group.addEventListener('change',filter);filter();document.querySelector('#bottoms').addEventListener('click',()=>{for(const el of document.querySelectorAll('details'))el.open=true});const dialog=document.querySelector('dialog'),large=dialog.querySelector('img');for(const img of document.querySelectorAll('article img')){const open=()=>{large.src=img.src;large.alt=img.alt;dialog.showModal()};img.addEventListener('click',open);img.addEventListener('keydown',e=>{if(e.key==='Enter')open()})}dialog.querySelector('button').addEventListener('click',()=>dialog.close());dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close()});
</script></html>'''
document = document.replace('Настоящее восстановление Student-сессии сейчас останавливает ошибка Schedule Service; этот API-путь ещё не принят.', 'Последняя реальная загрузка Student успешно открыла «Сегодня» без пар; её кадр находится в группе «Реальный сервер». Отметка и заявки с настоящими данными ещё требуют отдельной приёмки; прежняя серверная 503 сейчас не воспроизведена.').replace('__FONTS__', ''.join(font_faces)).replace('__COUNTS__', f'{len(base["states"])} сценариев × 2 оболочки, {len(extras)} дополнительных кадров проверок и {image_count-len(captures)} нижних кадров: {image_count} визуальных скриншотов и 1 кадр с реального сервера').replace('__OPTIONS__', ''.join(f'<option>{escaped(g)}</option>' for g in groups)).replace('__CARDS__', ''.join(cards))
(OUT / 'all-states.html').write_text(document, encoding='utf-8')

def wrap_label(draw, label, width):
    lines = []
    current = ''
    for word in label.split():
        next_line = current + (' ' if current else '') + word
        if measure(draw, next_line) <= width:
            current = next_line
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines

sheet_records = []
def sheet(batch, number, bottom=False):
    # Each state is a full-resolution pair: screenshots are only placed, never edited.
    state_width, state_height = 812, 978
    columns = 2
    rows = math.ceil(len(batch) / columns)
    canvas = Image.new('RGB', (24 + columns*state_width, 24 + rows*state_height), '#1c1922')
    draw = ImageDraw.Draw(canvas)
    for index, entry in enumerate(batch):
        x, y = 12 + (index % columns)*state_width, 12 + (index//columns)*state_height
        label = entry['label'] + (' · низ после прокрутки' if bottom else '')
        for j, line in enumerate(wrap_label(draw, label, 785)):
            draw_onest(draw, (x+10,y+6+j*30), line, '#edeaf3')
        for column, shell in enumerate(('pwa','tma')):
            capture = entry['images'][shell]
            path = capture.get('scrollPath') if bottom else capture['path']
            draw_onest(draw, (x+10+column*400,y+94), shell.upper()+' · '+str(capture['viewport']['width'])+'px', '#c8c2d6', True)
            if path:
                picture = Image.open(path).convert('RGB')
                canvas.paste(picture,(x+10+column*400,y+126))
    path = OUT / f'contact-{number:02}.jpg'
    canvas.save(path, quality=91, subsampling=0)
    sheet_records.append({'path':str(path),'bottom':bottom,'states':[e['id'] for e in batch],'labels':[e['label'] for e in batch]})

number = 1
for start in range(0, len(entries), 4):
    sheet(entries[start:start+4], number)
    number += 1
for start in range(0, len(companions), 4):
    sheet(companions[start:start+4], number, True)
    number += 1

manifest = {'scenarioCount':len(base['states']),'extraCount':len(extras),'primaryCount':len(captures),'imageCount':image_count,'realCapture':str(live_path),'totalImageCount':image_count+1,'contactSheets':sheet_records,'gallery':str(OUT/'all-states.html')}
(OUT / 'inventory.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({k:v for k,v in manifest.items() if k!='contactSheets'},ensure_ascii=False))
