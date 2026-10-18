"""Extract selected media into the ignored workspace and list candidates."""
from pathlib import Path
import sys
import zipfile
sys.path.insert(0, str(Path(__file__).parent / '.audio-work/libs'))
import py7zr
import soundfile as sf

WORK = Path(__file__).parent / '.audio-work'
for folder in WORK.iterdir():
    if not folder.is_dir() or folder.name == 'libs':
        continue
    for archive in list(folder.glob('*.zip')) + list(folder.glob('*.7z')):
        target = folder / 'extracted'
        if target.exists():
            continue
        target.mkdir()
        if archive.suffix == '.zip':
            with zipfile.ZipFile(archive) as z:
                for name in z.namelist():
                    path = (target / name).resolve()
                    if not path.is_relative_to(target.resolve()):
                        raise ValueError(name)
                    if Path(name).suffix.lower() in ('.wav', '.ogg', '.flac', '.mp3', '.txt'):
                        z.extract(name, target)
        else:
            with py7zr.SevenZipFile(archive) as z:
                names = z.getnames()
                assert all((target / name).resolve().is_relative_to(target.resolve()) for name in names)
                z.extract(target, targets=[name for name in names if Path(name).suffix.lower() in ('.wav', '.ogg', '.flac', '.mp3', '.txt')])
    for p in folder.rglob('*'):
        if p.suffix.lower() not in ('.wav', '.ogg', '.flac', '.mp3'):
            continue
        try:
            info = sf.info(p)
            print(p.relative_to(WORK), round(info.duration, 3), info.samplerate)
        except Exception as error:
            print(p, str(error))
