#!/usr/bin/env python3
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# Canvas & Palette Constants
CANVAS_W = 1080
CANVAS_H = 1920
BG_COLOR = (252, 251, 250)      # #FCFBFA (Cream)
TEXT_MAIN = (26, 26, 24)        # #1A1A18 (Charcoal Ink)
TEXT_MUTED = (124, 122, 117)   # #7C7A75 (Muted Steel)
ACCENT_BLUE = (46, 91, 255)    # #2E5BFF (Utility Blue)
FRAME_BODY = (26, 26, 24)       # Bezel color
FRAME_STROKE = (210, 208, 202)  # Subtle outer border

SCREENSHOT_ITEMS = [
    {
        "file": "Screenshot_20260725_075131.png",
        "output_name": "phone-01-live-preview-1080x1920.png",
        "tagline": "Instant Live Preview",
        "subtitle": "Type and preview rendered Markdown formatting seamlessly."
    },
    {
        "file": "Screenshot_20260725_075052.png",
        "output_name": "phone-02-workspace-1080x1920.png",
        "tagline": "Local File Explorer",
        "subtitle": "Navigate folders and documents with zero cloud lock-in."
    },
    {
        "file": "Screenshot_20260725_075141.png",
        "output_name": "phone-03-hemingway-1080x1920.png",
        "tagline": "Hemingway Analyzer",
        "subtitle": "Evaluate grade levels and readability metrics on demand."
    },
    {
        "file": "Screenshot_20260725_075155.png",
        "output_name": "phone-04-themes-1080x1920.png",
        "tagline": "Cream & Charcoal Themes",
        "subtitle": "Switch view modes and calm typographic color schemes."
    },
    {
        "file": "Screenshot_20260725_075203.png",
        "output_name": "phone-05-sticky-selection-1080x1920.png",
        "tagline": "Sticky Selection Control",
        "subtitle": "Apply stacked formatting without losing text selection."
    },
    {
        "file": "Screenshot_20260725_075208.png",
        "output_name": "phone-06-open-source-1080x1920.png",
        "tagline": "100% Open Source",
        "subtitle": "Built for community, local privacy, and Android sovereignty."
    }
]

def load_font(name, size):
    paths = [
        f"/System/Library/Fonts/{name}.ttc",
        f"/System/Library/Fonts/Supplemental/{name}.ttf",
        f"/Library/Fonts/{name}.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Supplemental/Arial.ttf"
    ]
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size, index=0)
            except Exception:
                pass
    return ImageFont.load_default()

def create_framed_graphic(item, input_dir, output_dirs):
    src_path = os.path.join(input_dir, item["file"])
    if not os.path.exists(src_path):
        print(f"Skipping {item['file']} - file not found.")
        return

    # Load raw screenshot
    raw_img = Image.open(src_path).convert("RGBA")
    
    # Create canvas
    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG_COLOR + (255,))
    draw = ImageDraw.Draw(canvas)

    # Fonts
    font_tagline = load_font("Helvetica", 54)
    font_sub = load_font("Helvetica", 30)

    # Draw Tagline Text (Top Center)
    tagline_text = item["tagline"]
    sub_text = item["subtitle"]

    # Measure tagline
    bbox_tagline = draw.textbbox((0, 0), tagline_text, font=font_tagline)
    w_tag = bbox_tagline[2] - bbox_tagline[0]
    draw.text(((CANVAS_W - w_tag) // 2, 100), tagline_text, font=font_tagline, fill=TEXT_MAIN)

    # Measure subtitle
    bbox_sub = draw.textbbox((0, 0), sub_text, font=font_sub)
    w_sub = bbox_sub[2] - bbox_sub[0]
    draw.text(((CANVAS_W - w_sub) // 2, 175), sub_text, font=font_sub, fill=TEXT_MUTED)

    # Calculate Device Frame Positioning
    target_screen_w = 720
    target_screen_h = 1600
    resized_screen = raw_img.resize((target_screen_w, target_screen_h), Image.Resampling.LANCZOS)

    bezel_padding = 16
    frame_w = target_screen_w + (bezel_padding * 2)
    frame_h = target_screen_h + (bezel_padding * 2)

    frame_x = (CANVAS_W - frame_w) // 2
    frame_y = 260

    # Draw Device Frame Shadow
    shadow_pad = 40
    shadow_img = Image.new("RGBA", (frame_w + shadow_pad * 2, frame_h + shadow_pad * 2), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow_img)
    shadow_draw.rounded_rectangle(
        [shadow_pad, shadow_pad, shadow_pad + frame_w, shadow_pad + frame_h],
        radius=44,
        fill=(0, 0, 0, 35)
    )
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(18))
    canvas.paste(shadow_img, (frame_x - shadow_pad, frame_y - shadow_pad + 12), shadow_img)

    # Draw Device Bezel Body
    bezel_img = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
    bezel_draw = ImageDraw.Draw(bezel_img)
    bezel_draw.rounded_rectangle(
        [0, 0, frame_w, frame_h],
        radius=44,
        fill=FRAME_BODY + (255,),
        outline=FRAME_STROKE + (255,),
        width=2
    )

    # Create Rounded Mask for Screen
    screen_mask = Image.new("L", (target_screen_w, target_screen_h), 0)
    mask_draw = ImageDraw.Draw(screen_mask)
    mask_draw.rounded_rectangle([0, 0, target_screen_w, target_screen_h], radius=32, fill=255)

    # Composite Screen into Bezel
    bezel_img.paste(resized_screen, (bezel_padding, bezel_padding), screen_mask)

    # Draw Punch-Hole Camera Notch at top center of screen
    notch_r = 10
    notch_x = frame_w // 2
    notch_y = bezel_padding + 22
    bezel_draw.ellipse(
        [notch_x - notch_r, notch_y - notch_r, notch_x + notch_r, notch_y + notch_r],
        fill=(10, 10, 10, 255)
    )

    # Paste Bezel Frame onto Canvas
    canvas.paste(bezel_img, (frame_x, frame_y), bezel_img)

    # Convert to RGB and Save
    final_rgb = Image.new("RGB", (CANVAS_W, CANVAS_H), BG_COLOR)
    final_rgb.paste(canvas, (0, 0), canvas)

    for out_dir in output_dirs:
        os.makedirs(out_dir, exist_ok=True)
        dest = os.path.join(out_dir, item["output_name"])
        final_rgb.save(dest, "PNG", quality=95)
        print(f"Generated: {dest}")

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    input_dir = os.path.join(base_dir, "website", "screenshots")
    
    output_dirs = [
        os.path.join(base_dir, "website", "screenshots", "framed"),
        os.path.join(base_dir, "store-assets", "google-play")
    ]

    print("Generating framed screenshots...")
    for item in SCREENSHOT_ITEMS:
        create_framed_graphic(item, input_dir, output_dirs)
    print("Done!")

if __name__ == "__main__":
    main()
