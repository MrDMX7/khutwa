#!/usr/bin/env python3
"""Generate the Khutwa v3 direction artboards (.dc.html) + canvas.json.

Three genuinely different directions for the Today screen, and the Trends
screen in the leading one. Static artboards — no logic, everything inline so
the canvas editor can restyle any element.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
BINS = json.load(open(os.path.join(HERE, "day.json")))
NOW_BIN = 69                     # 17:15
TOTAL = 4312
raw = BINS[:NOW_BIN] + [0] * (96 - NOW_BIN)
k = TOTAL / sum(raw)
BINS = [round(b * k) for b in raw]
PEAK = max(BINS)

W = 390
H = 844

HEAD = """<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Jost:wght@300;400&family=Tajawal:wght@400;500&family=IBM+Plex+Sans+Arabic:wght@400;500&family=JetBrains+Mono:wght@300;400&family=Readex+Pro:wght@300;400;500&display=swap">
  <style>
    body { margin: 0; }
    a { color: %(link)s; } a:hover { color: %(linkh)s; }
  </style>
</helmet>
"""
TAIL = "</x-dc>\n</body>\n</html>\n"


def icon(name, color, size=22, sw=1.5):
    d = {
        "today": '<path d="M12 3v4M12 17v4M3 12h4M17 12h4"/><circle cx="12" cy="12" r="4.5"/>',
        "session": '<path d="M13 4a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM7 21l3-7 3 2v5M10 14l-2-4 4-2 3 3 3-1"/>',
        "trends": '<path d="M4 19h16M6 15l4-5 3 3 5-7"/>',
        "more": '<circle cx="6" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="18" cy="12" r="1.2"/>',
        "streak": '<path d="M12 3c1 3 4 5 4 9a4 4 0 0 1-8 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-5 0-8z"/>',
        "chev": '<path d="M9 6l6 6-6 6"/>',
    }[name]
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" '
            f'stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{d}</svg>')


def timeline_svg(width, height, color, dim, future, marker, radius=1, gap=1.2, hours_color=None, hours_size=10, font="Jost"):
    """96 bars, one per quarter hour. Past bars in `color`, future in `future`."""
    n = 96
    bw = (width - gap * (n - 1)) / n
    parts = []
    base = height - (14 if hours_color else 0)
    for i, b in enumerate(BINS):
        x = i * (bw + gap)
        if i >= NOW_BIN:
            parts.append(f'<rect x="{x:.2f}" y="{base-2}" width="{bw:.2f}" height="2" rx="{radius}" fill="{future}"/>')
            continue
        h = max(2, base * (b / PEAK)) if b else 2
        fill = color if b else dim
        parts.append(f'<rect x="{x:.2f}" y="{base-h:.2f}" width="{bw:.2f}" height="{h:.2f}" rx="{radius}" fill="{fill}"/>')
    mx = NOW_BIN * (bw + gap)
    parts.append(f'<line x1="{mx:.2f}" y1="0" x2="{mx:.2f}" y2="{base}" stroke="{marker}" stroke-width="1" stroke-dasharray="2 3"/>')
    if hours_color:
        for hr in (6, 12, 18):
            x = hr * 4 * (bw + gap)
            parts.append(f'<text x="{x:.2f}" y="{height-1}" font-family="{font}, sans-serif" font-size="{hours_size}" fill="{hours_color}" text-anchor="middle">{hr:02d}</text>')
    return (f'<svg width="{width}" height="{height}" viewBox="0 0 {width} {height}" style="display:block;direction:ltr">'
            + "".join(parts) + '</svg>')


def nav(items, active, style):
    """items: [(key,label)] ; style: dict with keys ink, muted, bg, font, weight, accent, top"""
    cells = []
    for key, label in items:
        on = key == active
        c = style["accent"] if on else style["muted"]
        cells.append(
            f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px;flex:1;padding:6px 0;min-height:44px;justify-content:center">'
            f'{icon(key, c, 22, 1.5)}'
            f'<span style="font-family:{style["font"]};font-size:11.5px;font-weight:{500 if on else 400};color:{c}">{label}</span></div>')
    return (f'<div style="display:flex;flex-direction:row;align-items:stretch;padding:8px 12px 18px;'
            f'background:{style["bg"]};border-top:1px solid {style["top"]}">' + "".join(cells) + '</div>')


# ─────────────────────────────────────────────────────────────────────────────
# Direction A — «الخط»: light paper, one accent, the day as a line. Jost + Tajawal.
# ─────────────────────────────────────────────────────────────────────────────
A = dict(ground="#FCFAF9", surface="#FFFFFF", ink="#241A1F", ink2="#5C4E55", muted="#8E7A83", faint="#B8A5AD",
         wine="#8E2547", deep="#4A1226", tint="#F6E9EE", rule="#EFE2E6", rule2="#F4EAEE")
AR = "'Tajawal', 'Segoe UI', system-ui, sans-serif"
NUM = "'Jost', 'Century Gothic', system-ui, sans-serif"


def label(text, color, font=AR, size=11.5, extra=""):
    return f'<span style="font-family:{font};font-size:{size}px;font-weight:500;letter-spacing:.02em;color:{color};{extra}">{text}</span>'


def a_metric(value, unit, name, last=False):
    border = "" if last else f"border-left:1px solid {A['rule']};"
    return (f'<div style="display:flex;flex-direction:column;gap:2px;flex:1;padding:0 12px;{border}">'
            f'<div style="display:flex;flex-direction:row;align-items:baseline;gap:4px;direction:ltr;justify-content:flex-end">'
            f'<span style="font-family:{NUM};font-size:24px;font-weight:300;color:{A["ink"]};letter-spacing:-.01em">{value}</span>'
            f'<span style="font-family:{AR};font-size:11px;color:{A["muted"]}">{unit}</span></div>'
            f'<span style="font-family:{AR};font-size:11.5px;color:{A["muted"]}">{name}</span></div>')


def direction_a():
    dist_segments = [("#8E2547", 2610, "مشي"), ("#4A1226", 980, "مشي سريع"), ("#C9A6B2", 434, "متفرّقة"), ("#E6DADF", 288, "غير مصنّف")]
    tot = sum(s[1] for s in dist_segments)
    bar = "".join(f'<div style="flex:{s[1]};background:{s[0]};height:6px"></div>' for s in dist_segments)
    legend = "".join(
        f'<div style="display:flex;flex-direction:row;align-items:center;gap:6px">'
        f'<span style="width:8px;height:8px;border-radius:50%;background:{c};display:block"></span>'
        f'<span style="font-family:{AR};font-size:12px;color:{A["ink2"]}">{n}</span>'
        f'<span style="font-family:{NUM};font-size:12px;color:{A["muted"]};direction:ltr">{v:,}</span></div>'
        for c, v, n in dist_segments)
    body = f"""
