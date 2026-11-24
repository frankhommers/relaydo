#!/usr/bin/env python3
"""Original Relaydo artwork; deterministic raster exports from shared geometry.
Requires Pillow. No remote assets. Existing technical identifiers remain unchanged.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
OUT=ROOT/'branding/relaydo'
NAVY='#102A43'; TEAL='#16B8A6'; WHITE='#FFFFFF'
# A connected route ending in a forward arrow: no envelope/paper-plane stock artwork.
ROUTE=[(32,74),(32,41),(44,30),(68,30),(79,41),(79,55),(51,55),(64.6,69.4)]
ARM=[ROUTE[-2],(64.6,41.4)]
DOT=(32,74); RADIUS=8
CIRCLE=f'M{DOT[0]-RADIUS},{DOT[1]} a8,8 0,1 0,16 0 a8,8 0,1 0,-16 0'
def path(points):return ' '.join(('M' if i==0 else 'L')+f'{x},{y}' for i,(x,y) in enumerate(points))
def mark(size,background=True):
    scale=4; im=Image.new('RGBA',(size*scale,size*scale),(0,0,0,0));d=ImageDraw.Draw(im)
    def pts(v):return [(int(x*size*scale/108),int(y*size*scale/108)) for x,y in v]
    if background:d.rounded_rectangle((0,0,size*scale-1,size*scale-1),radius=size*scale*.22,fill=NAVY)
    d.line(pts(ROUTE),fill=WHITE if background else NAVY,width=round(size*scale*8/108),joint='curve')
    d.line(pts(ARM),fill=TEAL,width=round(size*scale*8/108))
    d.ellipse(((DOT[0]-RADIUS)*size*scale/108,(DOT[1]-RADIUS)*size*scale/108,(DOT[0]+RADIUS)*size*scale/108,(DOT[1]+RADIUS)*size*scale/108),fill=TEAL)
    return im.resize((size,size),Image.Resampling.LANCZOS)
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        p=RES/('mipmap-'+density)/'ic_launcher.png';p.parent.mkdir(exist_ok=True);mark(size).save(p)
    mark(512).save(OUT/'relaydo-icon-512.png')
    mark(96).save(RES/'drawable-xhdpi/relaydo_notification_large.png')
    mono=mark(48,False); alpha=mono.getchannel('A'); mono=Image.new('RGBA',mono.size,'white'); mono.putalpha(alpha)
    mono.save(RES/'drawable-xhdpi/ic_forwarder.webp',lossless=True)
    logo=Image.new('RGBA',(960,440),(0,0,0,0));logo.alpha_composite(mark(200),(380,20))
    font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',116)
    d=ImageDraw.Draw(logo);text='Relaydo';box=d.textbbox((0,0),text,font=font);d.text(((960-(box[2]-box[0]))/2,230),text,font=font,fill=TEAL)
    logo.save(OUT/'relaydo-logo.png');logo.save(RES/'drawable-xhdpi/ic_splash_app_logo.png')
    # Remove obsolete secondary wordmark visually, retaining the resource for compatible splash layouts.
    Image.new('RGBA',(121,30),(0,0,0,0)).save(RES/'drawable-xhdpi/ic_splash_company_logo.png')
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="{NAVY}"/><path d="{path(ROUTE)}" fill="none" stroke="white" stroke-width="8" stroke-linejoin="round"/><path d="{path(ARM)}" fill="none" stroke="{TEAL}" stroke-width="8"/><circle cx="{DOT[0]}" cy="{DOT[1]}" r="{RADIUS}" fill="{TEAL}"/></svg>'''
    (OUT/'relaydo-mark.svg').write_text(svg)
    fg=RES/'drawable/relaydo_launcher_foreground.xml'
    fg.write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<group android:pivotX="54" android:pivotY="54" android:scaleX="0.75" android:scaleY="0.75">
<path android:pathData="{path(ROUTE)}" android:strokeColor="#FFFFFF" android:strokeWidth="8" android:strokeLineJoin="round" android:fillColor="#00000000"/>
<path android:pathData="{path(ARM)}" android:strokeColor="#16B8A6" android:strokeWidth="8" android:fillColor="#00000000"/>
<path android:pathData="{CIRCLE}" android:fillColor="#16B8A6"/>
</group></vector>''')
    (RES/'drawable-anydpi-v24/ic_forwarder.xml').write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:pathData="{path(ROUTE)} {path(ARM)}" android:strokeColor="#FFFFFFFF" android:strokeWidth="8" android:strokeLineJoin="round" android:fillColor="#00000000"/>
<path android:pathData="{CIRCLE}" android:fillColor="#FFFFFFFF"/>
</vector>
''')
    folder=RES/'mipmap-anydpi-v26';folder.mkdir(exist_ok=True)
    (folder/'ic_launcher.xml').write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/relaydo_icon_background"/><foreground android:drawable="@drawable/relaydo_launcher_foreground"/></adaptive-icon>''')
    (RES/'values/relaydo_brand.xml').write_text('''<resources><color name="relaydo_icon_background">#102A43</color></resources>''')
if __name__=='__main__':main()
