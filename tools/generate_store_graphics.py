#!/usr/bin/env python3
"""Generate Google Play graphics from Kern's real app assets.

Store screenshots and the feature graphic use only captured app UI. The Play
icon is derived from the launcher artwork shipped in the Android application.
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store-assets" / "google-play"
OUT.mkdir(parents=True, exist_ok=True)

CANVAS = "#FCFBFA"
SURFACE = "#F4F3F0"
CREAM = "#ECEAE6"
INK = "#1A1A18"
MUTED = "#7C7A75"
BORDER = "#D9D5CE"
ACCENT = "#2E5BFF"
ICON = "#4A4A4D"
WHITE = "#FFFFFF"

FONT_DIRS = [
    Path("/System/Library/Fonts"),
    Path("/Library/Fonts"),
    Path("/usr/share/fonts/truetype/dejavu"),
]

def font(name: str, size: int):
    candidates = []
    if name == "sans-bold":
        candidates = ["SFNS.ttf", "Helvetica.ttc", "Arial Bold.ttf", "DejaVuSans-Bold.ttf"]
    elif name == "sans":
        candidates = ["SFNS.ttf", "Helvetica.ttc", "Arial.ttf", "DejaVuSans.ttf"]
    elif name == "mono":
        candidates = ["Menlo.ttc", "Monaco.ttf", "DejaVuSansMono.ttf"]
    elif name == "serif-bold":
        candidates = ["NewYork.ttf", "Georgia Bold.ttf", "Times New Roman Bold.ttf", "DejaVuSerif-Bold.ttf"]
    for d in FONT_DIRS:
        for c in candidates:
            p = d / c
            if p.exists():
                try:
                    return ImageFont.truetype(str(p), size)
                except Exception:
                    pass
    return ImageFont.load_default(size=size)

SANS = lambda s: font("sans", s)
BOLD = lambda s: font("sans-bold", s)
MONO = lambda s: font("mono", s)
SERIF_BOLD = lambda s: font("serif-bold", s)

def rounded(draw, box, r, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def text_size(draw, text, f):
    b = draw.textbbox((0,0), text, font=f)
    return b[2]-b[0], b[3]-b[1]

def wrap(draw, text, f, max_w):
    words = text.split()
    lines, current = [], ""
    for w in words:
        test = w if not current else current + " " + w
        if text_size(draw, test, f)[0] <= max_w:
            current = test
        else:
            if current:
                lines.append(current)
            current = w
    if current:
        lines.append(current)
    return lines

def draw_wrapped(draw, text, xy, f, fill, max_w, line_h=None):
    x, y = xy
    if line_h is None:
        line_h = int(f.size * 1.35)
    for line in wrap(draw, text, f, max_w):
        draw.text((x,y), line, font=f, fill=fill)
        y += line_h
    return y

def draw_grid(img_or_draw, w, h, step=64):
    """Draw an extremely quiet structural grid.

    ImageDraw on RGBA writes alpha directly; when later converted to RGB it
    can look black instead of translucent. Draw onto a layer and composite.
    """
    base = getattr(img_or_draw, "im", None)
    # If an ImageDraw was passed, recover its backing image when possible.
    # Otherwise assume the first argument is already the Image.
    img = img_or_draw if isinstance(img_or_draw, Image.Image) else None
    if img is None:
        return
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=(26, 26, 24, 10), width=1)
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=(26, 26, 24, 10), width=1)
    img.alpha_composite(layer)

def kern_mark(draw, cx, cy, size):
    r = size // 2
    draw.ellipse((cx-r, cy-r, cx+r, cy+r), fill=ICON)
    # subtle bottom shadow
    shadow = Image.new("RGBA", (size, size), (0,0,0,0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse((0, size*0.22, size, size*1.14), fill=(0,0,0,34))
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse((0,0,size,size), fill=255)
    shadow.putalpha(mask)
    # Can't paste here without base; skip shadow in mark helper for simplicity
    kf = SERIF_BOLD(int(size*0.58))
    tw, th = text_size(draw, "K", kf)
    draw.text((cx - tw/2, cy - th/2 - size*0.04), "K", font=kf, fill=CREAM)

def add_shadow(base, box, radius, blur=34, alpha=28):
    layer = Image.new("RGBA", base.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle(box, radius=radius, fill=(26,26,24,alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(layer)

def editor_card(base, x, y, w, h, mode="split"):
    add_shadow(base, (x+10,y+16,x+w+10,y+h+16), 46, 36, 28)
    d = ImageDraw.Draw(base)
    rounded(d, (x,y,x+w,y+h), 44, (252,251,250,240), BORDER, 2)
    d.ellipse((x+34,y+38,x+46,y+50), fill=ACCENT)
    d.text((x+64,y+33), "Project › notes › draft.md", font=SANS(18), fill=MUTED)
    inner=(x+28,y+88,x+w-28,y+h-28)
    rounded(d, inner, 34, WHITE)
    if mode == "split":
        rail_w=int((inner[2]-inner[0])*0.34)
        rounded(d, (inner[0],inner[1],inner[0]+rail_w,inner[3]), 34, SURFACE)
        d.text((inner[0]+34,inner[1]+34), "FILES", font=BOLD(14), fill=MUTED)
        d.text((inner[0]+34,inner[1]+82), "draft.md", font=BOLD(20), fill=INK)
        d.text((inner[0]+34,inner[1]+128), "research.md", font=SANS(20), fill=MUTED)
        d.text((inner[0]+34,inner[1]+174), "outline.md", font=SANS(20), fill=MUTED)
        tx=inner[0]+rail_w+56
    else:
        tx=inner[0]+54
    title_size = 34 if mode == "single" else 42
    d.text((tx,inner[1]+34), "EDITOR", font=BOLD(14), fill=MUTED)
    d.text((tx,inner[1]+84), "Quiet text,", font=BOLD(title_size), fill=INK)
    d.text((tx,inner[1]+84+title_size+12), "fast structure", font=BOLD(title_size), fill=INK)
    yy=inner[1]+84+(title_size*2)+50
    if mode == "single":
        d.text((tx, yy), "Markdown stays readable.", font=SANS(19), fill=MUTED)
        rounded(d, (tx, yy+48, min(tx+218, inner[2]-34), yy+96), 14, SURFACE)
        d.text((tx+16, yy+62), "## Next section", font=MONO(17), fill=INK)
        return
    yy=draw_wrapped(d, "Kern treats markdown as a working document, not a formatting trick. The surface stays plain until you need structure.", (tx, yy), SANS(22), MUTED, inner[2]-tx-44, 34)
    rounded(d, (tx, yy+30, tx+228, yy+82), 14, SURFACE)
    d.text((tx+18, yy+44), "## Next section", font=MONO(20), fill=INK)

def make_feature():
    w,h = 1024,500
    img = Image.new("RGBA", (w,h), CANVAS)
    d = ImageDraw.Draw(img)
    icon = launcher_icon(112)
    img.alpha_composite(icon, (64, 62))
    d.text((64, 210), "Kern", font=SERIF_BOLD(68), fill=INK)
    d.text((66, 296), "Markdown that", font=BOLD(42), fill=INK)
    d.text((66, 348), "stays readable.", font=BOLD(42), fill=INK)

    captures = [
        ROOT / "website/screenshots/Screenshot_20260725_075052.png",
        ROOT / "website/screenshots/Screenshot_20260725_075131.png",
        ROOT / "website/screenshots/Screenshot_20260725_075141.png",
    ]
    for index, source in enumerate(captures):
        shot = crop_phone_capture(source).resize((202, 359), Image.Resampling.LANCZOS)
        mask = Image.new("L", shot.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), 22, fill=255)
        shot.putalpha(mask)
        x = 400 + index * 194
        y = 70 + (index % 2) * 28
        add_shadow(img, (x + 8, y + 12, x + shot.width + 8, y + shot.height + 12), 22, 18, 34)
        img.alpha_composite(shot, (x, y))
    img.convert("RGB").save(OUT / "feature-graphic-1024x500.png", quality=95)

def phone_frame(base, x, y, w, h, title, subtitle, body_lines=None, split=False):
    d=ImageDraw.Draw(base)
    add_shadow(base, (x+8,y+18,x+w+8,y+h+18), 58, 36, 28)
    rounded(d, (x,y,x+w,y+h), 58, "#111111")
    rounded(d, (x+18,y+18,x+w-18,y+h-18), 42, CANVAS)
    # status pill
    rounded(d, (x+w//2-70,y+30,x+w//2+70,y+46), 8, "#111111")
    d.text((x+50,y+86), "Kern", font=BOLD(38), fill=INK)
    d.text((x+50,y+140), title, font=BOLD(34), fill=INK)
    d.text((x+50,y+188), subtitle, font=SANS(22), fill=MUTED)
    if split:
        rounded(d, (x+46,y+260,x+w-46,y+h-70), 34, WHITE, BORDER, 2)
        d.text((x+84,y+304), "FILES", font=BOLD(15), fill=MUTED)
        d.text((x+84,y+354), "draft.md", font=BOLD(26), fill=INK)
        d.text((x+84,y+404), "research.md", font=SANS(24), fill=MUTED)
        d.text((x+84,y+454), "outline.md", font=SANS(24), fill=MUTED)
        d.line((x+46,y+526,x+w-46,y+526), fill=BORDER, width=2)
        d.text((x+84,y+568), "EDITOR", font=BOLD(15), fill=MUTED)
        d.text((x+84,y+626), "Quiet text,", font=BOLD(42), fill=INK)
        d.text((x+84,y+684), "fast structure", font=BOLD(42), fill=INK)
        draw_wrapped(d, "Kern keeps markdown readable and close to the file tree.", (x+84,y+760), SANS(25), MUTED, w-168, 38)
    else:
        rounded(d, (x+46,y+264,x+w-46,y+h-70), 34, WHITE, BORDER, 2)
        yy=y+314
        for line, style in body_lines or []:
            if style == "h":
                d.text((x+84,yy), line, font=BOLD(38), fill=INK); yy += 60
            elif style == "code":
                rounded(d, (x+84,yy,x+w-84,yy+58), 14, SURFACE)
                d.text((x+106,yy+16), line, font=MONO(22), fill=INK); yy += 92
            else:
                yy=draw_wrapped(d,line,(x+84,yy),SANS(26),MUTED,w-168,40)+18

def make_screenshot(path, eyebrow, headline, body, phone_mode, body_lines=None, split=False):
    W,H=1080,1920
    img=Image.new("RGBA",(W,H),CANVAS)
    d=ImageDraw.Draw(img)
    draw_grid(img,W,H,72)
    kern_mark(d, 102, 104, 82)
    d.text((160,75), "Kern", font=BOLD(42), fill=INK)
    d.text((80,236), eyebrow.upper(), font=BOLD(22), fill=MUTED)
    y=286
    for line in headline.split("\n"):
        d.text((80,y), line, font=BOLD(74), fill=INK)
        y+=82
    draw_wrapped(d, body, (84,y+18), SANS(31), MUTED, 760, 46)
    phone_frame(img, 254, 760, 572, 980, phone_mode[0], phone_mode[1], body_lines=body_lines, split=split)
    img.convert("RGB").save(OUT / path, quality=95)

def make_screenshots():
    captures = [
        ("phone-01-local-markdown-1080x1920.png", "Screenshot_20260725_075052.png"),
        ("phone-02-focused-editor-1080x1920.png", "Screenshot_20260725_075131.png"),
        ("phone-03-file-tree-1080x1920.png", "Screenshot_20260725_075141.png"),
        ("phone-04-privacy-1080x1920.png", "Screenshot_20260725_075155.png"),
    ]
    source_dir = ROOT / "website" / "screenshots"
    for destination, source in captures:
        crop_phone_capture(source_dir / source).convert("RGB").save(OUT / destination, optimize=True)

def make_icon():
    launcher_icon(512).save(OUT / "app-icon-512.png", optimize=True)

def launcher_icon(size):
    """Return the exact launcher artwork shipped with the app, resized."""
    source = Image.open(ROOT / "app/src/main/res/drawable/ic_launcher_full_fg.png").convert("RGBA")
    alpha = source.getchannel("A")
    bounds = alpha.getbbox()
    if not bounds:
        raise ValueError("Launcher artwork has no visible pixels")
    return source.crop(bounds).resize((size, size), Image.Resampling.LANCZOS)

def crop_phone_capture(path):
    """Remove emulator chrome while preserving only captured in-app pixels."""
    source = Image.open(path).convert("RGBA")
    if source.size != (1080, 2400):
        raise ValueError(f"Unexpected phone capture size for {path}: {source.size}")
    return source.crop((0, 120, 1080, 2040))

def make_contact_sheet():
    assets = [
        OUT / "app-icon-512.png",
        OUT / "feature-graphic-1024x500.png",
        OUT / "phone-01-local-markdown-1080x1920.png",
        OUT / "phone-02-focused-editor-1080x1920.png",
        OUT / "phone-03-file-tree-1080x1920.png",
        OUT / "phone-04-privacy-1080x1920.png",
    ]
    sheet = Image.new("RGB", (1280, 1180), "#E8E6E1")
    icon = Image.open(assets[0]).convert("RGB").resize((240, 240), Image.Resampling.LANCZOS)
    feature = Image.open(assets[1]).convert("RGB").resize((512, 250), Image.Resampling.LANCZOS)
    sheet.paste(icon, (32, 32))
    sheet.paste(feature, (304, 32))
    for index, path in enumerate(assets[2:]):
        shot = Image.open(path).convert("RGB").resize((270, 480), Image.Resampling.LANCZOS)
        sheet.paste(shot, (32 + index * 302, 330))
    sheet.save(OUT / "contact-sheet.png", optimize=True)

def make_social_preview():
    w,h=1200,630
    img=Image.new("RGBA",(w,h),CANVAS)
    d=ImageDraw.Draw(img)
    img.alpha_composite(launcher_icon(116), (72, 68))
    d.text((72, 234), "Kern", font=SERIF_BOLD(76), fill=INK)
    d.text((74, 334), "Markdown that", font=BOLD(48), fill=INK)
    d.text((74, 392), "stays readable.", font=BOLD(48), fill=INK)
    captures = [
        ROOT / "website/screenshots/Screenshot_20260725_075052.png",
        ROOT / "website/screenshots/Screenshot_20260725_075131.png",
        ROOT / "website/screenshots/Screenshot_20260725_075141.png",
    ]
    for index, source in enumerate(captures):
        shot = crop_phone_capture(source).resize((236, 420), Image.Resampling.LANCZOS)
        mask = Image.new("L", shot.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), 24, fill=255)
        shot.putalpha(mask)
        x = 480 + index * 220
        y = 74 + (index % 2) * 34
        add_shadow(img, (x + 8, y + 12, x + shot.width + 8, y + shot.height + 12), 24, 20, 34)
        img.alpha_composite(shot, (x, y))
    img.convert("RGB").save(OUT / "social-preview-1200x630.png", quality=95)

if __name__ == "__main__":
    make_feature()
    make_icon()
    make_social_preview()
    make_screenshots()
    make_contact_sheet()
    print(f"Generated assets in {OUT}")
    for p in sorted(OUT.glob("*.png")):
        im=Image.open(p)
        print(f"{p.relative_to(ROOT)} {im.size[0]}x{im.size[1]}")