<div style="width:{W}px;height:{H}px;background:{A['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden">
  <div style="display:flex;flex-direction:column;gap:0;padding:56px 24px 0;flex:1">
    <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center">
      {label("الأحد ٦ سبتمبر", A['muted'])}
      <div style="display:flex;flex-direction:row;align-items:center;gap:6px">{icon("streak", A['wine'], 16, 1.6)}{label("٦ أيام متتالية", A['wine'])}</div>
    </div>
    <div style="display:flex;flex-direction:column;align-items:flex-start;gap:0;margin-top:34px">
      <span style="font-family:{NUM};font-size:88px;font-weight:300;line-height:.95;letter-spacing:-.03em;color:{A['ink']};direction:ltr">4,312</span>
      <div style="display:flex;flex-direction:row;align-items:baseline;gap:8px;margin-top:10px">
        <span style="font-family:{AR};font-size:14px;color:{A['ink2']}">من 7,000 خطوة</span>
        <span style="font-family:{NUM};font-size:14px;color:{A['wine']};direction:ltr">62%</span>
      </div>
    </div>
    <div style="height:2px;background:{A['rule']};margin-top:22px;display:flex;flex-direction:row;direction:ltr">
      <div style="width:62%;height:2px;background:{A['wine']}"></div>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;margin-top:30px">
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:baseline">
        {label("متى مشيت اليوم", A['ink2'], size=12.5)}
        <span style="font-family:{AR};font-size:11.5px;color:{A['muted']}">ذروة 112 خطوة/د</span>
      </div>
      {timeline_svg(W-48, 72, A['wine'], A['rule2'], A['rule2'], A['faint'], hours_color=A['faint'])}
    </div>
    <div style="display:flex;flex-direction:row;margin:30px -12px 0">
      {a_metric("3.1", "كم", "مسافة")}{a_metric("142", "سعرة", "نشاط")}{a_metric("28", "د", "نشاط معتدل")}{a_metric("1.5", "طابق", "صعود", last=True)}
    </div>
    <div style="display:flex;flex-direction:column;gap:12px;margin-top:34px;padding-top:22px;border-top:1px solid {A['rule']}">
      {label("توزيع الخطوات", A['ink2'], size=12.5)}
      <div style="display:flex;flex-direction:row;gap:2px;border-radius:3px;overflow:hidden;direction:ltr">{bar}</div>
      <div style="display:flex;flex-direction:row;flex-wrap:wrap;gap:8px 18px">{legend}</div>
    </div>
    <div style="display:flex;flex-direction:row;align-items:center;justify-content:space-between;margin-top:auto;padding:16px 0 18px;border-top:1px solid {A['rule']}">
      <span style="font-family:{AR};font-size:13px;color:{A['ink2']}">لماذا 7,000 وليس 10,000؟ <span style="color:{A['muted']}">— رقم 10,000 إعلان ياباني من 1965</span></span>
      <span style="transform:scaleX(-1);display:block">{icon("chev", A['wine'], 18, 1.6)}</span>
    </div>
  </div>
  {nav([("today","اليوم"),("session","جلسات"),("trends","اتجاهات"),("more","المزيد")], "today",
       dict(accent=A['wine'], muted=A['muted'], bg=A['ground'], font=AR, top=A['rule']))}
</div>
"""
    return HEAD % dict(link=A["wine"], linkh=A["deep"]) + body + TAIL


def direction_a_trends():
    days = [("س", 3127, False), ("أ", 6384, False), ("ن", 7830, True), ("ث", 7412, True), ("ر", 2210, False), ("خ", 13019, True), ("ج", 9102, True), ("س", 6380, False), ("أ", 7120, True), ("ن", 4312, False)]
    days = days[-7:]
    mx = 13019
    cols = "".join(
        f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;flex:1">'
        f'<div style="display:flex;flex-direction:column;justify-content:flex-end;height:120px;width:100%">'
        f'<div style="height:{max(3, 120*v/mx):.0f}px;background:{A["wine"] if met else A["faint"]};border-radius:2px 2px 0 0;margin:0 9px"></div></div>'
        f'<span style="font-family:{AR};font-size:12px;color:{A["ink2"] if met else A["muted"]}">{d}</span></div>'
        for d, v, met in days)
    # 24h activity profile: when you usually walk
    prof = [0, 0, 0, 0, 0, 1, 6, 8, 5, 2, 2, 3, 7, 5, 2, 1, 2, 6, 9, 7, 3, 2, 1, 0]
    pts = " ".join(f"{i*(W-48)/23:.1f},{56-52*p/9:.1f}" for i, p in enumerate(prof))
    profile = (f'<svg width="{W-48}" height="70" viewBox="0 0 {W-48} 70" style="display:block;direction:ltr">'
               f'<polyline points="{pts}" fill="none" stroke="{A["wine"]}" stroke-width="1.5" stroke-linejoin="round"/>'
               f'<polygon points="0,56 {pts} {W-48},56" fill="{A["tint"]}"/>'
               + "".join(f'<text x="{h*(W-48)/23:.1f}" y="69" font-family="Jost" font-size="10" fill="{A["faint"]}" text-anchor="middle">{h:02d}</text>' for h in (6, 12, 18))
               + '</svg>')
    seg = "".join(
        f'<span style="font-family:{AR};font-size:12.5px;padding:8px 14px;border-radius:100px;min-width:44px;text-align:center;'
        f'background:{A["ink"] if on else "transparent"};color:{A["ground"] if on else A["muted"]}">{t}</span>'
        for t, on in (("أسبوع", True), ("شهر", False), ("سنة", False)))
    body = f"""
