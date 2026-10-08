"""Full reproducible preparation: python tools/parentes/prepare_catalogue.py.
Requires network on first run. Requests are cached below app/build/parentes-import.
Source JSON is kept in tools/parentes/data; only the compact runtime archive,
content, stories and string resources are shipped to Android.
"""
import subprocess
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
for name in ['import_catalogue.py','enrich_catalogue.py','build_content.py',
             'freeze_provenance.py','build_stories.py','build_runtime.py','validate_catalogue.py','validate_stories.py']:
    subprocess.run([sys.executable,str(HERE/name)],check=True)
