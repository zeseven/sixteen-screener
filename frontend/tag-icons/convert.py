"""Converts the tag icon SVGs of this folder into Android vector drawables (`ic_tag_<id>.xml`).

Usage (from the `frontend` folder): `uv run tag-icons/convert.py`
"""

import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SVG_NAMESPACE: Final = "{http://www.w3.org/2000/svg}"
SOURCE_DIR: Final = Path(__file__).resolve().parent
TARGET_DIR: Final = SOURCE_DIR.parent / "app" / "src" / "main" / "res" / "drawable"
ICON_SIZE_DP: Final = 24
COLOR: Final = "#FF000000"


@dataclass(frozen=True, slots=True)
class SvgPath:
    """One path of an icon with the painting attributes it inherits from the `svg` element."""

    data: str
    stroked: bool
    even_odd: bool


@dataclass(frozen=True, slots=True)
class SvgIcon:
    """Paths and viewport of one icon."""

    viewport_width: str
    viewport_height: str
    stroke_width: str | None
    paths: list[SvgPath]


def read_icon(svg_file: Path) -> SvgIcon:
    """Reads the paths of [svg_file], skipping invisible ones such as the Tabler bounding box."""
    root = ElementTree.parse(svg_file).getroot()
    _, _, width, height = root.attrib["viewBox"].split()
    root_stroked = root.get("stroke", "none") != "none"
    paths = [
        SvgPath(
            data=element.attrib["d"],
            stroked=element.get("stroke", root.get("stroke", "none")) != "none",
            even_odd=element.get("fill-rule") == "evenodd",
        )
        for element in root.iter(f"{SVG_NAMESPACE}path")
        if not (root_stroked and element.get("stroke") == "none" and element.get("fill", "none") == "none")
    ]
    return SvgIcon(width, height, root.get("stroke-width") if root_stroked else None, paths)


def vector_path(path: SvgPath, stroke_width: str | None) -> str:
    """Path element of a vector drawable."""
    if path.stroked:
        attributes = [
            f'android:strokeColor="{COLOR}"',
            f'android:strokeWidth="{stroke_width or "1"}"',
            'android:strokeLineCap="round"',
            'android:strokeLineJoin="round"',
        ]
    else:
        attributes = [f'android:fillColor="{COLOR}"']
        if path.even_odd:
            attributes.append('android:fillType="evenOdd"')
    attributes.append(f'android:pathData="{path.data}"')
    return "    <path\n" + "\n".join(f"        {attribute}" for attribute in attributes) + " />\n"


def vector_drawable(icon: SvgIcon) -> str:
    """Vector drawable XML of [icon]."""
    header = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{ICON_SIZE_DP}dp"\n'
        f'    android:height="{ICON_SIZE_DP}dp"\n'
        f'    android:viewportWidth="{icon.viewport_width}"\n'
        f'    android:viewportHeight="{icon.viewport_height}">\n'
    )
    return header + "".join(vector_path(path, icon.stroke_width) for path in icon.paths) + "</vector>\n"


def drawable_name(svg_file: Path) -> str:
    """Resource file name of the drawable of [svg_file]."""
    return "ic_tag_" + svg_file.stem.replace("-", "_") + ".xml"


def main() -> int:
    svg_files = sorted(SOURCE_DIR.glob("*.svg"))
    if not svg_files:
        print(f"No SVG files in {SOURCE_DIR}", file=sys.stderr)
        return 1
    for svg_file in svg_files:
        TARGET_DIR.joinpath(drawable_name(svg_file)).write_text(vector_drawable(read_icon(svg_file)), encoding="utf-8")
    print(f"Converted {len(svg_files)} icons into {TARGET_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