<div style="width:{W}px;height:{H}px;background:{A['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden">
  <div style="display:flex;flex-direction:column;padding:56px 24px 0;flex:1;gap:0">
    <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center">
      <span style="font-family:{AR};font-size:22px;font-weight:500;color:{A['ink']}">اتجاهات</span>
      <div style="display:flex;flex-direction:row;gap:2px;background:{A['tint']};border-radius:100px;padding:3px">{seg}</div>
    </div>
    <div style="display:flex;flex-direction:row;margin:28px -12px 0">
      {a_metric("6,384", "", "متوسط يومي")}{a_metric("13,019", "", "أفضل يوم")}{a_metric("4/7", "", "حققت الهدف", last=True)}
    </div>
    <div style="display:flex;flex-direction:column;gap:14px;margin-top:30px">
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:baseline">
        {label("هذا الأسبوع", A['ink2'], size=12.5)}
        <span style="font-family:{AR};font-size:11.5px;color:{A['muted']}">الخط = الهدف 7,000</span>
      </div>
      <div style="position:relative">
        <div style="position:absolute;left:0;right:0;top:{120-120*7000/mx:.0f}px;border-top:1px dashed {A['faint']}"></div>
        <div style="display:flex;flex-direction:row;direction:rtl">{cols}</div>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;margin-top:30px;padding-top:22px;border-top:1px solid {A['rule']}">
      {label("متى تكون نشيطاً عادة", A['ink2'], size=12.5)}
      {profile}
      <span style="font-family:{AR};font-size:12.5px;color:{A['muted']};line-height:1.7">ذروتان: بعد الفجر وحول الساعة السادسة مساءً. الأيام التي تفوّت فيها مسيرة المساء تنتهي تحت الهدف.</span>
    </div>
    <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center;margin-top:auto;padding:18px 0;border-top:1px solid {A['rule']}">
      <div style="display:flex;flex-direction:column;gap:2px">
        {label("أطول سلسلة", A['muted'])}
        <span style="font-family:{NUM};font-size:24px;font-weight:300;color:{A['ink']};direction:ltr">11 <span style="font-family:{AR};font-size:12px;color:{A['muted']}">يوماً</span></span>
      </div>
      <div style="display:flex;flex-direction:column;gap:2px;align-items:flex-end">
        {label("دقائق النشاط الأسبوعية", A['muted'])}
        <span style="font-family:{NUM};font-size:24px;font-weight:300;color:{A['ink']};direction:ltr">63<span style="color:{A['faint']}"> / 150</span></span>
      </div>
    </div>
  </div>
  {nav([("today","اليوم"),("session","جلسات"),("trends","اتجاهات"),("more","المزيد")], "trends",
       dict(accent=A['wine'], muted=A['muted'], bg=A['ground'], font=AR, top=A['rule']))}
</div>
"""
    return HEAD % dict(link=A["wine"], linkh=A["deep"]) + body + TAIL


# ─────────────────────────────────────────────────────────────────────────────
# Direction B — «العدّاد»: warm black instrument, mono numerals, one amber accent, dense.
# ─────────────────────────────────────────────────────────────────────────────
B = dict(ground="#0F0C0D", surface="#171315", ink="#F3ECEE", ink2="#B9AEB2", muted="#7E7377", faint="#4A4145",
         accent="#E3A85C", accent2="#9C6F3A", rule="#241F21")
BAR = "'IBM Plex Sans Arabic', 'Tajawal', system-ui, sans-serif"
MONO = "'JetBrains Mono', 'SF Mono', Menlo, monospace"


def b_cell(name, value, unit):
    return (f'<div style="display:flex;flex-direction:column;gap:6px;padding:14px 16px;border-top:1px solid {B["rule"]}">'
            f'<span style="font-family:{BAR};font-size:11px;color:{B["muted"]};letter-spacing:.04em">{name}</span>'
            f'<div style="display:flex;flex-direction:row;align-items:baseline;gap:5px;direction:ltr;justify-content:flex-end">'
            f'<span style="font-family:{MONO};font-size:22px;font-weight:300;color:{B["ink"]}">{value}</span>'
            f'<span style="font-family:{MONO};font-size:11px;color:{B["muted"]}">{unit}</span></div></div>')


def direction_b():
    body = f"""
<div style="width:{W}px;height:{H}px;background:{B['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden">
  <div style="display:flex;flex-direction:column;padding:54px 20px 0;flex:1">
    <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center">
      <span style="font-family:{MONO};font-size:11px;color:{B['muted']};letter-spacing:.08em;direction:ltr">SUN 06 SEP · 17:15</span>
      <div style="display:flex;flex-direction:row;align-items:center;gap:6px">
        <span style="width:6px;height:6px;border-radius:50%;background:{B['accent']};display:block"></span>
        <span style="font-family:{BAR};font-size:11.5px;color:{B['ink2']}">يعدّ · دقيق</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;align-items:flex-start;margin-top:36px;gap:6px">
      <span style="font-family:{MONO};font-size:76px;font-weight:300;line-height:1;letter-spacing:-.04em;color:{B['ink']};direction:ltr">04312</span>
      <div style="display:flex;flex-direction:row;gap:14px;align-items:baseline">
        <span style="font-family:{MONO};font-size:12px;color:{B['accent']};direction:ltr">62%</span>
        <span style="font-family:{BAR};font-size:12.5px;color:{B['ink2']}">من 7,000</span>
        <span style="font-family:{BAR};font-size:12.5px;color:{B['muted']}">· سلسلة 6 أيام</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:row;gap:3px;direction:ltr;margin-top:18px">{"".join(f'<div style="flex:1;height:3px;background:{B["accent"] if i < 25 else B["rule"]}"></div>' for i in range(40))}</div>
    <div style="margin-top:28px;display:flex;flex-direction:column;gap:8px">
      <div style="display:flex;flex-direction:row;justify-content:space-between">
        <span style="font-family:{BAR};font-size:11px;color:{B['muted']};letter-spacing:.04em">التوزيع الزمني · 24 س</span>
        <span style="font-family:{MONO};font-size:11px;color:{B['muted']};direction:ltr">PEAK 112 spm</span>
      </div>
      {timeline_svg(W-40, 64, B['accent'], B['rule'], B['rule'], B['muted'], radius=0, gap=1, hours_color=B['faint'], font="JetBrains Mono")}
    </div>
    <div style="display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:0 16px;margin-top:22px">
      {b_cell("مسافة", "3.10", "km")}{b_cell("سعرات نشاط", "142", "kcal")}{b_cell("نشاط معتدل", "28", "min")}{b_cell("صعود", "1.5", "fl")}
    </div>
    <div style="display:flex;flex-direction:column;gap:8px;margin-top:18px;padding-top:14px;border-top:1px solid {B['rule']}">
      <div style="display:flex;flex-direction:row;justify-content:space-between">
        <span style="font-family:{BAR};font-size:11px;color:{B['muted']};letter-spacing:.04em">التصنيف</span>
        <span style="font-family:{MONO};font-size:11px;color:{B['muted']};direction:ltr">2610 · 980 · 434 · 288</span>
      </div>
      <div style="display:flex;flex-direction:row;gap:2px;direction:ltr">
        <div style="flex:2610;height:4px;background:{B['accent']}"></div><div style="flex:980;height:4px;background:{B['accent2']}"></div>
        <div style="flex:434;height:4px;background:{B['faint']}"></div><div style="flex:288;height:4px;background:{B['rule']}"></div>
      </div>
      <div style="display:flex;flex-direction:row;gap:14px">
        <span style="font-family:{BAR};font-size:11.5px;color:{B['ink2']}">مشي</span><span style="font-family:{BAR};font-size:11.5px;color:{B['ink2']}">سريع</span>
        <span style="font-family:{BAR};font-size:11.5px;color:{B['muted']}">متفرّقة</span><span style="font-family:{BAR};font-size:11.5px;color:{B['muted']}">غير مصنّف</span>
      </div>
    </div>
  </div>
  {nav([("today","اليوم"),("session","جلسات"),("trends","اتجاهات"),("more","المزيد")], "today",
       dict(accent=B['accent'], muted=B['muted'], bg=B['ground'], font=BAR, top=B['rule']))}
