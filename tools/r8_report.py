"""Record local R8 evidence, not a prediction of Play Console's DEX metrics."""
import json
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
classes = []
for line in mapping.read_text(encoding='utf-8').splitlines():
    match = re.fullmatch(r'(\S+) -> (\S+):', line)
    if match:
        classes.append(match.groups())
if not classes:
    raise SystemExit('Release mapping contains no classes')
renamed = sum(original != target for original, target in classes)
app_renamed = sum(original.startswith('nz.moneymate.app.') and original != target
                  for original, target in classes)
if not renamed or not app_renamed:
    raise SystemExit('Release mapping shows no app obfuscation')
bundle = root / 'app/build/outputs/bundle/release/app-release.aab'
with zipfile.ZipFile(bundle) as archive:
    dex_bytes = sum(entry.file_size for entry in archive.infolist()
                    if entry.filename.startswith('base/dex/') and entry.filename.endswith('.dex'))
report = dict(mapped_class_entries=len(classes), renamed_class_entries=renamed,
              renamed_app_class_entries=app_renamed, uncompressed_dex_bytes=dex_bytes,
              note='Mapping entry counts are not Play Console obfuscation, optimisation, or shrinking percentages.')
out = root / 'app/build/reports/r8-summary.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(out.read_text(encoding='utf-8'))
