#!/usr/bin/env python3
import csv, math, os, sys

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
rows = list(csv.DictReader(open(src, encoding='utf-8')))
def chart(path, title, key, suffix, color):
    w,h,p = 900,520,70
    vals = [float(r[key]) for r in rows]; labels=[r['clients'] for r in rows]
    maxv=max(vals) if vals else 1
    def x(i): return p + i*(w-2*p)/max(1,len(vals)-1)
    def y(v): return h-p-(v/maxv)*(h-2*p)
    points=' '.join(f'{x(i):.1f},{y(v):.1f}' for i,v in enumerate(vals))
    grid=''.join(f'<line x1="{p}" y1="{y(maxv*j/5):.1f}" x2="{w-p}" y2="{y(maxv*j/5):.1f}" stroke="#d9e0ea"/><text x="10" y="{y(maxv*j/5)+4:.1f}" font-size="12">{maxv*j/5:.1f}</text>' for j in range(6))
    ticks=''.join(f'<text x="{x(i):.1f}" y="{h-35}" text-anchor="middle" font-size="13">{lab}</text>' for i,lab in enumerate(labels))
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="white"/><text x="{w/2}" y="30" text-anchor="middle" font-family="sans-serif" font-size="22" font-weight="bold">{title}</text>{grid}<line x1="{p}" y1="{h-p}" x2="{w-p}" y2="{h-p}" stroke="#374151"/><polyline points="{points}" fill="none" stroke="{color}" stroke-width="4"/>''' + ''.join(f'<circle cx="{x(i):.1f}" cy="{y(v):.1f}" r="6" fill="{color}"/><text x="{x(i):.1f}" y="{y(v)-12:.1f}" text-anchor="middle" font-size="12">{v:.2f}</text>' for i,v in enumerate(vals)) + f'''{ticks}<text x="{w/2}" y="{h-8}" text-anchor="middle" font-family="sans-serif" font-size="13">Concurrent clients</text><text x="18" y="{h/2}" transform="rotate(-90 18 {h/2})" text-anchor="middle" font-family="sans-serif" font-size="13">{suffix}</text></svg>'''
    open(path,'w',encoding='utf-8').write(svg)
chart(os.path.join(out,'throughput.svg'),'Measured throughput','throughput_ops_sec','operations / second','#2d6cdf')
chart(os.path.join(out,'speedup.svg'),'Measured speedup','speedup','relative to one client','#2bb673')
chart(os.path.join(out,'efficiency.svg'),'Measured parallel efficiency','efficiency_percent','percent','#e2a93b')