</div>
"""
    return HEAD % dict(link=B["accent"], linkh=B["accent2"]) + body + TAIL


# ─────────────────────────────────────────────────────────────────────────────
# Direction C — «الحقل»: light, a tinted field with floating layered cards crossing its edge.
# ─────────────────────────────────────────────────────────────────────────────
C = dict(ground="#FBF8F7", field="#F3E4EA", card="#FFFFFF", ink="#2A1E23", ink2="#66565D", muted="#9A868E",
         wine="#8E2547", plum="#5E2138", rose="#D9A7B8", rule="#F0E4E8",
         shadow="0 2px 4px rgba(94,33,56,.05), 0 22px 44px -18px rgba(94,33,56,.22)")
RX = "'Readex Pro', 'Tajawal', system-ui, sans-serif"


def c_card(title, value, unit, sub, w="1"):
    return (f'<div style="flex:{w};display:flex;flex-direction:column;gap:6px;background:{C["card"]};border-radius:22px;padding:18px 18px 16px;box-shadow:{C["shadow"]}">'
            f'<span style="font-family:{RX};font-size:12px;color:{C["muted"]}">{title}</span>'
            f'<div style="display:flex;flex-direction:row;align-items:baseline;gap:5px;direction:ltr;justify-content:flex-end">'
            f'<span style="font-family:{NUM};font-size:28px;font-weight:300;color:{C["ink"]}">{value}</span>'
            f'<span style="font-family:{RX};font-size:12px;color:{C["muted"]}">{unit}</span></div>'
            f'<span style="font-family:{RX};font-size:11.5px;color:{C["ink2"]}">{sub}</span></div>')


def direction_c():
    body = f"""
<div style="width:{W}px;height:{H}px;background:{C['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden;position:relative">
  <div style="position:absolute;inset:0 0 auto 0;height:400px;background:{C['field']};border-radius:0 0 40px 40px;overflow:hidden">{contour_svg(W, 400)}</div>
  <div style="position:relative;display:flex;flex-direction:column;padding:56px 22px 0;flex:1">
    <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center">
      <div style="display:flex;flex-direction:column;gap:2px">
        <span style="font-family:{RX};font-size:20px;font-weight:500;color:{C['ink']}">مساء الخير</span>
        <span style="font-family:{RX};font-size:12.5px;color:{C['ink2']}">الأحد ٦ سبتمبر · سلسلة ٦ أيام</span>
      </div>
      {mark_svg(34, "t")}
    </div>
    <div style="display:flex;flex-direction:row;align-items:flex-end;justify-content:space-between;margin-top:40px">
      <div style="display:flex;flex-direction:column;gap:6px">
        <span style="font-family:{NUM};font-size:78px;font-weight:300;line-height:.95;letter-spacing:-.03em;color:{C['plum']};direction:ltr">4,312</span>
        <span style="font-family:{RX};font-size:13px;color:{C['ink2']}">خطوة · بقي 2,688 للهدف</span>
      </div>
      <div style="display:flex;flex-direction:column;align-items:center;gap:4px">
        <svg width="72" height="72" viewBox="0 0 72 72" style="display:block;transform:rotate(-90deg)"><circle cx="36" cy="36" r="31" fill="none" stroke="{C['card']}" stroke-width="6"/><circle cx="36" cy="36" r="31" fill="none" stroke="{C['wine']}" stroke-width="6" stroke-linecap="round" stroke-dasharray="{2*3.1416*31*.62:.1f} 999"/></svg>
        <span style="font-family:{NUM};font-size:12px;color:{C['wine']};direction:ltr;margin-top:-46px">62%</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:12px;margin-top:38px;background:{C['card']};border-radius:24px;padding:18px 20px 14px;box-shadow:{C['shadow']}">
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:baseline">
        <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">يومك حتى الآن</span>
        <span style="font-family:{RX};font-size:11.5px;color:{C['muted']}">ذروة 112 خطوة/د</span>
      </div>
      {timeline_svg(W-84, 64, C['wine'], C['rule'], C['rule'], C['rose'], radius=2, gap=1.5, hours_color=C['muted'])}
    </div>
    <div style="display:flex;flex-direction:row;gap:12px;margin-top:14px">
      {c_card("مسافة", "3.1", "كم", "طول خطوتك 72 سم")}{c_card("سعرات نشاط", "142", "سعرة", "بمعادلة ACSM")}
    </div>
    <div style="display:flex;flex-direction:row;gap:12px;margin-top:12px">
      {c_card("نشاط معتدل", "28", "د", "63 / 150 هذا الأسبوع")}{c_card("مشي سريع", "980", "خطوة", "23% من اليوم")}
    </div>
    <div style="display:flex;flex-direction:row;align-items:center;gap:12px;margin-top:auto;padding:14px 0 16px">
      <div style="width:36px;height:36px;border-radius:12px;background:{C['field']};display:flex;align-items:center;justify-content:center">{icon("more", C['wine'], 18, 1.8)}</div>
      <span style="font-family:{RX};font-size:12.5px;color:{C['ink2']};flex:1">7,000 هدف مبني على تحليل 57 دراسة، لا على إعلان.</span>
      <span style="transform:scaleX(-1);display:block">{icon("chev", C['muted'], 18, 1.6)}</span>
    </div>
  </div>
  {nav([("today","اليوم"),("session","جلسات"),("trends","اتجاهات"),("more","المزيد")], "today",
       dict(accent=C['wine'], muted=C['muted'], bg=C['card'], font=RX, top=C['rule']))}
