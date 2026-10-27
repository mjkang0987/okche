"""옥체 디자인 토큰(design/tokens.json)으로 SwiftUI·Jetpack Compose 코드를 만든다.

사용법:
    python3 design/generate.py          # design/generated/ 에 두 파일을 다시 씀
    python3 design/generate.py --check  # 생성 파일이 tokens.json과 맞는지만 확인 (다르면 종료 코드 1)

파이썬 표준 라이브러리만 쓴다.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "tokens.json"
OUT_DIR = ROOT / "generated"
SWIFT_FILE = "OkcheTokens.swift"
KOTLIN_FILE = "OkcheTokens.kt"
KOTLIN_PACKAGE = "kr.pikaworks.okche.ui.theme"  # 앱 ID 확정 전 가정값 (plan.md 2단계)

# 토큰 이름은 그대로 코드 식별자가 되므로 좁게 허용한다 (코드 주입 방지).
NAME_RE = re.compile(r"[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
HEX_RE = re.compile(r"#([0-9A-Fa-f]{6})")
PX_RE = re.compile(r"(\d+(?:\.\d+)?)px")
FAMILIES = ("display", "sans")
SWIFT_WEIGHTS = {100: "ultraLight", 200: "thin", 300: "light", 400: "regular", 500: "medium",
                 600: "semibold", 700: "bold", 800: "heavy", 900: "black"}

HEADER = ("자동 생성 파일 — 직접 고치지 말 것.\n"
          "design/tokens.json을 고친 뒤 `python3 design/generate.py`를 실행한다.")


class TokenError(ValueError):
    """tokens.json 형식이 기대와 다를 때."""


def check_name(name):
    if not isinstance(name, str) or not NAME_RE.fullmatch(name):
        raise TokenError(f"토큰 이름 형식이 맞지 않음: {name!r}")
    return name


def camel(name, upper=False):
    parts = check_name(name).split("-")
    head = parts[0].capitalize() if upper else parts[0]
    return head + "".join(p.capitalize() for p in parts[1:])


def hex_rgb(value):
    m = HEX_RE.fullmatch(value) if isinstance(value, str) else None
    if not m:
        raise TokenError(f"색은 #RRGGBB 형식이어야 함: {value!r}")
    h = m.group(1).upper()
    return h, tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def px(value):
    m = PX_RE.fullmatch(value) if isinstance(value, str) else None
    if not m:
        raise TokenError(f"크기는 숫자px 형식이어야 함: {value!r}")
    n = float(m.group(1))
    return int(n) if n.is_integer() else n


def comment(text):
    # 한 줄 주석에만 넣으므로 줄바꿈만 없애면 된다.
    return " ".join(str(text or "").split())


def unique(items, kind):
    seen = set()
    for it in items:
        if it["name"] in seen:
            raise TokenError(f"{kind} 토큰 이름이 겹침: {it['name']}")
        seen.add(it["name"])
    return items


def load(data):
    """tokens.json 내용을 검증하고 생성에 필요한 형태로 바꾼다."""
    try:
        colors = unique([{"name": check_name(t["name"]), "hex": hex_rgb(t["value"]), "usage": comment(t.get("usage"))}
                         for t in data["color"]["tokens"]], "색")
        spacing = unique([{"name": check_name(t["name"]), "px": px(t["value"]), "usage": comment(t.get("usage"))}
                          for t in data["spacing"]["tokens"]], "간격")
        radius = unique([{"name": check_name(t["name"]), "px": px(t["value"]), "usage": comment(t.get("usage"))}
                         for t in data["radius"]["tokens"]], "모서리")
        styles = []
        for group in data["type"]["groups"]:
            family = group["family"]
            if family not in FAMILIES:
                raise TokenError(f"알 수 없는 글꼴 종류: {family!r}")
            for s in group["styles"]:
                weight = s["fontWeight"]
                if weight not in SWIFT_WEIGHTS:
                    raise TokenError(f"글자 굵기는 100~900 사이 100 단위여야 함: {weight!r}")
                styles.append({"name": check_name(s["name"]), "family": family, "size": px(s["fontSize"]),
                               "line": px(s["lineHeight"]), "weight": weight})
        unique(styles, "글자")
    except (KeyError, TypeError) as e:
        raise TokenError(f"tokens.json 구조가 맞지 않음: {e!r}") from e
    return {"name": data.get("name", ""), "version": data.get("version", ""), "colors": colors,
            "spacing": spacing, "radius": radius, "styles": styles}


def num(v):
    return str(v)


def swift(t):
    out = [f"// {line}" for line in HEADER.split("\n")]
    out += [f"// 원본: {comment(t['name'])} 디자인 토큰 v{comment(t['version'])}", "", "import SwiftUI", "", "enum OkcheColor {"]
    for c in t["colors"]:
        h, (r, g, b) = c["hex"]
        out += [f"    /// {c['usage']}" if c["usage"] else None,
                f"    static let {camel(c['name'])} = Color(.sRGB, red: {r} / 255, green: {g} / 255, blue: {b} / 255, opacity: 1) // #{h}"]
    for title, key in (("OkcheSpacing", "spacing"), ("OkcheRadius", "radius")):
        out += ["}", "", f"enum {title} {{"]
        for s in t[key]:
            out += [f"    /// {s['usage']}" if s["usage"] else None, f"    static let {camel(s['name'])}: CGFloat = {num(s['px'])}"]
    out += ["}", "", "enum OkcheFontFamily {", "    /// 고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구", "    case display",
            "    /// 시스템 글꼴 — 나머지 전부", "    case sans", "}", "",
            "struct OkcheTextStyle {", "    let family: OkcheFontFamily", "    let size: CGFloat", "    let lineHeight: CGFloat",
            "    let weight: Font.Weight", "}", "", "enum OkcheType {"]
    for s in t["styles"]:
        out.append(f"    static let {camel(s['name'])} = OkcheTextStyle(family: .{s['family']}, size: {num(s['size'])}, "
                   f"lineHeight: {num(s['line'])}, weight: .{SWIFT_WEIGHTS[s['weight']]})")
    out += ["}", ""]
    return "\n".join(line for line in out if line is not None)


def kotlin(t):
    out = [f"// {line}" for line in HEADER.split("\n")]
    out += [f"// 원본: {comment(t['name'])} 디자인 토큰 v{comment(t['version'])}", "", f"package {KOTLIN_PACKAGE}", "",
            "import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.text.font.FontWeight",
            "import androidx.compose.ui.unit.TextUnit", "import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.sp", "",
            "object OkcheColor {"]
    for c in t["colors"]:
        h, _ = c["hex"]
        out += [f"    // {c['usage']}" if c["usage"] else None, f"    val {camel(c['name'], upper=True)} = Color(0xFF{h})"]
    for title, key in (("OkcheSpacing", "spacing"), ("OkcheRadius", "radius")):
        out += ["}", "", f"object {title} {{"]
        for s in t[key]:
            out += [f"    // {s['usage']}" if s["usage"] else None, f"    val {camel(s['name'], upper=True)} = {num(s['px'])}.dp"]
    out += ["}", "", "enum class OkcheFontFamily {", "    // 고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구", "    Display,",
            "    // 시스템 글꼴 — 나머지 전부", "    Sans,", "}", "",
            "data class OkcheTextStyle(", "    val family: OkcheFontFamily,", "    val fontSize: TextUnit,",
            "    val lineHeight: TextUnit,", "    val fontWeight: FontWeight,", ")", "", "object OkcheType {"]
    for s in t["styles"]:
        out.append(f"    val {camel(s['name'], upper=True)} = OkcheTextStyle(OkcheFontFamily.{s['family'].capitalize()}, "
                   f"{num(s['size'])}.sp, {num(s['line'])}.sp, FontWeight.W{s['weight']})")
    out += ["}", ""]
    return "\n".join(line for line in out if line is not None)


def build(source=SOURCE):
    t = load(json.loads(Path(source).read_text(encoding="utf-8")))
    return {SWIFT_FILE: swift(t), KOTLIN_FILE: kotlin(t)}


def run(check=False, source=SOURCE, out_dir=OUT_DIR):
    """생성하거나(check=False) 최신인지 확인한다. 반환값은 종료 코드."""
    files = build(source)
    out_dir = Path(out_dir)
    if check:
        stale = [n for n, text in files.items()
                 if not (out_dir / n).exists() or (out_dir / n).read_text(encoding="utf-8") != text]
        for n in stale:
            print(f"최신이 아님: {out_dir / n}", file=sys.stderr)
        return 1 if stale else 0
    out_dir.mkdir(parents=True, exist_ok=True)
    for n, text in files.items():
        (out_dir / n).write_text(text, encoding="utf-8")
        print(f"썼음: {out_dir / n}")
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--check", action="store_true", help="생성 파일이 최신인지 확인만 한다")
    args = p.parse_args(argv)
    try:
        return run(check=args.check)
    except TokenError as e:
        print(f"토큰 오류: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
