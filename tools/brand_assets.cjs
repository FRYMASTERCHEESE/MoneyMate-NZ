const fs=require('fs');
const path=require('path');
const sharp=require('sharp');
const repo=path.resolve(__dirname,'..'),out=process.argv[2]||path.join(repo,'store-assets');
fs.mkdirSync(out,{recursive:true});
const icon=fs.readFileSync(path.join(repo,'docs/wallet.svg'),'utf8');
// Play applies its own mask; store artwork has an opaque square canvas.
const square=icon.replace(' clip-path="url(#round)"','');
const inner=icon.slice(icon.indexOf('>')+1,icon.lastIndexOf('</svg>'));
const feature=`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#0b302b"/><stop offset="1" stop-color="#1d6248"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#bg)"/><circle cx="980" cy="40" r="235" fill="none" stroke="#b8f28b" stroke-opacity=".1" stroke-width="70"/>
<rect x="64" y="121" width="238" height="238" rx="58" fill="#b8f28b"/>
<svg x="76" y="133" width="214" height="214" viewBox="0 0 108 108">${inner}</svg>
<text x="350" y="141" font-family="Segoe UI,Arial,sans-serif" font-size="27" font-weight="700" fill="#c7f5a7">MoneyMate NZ</text>
<text x="346" y="216" font-family="Segoe UI,Arial,sans-serif" font-size="58" font-weight="700" fill="#fff">Your money.</text>
<text x="346" y="283" font-family="Segoe UI,Arial,sans-serif" font-size="58" font-weight="700" fill="#fff">Your next move.</text>
<rect x="350" y="316" width="54" height="5" rx="2" fill="#ffd66d"/>
<text x="350" y="367" font-family="Segoe UI,Arial,sans-serif" font-size="23" fill="#d4ead9">Budgets · bills · savings goals</text>
</svg>`;
(async()=>{
 fs.writeFileSync(path.join(repo,'store-assets/feature-graphic.svg'),feature);
 await sharp(Buffer.from(square)).resize(512,512).flatten({background:'#0b302b'}).ensureAlpha(1).png().toFile(path.join(repo,'store-assets/play-icon-512.png'));
 await sharp(Buffer.from(feature)).flatten({background:'#0b302b'}).png().toFile(path.join(repo,'store-assets/feature-graphic-1024x500.png'));
 if(path.resolve(out)!==path.join(repo,'store-assets')) for(const name of ['play-icon-512.png','feature-graphic-1024x500.png'])fs.copyFileSync(path.join(repo,'store-assets',name),path.join(out,name));
 console.log('Exported opaque 512×512 icon and 1024×500 Play feature graphic.');
})();