</div>
"""
    return HEAD % dict(link=C["wine"], linkh=C["plum"]) + body + TAIL


# ─────────────────────────────────────────────────────────────────────────────
# Brand pieces for C: the Khutwa mark (the day-line in a tile), a contour texture
# for the field, and single-colour line illustrations.
# ─────────────────────────────────────────────────────────────────────────────
def mark_svg(size, uid="m", radius_pct=24, bg=True):
    """Rounded tile, wine gradient, the Khutwa walker: head + torso + stride, five strokes."""
    r = 100 * radius_pct / 100
    tile = (f'<defs><linearGradient id="g{uid}" x1="0" y1="1" x2="1" y2="0">'
            f'<stop offset="0%" stop-color="#5E2138"/><stop offset="100%" stop-color="#9C3352"/></linearGradient></defs>'
            + (f'<rect width="100" height="100" rx="{r}" fill="url(#g{uid})"/>' if bg else ""))
    ink = "#FBF8F7" if bg else "#8E2547"
    st = f'stroke="{ink}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round" fill="none"'
    walker = (f'<circle cx="54" cy="24" r="7" fill="{ink}"/>'
              f'<path d="M50 36 L46 58" {st}/>'
              f'<path d="M46 58 L60 74 L58 80" {st}/>'
              f'<path d="M46 58 L36 70 L28 76" {st}/>'
              f'<path d="M50 42 L64 50" {st}/>'
              f'<path d="M50 42 L38 52" {st}/>')
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 100 100" style="display:block;flex:none">'
            f'{tile}{walker}</svg>')


def contour_svg(width, height, color="#8E2547", opacity=.07):
    """Elevation-line texture: nested smooth loops, offset to the top-left."""
    loops = []
    for i in range(1, 8):
        k = i * 26
        loops.append(
            f'<path d="M {-40+k*0.2} {60+k*0.9} C {60+k*0.4} {-20-k*0.5}, {230+k*0.8} {-10-k*0.4}, {300+k} {70+k*0.6} '
            f'S {160+k*0.9} {210+k*1.1}, {-40+k*0.2} {60+k*0.9} Z" fill="none" stroke="{color}" stroke-width="1"/>')
    return (f'<svg width="{width}" height="{height}" viewBox="0 0 {width} {height}" '
            f'style="position:absolute;inset:0;display:block;opacity:{opacity};direction:ltr" preserveAspectRatio="none">'
            + "".join(loops) + '</svg>')


def illo_path(width=200, height=120, color="#8E2547", light="#D9A7B8"):
    """A walking path rising across seven day-marks; the seventh carries the flag."""
    pts = [(10, 100), (40, 92), (70, 80), (100, 74), (130, 56), (158, 50), (184, 36)]
    d = "M " + " L ".join(f"{x} {y}" for x, y in pts)
    dots = "".join(f'<circle cx="{x}" cy="{y}" r="4.5" fill="{"#FFFFFF"}" stroke="{color}" stroke-width="1.6"/>' for x, y in pts[:-1])
    last = pts[-1]
    flag = (f'<circle cx="{last[0]}" cy="{last[1]}" r="6" fill="{color}"/>'
            f'<path d="M {last[0]} {last[1]-6} v-22 M {last[0]} {last[1]-28} h14 l-4 5 4 5 h-14" fill="{light}" stroke="{color}" stroke-width="1.6" stroke-linejoin="round"/>')
    ground = f'<path d="M 0 112 Q 60 104 110 110 T 200 106" fill="none" stroke="{light}" stroke-width="1.4" stroke-dasharray="3 5"/>'
    return (f'<svg width="{width}" height="{height}" viewBox="0 0 200 120" style="display:block;direction:ltr">'
            f'{ground}<path d="{d}" fill="none" stroke="{color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'
            f'{dots}{flag}</svg>')


def illo_empty(width=200, height=110, color="#8E2547", light="#D9A7B8"):
    """Empty state for sessions: a loop of path with a start pin and no track yet."""
    return (f'<svg width="{width}" height="{height}" viewBox="0 0 200 110" style="display:block;direction:ltr">'
            f'<path d="M 30 80 C 40 30, 120 20, 150 50 S 120 100, 80 90" fill="none" stroke="{light}" stroke-width="1.8" stroke-dasharray="4 6" stroke-linecap="round"/>'
            f'<path d="M 30 80 m -9 -12 a 9 9 0 1 1 18 0 c 0 7 -9 16 -9 16 s -9 -9 -9 -16 z" fill="#FFFFFF" stroke="{color}" stroke-width="1.6"/>'
            f'<circle cx="30" cy="68" r="2.4" fill="{color}"/>'
            f'<path d="M 150 50 l 6 -3 -1 7" fill="none" stroke="{color}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>')


def c_head(title, sub=None, right=None):
    r = right or mark_svg(30, "h")
    return (f'<div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center">'
            f'<div style="display:flex;flex-direction:column;gap:2px">'
            f'<span style="font-family:{RX};font-size:20px;font-weight:500;color:{C["ink"]}">{title}</span>'
            + (f'<span style="font-family:{RX};font-size:12.5px;color:{C["ink2"]}">{sub}</span>' if sub else "")
            + f'</div>{r}</div>')


def c_frame(inner, active, field_h=400):
    return f"""
<div style="width:{W}px;height:{H}px;background:{C['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden;position:relative">
  <div style="position:absolute;inset:0 0 auto 0;height:{field_h}px;background:{C['field']};border-radius:0 0 40px 40px;overflow:hidden">{contour_svg(W, field_h)}</div>
  <div style="position:relative;display:flex;flex-direction:column;padding:56px 22px 0;flex:1">
{inner}
  </div>
  {nav([("today","اليوم"),("session","جلسات"),("trends","اتجاهات"),("more","المزيد")], active,
       dict(accent=C['wine'], muted=C['muted'], bg=C['card'], font=RX, top=C['rule']))}
