"""design/generate.py 확인. 실행: python3 -m unittest discover -s design"""
import copy
import json
import tempfile
import unittest
from pathlib import Path

import generate as g

REAL = json.loads(g.SOURCE.read_text(encoding="utf-8"))


class Parts(unittest.TestCase):
    def test_camel(self):
        self.assertEqual(g.camel("ink-muted"), "inkMuted")
        self.assertEqual(g.camel("space-1"), "space1")
        self.assertEqual(g.camel("radius-pill", upper=True), "RadiusPill")

    def test_name_rejects_code(self):
        for bad in ["Hanji", "a b", "x\"; drop", "1abc", "a--b", "", None]:
            with self.assertRaises(g.TokenError, msg=bad):
                g.check_name(bad)

    def test_hex(self):
        self.assertEqual(g.hex_rgb("#fbf7ef"), ("FBF7EF", (251, 247, 239)))
        for bad in ["FBF7EF", "#FFF", "#GGGGGG", 123]:
            with self.assertRaises(g.TokenError):
                g.hex_rgb(bad)

    def test_px(self):
        self.assertEqual(g.px("16px"), 16)
        self.assertEqual(g.px("1.5px"), 1.5)
        for bad in ["16", "16 px", "1rem", None]:
            with self.assertRaises(g.TokenError):
                g.px(bad)

    def test_swift_keyword_rejected(self):
        for kw in ["default", "in", "self", "true"]:
            with self.assertRaises(g.TokenError, msg=kw):
                g.check_name(kw)

    def test_first_font(self):
        self.assertEqual(g.first_font('"Gowun Batang", "Nanum Myeongjo", serif'), "Gowun Batang")

    def test_comment_is_one_line(self):
        self.assertEqual(g.comment("a\nb\r\n  c"), "a b c")


class Load(unittest.TestCase):
    def test_duplicate_name(self):
        d = copy.deepcopy(REAL)
        d["color"]["tokens"].append(dict(d["color"]["tokens"][0]))
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_same_identifier_after_camel(self):
        d = copy.deepcopy(REAL)
        d["spacing"]["tokens"] += [{"name": "space-1-0", "value": "1px"}, {"name": "space-10", "value": "2px"}]
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_kotlin_type_name_rejected(self):
        d = copy.deepcopy(REAL)
        d["type"]["groups"][1]["styles"].append({"name": "font-weight", "fontSize": "1px", "lineHeight": "1px", "fontWeight": 400})
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_float_weight_rejected(self):
        d = copy.deepcopy(REAL)
        d["type"]["groups"][0]["styles"][0]["fontWeight"] = 700.0
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_families_must_match(self):
        d = copy.deepcopy(REAL)
        d["type"]["families"]["mono"] = "monospace"
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_unknown_family_and_weight(self):
        d = copy.deepcopy(REAL)
        d["type"]["groups"][0]["family"] = "mono"
        with self.assertRaises(g.TokenError):
            g.load(d)
        d = copy.deepcopy(REAL)
        d["type"]["groups"][0]["styles"][0]["fontWeight"] = 650
        with self.assertRaises(g.TokenError):
            g.load(d)

    def test_missing_section(self):
        d = copy.deepcopy(REAL)
        del d["radius"]
        with self.assertRaises(g.TokenError):
            g.load(d)


class Output(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = g.build()
        cls.sw, cls.kt = cls.files[g.SWIFT_FILE], cls.files[g.KOTLIN_FILE]

    def test_every_token_present(self):
        for t in REAL["color"]["tokens"]:
            h = t["value"].lstrip("#").upper()
            self.assertIn(f"static let {g.camel(t['name'])} = Color(", self.sw)
            self.assertIn(f"val {g.camel(t['name'], True)} = Color(0xFF{h})", self.kt)
        for section in ("spacing", "radius"):
            for t in REAL[section]["tokens"]:
                n = g.px(t["value"])
                self.assertIn(f"static let {g.camel(t['name'])}: CGFloat = {n}", self.sw)
                self.assertIn(f"val {g.camel(t['name'], True)} = {n}.dp", self.kt)
        for group in REAL["type"]["groups"]:
            for s in group["styles"]:
                self.assertIn(f"static let {g.camel(s['name'])} = OkcheTextStyle(family: .{group['family']}, ", self.sw)
                self.assertIn(f"FontWeight.W{s['fontWeight']})", self.kt)

    def test_known_values(self):
        self.assertIn("static let hanji = Color(.sRGB, red: 251 / 255, green: 247 / 255, blue: 239 / 255, opacity: 1)", self.sw)
        self.assertIn("val Namsaek = Color(0xFF2F4B7C)", self.kt)
        self.assertIn("static let body = OkcheTextStyle(family: .sans, size: 16, lineHeight: 24, weight: .regular)", self.sw)
        self.assertIn("val Display = OkcheTextStyle(OkcheFontFamily.Display, 28.sp, 36.sp, FontWeight.W700)", self.kt)
        self.assertIn(f"package {g.KOTLIN_PACKAGE}", self.kt)

    def test_braces_balanced(self):
        # 주석(usage 문구)은 빼고 코드 줄만 센다
        for text in self.files.values():
            code = "\n".join(l.split("//")[0] for l in text.splitlines())
            self.assertEqual(code.count("{"), code.count("}"))
            self.assertEqual(code.count("("), code.count(")"))

    def test_bad_json_exit_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad = Path(tmp) / "tokens.json"
            bad.write_text("{", encoding="utf-8")
            self.assertEqual(g.main([], source=bad, out_dir=tmp), 2)
            self.assertEqual(g.main([], source=Path(tmp) / "없음.json", out_dir=tmp), 2)
            self.assertEqual(list(Path(tmp).glob("Okche*")), [])  # 실패하면 아무것도 쓰지 않음

    def test_check_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(g.run(check=True, out_dir=tmp), 1)  # 아직 없음
            self.assertEqual(g.run(out_dir=tmp), 0)
            self.assertEqual(g.run(check=True, out_dir=tmp), 0)
            p = Path(tmp) / g.SWIFT_FILE
            p.write_text(p.read_text(encoding="utf-8") + "// 손으로 고침\n", encoding="utf-8")
            self.assertEqual(g.run(check=True, out_dir=tmp), 1)

    def test_committed_files_up_to_date(self):
        self.assertEqual(g.run(check=True), 0, "design/generated 가 tokens.json과 다름: python3 design/generate.py 실행")


if __name__ == "__main__":
    unittest.main()
