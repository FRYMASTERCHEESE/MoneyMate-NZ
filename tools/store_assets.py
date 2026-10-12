"""Create branded Play assets from genuine Android emulator screenshots.
Usage: python tools/store_assets.py --screenshots PATH --out PATH
Requires Pillow. Captured screens retain example-data labels.
"""
from pathlib import Path
import argparse,json
from PIL import Image,ImageDraw,ImageFont
P=argparse.ArgumentParser();P.add_argument('--screenshots',type=Path);P.add_argument('--out',type=Path,required=True);args=P.parse_args();args.out.mkdir(parents=True,exist_ok=True)
FONT=Path('C:/Windows/Fonts')
WINDOWS_FONT=FONT.exists()
if not WINDOWS_FONT:FONT=Path('/usr/share/fonts/truetype/dejavu')
def font(n,bold=False):return ImageFont.truetype(str(FONT/(('segoeuib.ttf' if bold else 'segoeui.ttf') if WINDOWS_FONT else ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'))),n)
INK='#123F35';CREAM='#F5F6EF';GREEN='#69BC91';GOLD='#FFD773'
def wallet(im,box):
    d=ImageDraw.Draw(im);x,y,w=box
    def rect(bounds,fill,r=0):
        b=tuple(int(v*w/108+(x if i%2==0 else y)) for i,v in enumerate(bounds));d.rounded_rectangle(b,radius=int(r*w/108),fill=fill)
    rect((28,36,80,78),GREEN,6)
    d.ellipse((x+41*w/108,y+25*w/108,x+67*w/108,y+51*w/108),fill=GOLD)
    rect((66,51,83,67),INK,2)
    d.ellipse((x+71*w/108,y+57*w/108,x+75*w/108,y+61*w/108),fill='white')
def save(im,name):im.save(args.out/name,optimize=True)
icon=Image.new('RGB',(2048,2048),INK);wallet(icon,(0,0,2048));icon=icon.resize((512,512),Image.Resampling.LANCZOS).convert('RGBA');save(icon,'play-icon-512.png')
feature=Image.new('RGB',(2048,1000),INK);d=ImageDraw.Draw(feature)
d.ellipse((1300,-260,2360,800),fill='#205445');d.ellipse((1450,520,2180,1250),fill='#2A614C');wallet(feature,(1310,110,610))
d.text((128,180),'MoneyMate NZ',font=font(92,True),fill=CREAM)
d.text((132,330),'Your money. Your plan.',font=font(66),fill=CREAM)
d.text((132,466),'Track • Budget • Save',font=font(39),fill='#BCE7D6')
d.rounded_rectangle((130,645,440,720),radius=37,fill=GOLD);d.text((166,651),'Made for NZD',font=font(36,True),fill=INK)
save(feature.resize((1024,500),Image.Resampling.LANCZOS),'feature-graphic-1024x500.png')
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
print('Created',len(manifest),'promotional screenshots plus icon and feature graphic.')