</div>
"""


def c_wrap(body):
    return HEAD % dict(link=C["wine"], linkh=C["plum"]) + body + TAIL


def c_seg(items):
    return ('<div style="display:flex;flex-direction:row;gap:2px;background:' + C["card"] + ';border-radius:100px;padding:3px;box-shadow:' + C["shadow"] + '">'
            + "".join(f'<span style="font-family:{RX};font-size:12.5px;padding:8px 14px;border-radius:100px;min-width:44px;text-align:center;'
                      f'background:{C["plum"] if on else "transparent"};color:{C["card"] if on else C["muted"]}">{t}</span>' for t, on in items)
            + '</div>')


def c_trends():
    days = [("ن", 7830, True), ("ث", 7412, True), ("ر", 2210, False), ("خ", 13019, True), ("ج", 9102, True), ("س", 6380, False), ("أ", 4312, False)]
    mx = 13019
    cols = "".join(
        f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;flex:1">'
        f'<div style="display:flex;flex-direction:column;justify-content:flex-end;height:110px;width:100%">'
        f'<div style="height:{max(4, 110*v/mx):.0f}px;background:{C["wine"] if met else C["rose"]};border-radius:6px;margin:0 7px"></div></div>'
        f'<span style="font-family:{RX};font-size:12px;color:{C["ink2"] if met else C["muted"]}">{d}</span></div>'
        for d, v, met in days)
    prof = [0, 0, 0, 0, 0, 1, 6, 8, 5, 2, 2, 3, 7, 5, 2, 1, 2, 6, 9, 7, 3, 2, 1, 0]
    pw = W - 84
    pts = " ".join(f"{i*pw/23:.1f},{56-52*p/9:.1f}" for i, p in enumerate(prof))
    profile = (f'<svg width="{pw}" height="70" viewBox="0 0 {pw} 70" style="display:block;direction:ltr">'
               f'<polygon points="0,56 {pts} {pw},56" fill="{C["field"]}"/>'
               f'<polyline points="{pts}" fill="none" stroke="{C["wine"]}" stroke-width="1.5" stroke-linejoin="round"/>'
               + "".join(f'<text x="{h*pw/23:.1f}" y="69" font-family="Jost" font-size="10" fill="{C["muted"]}" text-anchor="middle">{h:02d}</text>' for h in (6, 12, 18))
               + '</svg>')
    inner = f"""
    {c_head("اتجاهات", "آخر 7 أيام", c_seg((("أسبوع", True), ("شهر", False), ("سنة", False))))}
    <div style="display:flex;flex-direction:row;align-items:flex-end;gap:18px;margin-top:34px">
      <div style="display:flex;flex-direction:column;gap:4px">
        <span style="font-family:{RX};font-size:12.5px;color:{C['ink2']}">متوسط يومي</span>
        <span style="font-family:{NUM};font-size:56px;font-weight:300;line-height:1;letter-spacing:-.03em;color:{C['plum']};direction:ltr">6,384</span>
      </div>
      <div style="display:flex;flex-direction:column;gap:2px;padding-bottom:6px">
        <span style="font-family:{RX};font-size:12px;color:{C['ink2']}">حققت الهدف <span style="font-family:{NUM};color:{C['wine']}">4</span> من 7</span>
        <span style="font-family:{RX};font-size:12px;color:{C['muted']}">أفضل يوم <span style="font-family:{NUM};direction:ltr">13,019</span></span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:12px;margin-top:34px;background:{C['card']};border-radius:24px;padding:18px 16px 14px;box-shadow:{C['shadow']}">
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:baseline;padding:0 4px">
        <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">هذا الأسبوع</span>
        <span style="font-family:{RX};font-size:11.5px;color:{C['muted']}">الخط المنقّط = 7,000</span>
      </div>
      <div style="position:relative">
        <div style="position:absolute;left:4px;right:4px;top:{110-110*7000/mx:.0f}px;border-top:1px dashed {C['rose']}"></div>
        <div style="display:flex;flex-direction:row;direction:rtl">{cols}</div>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;margin-top:14px;background:{C['card']};border-radius:24px;padding:18px 20px 12px;box-shadow:{C['shadow']}">
      <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">متى تكون نشيطاً عادة</span>
      {profile}
      <span style="font-family:{RX};font-size:12px;color:{C['ink2']};line-height:1.7">ذروتان: بعد الفجر وحول السادسة مساءً. الأيام التي تفوّت فيها مسيرة المساء تنتهي تحت الهدف.</span>
    </div>
    <div style="display:flex;flex-direction:row;gap:12px;margin-top:14px;margin-bottom:16px">
      {c_card("أطول سلسلة", "11", "يوماً", "الحالية 6 أيام")}{c_card("دقائق النشاط", "63", "/ 150", "توصية منظمة الصحة")}
    </div>
"""
    return c_wrap(c_frame(inner, "trends"))


def c_route(w, h, color):
    pts = "4,42 18,30 30,34 44,18 60,22 72,8 86,14 98,6"
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 102 48" style="display:block;direction:ltr">'
            f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<circle cx="4" cy="42" r="2.5" fill="{color}"/><circle cx="98" cy="6" r="2.5" fill="{C["card"]}" stroke="{color}" stroke-width="1.5"/></svg>')


def c_session_row(date, km, dur, pace, last=False):
    border = "" if last else f"border-bottom:1px solid {C['rule']};"
    return (f'<div style="display:flex;flex-direction:row;align-items:center;gap:14px;padding:14px 0;{border}">'
            f'{c_route(64, 32, C["wine"])}'
            f'<div style="display:flex;flex-direction:column;gap:3px;flex:1">'
            f'<span style="font-family:{RX};font-size:13.5px;font-weight:500;color:{C["ink"]}">{date}</span>'
            f'<span style="font-family:{RX};font-size:12px;color:{C["muted"]}">{dur} · {pace} د/كم</span></div>'
            f'<span style="font-family:{NUM};font-size:22px;font-weight:300;color:{C["plum"]};direction:ltr">{km}<span style="font-family:{RX};font-size:11px;color:{C["muted"]}"> كم</span></span></div>')


def c_sessions():
    chips = "".join(
        f'<span style="font-family:{RX};font-size:12.5px;padding:10px 16px;border-radius:100px;min-width:44px;text-align:center;'
        f'background:{C["plum"] if on else C["field"]};color:{C["card"] if on else C["ink2"]}">{t}</span>'
        for t, on in (("بدون", False), ("5 د", True), ("10 د", False)))
    inner = f"""
    {c_head("جلسات", "تسجيل مسار مع إرشاد صوتي")}
    <div style="display:flex;flex-direction:column;gap:18px;margin-top:34px;background:{C['card']};border-radius:26px;padding:22px 20px 20px;box-shadow:{C['shadow']}">
      <div style="display:flex;flex-direction:column;gap:8px">
        <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">الإحماء</span>
        <div style="display:flex;flex-direction:row;gap:8px">{chips}</div>
        <span style="font-family:{RX};font-size:12px;color:{C['muted']};line-height:1.7">مشي سريع أو هرولة خفيفة. الإطالة الساكنة قبل الجهد تُضعف القوة 4–7٪.</span>
      </div>
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:center;padding-top:14px;border-top:1px solid {C['rule']}">
        <div style="display:flex;flex-direction:column;gap:2px">
          <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">المدرّب الصوتي</span>
          <span style="font-family:{RX};font-size:12px;color:{C['muted']}">يستهدف إيقاعك +5–10٪، لا رقم 180</span>
        </div>
        <div style="width:46px;height:28px;border-radius:100px;background:{C['wine']};position:relative"><div style="position:absolute;top:3px;left:21px;width:22px;height:22px;border-radius:50%;background:{C['card']}"></div></div>
      </div>
      <div style="display:flex;flex-direction:row;align-items:center;justify-content:center;gap:10px;height:54px;border-radius:100px;background:{C['wine']};min-height:44px">
        {icon("session", C['card'], 20, 1.8)}<span style="font-family:{RX};font-size:15px;font-weight:500;color:{C['card']}">ابدأ الجلسة</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;margin-top:26px;gap:2px">
      <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']};padding:0 4px 6px">جلسات سابقة</span>
      <div style="display:flex;flex-direction:column;background:{C['card']};border-radius:24px;padding:4px 18px;box-shadow:{C['shadow']}">
        {c_session_row("الخميس 3 سبتمبر · 17:45", "7.35", "1 س 5 د", "8:53")}
        {c_session_row("الاثنين 31 أغسطس · 06:20", "5.02", "44 د", "8:46")}
        {c_session_row("السبت 29 أغسطس · 18:10", "6.10", "52 د", "8:31", last=True)}
      </div>
    </div>
