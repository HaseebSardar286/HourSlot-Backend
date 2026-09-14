"""Move domain *Service classes into <module>.services packages."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "src" / "main" / "java" / "com" / "hourslot"

MODULES = [
    "availability",
    "booking",
    "catalog",
    "geo",
    "media",
    "notification",
    "organization",
    "payment",
]

EXTRA = {
    "booking": ["BookingStatusRules.java"],
}


def main() -> None:
    moves: list[tuple[Path, Path, str, str, str]] = []  # src, dest, cls, old_pkg, new_pkg

    for mod in MODULES:
        mod_dir = BASE / mod
        services_dir = mod_dir / "services"
        services_dir.mkdir(parents=True, exist_ok=True)
        old_pkg = f"com.hourslot.{mod}"
        new_pkg = f"com.hourslot.{mod}.services"

        names = {p.name for p in mod_dir.glob("*Service.java")}
        names.update(EXTRA.get(mod, []))

        for name in sorted(names):
            src = mod_dir / name
            if not src.exists() or src.parent.name == "services":
                continue
            if (mod_dir / "model" / name).exists():
                continue
            cls = src.stem
            dest = services_dir / name
            moves.append((src, dest, cls, old_pkg, new_pkg))

    # Move files and update package declaration
    for src, dest, cls, old_pkg, new_pkg in moves:
        text = src.read_text(encoding="utf-8")
        text = re.sub(
            rf"^package\s+{re.escape(old_pkg)}\s*;",
            f"package {new_pkg};",
            text,
            count=1,
            flags=re.M,
        )
        dest.write_text(text, encoding="utf-8")
        src.unlink()

    # Rewrite imports / FQCNs project-wide (longest old_pkg.class first)
    replacements = sorted(
        [(cls, old_pkg, new_pkg) for _, _, cls, old_pkg, new_pkg in moves],
        key=lambda t: -len(t[1] + t[0]),
    )

    for java in (ROOT / "src").rglob("*.java"):
        text = java.read_text(encoding="utf-8")
        orig = text
        for cls, old_pkg, new_pkg in replacements:
            text = text.replace(f"import {old_pkg}.{cls};", f"import {new_pkg}.{cls};")
            # FQCN usages (avoid double-prefix if already .services.)
            text = re.sub(
                rf"(?<!\.){re.escape(old_pkg)}\.{re.escape(cls)}\b",
                f"{new_pkg}.{cls}",
                text,
            )
        if text != orig:
            java.write_text(text, encoding="utf-8")

    print(f"Moved {len(moves)} classes into *.services:")
    for _, dest, cls, old_pkg, new_pkg in moves:
        print(f"  {old_pkg}.{cls} -> {new_pkg}.{cls}")


if __name__ == "__main__":
    main()
