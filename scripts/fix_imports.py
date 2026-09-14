"""Fix missing cross-module imports after package split."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"

# Rebuild CLASS_PKG from filesystem
CLASS_PKG: dict[str, str] = {}
for java in (SRC / "main" / "java").rglob("*.java"):
    if java.name == "package-info.java":
        continue
    text = java.read_text(encoding="utf-8")
    m = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    if not m:
        continue
    CLASS_PKG[java.stem] = m.group(1)

# Also include test classes' packages when fixing tests
for java in (SRC / "test" / "java").rglob("*.java"):
    text = java.read_text(encoding="utf-8")
    m = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    if m and java.stem not in CLASS_PKG:
        CLASS_PKG[java.stem] = m.group(1)

# Classes that collide with common Spring / JDK names
AMBIGUOUS = {"Service"}  # catalog.model.Service vs org.springframework.stereotype.Service


def current_imports(text: str) -> set[str]:
    return set(re.findall(r"^import\s+([\w.]+)\s*;", text, re.M))


def package_of(text: str) -> str:
    m = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    return m.group(1) if m else ""


def referenced_types(text: str) -> set[str]:
    # Strip strings and comments roughly
    cleaned = re.sub(r'"([^"\\]|\\.)*"', '""', text)
    cleaned = re.sub(r"/\*.*?\*/", "", cleaned, flags=re.S)
    cleaned = re.sub(r"//.*?$", "", cleaned, flags=re.M)
    found = set()
    for cls in CLASS_PKG:
        if re.search(rf"(?<![\w.]){cls}(?![\w])", cleaned):
            found.add(cls)
    return found


def insert_imports(text: str, imports: list[str]) -> str:
    if not imports:
        return text
    lines = text.splitlines(keepends=True)
    # Find last import or package line
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith("package ") or line.startswith("import "):
            insert_at = i + 1
    block = "".join(f"import {imp};\n" for imp in imports)
    # Ensure blank line after imports if next is not blank
    lines.insert(insert_at, block)
    return "".join(lines)


def fix_service_ambiguity(text: str, pkg: str) -> str:
    has_spring = "import org.springframework.stereotype.Service;" in text
    has_model = "import com.hourslot.catalog.model.Service;" in text
    uses_model = bool(re.search(r"(?<![\w.])Service(?![\w])", text)) and (
        "ServiceRepository" in text or "catalog.model.Service" in text or has_model
        or re.search(r"\bService\b\s+\w+", text)
    )
    if not (has_spring and (has_model or "com.hourslot.catalog.model.Service" in text or uses_model)):
        # If only spring Service annotation and also catalog Service type used without import
        if has_spring and uses_model and "ServiceRepository" in text:
            # remove model import if present; qualify type usages that are not annotations
            text = text.replace("import com.hourslot.catalog.model.Service;\n", "")
            # Qualify field/param/return types: Service foo -> com.hourslot.catalog.model.Service foo
            # Careful not to touch @Service
            def qual(m: re.Match) -> str:
                return f"{m.group(1)}com.hourslot.catalog.model.Service{m.group(2)}"
            text = re.sub(
                r"(?<![\w.@])(Service)(\s+[a-zA-Z_]\w*)",
                lambda m: m.group(0) if False else f"com.hourslot.catalog.model.Service{m.group(2)}",
                text,
            )
            # Fix @Service that may have been broken - shouldn't happen with (?<!@)
            text = text.replace("@com.hourslot.catalog.model.Service", "@Service")
            # Fix import lines / annotations wrongly qualified
            text = re.sub(
                r"(?<![\w.@])Service(\s+[a-z]\w*\s*[=;,\)])",
                r"com.hourslot.catalog.model.Service\1",
                text,
            )
            # Optional generics List<Service>
            text = re.sub(r"\bList<Service>", "List<com.hourslot.catalog.model.Service>", text)
            text = re.sub(r"\bOptional<Service>", "Optional<com.hourslot.catalog.model.Service>", text)
            text = re.sub(r"\bSet<Service>", "Set<com.hourslot.catalog.model.Service>", text)
        return text

    # Both imports present — drop model import and qualify usages
    text = text.replace("import com.hourslot.catalog.model.Service;\n", "")
    text = re.sub(r"\bList<Service>", "List<com.hourslot.catalog.model.Service>", text)
    text = re.sub(r"\bOptional<Service>", "Optional<com.hourslot.catalog.model.Service>", text)
    text = re.sub(r"\bSet<Service>", "Set<com.hourslot.catalog.model.Service>", text)
    text = re.sub(
        r"(?<![\w.@])Service(\s+[a-z]\w*)",
        r"com.hourslot.catalog.model.Service\1",
        text,
    )
    return text


def fix_file(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if path.name == "package-info.java":
        return
    pkg = package_of(text)
    self_name = path.stem

    # Remove bad wildcard
    text = re.sub(r"^import\s+com\.hourslot\.dto\.\*\s*;\s*\n", "", text, flags=re.M)

    # Fix Service ambiguity first
    text = fix_service_ambiguity(text, pkg)

    imports = current_imports(text)
    needed = []
    for cls in sorted(referenced_types(text)):
        if cls == self_name:
            continue
        if cls in AMBIGUOUS and "org.springframework.stereotype.Service" in text:
            # model Service should be FQCN already
            continue
        target_pkg = CLASS_PKG.get(cls)
        if not target_pkg:
            continue
        if target_pkg == pkg:
            continue  # same package
        fqcn = f"{target_pkg}.{cls}"
        if fqcn in imports:
            continue
        # Already FQCN-used everywhere? still add import if simple name used
        if re.search(rf"(?<![\w.]){cls}(?![\w])", text):
            needed.append(fqcn)

    # Special: GlobalExceptionHandler needs ApiError
    if self_name == "GlobalExceptionHandler" and "com.hourslot.shared.exception.ApiError" not in current_imports(text):
        if "ApiError" in text:
            needed.append("com.hourslot.shared.exception.ApiError")

    # Deduplicate
    needed = [n for n in needed if n not in current_imports(text)]
    # Sort for stability
    needed = sorted(set(needed))

    if needed:
        text = insert_imports(text, needed)

    # Deduplicate import lines
    lines = text.splitlines(keepends=True)
    out = []
    seen = set()
    for line in lines:
        if line.startswith("import "):
            key = line.strip()
            if key in seen:
                continue
            seen.add(key)
        out.append(line)
    text = "".join(out)
    text = re.sub(r"\n{3,}", "\n\n", text)

    path.write_text(text, encoding="utf-8")


def main() -> None:
    files = list((SRC / "main" / "java").rglob("*.java")) + list((SRC / "test" / "java").rglob("*.java"))
    for f in files:
        fix_file(f)
    print(f"Fixed imports in {len(files)} files.")


if __name__ == "__main__":
    main()
