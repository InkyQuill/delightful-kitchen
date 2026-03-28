#!/usr/bin/env python3
"""
Generate GUI textures for CfB-Delight.
Each PNG is 256×256 RGBA.  Main panel is in the top-left (up to 176×imageHeight).
Animation sprite area starts at x=176.
"""

from PIL import Image, ImageDraw
import os

OUT = "src/main/resources/assets/cfb_delight/textures/gui"

# ── Palette ──────────────────────────────────────────────────────────────────
BG      = (198, 198, 198, 255)   # standard MC gray
DARK    = ( 55,  55,  55, 255)   # shadow edge / slot top-left border
LIGHT   = (255, 255, 255, 255)   # highlight edge / slot bottom-right border
SLOT    = (139, 139, 139, 255)   # recessed slot interior
ALPHA0  = (  0,   0,   0,   0)   # transparent

ORANGE  = (220, 100,  20, 255)   # progress arrows / flame body
YELLOW  = (255, 220,   0, 255)   # flame tip highlight

# Tinted slot variants for visual grouping
BOWL_TINT    = (128, 138, 158, 255)   # slightly blue-grey  (bowl slots)
BOTTLE_TINT  = (128, 152, 140, 255)   # slightly green-grey (bottle slots)
FUEL_TINT    = (158, 130, 110, 255)   # brownish            (fuel slots)
EMPTY_FRAME  = (170, 170, 170, 255)   # lighter grey for empty progress areas


# ── Primitives ───────────────────────────────────────────────────────────────

def new_img():
    return Image.new("RGBA", (256, 256), ALPHA0)


def draw_panel(img, w, h):
    """Fill main panel (0,0)→(w-1,h-1) with MC-style raised background."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=BG)
    d.line([0, 0, w - 2, 0], fill=LIGHT)         # top
    d.line([0, 1, 0, h - 2], fill=LIGHT)          # left
    d.line([w - 1, 0, w - 1, h - 1], fill=DARK)  # right
    d.line([0, h - 1, w - 2, h - 1], fill=DARK)  # bottom


def draw_slot(img, x, y, tint=None):
    """Draw a 16×16 recessed slot at (x, y)."""
    d = ImageDraw.Draw(img)
    fill = tint if tint else SLOT
    d.rectangle([x, y, x + 15, y + 15], fill=fill)
    d.line([x, y, x + 15, y], fill=DARK)               # top border
    d.line([x, y, x, y + 15], fill=DARK)               # left border
    d.line([x + 1, y + 15, x + 15, y + 15], fill=LIGHT)  # bottom border
    d.line([x + 15, y + 1, x + 15, y + 15], fill=LIGHT)  # right border


def draw_empty_frame(img, x, y, w, h):
    """Draw a faint recessed frame (no fill change) to hint at the progress area."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=EMPTY_FRAME)
    d.line([x, y, x + w - 1, y], fill=DARK)
    d.line([x, y, x, y + h - 1], fill=DARK)
    d.line([x, y + h - 1, x + w - 1, y + h - 1], fill=LIGHT)
    d.line([x + w - 1, y, x + w - 1, y + h - 1], fill=LIGHT)


def draw_divider(img, y, x1=7, x2=168):
    """Two-pixel recessed horizontal divider."""
    d = ImageDraw.Draw(img)
    d.line([x1, y, x2, y], fill=DARK)
    d.line([x1, y + 1, x2, y + 1], fill=LIGHT)


def draw_player_inv(img, inv_y):
    """Draw 3×9 inventory + hotbar (4 px gap) at inv_y."""
    for row in range(3):
        for col in range(9):
            draw_slot(img, 8 + col * 18, inv_y + row * 18)
    for col in range(9):
        draw_slot(img, 8 + col * 18, inv_y + 3 * 18 + 4)


# ── Animation sprites ────────────────────────────────────────────────────────