"""
    return c_wrap(c_frame(inner, "session", field_h=300))


def c_row(title, sub, trailing="", last=False):
    border = "" if last else f"border-bottom:1px solid {C['rule']};"
    tr = trailing or f'<span style="transform:scaleX(-1);display:block">{icon("chev", C["muted"], 18, 1.6)}</span>'
    return (f'<div style="display:flex;flex-direction:row;align-items:center;gap:12px;padding:14px 0;min-height:44px;{border}">'
            f'<div style="display:flex;flex-direction:column;gap:2px;flex:1">'
            f'<span style="font-family:{RX};font-size:13.5px;font-weight:500;color:{C["ink"]}">{title}</span>'
            + (f'<span style="font-family:{RX};font-size:12px;color:{C["muted"]}">{sub}</span>' if sub else "")
            + f'</div>{tr}</div>')


def c_group(title, rows):
    return (f'<div style="display:flex;flex-direction:column;gap:6px">'
            f'<span style="font-family:{RX};font-size:12px;color:{C["muted"]};padding:0 6px">{title}</span>'
            f'<div style="display:flex;flex-direction:column;background:{C["card"]};border-radius:22px;padding:2px 18px;box-shadow:{C["shadow"]}">{rows}</div></div>')


def c_toggle(on):
    bg = C["wine"] if on else C["rule"]
    x = 21 if on else 3
    return f'<div style="width:46px;height:28px;border-radius:100px;background:{bg};position:relative"><div style="position:absolute;top:3px;left:{x}px;width:22px;height:22px;border-radius:50%;background:{C["card"]};box-shadow:0 1px 3px rgba(0,0,0,.15)"></div></div>'


def c_more():
    inner = f"""
    {c_head("المزيد", "بياناتك تبقى على جهازك")}
    <div style="display:flex;flex-direction:column;gap:12px;margin-top:30px;background:{C['card']};border-radius:24px;padding:18px 20px;box-shadow:{C['shadow']}">
      <div style="display:flex;flex-direction:row;justify-content:space-between;align-items:baseline">
        <span style="font-family:{RX};font-size:13px;font-weight:500;color:{C['ink']}">المعرفة</span>
        <span style="font-family:{RX};font-size:12px;color:{C['wine']}">15 مقالة</span>
      </div>
      <span style="font-family:{RX};font-size:12.5px;color:{C['ink2']};line-height:1.7">رقم 180 خطوة/دقيقة ليس هدفاً لك · لماذا الإيقاع البطيء يؤذي · التنفس من الأنف: أدق مما يُقال</span>
    </div>
    <div style="display:flex;flex-direction:column;gap:16px;margin-top:16px">
      {c_group("المزامنة والنسخ", c_row("Health Connect", "كتابة الخطوات والجلسات فقط — لا قراءة", c_toggle(False)) + c_row("نسخة احتياطية", "آخر نسخة: اليوم 09:12 · Drive", last=True))}
      {c_group("بياناتك", c_row("الطول · الوزن · العمر", "175 سم · 77 كجم · 29") + c_row("الهدف اليومي", "7,000 خطوة — مبني على 57 دراسة") + c_row("معايرة طول الخطوة", "من GPS · 72 سم", last=True))}
      {c_group("التطبيق", c_row("اللغة", "العربية · الأرقام العربية") + c_row("التشخيص", "المجموع = عدّاد الجهاز · الفرق 0") + c_row("حول Khutwa", "v3.0 · بلا إعلانات ولا تتبّع", last=True))}
    </div>
"""
    return c_wrap(c_frame(inner, "more", field_h=260))


def c_brand():
    sizes = "".join(f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px">{mark_svg(sz, f"s{sz}")}<span style="font-family:{NUM};font-size:11px;color:{C["muted"]}">{sz}</span></div>' for sz in (96, 48, 32, 24))
    widget = f"""
      <div style="display:flex;flex-direction:row;align-items:center;gap:14px;background:{C['card']};border-radius:22px;padding:16px 18px;box-shadow:{C['shadow']};width:250px">
        {mark_svg(40, "w")}
        <div style="display:flex;flex-direction:column;gap:2px;flex:1">
          <span style="font-family:{NUM};font-size:26px;font-weight:300;color:{C['plum']};direction:ltr;line-height:1">4,312</span>
          <span style="font-family:{RX};font-size:11.5px;color:{C['muted']}">62% · سلسلة 6 أيام</span>
        </div>
        <svg width="36" height="36" viewBox="0 0 36 36" style="transform:rotate(-90deg)"><circle cx="18" cy="18" r="15" fill="none" stroke="{C['field']}" stroke-width="4"/><circle cx="18" cy="18" r="15" fill="none" stroke="{C['wine']}" stroke-width="4" stroke-linecap="round" stroke-dasharray="{2*3.1416*15*.62:.1f} 999"/></svg>
      </div>"""
    body = f"""
<div style="width:{W}px;height:{H}px;background:{C['ground']};direction:rtl;display:flex;flex-direction:column;overflow:hidden;position:relative">
  <div style="position:absolute;inset:0 0 auto 0;height:300px;background:{C['field']};border-radius:0 0 40px 40px;overflow:hidden">{contour_svg(W, 300)}</div>
  <div style="position:relative;display:flex;flex-direction:column;padding:56px 22px 0;gap:26px">
    <span style="font-family:{RX};font-size:12px;color:{C['muted']}">الهوية</span>
    <div style="display:flex;flex-direction:row;align-items:center;gap:18px">
      {mark_svg(120, "big")}
      <div style="display:flex;flex-direction:column;gap:2px">
        <span style="font-family:{RX};font-size:34px;font-weight:500;color:{C['plum']};line-height:1.1">خطوة</span>
        <span style="font-family:{NUM};font-size:20px;font-weight:300;letter-spacing:.14em;color:{C['wine']};direction:ltr">KHUTWA</span>
        <span style="font-family:{RX};font-size:12px;color:{C['ink2']};margin-top:6px">عدّاد خطوات صادق · بلا إعلانات ولا حساب</span>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;background:{C['card']};border-radius:24px;padding:18px 20px;box-shadow:{C['shadow']}">
      <span style="font-family:{RX};font-size:12px;color:{C['muted']}">العلامة بالمقاسات — تبقى مقروءة عند 24</span>
      <div style="display:flex;flex-direction:row;align-items:flex-end;justify-content:space-between;padding:6px 4px 0">{sizes}</div>
      <span style="font-family:{RX};font-size:12px;color:{C['ink2']};line-height:1.7;margin-top:6px">خمسة خطوط وحرف واحد من الفكرة: شخص يمشي. تبقى مقروءة عند 24 وتُرسم كـVectorDrawable بلا صور نقطية.</span>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px">
      <span style="font-family:{RX};font-size:12px;color:{C['muted']};padding:0 6px">الويدجت</span>
      {widget}
    </div>
    <div style="display:flex;flex-direction:column;gap:10px">
      <span style="font-family:{RX};font-size:12px;color:{C['muted']};padding:0 6px">الرسوم — خط واحد، لون واحد</span>
      <div style="display:flex;flex-direction:row;gap:12px">
        <div style="flex:1;background:{C['card']};border-radius:20px;padding:12px;box-shadow:{C['shadow']};display:flex;justify-content:center">{illo_path(150, 90)}</div>
        <div style="flex:1;background:{C['card']};border-radius:20px;padding:12px;box-shadow:{C['shadow']};display:flex;justify-content:center">{illo_empty(150, 84)}</div>
      </div>
    </div>
  </div>
