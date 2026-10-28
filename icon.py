from PIL import Image, ImageDraw
import math, os
os.makedirs('assets', exist_ok=True)
S = 1024
def cyl(d, cx, cy, R):
    d.ellipse([cx-R, cy-R, cx+R, cy+R], fill=(58, 58, 58), outline=(224, 160, 48), width=int(R/10))
    for i in range(6):
        a = math.radians(i*60-90)
        x, y, r = cx+math.cos(a)*R*.55, cy+math.sin(a)*R*.55, R*.2
        d.ellipse([x-r, y-r, x+r, y+r], fill=(176, 30, 30) if i == 0 else (12, 12, 12))
    d.ellipse([cx-R*.12, cy-R*.12, cx+R*.12, cy+R*.12], fill=(224, 160, 48))
bg = Image.new('RGBA', (S, S), (26, 15, 8, 255)); bg.save('assets/icon-background.png')
full = bg.copy(); cyl(ImageDraw.Draw(full), 512, 512, 400); full.save('assets/icon-only.png')
fg = Image.new('RGBA', (S, S), (0, 0, 0, 0)); cyl(ImageDraw.Draw(fg), 512, 512, 290); fg.save('assets/icon-foreground.png')
