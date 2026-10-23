#!/usr/bin/env python3
"""Generate labelled synthetic Spanish speech locally with system libespeak-ng + ffmpeg."""
import argparse
import ctypes
import subprocess
import tempfile
import wave
from pathlib import Path
lib = ctypes.CDLL('libespeak-ng.so.1')
rate = lib.espeak_Initialize(2, 0, None, 0)
if rate <= 0:
    raise SystemExit('espeak-ng data unavailable')
chunks = []
CALLBACK = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)
@CALLBACK
def receive(samples, count, events):
    if count > 0 and samples:
        chunks.append(ctypes.string_at(samples, count * 2))
    return 0
lib.espeak_SetSynthCallback(receive)
if lib.espeak_SetVoiceByName(b'es') != 0:
    raise SystemExit('Spanish espeak-ng voice unavailable')
parser = argparse.ArgumentParser()
parser.add_argument('--text', default='Hola. Esta es una prueba de dictado local. Vamos a revisar el texto antes de enviar.')
parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent.parent / 'benchmarks' / 'smoke-es.wav')
args = parser.parse_args()
text = args.text
raw = text.encode('utf-8') + b'\x00'
lib.espeak_Synth(raw, len(raw), 0, 0, 0, 1, None, None)
lib.espeak_Synchronize()
output = args.output
with tempfile.TemporaryDirectory() as temporary:
    source = str(Path(temporary) / 'source.wav')
    with wave.open(source, 'wb') as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(rate); wav.writeframes(b''.join(chunks))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', source, '-ar', '16000', '-ac', '1', '-c:a', 'pcm_s16le', str(output)], check=True)
print(f'SYNTHETIC Spanish fixture: {output}\nReference: {text}')
