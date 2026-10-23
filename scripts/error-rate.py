#!/usr/bin/env python3
"""Compute normalized WER/CER from UTF-8 text files; no external dependencies."""
import argparse
import json
import unicodedata
from pathlib import Path

def normalize(text):
    return ' '.join(''.join(c for c in unicodedata.normalize('NFC', text.lower())
                           if not unicodedata.category(c).startswith('P')).split())

def distance(a, b):
    row = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        next_row = [i]
        for j, y in enumerate(b, 1):
            next_row.append(min(next_row[-1] + 1, row[j] + 1, row[j - 1] + (x != y)))
        row = next_row
    return row[-1]

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('reference', type=Path)
    parser.add_argument('hypothesis', type=Path)
    args = parser.parse_args()
    reference, hypothesis = [normalize(p.read_text()) for p in (args.reference, args.hypothesis)]
    if not reference:
        parser.error('Reference must not be empty')
    words = reference.split()
    print(json.dumps({'wer': distance(words, hypothesis.split()) / len(words),
                      'cer': distance(reference, hypothesis) / len(reference)}, indent=2))