def draw_down_arrow(img, x, y):
    """9×14 downward-pointing progress arrow at UV (x, y).
    Rendered top-first (partial height = partial progress).
    Shaft rows 0-9 (3 px wide) + arrowhead rows 10-13.
    """
    # Shaft: cols 3-5
    for row in range(10):
        for col in range(3, 6):
            img.putpixel((x + col, y + row), ORANGE)
    # Arrowhead (converging to tip)
    head = [
        "XXXXXXXXX",   # row 10
        ".XXXXXXX.",   # row 11
        "...XXX...",   # row 12
        "....X....",   # row 13
    ]
    for ry, row_str in enumerate(head):
        for rx, ch in enumerate(row_str):
            if ch == 'X':
                img.putpixel((x + rx, y + 10 + ry), ORANGE)


def draw_fire(img, x, y):
    """9×14 fuel-fire sprite at UV (x, y).
    Rendered from the bottom (shrinks from top as fuel depletes).
    Row 0 = flame tip (yellow), rows 3-13 = orange body.
    """
    pattern = [
        "...X.X...",   # 0  flame tips (yellow)
        "..X.X.X..",   # 1
        ".X.XXX.X.",   # 2
        "XXXXXXXXX",   # 3  solid body (orange)
        "XXXXXXXXX",   # 4
        ".XXXXXXX.",   # 5
        ".XXXXXXX.",   # 6
        ".XXXXXXX.",   # 7
        "..XXXXX..",   # 8
        "..XXXXX..",   # 9
        "..XXXXX..",   # 10
        "...XXX...",   # 11
        "...XXX...",   # 12
        "...XXX...",   # 13
    ]
    for ry, row_str in enumerate(pattern):
        for rx, ch in enumerate(row_str):
            if ch == 'X':
                color = YELLOW if ry < 3 else ORANGE
                img.putpixel((x + rx, y + ry), color)


def draw_right_arrow(img, x, y):
    """22×16 right-pointing arrow at UV (x, y). Always drawn at full size."""
    for row in range(16):
        for col in range(22):
            draw_px = False
            # Stem: 4 rows high, first 14 cols
            if col <= 13 and 6 <= row <= 9:
                draw_px = True
            # Arrowhead: triangle expanding rightward
            elif col >= 14:
                dist = col - 13        # 1 … 8
                centre = 7.5
                if abs(row - centre) < dist:
                    draw_px = True
            if draw_px:
                img.putpixel((x + col, y + row), ORANGE)


# ── Individual GUIs ──────────────────────────────────────────────────────────

def make_grill():
    """
    176×196
    Input row y=18 (6 slots), output row y=54 (6 slots),
    fuel slot (152,36), progress frames between rows (y=37).
    Player inv y=114,132,150 + hotbar y=172.
    """
    img = new_img()
    W, H = 176, 196
    draw_panel(img, W, H)

    for i in range(6):
        draw_slot(img, 8 + i * 18, 18)          # input
        draw_slot(img, 8 + i * 18, 54)          # output
        draw_empty_frame(img, 10 + i * 18, 37, 9, 14)  # progress hint

    draw_slot(img, 152, 36)                      # fuel

    draw_divider(img, 108)
    draw_player_inv(img, 114)

    # Sprite area
    draw_down_arrow(img, 176, 0)   # progress flame  UV(176,0)
    draw_fire(img, 185, 0)         # fuel fire        UV(185,0)
    return img


def make_pot_stand():
    """
    176×188
    Ingredients 0-2 (y=18), 3-5 (y=36); container (62,54); output (116,54);
    fuel (8,72); progress frame (80,37).
    Player inv y=96,114,132 + hotbar y=154.
    inventoryLabelY = 188-94 = 94.
    """
    img = new_img()
    W, H = 176, 188
    draw_panel(img, W, H)

    for i in range(3):
        draw_slot(img, 8 + i * 18, 18)   # ingredients top row
        draw_slot(img, 8 + i * 18, 36)   # ingredients bottom row

    draw_slot(img, 62, 54)               # container
    draw_slot(img, 116, 54)             # output
    draw_slot(img, 8, 72, tint=FUEL_TINT)  # fuel
    draw_empty_frame(img, 80, 37, 9, 14)   # progress hint

    draw_divider(img, 90)
    draw_player_inv(img, 96)

    draw_down_arrow(img, 176, 0)
    draw_fire(img, 185, 0)
    return img


