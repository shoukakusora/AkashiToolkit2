"""Create one uniform placeholder image used for ships without official art."""
import os

from PIL import Image, ImageDraw

BASE = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\apkwork"
OUT = os.path.join(BASE, "appbuild", "app", "src", "main", "res", "drawable-nodpi",
                   "ship_thumb_placeholder.jpg")

W, H = 360, 156


def main():
    img = Image.new("RGB", (W, H), (38, 46, 58))
    draw = ImageDraw.Draw(img)

    # 纵向渐变
    for y in range(H):
        t = y / float(H)
        r = int(38 + (18 - 38) * t)
        g = int(46 + (24 - 46) * t)
        b = int(58 + (34 - 58) * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))

    # 海浪线条
    for i in range(3):
        base_y = 108 + i * 14
        pts = []
        for x in range(0, W + 1, 8):
            import math
            y = base_y + int(6 * math.sin((x / 34.0) + i))
            pts.append((x, y))
        draw.line(pts, fill=(70, 92, 118), width=2)

    # 中央圆形轮廓
    cx, cy, r = W // 2, 64, 34
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(96, 122, 152), width=3)
    draw.ellipse([cx - 14, cy - 8, cx - 6, cy], fill=(120, 148, 178))
    draw.ellipse([cx + 6, cy - 8, cx + 14, cy], fill=(120, 148, 178))

    img.save(OUT, "JPEG", quality=88, optimize=True)
    print("placeholder written:", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
