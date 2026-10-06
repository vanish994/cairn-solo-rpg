#!/usr/bin/env python3
import argparse
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser(description='Create a contact sheet for individual sprite PNGs.')
    ap.add_argument('--input-dir', required=True)
    ap.add_argument('--output', required=True)
    ap.add_argument('--tile-width', type=int, default=320)
    ap.add_argument('--tile-height', type=int, default=520)
    args = ap.parse_args()

    source = Path(args.input_dir)
    paths = sorted(p for p in source.glob('*.png') if p.is_file())
    if not paths:
        raise SystemExit(f'No PNG sprites found in {source}')

    sheet = Image.new('RGBA', (args.tile_width * len(paths), args.tile_height), (24, 28, 32, 255))
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(paths):
        with Image.open(path).convert('RGBA') as image:
            scale = min((args.tile_width - 24) / image.width, (args.tile_height - 58) / image.height)
            preview = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.NEAREST)
        x = index * args.tile_width + (args.tile_width - preview.width) // 2
        sheet.alpha_composite(preview, (x, 10))
        draw.text((index * args.tile_width + 12, args.tile_height - 34), path.stem, fill=(240, 240, 240, 255))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output, optimize=True)
    print(f'generated={output.resolve()}')
    print(f'count={len(paths)}')


if __name__ == '__main__':
    main()
