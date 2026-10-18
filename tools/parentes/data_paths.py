"""Keep auditable import sources outside Android assets.

Only runtime.bin, content.json and stories.json are shipped to Android.
Source JSON stays readable and versioned here for regeneration and validation.
"""
from pathlib import Path

SOURCE = Path(__file__).resolve().parent / 'data'