def make_auto_cutter():
    """
    176×166
    Input (26,35); output 2×2 at (98,26),(116,26),(98,44),(116,44).
    Arrow sprite from UV(176,0) drawn at (53,34) by renderBg.
    Player inv y=84,102,120 + hotbar y=142.
    """
    img = new_img()
    W, H = 176, 166
    draw_panel(img, W, H)

    draw_slot(img, 26, 35)               # input
    for r in range(2):
        for c in range(2):
            draw_slot(img, 98 + c * 18, 26 + r * 18)  # output 2×2

    draw_divider(img, 74)
    draw_player_inv(img, 84)

    draw_right_arrow(img, 176, 0)        # UV(176,0) drawn each frame by renderBg
    return img


def make_container_storage():
    """
    176×196
    Bowl row (8 slots, y=28), bottle row (8 slots, y=56), general (9 slots, y=84).
    Player inv y=114,132,150 + hotbar y=172.
    Section labels drawn by renderLabels.
    """
    img = new_img()
    W, H = 176, 196
    draw_panel(img, W, H)

    for i in range(8):
        draw_slot(img, 8 + i * 18, 28, tint=BOWL_TINT)     # bowls
        draw_slot(img, 8 + i * 18, 56, tint=BOTTLE_TINT)   # bottles

    for i in range(9):
        draw_slot(img, 8 + i * 18, 84)                      # general

    # Section dividers (between rows, above labels)
    draw_divider(img, 46)   # between bowls(ends y=43) and "Bottles" label(y=44)
    draw_divider(img, 72)   # between bottles(ends y=71) and "Other" label(y=74)
    draw_divider(img, 100)  # above player inventory section
    draw_player_inv(img, 114)
    return img


def make_fuel_provider():
    """
    176×165
    9 fuel slots at y=35.
    Player inv y=83,101,119 + hotbar y=141.
    inventoryLabelY = 165-94 = 71.
    """
    img = new_img()
    W, H = 176, 165
    draw_panel(img, W, H)

    for i in range(9):
        draw_slot(img, 8 + i * 18, 35, tint=FUEL_TINT)

    draw_divider(img, 69)
    # Player inv rows at y=83,101,119; hotbar at y=141 (gap = 141-(119+16) = 6)
    for row in range(3):
        for col in range(9):
            draw_slot(img, 8 + col * 18, 83 + row * 18)
    for col in range(9):
        draw_slot(img, 8 + col * 18, 141)
    return img


def make_cabinet_of_holding():
    """
    176×222
    6×9 storage grid starting y=18.
    Player inv y=140,158,176 + hotbar y=198.
    inventoryLabelY = 222-94 = 128.
    """
    img = new_img()
    W, H = 176, 222
    draw_panel(img, W, H)

    for row in range(6):
        for col in range(9):
            draw_slot(img, 8 + col * 18, 18 + row * 18)

    draw_divider(img, 126)
    # Player inv
    for row in range(3):
        for col in range(9):
            draw_slot(img, 8 + col * 18, 140 + row * 18)
    for col in range(9):
        draw_slot(img, 8 + col * 18, 198)
    return img


# ── Main ─────────────────────────────────────────────────────────────────────

GENERATORS = {
    "grill.png":              make_grill,
    "pot_stand.png":          make_pot_stand,
    "auto_cutter.png":        make_auto_cutter,
    "container_storage.png":  make_container_storage,
    "fuel_provider.png":      make_fuel_provider,
    "cabinet_of_holding.png": make_cabinet_of_holding,
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for filename, gen_fn in GENERATORS.items():
        path = os.path.join(OUT, filename)
        img = gen_fn()
        img.save(path)
        print(f"  wrote {path}")
    print("Done.")
