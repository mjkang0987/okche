# 시트·확인창 뒤에 실제 부모 화면을 그대로 깔기 (흐린 덮개 아래, 조작·읽기 불가)
import re
import os
P=os.environ.get('DESIGN','/home/claude/okche_design/project/')
MAP={'Vomit':'Record','Stool':'Record','Weight':'Record','Snack':'Sura','Assign':'Log','DeleteConfirm':'Log','SillokFilter':'Sillok','PetSwitch':'Main','Celebrate':'Record','CelebrateStage':'Record','CelebrateFamily':'Record','CelebrateDays':'Record','CelebrateSchool':'Record','CelebrateHoliday':'Record','GuideAlert':'Vomit','GuideAlertStool':'Stool','FeederTime':'Feeder','BackupRestore':'Backup','PetDelete':'PetEdit','MemoryKeep':'PetEdit','CelebrateSeollal':'Record','Urine':'Record','GuideAlertUrine':'Urine','MedSheet':'Record','Glucose':'RecordGlucose','Insulin':'RecordGlucose','Food':'PetEdit','SnackReg':'SnackList','MedReg':'Yakbang','MedRegSheet':'MedAdd','SnackRegSheet':'Sura'}
DIM='<div aria-hidden="true" style="position: absolute; inset: 0; background: rgba(43, 37, 34, 0.45)"></div>'
def parent_copy(name):
    s=open(P+name+'.dc.html').read()
    m=re.search(r'</helmet>\s*<div style="([^"]*)">',s)
    style=m.group(1); start=m.end(); end=s.rindex('</div>\n</x-dc>')
    inner=s[start:end]
    inner=re.sub(r'\s(id|for|aria-labelledby|aria-describedby|name|autofocus)="[^"]*"','',inner)
    inner=re.sub(r'<(a|button|input|select|textarea|sc-raw-select)(\s)',r'<\1 tabindex="-1"\2',inner)
    style=re.sub(r'height: \d+px','height: 100%',style)
    return f'<div aria-hidden="true" style="position: absolute; left: 0; top: 0; right: 0; {style}; pointer-events: none">{inner}</div>\n  '
for child,parent in MAP.items():
    f=P+child+'.dc.html'; s=open(f).read()
    d=s.rindex(DIM)  # 바깥 레이어의 덮개 = 마지막 DIM (배경 복사본 안의 DIM은 건너뜀)
    a=s.rfind('<div aria-hidden="true" style="position: absolute;',0,d)
    # 이전에 깐 배경(가짜 또는 복사본) 시작점: 루트 div 바로 다음
    root_end=re.search(r'</helmet>\s*<div style="[^"]*">\s*',s).end()
    s=s[:root_end]+parent_copy(parent)+s[d:]
    open(f,'w').write(s)
print('ok')
