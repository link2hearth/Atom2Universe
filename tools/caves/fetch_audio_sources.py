"""Download only media linked from the selected authors' pages (no downloaded code)."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.request import Request, urlopen
from urllib.parse import urljoin, urlparse, unquote
import json

WORK = Path(__file__).parent / '.audio-work'
WORK.mkdir(exist_ok=True)
PAGES = {
    'impact': 'https://kenney.nl/assets/impact-sounds',
    'firearms': 'https://opengameart.org/content/the-free-firearm-sound-library',
    'farm': 'https://opengameart.org/content/farm-animals',
    'sheep': 'https://opengameart.org/content/sheep-baa',
    'chicken': 'https://opengameart.org/content/chicken-sound-effect',
    'bow': 'https://opengameart.org/content/battle-sound-effects',
    'crossbow': 'https://opengameart.org/content/crossbow-shot',
}


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            self.links += [v for k, v in attrs if k == 'href']


def fetch(url):
    with urlopen(Request(url, headers={'User-Agent': 'Mozilla/5.0'}), timeout=90) as r:
        return r.read()


if __name__ == '__main__':
    index = {}
    for key, page in PAGES.items():
        destination = WORK / key
        destination.mkdir(exist_ok=True)
        html = fetch(page)
        (destination / 'source.html').write_bytes(html)
        links = Links()
        links.feed(html.decode())
        urls = sorted(set(urljoin(page, x) for x in links.links if
                          urlparse(x).path.lower().endswith(('.zip', '.7z', '.wav', '.ogg', '.flac', '.mp3'))))
        index[key] = {'page': page, 'files': urls}
        for url in urls:
            target = destination / Path(unquote(urlparse(url).path)).name
            if not target.exists():
                print('Downloading', key, target.name, flush=True)
                target.write_bytes(fetch(url))
            print(key, target.name, target.stat().st_size, flush=True)
    (WORK / 'index.json').write_text(json.dumps(index, indent=2))
