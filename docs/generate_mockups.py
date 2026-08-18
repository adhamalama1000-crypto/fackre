#!/usr/bin/env python3
# Generates clean Arabic-RTL phone-frame MOCKUPS (design previews) as SVG.
# These are design representations of the Compose layouts, NOT device captures.
import os

OUT = "/home/claude/WarehouseInventory/docs/screenshots"
os.makedirs(OUT, exist_ok=True)

W, H = 380, 780              # phone content area
PRIMARY = "#1565C0"
PRIMARY_CT = "#D6E3FF"
ON_PRIMARY_CT = "#0D47A1"
BG = "#F7F9FC"
SURFACE = "#FFFFFF"
OUTLINE = "#8A94A6"
ERR = "#C62828"
ERR_CT = "#FBD8D8"
OK = "#2E7D32"
AMBER = "#B26A00"
TEXT = "#1A1F28"

DARK_BG = "#11151C"
DARK_SURFACE = "#1A1F28"
DARK_TEXT = "#E8EAF0"

def frame(inner, title, dark=False):
    bg = DARK_BG if dark else BG
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Segoe UI, Tahoma, sans-serif" direction="rtl">
<defs><clipPath id="r"><rect x="0" y="0" width="{W}" height="{H}" rx="28"/></clipPath></defs>
<g clip-path="url(#r)">
<rect x="0" y="0" width="{W}" height="{H}" fill="{bg}"/>
<!-- status bar -->
<rect x="0" y="0" width="{W}" height="34" fill="{bg}"/>
<text x="24" y="22" fill="{DARK_TEXT if dark else TEXT}" font-size="13" text-anchor="start">100%</text>
<text x="{W-24}" y="22" fill="{DARK_TEXT if dark else TEXT}" font-size="13" text-anchor="end">9:41</text>
{inner}
</g>
<rect x="0.5" y="0.5" width="{W-1}" height="{H-1}" rx="28" fill="none" stroke="#00000022"/>
</svg>'''

def txt(x, y, s, size=15, fill=TEXT, anchor="end", weight="normal"):
    return f'<text x="{x}" y="{y}" fill="{fill}" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}">{s}</text>'

def card(x, y, w, h, fill=SURFACE, rx=16, stroke="none"):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}"/>'

def topbar(title, dark=False, back=True):
    s = SURFACE if not dark else DARK_SURFACE
    t = TEXT if not dark else DARK_TEXT
    b = f'<text x="{W-22}" y="66" fill="{t}" font-size="20" text-anchor="end">→</text>' if back else ''
    return f'<rect x="0" y="34" width="{W}" height="52" fill="{s}"/>{txt(W-52 if back else W-22, 66, title, 19, t, "end", "bold")}{b}'

# ---------- 1. LOGIN ----------
inner = f'''
<circle cx="{W//2}" cy="150" r="46" fill="{PRIMARY_CT}"/>
<text x="{W//2}" y="164" font-size="40" text-anchor="middle">🏭</text>
{txt(W//2, 235, "مرحباً بك", 26, TEXT, "middle", "bold")}
{txt(W//2, 262, "سجّل الدخول لإدارة المخزون", 14, OUTLINE, "middle")}
{card(28, 300, W-56, 56, SURFACE, 12, OUTLINE)}
{txt(W-46, 334, "البريد الإلكتروني", 14, OUTLINE)}
<text x="46" y="334" font-size="16" text-anchor="start">✉</text>
{card(28, 372, W-56, 56, SURFACE, 12, OUTLINE)}
{txt(W-46, 406, "••••••••", 16, TEXT)}
<text x="46" y="406" font-size="16" text-anchor="start">🔒</text>
{card(28, 452, W-56, 54, PRIMARY, 14)}
{txt(W//2, 486, "تسجيل الدخول", 17, "#FFFFFF", "middle", "bold")}
{txt(W//2, 545, "admin@warehouse.com / admin123", 12, OUTLINE, "middle")}
'''
open(f"{OUT}/01_login.svg","w").write(frame(inner,"login"))

# ---------- 2. DASHBOARD ----------
def stat(x,y,val,label,color,icon):
    return f'{card(x,y,150,96)}<circle cx="{x+126}" cy="{y+26}" r="16" fill="{color}22"/><text x="{x+126}" y="{y+31}" font-size="15" text-anchor="middle">{icon}</text>{txt(x+134,y+66,val,24,TEXT,"end","bold")}{txt(x+134,y+86,label,12,OUTLINE)}'
def menu(x,y,label,icon):
    return f'{card(x,y,150,104,PRIMARY_CT,18)}<text x="{x+126}" y="{y+42}" font-size="26" text-anchor="middle">{icon}</text>{txt(x+134,y+84,label,15,ON_PRIMARY_CT,"end","bold")}'
inner = topbar("لوحة التحكم") + f'''
{txt(W-22, 82, "admin", 12, OUTLINE)}
{stat(200,100,"24","إجمالي المنتجات",PRIMARY,"▦")}
{stat(30,100,"318","إجمالي الكمية",OK,"#")}
{stat(200,206,"5","مخزون منخفض",ERR,"⚠")}
{stat(30,206,"7","صرف اليوم",AMBER,"◔")}
{menu(200,330,"المنتجات","▦")}
{menu(30,330,"الجرد","▤")}
{menu(200,444,"صرف مخزون","🛒")}
{menu(30,444,"الفئات","🏷")}
{menu(200,558,"سجل الصرف","↺")}
{menu(30,558,"المستخدمون","👥")}
'''
open(f"{OUT}/02_dashboard.svg","w").write(frame(inner,"dashboard"))

# ---------- 3. PRODUCTS ----------
def prow(y,name,cat,qty,low=False):
    qc = ERR if low else PRIMARY
    return f'''{card(20,y,W-40,88)}<rect x="{W-104}" y="{y+12}" width="64" height="64" rx="12" fill="#DCE3EE"/><text x="{W-72}" y="{y+52}" font-size="24" text-anchor="middle">📷</text>
{txt(W-116,y+30,name,16,TEXT,"end","bold")}{txt(W-116,y+52,cat,13,OUTLINE)}{txt(W-116,y+74,"الكمية الحالية: "+qty,13,qc)}
<text x="60" y="{y+50}" font-size="17" text-anchor="middle">✎</text><text x="34" y="{y+50}" font-size="17" text-anchor="middle" fill="{ERR}">🗑</text>'''
inner = topbar("المنتجات") + prow(100,"كاميرا Hikvision 4MP","الكاميرات","42") + prow(200,"قفل ذكي Yale","التحكم بالدخول","3",True) + prow(300,"راوتر Ubiquiti","الشبكات","15") + prow(400,"إنتركوم Fanvil","الإنتركم","28") + f'''
{card(W-190,H-80,168,52,PRIMARY,26)}<text x="{W-160}" y="{H-48}" font-size="22" text-anchor="middle" fill="#fff">＋</text>{txt(W-40,H-48,"إضافة منتج",15,"#fff","end","bold")}'''
open(f"{OUT}/03_products.svg","w").write(frame(inner,"products"))

# ---------- 4. ADD PRODUCT ----------
def field(y,label,val="",h=56):
    return f'{card(24,y,W-48,h,SURFACE,12,OUTLINE)}{txt(W-40,y+22 if val else y+34,label,12 if val else 14,OUTLINE)}' + (txt(W-40,y+44,val,15,TEXT) if val else "")
inner = topbar("إضافة منتج") + f'''
{card(24,100,W-48,150,"#DCE3EE",16)}<text x="{W//2}" y="185" font-size="40" text-anchor="middle">🖼</text>
{card(24,262,152,44,SURFACE,10,OUTLINE)}<text x="{W-60}" y="289" font-size="14" text-anchor="middle">📷 التقاط صورة</text>
{card(204,262,152,44,SURFACE,10,OUTLINE)}<text x="280" y="289" font-size="13" text-anchor="middle">🖼 من المعرض</text>
{field(322,"اسم المنتج","كاميرا Hikvision 4MP")}
{field(390,"الفئة","الكاميرات")}
{field(458,"الكمية","42")}
{field(526,"موقع المخزن","رف A-12")}
{card(24,600,W-48,54,PRIMARY,14)}{txt(W//2,634,"حفظ",17,"#fff","middle","bold")}
'''
open(f"{OUT}/04_add_product.svg","w").write(frame(inner,"add"))

# ---------- 5. INVENTORY ----------
def chip(x,y,label,sel=False):
    fill = PRIMARY if sel else "none"
    tc = "#fff" if sel else TEXT
    wdt = 30 + len(label)*8
    return f'<rect x="{x-wdt}" y="{y}" width="{wdt}" height="32" rx="16" fill="{fill}" stroke="{OUTLINE if not sel else PRIMARY}"/>{txt(x-wdt//2,y+21,label,12,tc,"middle")}'
def icard(x,y,name,cat,qty,low=False):
    c = ERR_CT if low else PRIMARY_CT
    tc = ERR if low else ON_PRIMARY_CT
    return f'''{card(x,y,166,196)}<rect x="{x}" y="{y}" width="166" height="112" rx="16" fill="#DCE3EE"/><text x="{x+83}" y="{y+64}" font-size="30" text-anchor="middle">📷</text>
{txt(x+152,y+138,name,14,TEXT,"end","bold")}{txt(x+152,y+158,cat,12,OUTLINE)}<rect x="{x+92}" y="{y+168}" width="60" height="22" rx="8" fill="{c}"/>{txt(x+146,y+184,qty+" وحدة",11,tc)}'''
inner = topbar("الجرد") + f'''
{card(24,96,W-48,50,SURFACE,12,OUTLINE)}{txt(W-46,127,"ابحث بالاسم أو الفئة",14,OUTLINE)}<text x="46" y="127" font-size="15" text-anchor="start">🔍</text>
{chip(W-24,160,"الكل",True)}{chip(W-70,160,"الكاميرات")}{chip(W-160,160,"الشبكات")}
{icard(200,210,"كاميرا 4MP","الكاميرات","42")}
{icard(24,210,"قفل ذكي","التحكم","3",True)}
{icard(200,418,"راوتر","الشبكات","15")}
{icard(24,418,"إنتركوم","الإنتركم","28")}
'''
open(f"{OUT}/05_inventory.svg","w").write(frame(inner,"inventory"))

# ---------- 6. STOCK OUT ----------
inner = topbar("صرف مخزون") + f'''
{card(24,100,W-48,56,SURFACE,12,OUTLINE)}{txt(W-40,122,"اختر المنتج",12,OUTLINE)}{txt(W-40,144,"كاميرا Hikvision 4MP  (42)",14,TEXT)}<text x="44" y="134" font-size="16" text-anchor="start">▾</text>
{txt(W-24,180,"المتاح: 42",14,PRIMARY,"end","bold")}
{card(24,196,W-48,56,SURFACE,12,OUTLINE)}{txt(W-40,218,"الكمية المصروفة",12,OUTLINE)}{txt(W-40,240,"5",15,TEXT)}
{card(24,264,W-48,56,SURFACE,12,OUTLINE)}{txt(W-40,286,"اسم الموظف",12,OUTLINE)}{txt(W-40,308,"أحمد علي",15,TEXT)}
{card(24,332,W-48,56,SURFACE,12,OUTLINE)}{txt(W-40,354,"التاريخ",12,OUTLINE)}{txt(W-40,376,"2026/07/09",15,TEXT)}<text x="44" y="366" font-size="15" text-anchor="start">📅</text>
{card(24,400,W-48,72,SURFACE,12,OUTLINE)}{txt(W-40,424,"ملاحظات",12,OUTLINE)}
{card(24,500,W-48,54,PRIMARY,14)}{txt(W//2,534,"تأكيد الصرف",17,"#fff","middle","bold")}
{card(70,590,W-140,40,"#20202080",20)}{txt(W//2,616,"تم الصرف بنجاح",13,"#fff","middle")}
'''
open(f"{OUT}/06_stock_out.svg","w").write(frame(inner,"stockout"))

# ---------- 7. CATEGORIES ----------
def crow(y,name):
    return f'{card(20,y,W-40,58)}<text x="{W-40}" y="{y+37}" font-size="17" text-anchor="middle" fill="{PRIMARY}">🏷</text>{txt(W-70,y+37,name,16,TEXT)}<text x="40" y="{y+37}" font-size="16" text-anchor="middle" fill="{ERR}">🗑</text>'
inner = topbar("الفئات")
yy=100
for n in ["الكاميرات","المنزل الذكي","الإنتركم","التحكم بالدخول","الشبكات","الأدوات","الإكسسوارات"]:
    inner += crow(yy,n); yy+=70
inner += f'{card(W-160,H-80,138,52,PRIMARY,26)}<text x="{W-136}" y="{H-48}" font-size="20" text-anchor="middle" fill="#fff">＋</text>{txt(W-40,H-48,"إضافة فئة",14,"#fff","end","bold")}'
open(f"{OUT}/07_categories.svg","w").write(frame(inner,"categories"))

# ---------- 8. DASHBOARD DARK ----------
def statd(x,y,val,label,color,icon):
    return f'{card(x,y,150,96,DARK_SURFACE)}<circle cx="{x+126}" cy="{y+26}" r="16" fill="{color}33"/><text x="{x+126}" y="{y+31}" font-size="15" text-anchor="middle">{icon}</text>{txt(x+134,y+66,val,24,DARK_TEXT,"end","bold")}{txt(x+134,y+86,label,12,OUTLINE)}'
def menud(x,y,label,icon):
    return f'{card(x,y,150,104,"#25406B",18)}<text x="{x+126}" y="{y+42}" font-size="26" text-anchor="middle">{icon}</text>{txt(x+134,y+84,label,15,"#BBD0FF","end","bold")}'
inner = topbar("لوحة التحكم", dark=True) + f'''
{txt(W-22, 82, "admin", 12, OUTLINE)}
{statd(200,100,"24","إجمالي المنتجات",PRIMARY,"▦")}
{statd(30,100,"318","إجمالي الكمية",OK,"#")}
{statd(200,206,"5","مخزون منخفض",ERR,"⚠")}
{statd(30,206,"7","صرف اليوم",AMBER,"◔")}
{menud(200,330,"المنتجات","▦")}
{menud(30,330,"الجرد","▤")}
{menud(200,444,"صرف مخزون","🛒")}
{menud(30,444,"الفئات","🏷")}
'''
open(f"{OUT}/08_dashboard_dark.svg","w").write(frame(inner,"dark",dark=True))

print("Generated mockups:")
for f in sorted(os.listdir(OUT)):
    print("  ", f)
