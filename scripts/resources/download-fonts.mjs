#!/usr/bin/env node
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const FONT_CSS_URL =
  'https://fonts.googleapis.com/css2?family=Audiowide&family=Orbitron:wght@400;600;700&family=Seven+Segment&family=VT323&display=swap';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const fontsDir = path.resolve(__dirname, '../../fonts');

const sanitizeFamily = (family) => family.replace(/\s+/g, '');

const computeFileName = (family, weight, style) => {
  const base = `${sanitizeFamily(family)}-${weight}`;
  if (style && style.toLowerCase() !== 'normal') {
    return `${base}-${style.toLowerCase()}.woff2`;
  }
  return `${base}.woff2`;
};

const ensureFontsDir = async () => {
  await fs.mkdir(fontsDir, { recursive: true });
};

const fetchText = async (url) => {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (compatible; Atom2Univers Font Fetcher)'
    }
  });

  if (!response.ok) {
    throw new Error(`Échec du téléchargement de ${url}: ${response.status} ${response.statusText}`);
  }

  return response.text();
};

const fetchBinary = async (url) => {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (compatible; Atom2Univers Font Fetcher)'
    }
  });

  if (!response.ok) {
    throw new Error(`Échec du téléchargement de ${url}: ${response.status} ${response.statusText}`);
  }

  const buffer = Buffer.from(await response.arrayBuffer());
  return buffer;
};

const parseFontFaces = (css) => {
  const blocks = css.match(/@font-face\s*{[^}]+}/g) || [];

  return blocks.map((block) => {
    const familyMatch = block.match(/font-family:\s*'([^']+)'/);
    const weightMatch = block.match(/font-weight:\s*(\d+)/);
    const styleMatch = block.match(/font-style:\s*([^;]+)/);
    const srcMatch = block.match(/src:\s*url\(([^)]+)\)/);

    if (!familyMatch || !weightMatch || !styleMatch || !srcMatch) {
      throw new Error(`Bloc @font-face incomplet:\n${block}`);
    }

    const url = srcMatch[1].replace(/"|'/g, '');

    return {
      family: familyMatch[1],
      weight: weightMatch[1],
      style: styleMatch[1].trim(),
      url
    };
  });
};

const downloadFonts = async () => {
  console.log('Téléchargement de la feuille de style Google Fonts…');
  const css = await fetchText(FONT_CSS_URL);
  const fontFaces = parseFontFaces(css);

  await ensureFontsDir();

  for (const face of fontFaces) {
    const fileName = computeFileName(face.family, face.weight, face.style);
    const destination = path.join(fontsDir, fileName);

    console.log(`→ ${face.family} ${face.style} ${face.weight} → ${fileName}`);
    const data = await fetchBinary(face.url);
    await fs.writeFile(destination, data);
  }

  console.log(`\nPolices téléchargées dans ${fontsDir}`);
};

try {
  await downloadFonts();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
