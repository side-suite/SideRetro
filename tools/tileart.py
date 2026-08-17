#!/usr/bin/env python3
"""
Turns a Keytile SVG from `sideretro/tiles-imgs` into Kotlin geometry the legend can draw.

Note when editing the generated file's header: Kotlin nests block comments, so a literal
"slash star" inside a KDoc — a glob like `tiles-imgs/<star>.svg` — opens a comment that the
closing delimiter then only half-closes. It fails as "unclosed comment" pointing at the file's
last line, which is a long way from the cause.

    python3 tools/tileart.py tiles-imgs/mini-controller-layers.svg MINI_CONTROLLER

Why a converter rather than an SVG parser in the app: every shape these tiles need is a circle
or a rounded rect, often rotated in place. Emitting that as data costs nothing at runtime and
skips a rendering dependency — and it fails loudly *here*, at build time, if artwork arrives
with a shape or an id the app cannot use.

The SVG contract (see SID-206):
  #body     — plate, bezel, non-interactive detail, and OS keys that can never light up
  #legends  — the glyphs printed on the physical tile
  #buttons  — one shape per app-reachable key, id = the Android keycode name

Illustrator mangles ids on export: `#` becomes `_x23_` and `_` becomes `_x5F_`, so
`#buttons` arrives as `_x23_buttons` and `KEYCODE_BUTTON_B` as `KEYCODE_x5F_BUTTON_x5F_B`.
Both are unescaped here rather than being made Oliver's problem.
"""

import math
import re
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"


def unescape_id(raw):
    """Undo Illustrator's `_xHH_` escaping."""
    return re.sub(r"_x([0-9A-Fa-f]{2})_", lambda m: chr(int(m.group(1), 16)), raw or "")


def parse_styles(root):
    """Maps a class name to (fill, stroke, stroke_width) from the embedded <style> block."""
    styles = {}
    for style in root.iter(SVG + "style"):
        text = style.text or ""
        # Illustrator emits shared rules like `.st1, .st2 { ... }`, so a block can name several.
        for selectors, body in re.findall(r"([^{}]+)\{([^}]*)\}", text):
            names = [s.strip().lstrip(".") for s in selectors.split(",") if s.strip().startswith(".")]
            if not names:
                continue
            props = dict(
                (k.strip(), v.strip())
                for k, v in (p.split(":", 1) for p in body.split(";") if ":" in p)
            )
            for name in names:
                entry = styles.setdefault(name, {})
                entry.update(props)
    return styles


def style_of(styles, element):
    """The effective properties we need from Illustrator's class-based export."""
    props = {}
    for name in (element.get("class") or "").split():
        props.update(styles.get(name, {}))
    # A handful of hand-edited assets use presentation attributes instead of a class.
    for name in ("display", "visibility", "fill", "stroke", "stroke-width"):
        if element.get(name) is not None:
            props[name] = element.get(name)
    return props


def hidden(styles, element):
    """Illustrator keeps hidden construction layers in the SVG; they are not artwork."""
    props = style_of(styles, element)
    return props.get("display") == "none" or props.get("visibility") == "hidden"


