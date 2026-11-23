"""Turns the GibberCodes logo SVG into an Android vector drawable.
usage: svg2vd.py in.svg out.xml size_dp scale [ink]   (ink replaces the #0B0B0F key line color)"""
import sys, re
import xml.etree.ElementTree as ET
src, out, size, scale = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
ink = sys.argv[5] if len(sys.argv) > 5 else None
NS = '{http://www.w3.org/2000/svg}'
root = ET.parse(src).getroot()
clips = {}
for cp in root.iter(NS + 'clipPath'):
    r = cp.find(NS + 'rect')
    x, y, w, h = (float(r.get(k)) for k in ('x', 'y', 'width', 'height'))
    clips[cp.get('id')] = f'M{x},{y}h{w}v{h}h{-w}Z'
def color(c):
    if c and ink and c.upper() == '#0B0B0F': return ink
    return c
lines = []
def walk(el, inherited, depth):
    pad = '    ' * depth
    tag = el.tag.replace(NS, '')
    style = dict(inherited)
    for k in ('fill', 'stroke', 'stroke-width', 'stroke-linejoin', 'stroke-linecap', 'fill-rule'):
        if el.get(k) is not None: style[k] = el.get(k)
    if tag == 'g':
        attrs = ''
        t = el.get('transform')
        if t:
            m = re.match(r'translate\(([-\d.]+),([-\d.]+)\)', t)
            attrs += f' android:translateX="{m.group(1)}" android:translateY="{m.group(2)}"'
        lines.append(f'{pad}<group{attrs}>')
        cp = el.get('clip-path')
        if cp:
            cid = re.match(r'url\(#(.+)\)', cp).group(1)
            lines.append(f'{pad}    <clip-path android:pathData="{clips[cid]}" />')
        for c in el: walk(c, style, depth + 1)
        lines.append(f'{pad}</group>')
    elif tag == 'path':
        a = f'{pad}<path android:pathData="{el.get("d")}"'
        f = style.get('fill', 'black')
        if f != 'none': a += f' android:fillColor="{color(f)}"'
        if style.get('fill-rule') == 'evenodd': a += ' android:fillType="evenOdd"'
        s = style.get('stroke')
        if s and s != 'none':
            a += f' android:strokeColor="{color(s)}" android:strokeWidth="{style.get("stroke-width", "1")}"'
            if style.get('stroke-linecap'): a += f' android:strokeLineCap="{style["stroke-linecap"]}"'
            if style.get('stroke-linejoin'): a += f' android:strokeLineJoin="{style["stroke-linejoin"]}"'
        lines.append(a + ' />')
off = 256 * (1 - scale)
body = []
for c in root:
    if c.tag.replace(NS, '') == 'g': walk(c, {}, 2)
print(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- The GibberCodes logo, made from gibbercodes-logo.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp" android:height="{size}dp"
    android:viewportWidth="512" android:viewportHeight="512">
    <group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{off:.2f}" android:translateY="{off:.2f}">
''' + '\n'.join(lines) + '''
    </group>
</vector>''', file=open(out, 'w'))
