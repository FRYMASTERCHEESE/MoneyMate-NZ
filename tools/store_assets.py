"""Create branded Play assets from genuine Android emulator screenshots.
Usage: python tools/store_assets.py --screenshots PATH --out PATH
Requires Pillow. Captured screens retain example-data labels.
"""
from pathlib import Path
import argparse,json
from PIL import Image,ImageDraw,ImageFont
P=argparse.ArgumentParser();P.add_argument('--screenshots',type=Path,required=True);P.add_argument('--out',type=Path,required=True);args=P.parse_args();args.out.mkdir(parents=True,exist_ok=True)
FONT=Path('C:/Windows/Fonts')
WINDOWS_FONT=FONT.exists()
if not WINDOWS_FONT:FONT=Path('/usr/share/fonts/truetype/dejavu')
def font(n,bold=False):return ImageFont.truetype(str(FONT/(('segoeuib.ttf' if bold else 'segoeui.ttf') if WINDOWS_FONT else ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'))),n)
INK='#0B302B';CREAM='#F5F7F2'
def save(im,name):im.save(args.out/name,optimize=True)
# Logo and feature graphic come from the shared SVG via brand_assets.cjs.
# Do not overwrite them with a divergent hand-drawn mark.
manifest=[]
if args.screenshots:
    specs=[('02-home.png','A clearer money picture','Your month at a glance.'),('03-spending.png','See where it goes','A category breakdown that makes sense.'),('budgets.png','Spend with a plan','Follow your monthly category limits.'),('goals.png','Save for something good','Track each step towards your goals.'),('bills.png','Know what’s coming','Keep bills and due dates in view.')]
    for i,(file,title,sub) in enumerate(specs,1):
        raw=args.screenshots/file
        if not raw.exists():continue
        shot=Image.open(raw).convert('RGB');im=Image.new('RGB',(1080,1920),CREAM);d=ImageDraw.Draw(im)
        d.text((72,56),'MONEYMATE NZ',font=font(27,True),fill='#08785B')
        d.text((72,112),title,font=font(52,True),fill=INK);d.text((72,192),sub,font=font(30),fill='#60746D')
        thumb=shot.copy();thumb.thumbnail((800,1480),Image.Resampling.LANCZOS);x=(1080-thumb.width)//2;y=294
        d.rounded_rectangle((x-14,y-14,x+thumb.width+14,y+thumb.height+14),radius=35,fill=INK)
        im.paste(thumb,(x,y));d.text((72,1832),'Example data • Actual Android app screen',font=font(25),fill='#60746D')
        name=f'phone-{i:02d}.png';save(im,name);manifest.append({'file':name,'capture':file,'alt':title+'. '+sub+' Screenshot with example financial data.'})
(args.out/'asset-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print('Created',len(manifest),'promotional screenshots from actual app captures.')
