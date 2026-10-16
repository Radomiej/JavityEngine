"""Assemble unmodified renderer captures into a licensed animated preview (requires Pillow)."""
from pathlib import Path
import json
from PIL import Image
root = Path(__file__).resolve().parents[2]
base = root/'docs/artifacts/imported-animation'
g = json.loads((root/'tools/animation-validation/fox/Fox.g3dj').read_text())
frames, durations = [], []
for clip in ['Survey', 'Walk', 'Run']:
    animation = next(a for a in g['animations'] if a['id'] == clip)
    duration = max(k['keytime'] for node in animation['bones']
                   for key in ['translation', 'rotation', 'scaling']
                   for k in node.get(key, []))/1000
    paths = sorted((base/'gl20/frames').glob(clip+'-*.png'))
    if len(paths) != 20:
        raise SystemExit('Run the GL20 proof first: expected 20 frames of '+clip)
    for path in paths:
        frame = Image.open(path).convert('RGB')
        frame.thumbnail((720, 504))
        frames.append(frame)
        durations.append(max(20, round(duration*1000/20)))
frames[0].save(base/'fox-animations.gif', save_all=True, append_images=frames[1:],
               duration=durations, loop=0, optimize=True)
canvas = Image.new('RGB', (1500, 350))
for i, name in enumerate(['survey-025.png', 'walk-025.png', 'run-025.png']):
    canvas.paste(Image.open(base/'gl20'/name).convert('RGB').resize((500, 350)), (i*500, 0))
canvas.save(base/'fox-clips.png')
print('Preview: 60 captured frames, source clip timings rounded to GIF milliseconds')