def transform_of(element):
    """Returns (translate_x, translate_y, rotation_degrees) for the transforms Illustrator emits."""
    raw = element.get("transform", "")
    tx = ty = rot = 0.0
    for name, args in re.findall(r"(translate|rotate|matrix)\(([^)]*)\)", raw):
        nums = [float(n) for n in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", args)]
        if name == "translate":
            tx, ty = nums[0], (nums[1] if len(nums) > 1 else 0.0)
        elif name == "rotate":
            rot = nums[0]
        elif name == "matrix":
            raise SystemExit(
                f"matrix() transform on <{element.tag.split('}')[-1]} id={element.get('id')}>. "
                "Re-export with transforms flattened, or as translate/rotate only."
            )
    return tx, ty, rot


def apply(tx, ty, rot, x, y):
    """SVG applies translate then rotate, so a point goes through R first, then T."""
    a = math.radians(rot)
    return (tx + x * math.cos(a) - y * math.sin(a),
            ty + x * math.sin(a) + y * math.cos(a))


def shape_of(element, styles=None):
    """
    Normalises a circle or rect into a centre-anchored primitive.

    Illustrator rotates shapes about their own centre, so carrying the rotation as an angle plus a
    centre is both exact and trivial to draw — no path data, no matrix maths at runtime.
    """
    tag = element.tag.split("}")[-1]
    tx, ty, rot = transform_of(element)

    if tag == "circle":
        cx, cy = apply(tx, ty, rot, float(element.get("cx")), float(element.get("cy")))
        r = float(element.get("r"))
        return f"Circle({cx:.2f}f, {cy:.2f}f, {r:.2f}f)"

    if tag == "rect":
        x, y = float(element.get("x")), float(element.get("y"))
        w, h = float(element.get("width")), float(element.get("height"))
        cx, cy = apply(tx, ty, rot, x + w / 2, y + h / 2)
        rx = float(element.get("rx") or 0)
        ry = float(element.get("ry") or rx)
        return f"RoundRect({cx:.2f}f, {cy:.2f}f, {w:.2f}f, {h:.2f}f, {rx:.2f}f, {ry:.2f}f, {rot:.2f}f)"

    if tag == "line":
        # Illustrator exports the QWERTY key grid as stroked lines.  TileArt has no separate
        # line primitive, but a thin solid RoundRect preserves the authored geometry exactly
        # enough at legend scale and keeps the runtime representation deliberately small.
        x1, y1 = apply(tx, ty, rot, float(element.get("x1")), float(element.get("y1")))
        x2, y2 = apply(tx, ty, rot, float(element.get("x2")), float(element.get("y2")))
        width = math.hypot(x2 - x1, y2 - y1)
        if width == 0:
            raise SystemExit("A legend line needs two distinct endpoints.")
        props = style_of(styles or {}, element)
        stroke = float(re.sub(r"[^0-9.eE+-]", "", props.get("stroke-width", "1")) or "1")
        angle = math.degrees(math.atan2(y2 - y1, x2 - x1))
        return f"RoundRect({(x1 + x2) / 2:.2f}f, {(y1 + y2) / 2:.2f}f, {width:.2f}f, {stroke:.2f}f, 0.00f, 0.00f, {angle:.2f}f)"

    raise SystemExit(
        f"Unsupported <{tag}> in the artwork. The legend can draw circles and rects; "
        "convert anything else, or extend this script and the Shape type together."
    )


def numpad_name(element):
    """Recover the intended T9 key name from its physical cell.

    Illustrator gave the three centre-column overlay rectangles duplicate ``KEYCODE_0`` ids
    (with suffixed XML ids to keep the document valid).  Their geometry is authoritative: the
    cells are the ordinary 1–9 / star–0–pound matrix.  Key 5 intentionally has no SideRetro
    binding, so it is deliberately omitted rather than silently becoming a second 0 overlay.
    """
    tag = element.tag.split("}")[-1]
    raw = unescape_id(element.get("id"))
    if tag == "path":
        if raw == "KEYCODE_STAR":
            return "KEYCODE_STAR"
        if raw == "KEYCODE_POUND":
            return "KEYCODE_POUND"
        raise SystemExit(f"Unsupported numpad path overlay {raw!r}.")

    x = float(element.get("x"))
    y = float(element.get("y"))
    column = 0 if x < 675 else 1 if x < 1245 else 2
    row = 0 if y < 790 else 1 if y < 1130 else 2 if y < 1475 else 3
    matrix = (
        ("KEYCODE_1", "KEYCODE_2", "KEYCODE_3"),
        ("KEYCODE_4", None, "KEYCODE_6"),
        ("KEYCODE_7", "KEYCODE_8", "KEYCODE_9"),
        ("KEYCODE_STAR", "KEYCODE_0", "KEYCODE_POUND"),
    )
    return matrix[row][column]


def numpad_path_shape(element):
    """The two outer bottom keys are rounded SVG paths; use their exact cell bounds as caps."""
    raw = unescape_id(element.get("id"))
    if raw == "KEYCODE_STAR":
        return "RoundRect(389.55f, 1645.65f, 561.90f, 337.30f, 0.00f, 0.00f, 0.00f)"
    if raw == "KEYCODE_POUND":
        return "RoundRect(1530.45f, 1645.65f, 561.90f, 337.30f, 0.00f, 0.00f, 0.00f)"
    raise SystemExit(f"Unsupported numpad path overlay {raw!r}.")


def role_of(styles, element):
    """A stroked-but-unfilled shape is an outline; anything else is a solid."""
    props = style_of(styles, element)
    fill = props.get("fill", "none")
    return "OUTLINE" if fill == "none" else "SOLID"


def group(root, name):
    for g in root.iter(SVG + "g"):
        if unescape_id(g.get("id")) == name:
            return g
    raise SystemExit(f"No <g id=\"{name}\"> in the artwork. See the layer contract in SID-206.")


def optional_group(root, name):
    """Return a layer when the drawing has one, otherwise no artwork for that layer.

    Sundial deliberately has no printed key legends: its rings and corners are contextual game
    controls, so SideRetro supplies their labels at runtime.  `#body` and `#buttons` are still
    mandatory, but making an empty `#legends` group mandatory would turn that intentional absence
    into an export failure.
    """
    for g in root.iter(SVG + "g"):
        if unescape_id(g.get("id")) == name:
            return g
    return None


def canonical_button_name(name, tile):
    """Accept the Sundial export's extra `BUTTON_` prefix on DPAD keycodes.

    Android names these keys `KEYCODE_DPAD_*`; `KEYCODE_BUTTON_DPAD_*` is a useful Illustrator
    layer mnemonic but not a key Android can resolve.  Normalising at conversion keeps the source
    graphic readable without letting an invalid keycode leak into the app.
    """
    if tile == "SUNDIAL":
        return {
            "KEYCODE_BUTTON_DPAD_UP": "KEYCODE_DPAD_UP",
            "KEYCODE_BUTTON_DPAD_DOWN": "KEYCODE_DPAD_DOWN",
            "KEYCODE_BUTTON_DPAD_LEFT": "KEYCODE_DPAD_LEFT",
            "KEYCODE_BUTTON_DPAD_RIGHT": "KEYCODE_DPAD_RIGHT",
            "KEYCODE_BUTTON_MEDIA_PREVIOUS": "KEYCODE_MEDIA_PREVIOUS",
            "KEYCODE_BUTTON_MEDIA_NEXT": "KEYCODE_MEDIA_NEXT",
            "KEYCODE_BUTTON_ENTER": "KEYCODE_ENTER",
            "KEYCODE_BUTTON_TAB": "KEYCODE_TAB",
        }.get(name, name)
    return name


def shapes_in(styles, container):
    """Yield visible circles and rounded rects, including Illustrator's grouping wrappers."""
    for element in container:
        if hidden(styles, element):
            continue
        tag = element.tag.split("}")[-1]
        if tag in ("circle", "rect", "line"):
            yield element
        elif tag == "g":
            yield from shapes_in(styles, element)
        else:
            raise SystemExit(
                f"Unsupported <{tag}> in #body. The legend can draw circles and rects; "
                "convert anything else, or extend this script and the Shape type together."
            )


def glyph_text(element):
    """Keep Illustrator's manual line breaks: Select is deliberately a two-line keycap."""
    spans = list(element.iter(SVG + "tspan"))
    lines = ["".join(span.itertext()).strip() for span in spans] if spans else ["".join(element.itertext()).strip()]
    return "\n".join(line for line in lines if line)


def kotlin_string(value):
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace('"', '\\"')


def control_owner(glyph):
    """Printed Start/Select labels are semantic labels even when Illustrator places them off-centre."""
    compact = re.sub(r"[^A-Z]", "", glyph.upper())
    if compact == "START":
        return "KEYCODE_BUTTON_START"
    if compact == "SELECT":
        return "KEYCODE_BUTTON_SELECT"
    return None


def expanded_glyphs(styles, container, geometry):
    """Recover simple key legends when Illustrator has converted its text to outlines.

    The runtime deliberately consumes geometry rather than SVG paths, so it cannot render an
    outlined word verbatim.  Mini Controller exports give each outlined word a useful group id
    (``A``, ``START`` and so on), though.  When there are no editable ``<text>`` nodes, use that
    id and the owning button centre to retain the physical label.  Artists should still prefer
    editable text: it preserves their exact typography and positioning.
    """
    labels = []
    for element in container:
        if element.tag != SVG + "g" or hidden(styles, element):
            # The caller has already filtered hidden layers.  This keeps the helper deliberately
            # narrow: only direct, named legend groups describe printable controls.
            continue
        label = unescape_id(element.get("id"))
        compact = re.sub(r"[^A-Z]", "", label.upper())
        owner = control_owner(compact) or f"KEYCODE_BUTTON_{compact}"
        shape = geometry.get(owner)
        if shape is None:
            continue

        # The current renderer takes Illustrator's text origin, then converts that to a visual
        # centre.  Supply the inverse so recovered glyphs land in the centre of their keycap.
        centre_x, centre_y, extent = shape
        text = "SEL-\\nECT" if compact == "SELECT" else compact
        size = extent if len(text) == 1 else min(103.0, extent)
        x = centre_x - size * 0.3
        y = centre_y + size * 0.36
        labels.append(
            f"        Glyph(\"{kotlin_string(text)}\", {x:.2f}f, {y:.2f}f, "
            f"{size:.2f}f, \"{owner}\"),"
        )
    return labels


def polygon_path(points):
    """Turn SVG polygon points into Android/SVG path data without changing their geometry."""
    nums = re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", points or "")
    if len(nums) < 4 or len(nums) % 2:
        raise SystemExit("A legend polygon needs complete x,y point pairs.")
    pairs = list(zip(nums[::2], nums[1::2]))
    return "M" + " L".join(f"{x},{y}" for x, y in pairs) + " Z"


def rect_path(element):
    """Keep a rectangular outlined glyph (the Select hyphen) as a real vector path."""
    x = element.get("x", "0")
    y = element.get("y", "0")
    w = element.get("width", "0")
    h = element.get("height", "0")
    return f"M{x},{y} h{w} v{h} h-{w} Z"


def circle_path(element):
    """Represent an outlined Illustrator circle as ordinary SVG path geometry."""
    cx = element.get("cx", "0")
    cy = element.get("cy", "0")
    r = element.get("r", "0")
    return f"M{cx},{cy} m-{r},0 a{r},{r} 0 1,0 {float(r) * 2},0 a{r},{r} 0 1,0 -{float(r) * 2},0"


def outlined_glyphs(styles, container):
    """Emit Illustrator's outlined lettering as paths, never as a substitute system font.

    The Mini Controller labels are intentionally hand-positioned: Start follows its diagonal pill
    and Select has an optical line break.  Reducing those outlines to a word plus a guessed centre
    threw that information away.  Android's PathParser can render the original SVG commands, so
    retain the source paths verbatim and let the app apply the artwork transform as a whole.
    """
    glyphs = []
    for group_el in container:
        if group_el.tag != SVG + "g" or hidden(styles, group_el):
            continue
        label = unescape_id(group_el.get("id"))
        compact = re.sub(r"[^A-Z]", "", label.upper())
        owner = control_owner(compact) or (f"KEYCODE_BUTTON_{compact}" if compact else None)
        paths = []
        transforms = set()
        for element in group_el:
            if hidden(styles, element):
                continue
            tag = element.tag.split("}")[-1]
            if tag == "path":
                data = element.get("d")
            elif tag == "polygon":
                data = polygon_path(element.get("points"))
            elif tag == "rect":
                data = rect_path(element)
            elif tag == "circle":
                data = circle_path(element)
            else:
                raise SystemExit(
                    f"Unsupported <{tag}> in #legends. Convert it to a path, polygon or rect."
                )
            if not data:
                continue
            paths.append(data)
            transforms.add(transform_of(element))
        if len(transforms) != 1:
            raise SystemExit(
                f"Outlined legend {label!r} has mixed element transforms. Flatten the group before export."
            )
        if paths:
            tx, ty, rot = transforms.pop()
            kotlin_owner = f'\"{owner}\"' if owner else "null"
            glyphs.append(
                f"        VectorGlyph(\"{kotlin_string(' '.join(paths))}\", {kotlin_owner}, "
                f"{tx:.2f}f, {ty:.2f}f, {rot:.2f}f),"
            )
    return glyphs


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    path, tile = sys.argv[1], sys.argv[2]

    root = ET.parse(path).getroot()
    styles = parse_styles(root)
    view = [float(n) for n in root.get("viewBox").split()]

    required_buttons = {
        "MINI_CONTROLLER": {
            "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT",
            "KEYCODE_BUTTON_X", "KEYCODE_BUTTON_Y", "KEYCODE_BUTTON_A", "KEYCODE_BUTTON_B",
            "KEYCODE_BUTTON_START", "KEYCODE_BUTTON_SELECT",
        },
        "COMPACT_QWERTY": {
            "KEYCODE_E", "KEYCODE_C", "KEYCODE_A", "KEYCODE_G", "KEYCODE_J", "KEYCODE_L",
            "KEYCODE_U", "KEYCODE_O", "KEYCODE_SPACE", "KEYCODE_SHIFT_LEFT", "KEYCODE_BACK",
        },
        "NUMPAD": {
            "KEYCODE_0", "KEYCODE_1", "KEYCODE_2", "KEYCODE_3", "KEYCODE_4",
            "KEYCODE_6", "KEYCODE_7", "KEYCODE_8", "KEYCODE_9", "KEYCODE_STAR",
            "KEYCODE_POUND",
        },
        "SUNDIAL": {
            "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN",
            "KEYCODE_MEDIA_PREVIOUS", "KEYCODE_MEDIA_NEXT", "KEYCODE_MEDIA_PLAY_PAUSE",
            "KEYCODE_ENTER", "KEYCODE_TAB", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT",
        },
    }

    buttons = []
    button_shapes = set()
    owners = []
    geometry = {}
    seen_names = set()
    for element in group(root, "#buttons"):
        if hidden(styles, element):
            continue
        name = numpad_name(element) if tile == "NUMPAD" else canonical_button_name(
            unescape_id(element.get("id")), tile
        )
        # 5 is visibly printed in the source art, but intentionally unbound by Keymap. It stays
        # in #legends only: an overlay would make the miniature promise a control it cannot send.
        if name is None:
            continue
        if not name.startswith("KEYCODE_"):
            raise SystemExit(f"Button id \"{name}\" is not an Android keycode name.")
        if name in seen_names:
            raise SystemExit(f"Button id \"{name}\" is duplicated. Every overlay needs one keycode id.")
        seen_names.add(name)
        shape = (
            numpad_path_shape(element)
            if tile == "NUMPAD" and element.tag == SVG + "path"
            else shape_of(element, styles)
        )
        button_shapes.add(shape)
        buttons.append(f"        \"{name}\" to {shape},")
        nums = [float(n) for n in re.findall(r"-?\d+\.?\d*", shape.split("(", 1)[1])]
        geometry[name] = (nums[0], nums[1], nums[2] if shape.startswith("Circle") else min(nums[2], nums[3]) / 2)
        owners.append((name, geometry[name]))

    expected = required_buttons.get(tile)
    if expected is not None and seen_names != expected:
        missing = ", ".join(sorted(expected - seen_names)) or "none"
        unexpected = ", ".join(sorted(seen_names - expected)) or "none"
        raise SystemExit(
            f"{tile} #buttons does not match the keymap. Missing: {missing}. Unexpected: {unexpected}. "
            "Use each Android keycode name once; KEYCODE_DEL is intentionally not part of Compact QWERTY."
        )

    # A body shape can coincide with a live-button overlay.  Only discard a *solid* duplicate:
    # the runtime supplies that fill from the button's current state.  An outlined body copy is
    # authored visual structure (the Sundial's rings are the important example) and must survive
    # conversion — otherwise replacing a cap silently erases the drawing that locates it.
    body = []
    for shape_el in shapes_in(styles, group(root, "#body")):
        shape = shape_of(shape_el, styles)
        if shape in button_shapes and role_of(styles, shape_el) == "SOLID":
            continue
        body.append(f"        Piece({shape}, Role.{role_of(styles, shape_el)}),")

    # Each printed glyph is tied to the button it sits on, so the renderer can drop it when that
    # button is showing what it *does* instead. Illustrator anchors text at the glyph's left
    # baseline, so the centre is approximated before matching.
    legend_group = optional_group(root, "#legends")
    legends = []
    for text in (legend_group.iter(SVG + "text") if legend_group is not None else []):
        tx, ty, _ = transform_of(text)
        size = float(re.sub(r"[^\d.]", "", styles.get(text.get("class", ""), {}).get("font-size", "100")))
        glyph = glyph_text(text)
        if not glyph:
            continue
        gx, gy = tx + size * 0.3, ty - size * 0.36
        owner, best = None, None
        for name, shape in owners:
            d = math.hypot(shape[0] - gx, shape[1] - gy)
            if d <= shape[2] and (best is None or d < best):
                owner, best = name, d
        owner = control_owner(glyph) or owner
        owner = f"\"{owner}\"" if owner else "null"
        legends.append(f"        Glyph(\"{kotlin_string(glyph)}\", {tx:.2f}f, {ty:.2f}f, {size:.2f}f, {owner}),")

    # Do not turn hand-set outlined labels back into a generic font.  It loses optical alignment
    # (especially the diagonal Start pill).  Preserve their original path commands instead.
    vectors = outlined_glyphs(styles, legend_group) if legend_group is not None and not legends else []
    if legend_group is not None and not legends and not vectors:
        legends = expanded_glyphs(styles, legend_group, geometry)

    print(f"// Generated by tools/tileart.py from {path.split('/')[-1]} — do not edit by hand.")
    print(f"    val {tile} = TileArt(")
    print(f"        width = {view[2]:.1f}f,")
    print(f"        height = {view[3]:.1f}f,")
    print("        body = listOf(")
    print("\n".join(body))
    print("        ),")
    print("        legends = listOf(")
    print("\n".join(legends))
    print("        ),")
    print("        vectors = listOf(")
    print("\n".join(vectors))
    print("        ),")
    print("        buttons = mapOf(")
    print("\n".join(buttons))
    print("        ),")
    print("    )")


if __name__ == "__main__":
    main()