</div>
"""
    return c_wrap(body)


def c_milestone():
    inner = f"""
    {c_head("إنجاز", "الأحد ٦ سبتمبر")}
    <div style="display:flex;flex-direction:column;align-items:center;gap:6px;margin-top:40px;background:{C['card']};border-radius:28px;padding:30px 24px 26px;box-shadow:{C['shadow']}">
      {illo_path(240, 140)}
      <span style="font-family:{NUM};font-size:64px;font-weight:300;line-height:1;color:{C['plum']};direction:ltr;margin-top:10px">7</span>
      <span style="font-family:{RX};font-size:18px;font-weight:500;color:{C['ink']}">أيام متتالية على الهدف</span>
      <span style="font-family:{RX};font-size:12.5px;color:{C['ink2']};text-align:center;line-height:1.7;max-width:260px;margin-top:4px">أسبوع كامل فوق 7,000. الأثر الصحي يبدأ من هنا لا من 10,000 — استمرارية سبعة أيام أهم من يوم واحد كبير.</span>
      <div style="display:flex;flex-direction:row;gap:10px;margin-top:18px;width:100%">
        <div style="flex:1;display:flex;align-items:center;justify-content:center;height:48px;border-radius:100px;background:{C['wine']};min-height:44px"><span style="font-family:{RX};font-size:14px;font-weight:500;color:{C['card']}">مشاركة</span></div>
        <div style="flex:1;display:flex;align-items:center;justify-content:center;height:48px;border-radius:100px;background:{C['field']};min-height:44px"><span style="font-family:{RX};font-size:14px;font-weight:500;color:{C['plum']}">التالي: 14 يوماً</span></div>
      </div>
    </div>
    <div style="display:flex;flex-direction:column;gap:6px;margin-top:16px">
      <span style="font-family:{RX};font-size:12px;color:{C['muted']};padding:0 6px">حالة فارغة — الجلسات قبل أول تسجيل</span>
      <div style="display:flex;flex-direction:row;align-items:center;gap:16px;background:{C['card']};border-radius:24px;padding:16px 18px;box-shadow:{C['shadow']}">
        {illo_empty(120, 66)}
        <div style="display:flex;flex-direction:column;gap:4px;flex:1">
          <span style="font-family:{RX};font-size:13.5px;font-weight:500;color:{C['ink']}">لا جلسات بعد</span>
          <span style="font-family:{RX};font-size:12px;color:{C['muted']};line-height:1.6">أول مسار مسجّل يظهر هنا بخريطته وإيقاعه.</span>
        </div>
      </div>
    </div>
"""
    return c_wrap(c_frame(inner, "today", field_h=320))


def main():
    files = {
        "Main.dc.html": direction_c(),
        "Trends.dc.html": c_trends(),
        "Sessions.dc.html": c_sessions(),
        "More.dc.html": c_more(),
        "Brand.dc.html": c_brand(),
        "Milestone.dc.html": c_milestone(),
        "DirectionA.dc.html": direction_a(),
        "TrendsA.dc.html": direction_a_trends(),
        "DirectionB.dc.html": direction_b(),
    }
    for name, html in files.items():
        with open(os.path.join(HERE, name), "w", encoding="utf-8") as fh:
            fh.write(html)
    step = W + 90
    canvas = {
        "pages": [{"id": "page-1", "name": "التصميم"}, {"id": "page-2", "name": "المسودات"}],
        "artboards": [
            {"file": "Main.dc.html", "x": 0, "y": 0, "w": W, "h": H, "title": "اليوم", "page": "page-1"},
            {"file": "Trends.dc.html", "x": step, "y": 0, "w": W, "h": H, "title": "اتجاهات", "page": "page-1"},
            {"file": "Sessions.dc.html", "x": 2 * step, "y": 0, "w": W, "h": H, "title": "جلسات", "page": "page-1"},
            {"file": "More.dc.html", "x": 3 * step, "y": 0, "w": W, "h": H, "title": "المزيد", "page": "page-1"},
            {"file": "Brand.dc.html", "x": 0, "y": H + 140, "w": W, "h": H, "title": "الهوية", "page": "page-1"},
            {"file": "Milestone.dc.html", "x": step, "y": H + 140, "w": W, "h": H, "title": "إنجاز + حالة فارغة", "page": "page-1"},
            {"file": "DirectionA.dc.html", "x": 0, "y": 0, "w": W, "h": H, "title": "A — الخط · اليوم", "page": "page-2"},
            {"file": "TrendsA.dc.html", "x": step, "y": 0, "w": W, "h": H, "title": "A — الخط · اتجاهات", "page": "page-2"},
            {"file": "DirectionB.dc.html", "x": 2 * step, "y": 0, "w": W, "h": H, "title": "B — العدّاد الليلي", "page": "page-2"},
        ],
        "annotations": [
            {"id": "system", "x": 0, "y": -150, "w": 520, "page": "page-1",
             "text": "Khutwa v3 — الاتجاه C معتمد.\nالنظام: أرضية #FBF8F7 · حقل #F3E4EA بزوايا 40 · بطاقات بيضاء 22–26 بظل عنابي خافت · عنابي #8E2547 للتقدم والفعل، عميق #5E2138 للأرقام الكبيرة · Readex Pro للعربي، Jost 300 للأرقام · 4 تبويبات.\nالفكرة الحاكمة: «اليوم كخط» — بطاقة الخط الزمني تعبر حدّ الحقل في كل شاشة."},
            {"id": "drafts", "x": 0, "y": -110, "w": 420, "page": "page-2",
             "text": "مسودات الاتجاهين A وB — للمرجع فقط، غير معتمدة."},
        ],
        "launch": {"view": "canvas", "page": "page-1"},
    }
    with open(os.path.join(HERE, "canvas.json"), "w", encoding="utf-8") as fh:
        json.dump(canvas, fh, ensure_ascii=False, indent=1)
    print("wrote", ", ".join(files), "+ canvas.json")


if __name__ == "__main__":
    main()
