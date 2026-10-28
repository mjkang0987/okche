# 디자인 가이드(옥체 디자인 시스템 README·tokens) 값으로 화면을 맞춤. 여러 번 돌려도 같은 결과.
# 생성 스크립트를 다시 돌린 뒤에는 backdrops.py → guidefix.py 순서로 마지막에 돌린다.
import re, glob, os, sys
P = os.environ.get('DESIGN', '/home/claude/okche_design/project/')
SKIP = ('Flow', 'AppIcon')  # 화면 흐름도·앱 아이콘 보드는 앱 화면이 아님
FS = {15: 16, 13: 14, 17: 18, 26: 28, 11: 12}
NUM = re.compile(r'^[\d.,]+$')
TAG = re.compile(r'<([a-zA-Z][\w-]*)((?:\s[^>]*?)?)\sstyle="([^"]*)"([^>]*)>([^<]*)')

def has_color(st):
    return re.search(r'(^|;)\s*color:', st) is not None

def fix(tag, attrs, st, text, fname):
    t = tag.lower(); text = text.strip(); aria = re.search(r'aria-label="([^"]*)"', attrs)
    aria = aria.group(1) if aria else ''
    def fsub(m):
        n = float(m.group(1)); n = int(n) if n.is_integer() else n
        if n in FS: n = FS[n]
        elif n in (20, 24): n = 32 if NUM.match(text or '') else (22 if n == 24 else 18)
        return f'font-size: {n}px'
    st = re.sub(r'font-size: (\d+(?:\.\d+)?)px', fsub, st)
    # 둥근 모서리: 가이드 값(8·14·20·999)만. 높이 20px 막대의 10px는 알약 모양이라 둔다.
    if t == 'label' and 'height: 44px' in st and 'border-radius: 10px' in st:  # 나눔 버튼(직접 급여 / 급식기)
        st = st.replace('height: 44px', 'height: 40px').replace('border-radius: 10px', 'border-radius: 8px')
    def rsub(m):
        vals = m.group(1)
        if 'height: 20px' in st and vals.startswith('10px'): return 'border-radius: 999px'  # 막대는 알약 모양
        big = 14 if fname.startswith('BodyCamera') else 20
        vals = re.sub(r'\b(12|10)px', '14px', vals); vals = re.sub(r'\b16px', f'{big}px', vals)
        return 'border-radius: ' + vals
    st = re.sub(r'border-radius: ([^;"]+)', rsub, st)
    # 높이: 바로 기록 버튼 56 이상, 글자 버튼 40·52, 입력칸·셀렉트 52, 아이콘 버튼 44
    if '바로 기록' in aria: st = re.sub(r'padding: 0 1[02]px', 'padding: 0 8px', st)  # 글자가 16으로 커져 한 줄에 세 개 들어가게
    if '바로 기록' in aria and 'height: 44px' in st: st = st.replace('height: 44px', 'height: 56px')
    elif 'height: 44px; overflow: hidden; display: flex; flex-wrap: wrap' in st:
        st = st.replace('height: 44px', 'height: 56px')
    elif t in ('select', 'input') and 'height: 44px' in st: st = st.replace('height: 44px', 'height: 52px')
    elif t in ('button', 'a', 'label') and text and 'height: 44px' in st:
        st = st.replace('height: 44px', 'height: 40px')
    if '바로 기록' not in aria and '눌러서 고치기' in aria and 'height: 44px' in st:  # 처리한 기록 버튼도 기록 줄 높이
        st = st.replace('height: 44px', 'height: 56px')
    if (t == 'button' and text == '남기기') or 'id="q-glu"' in attrs:  # 일기 혈당 줄도 기록 줄 높이 56
        st = re.sub(r'(?<!-)height: (40|44|52)px', 'height: 56px', st)
    st = st.replace('min-height: 48px', 'min-height: 52px')
    if 'min-height: 52px' in st and 'box-sizing' not in st: st = 'box-sizing: border-box; ' + st  # 구분선까지 52
    if t == 'label' and text.startswith('오늘 ·') and 'min-height' not in st:  # 날짜 고르기
        st = st.replace('align-items: center;', 'align-items: center; justify-content: center; min-height: 40px;')
    if re.search(r'height: (40|52|56)px', st) and re.search(r'border: [1-9]', st) and 'box-sizing' not in st:
        st = 'box-sizing: border-box; ' + st
    if t == 'button' and not has_color(st): st = st + '; color: #2B2522'
    return st

def run(only=None):
    import json
    killed = {k for k, v in json.load(open(P + 'canvas.json'))['boards'].items() if v.get('page') == 'killed'}  # 킬된 시안은 기록이라 그대로 둠
    n = 0
    for f in sorted(glob.glob(P + '*.dc.html')):
        name = os.path.basename(f)
        if name in killed or name.startswith(SKIP) or (only and name[:-8] not in only): continue
        s = o = open(f, encoding='utf-8').read()
        s = re.sub(r'font-size:(15|13)px;line-height:(22|20)px', lambda m: 'font-size:16px;line-height:24px' if m.group(1) == '15' else 'font-size:14px;line-height:20px', s)  # <style> 안
        s = s.replace('#6E86B4', '#2F4B7C').replace('#6e86b4', '#2F4B7C')  # 그래프 막대는 namsaek
        s = TAG.sub(lambda m: f'<{m.group(1)}{m.group(2)} style="{fix(m.group(1), m.group(2) + m.group(4), m.group(3), m.group(5), name)}"{m.group(4)}>{m.group(5)}', s)
        if s != o: open(f, 'w', encoding='utf-8').write(s); n += 1
    return n

if __name__ == '__main__':
    print('고친 파일', run(sys.argv[1:] or None))
