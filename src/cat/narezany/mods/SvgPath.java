package cat.narezany.mods;

import android.graphics.Path;
import android.graphics.RectF;

/** Мини-разбор SVG path (M L H V C S Z и A, абсолютные и относительные) для значков без ресурсов. */
final class SvgPath {
    private final String s;
    private int i;

    private SvgPath(String s) {
        this.s = s;
    }

    static Path parse(String d) {
        return new SvgPath(d).run();
    }

    private Path run() {
        Path p = new Path();
        float x = 0, y = 0, sx = 0, sy = 0, cx = 0, cy = 0;
        char cmd = 'M';
        while (true) {
            skip();
            if (i >= s.length()) {
                break;
            }
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
            } else if (cmd == 'M') {
                cmd = 'L'; // числа после M без новой команды — это L
            } else if (cmd == 'm') {
                cmd = 'l';
            }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                    x = ox + num();
                    y = oy + num();
                    p.moveTo(x, y);
                    sx = x;
                    sy = y;
                    cx = x;
                    cy = y;
                    break;
                case 'L':
                    x = ox + num();
                    y = oy + num();
                    p.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                case 'H':
                    x = ox + num();
                    p.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                case 'V':
                    y = oy + num();
                    p.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                case 'C': {
                    float x1 = ox + num(), y1 = oy + num(), x2 = ox + num(), y2 = oy + num();
                    x = ox + num();
                    y = oy + num();
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    cx = x2;
                    cy = y2;
                    break;
                }
                case 'S': {
                    float x1 = 2 * x - cx, y1 = 2 * y - cy, x2 = ox + num(), y2 = oy + num();
                    x = ox + num();
                    y = oy + num();
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    cx = x2;
                    cy = y2;
                    break;
                }
                case 'A': {
                    float rx = num(), ry = num(), rot = num();
                    boolean large = flag(), sweep = flag();
                    float nx = ox + num(), ny = oy + num();
                    arc(p, x, y, nx, ny, rx, ry, rot, large, sweep);
                    x = nx;
                    y = ny;
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Z':
                    p.close();
                    x = sx;
                    y = sy;
                    cx = x;
                    cy = y;
                    break;
                default:
                    return p;
            }
        }
        return p;
    }

    private void skip() {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',' || s.charAt(i) == '\n')) {
            i++;
        }
    }

    private boolean flag() {
        skip();
        return s.charAt(i++) == '1'; // флаги дуги бывают слитно: «0016»
    }

    private float num() {
        skip();
        int start = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            i++;
        }
        boolean dot = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                i++;
            } else if (c == '.' && !dot) {
                dot = true;
                i++;
            } else if ((c == 'e' || c == 'E') && i + 1 < s.length()) {
                i += (s.charAt(i + 1) == '-' || s.charAt(i + 1) == '+') ? 2 : 1;
            } else {
                break;
            }
        }
        return Float.parseFloat(s.substring(start, i));
    }

    /** Дуга SVG через центр эллипса (без поворота, для значков этого хватает). */
    private static void arc(Path p, float x0, float y0, float x, float y, float rx, float ry, float rot,
                            boolean large, boolean sweep) {
        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }
        double dx = (x0 - x) / 2.0, dy = (y0 - y) / 2.0;
        double rx2 = rx * rx, ry2 = ry * ry, dx2 = dx * dx, dy2 = dy * dy;
        double lambda = dx2 / rx2 + dy2 / ry2;
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
            rx2 = rx * rx;
            ry2 = ry * ry;
        }
        double k = Math.sqrt(Math.max(0, (rx2 * ry2 - rx2 * dy2 - ry2 * dx2) / (rx2 * dy2 + ry2 * dx2)));
        if (large == sweep) {
            k = -k;
        }
        double cxp = k * rx * dy / ry, cyp = -k * ry * dx / rx;
        double cx = cxp + (x0 + x) / 2.0, cy = cyp + (y0 + y) / 2.0;
        double start = Math.toDegrees(Math.atan2((dy - cyp) / ry, (dx - cxp) / rx));
        double end = Math.toDegrees(Math.atan2((-dy - cyp) / ry, (-dx - cxp) / rx));
        double extent = end - start;
        if (sweep && extent < 0) {
            extent += 360;
        } else if (!sweep && extent > 0) {
            extent -= 360;
        }
        p.arcTo(new RectF((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry)),
                (float) start, (float) extent);
    }
}
