"""Collapse Maven multi-module into single-src package modular monolith."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC_MAIN = ROOT / "src" / "main" / "java"
SRC_TEST = ROOT / "src" / "test" / "java"
SRC_RES = ROOT / "src" / "main" / "resources"

# Simple class name -> new fully-qualified package (without class)
# Format: "ClassName" -> "com.hourslot.module.sub"
CLASS_PKG: dict[str, str] = {}

def map_cls(name: str, pkg: str) -> None:
    CLASS_PKG[name] = pkg

# --- shared ---
for n in ["JdbcSupport", "RowMappers"]:
    map_cls(n, "com.hourslot.shared.jdbc")
for n in ["EnvFileLoader", "DatabaseUrlParser"]:
    map_cls(n, "com.hourslot.shared.config")
map_cls("ApiError", "com.hourslot.shared.exception")
map_cls("PlanLimitException", "com.hourslot.shared.exception")
for n in ["MoneyAmounts", "TokenHashes"]:
    map_cls(n, "com.hourslot.shared.util")

# --- identity ---
for n in ["AuthEntryPointJwt", "AuthRateLimitFilter", "AuthTokenFilter", "CustomUserDetails", "JwtUtils"]:
    map_cls(n, "com.hourslot.identity.security")
for n in ["LoginRequest", "LoginResponse", "MessageResponse", "RegisterRequest",
          "TokenRefreshRequest", "TokenRefreshResponse", "CustomerView"]:
    map_cls(n, "com.hourslot.identity.dto")
for n in ["User", "UserRole", "AuthRefreshToken", "PasswordResetToken",
          "EmailVerificationToken", "CustomerProfile"]:
    map_cls(n, "com.hourslot.identity.model")
for n in ["UserRepository", "AuthRefreshTokenRepository", "PasswordResetTokenRepository",
          "EmailVerificationTokenRepository", "CustomerProfileRepository"]:
    map_cls(n, "com.hourslot.identity.repository")

# --- organization ---
for n in ["TenancyService", "RbacService", "EntitlementService", "SystemSettingService",
          "VerificationDocumentService", "StaffInviteService"]:
    map_cls(n, "com.hourslot.organization")
for n in ["Organization", "OrganizationMember", "Business", "BusinessStatus", "Branch", "Staff",
          "StaffInvite", "BusinessVerificationDocument", "Permission", "Role", "MemberRole",
          "SubscriptionPlan", "PlanEntitlement", "OrganizationSubscription", "SystemSetting",
          "AuditEvent"]:
    map_cls(n, "com.hourslot.organization.model")
for n in ["OrganizationRepository", "OrganizationMemberRepository", "BusinessRepository",
          "BranchRepository", "StaffRepository", "StaffInviteRepository",
          "BusinessVerificationDocumentRepository", "RoleRepository", "MemberRoleRepository",
          "SubscriptionPlanRepository", "PlanEntitlementRepository",
          "OrganizationSubscriptionRepository", "SystemSettingRepository", "AuditEventRepository"]:
    map_cls(n, "com.hourslot.organization.repository")

# --- catalog ---
for n in ["PricingService", "CatalogLocaleService"]:
    map_cls(n, "com.hourslot.catalog")
for n in ["Category", "Service", "ServicePackage", "CustomerPackage", "StaffService",
          "TimeOfDayPricing"]:
    map_cls(n, "com.hourslot.catalog.model")
for n in ["CategoryRepository", "ServiceRepository", "ServicePackageRepository",
          "CustomerPackageRepository", "StaffServiceRepository", "TimeOfDayPricingRepository"]:
    map_cls(n, "com.hourslot.catalog.repository")

# --- geo ---
map_cls("GeoCatalogService", "com.hourslot.geo")
for n in ["CountryView", "RegionView", "CurrencyView"]:
    map_cls(n, "com.hourslot.geo.dto")

# --- availability ---
for n in ["AvailabilityService", "ScheduleService", "SlotLockService"]:
    map_cls(n, "com.hourslot.availability")
for n in ["BranchWorkingHour", "BranchWorkingInterval", "BranchBreak", "BranchHoliday",
          "StaffWorkingHour", "StaffWorkingInterval", "StaffBreak", "StaffTimeOff"]:
    map_cls(n, "com.hourslot.availability.model")
for n in ["BranchWorkingHourRepository", "BranchBreakRepository", "BranchHolidayRepository",
          "StaffWorkingHourRepository", "StaffBreakRepository", "StaffTimeOffRepository"]:
    map_cls(n, "com.hourslot.availability.repository")

# --- booking ---
for n in ["BookingService", "BookingStatusRules"]:
    map_cls(n, "com.hourslot.booking")
map_cls("DiscoverBranchResponse", "com.hourslot.booking.dto")
for n in ["Booking", "BookingItem", "BookingStatus", "BookingStatusHistory", "Review", "Favorite"]:
    map_cls(n, "com.hourslot.booking.model")
for n in ["BookingRepository", "BookingStatusHistoryRepository", "ReviewRepository", "FavoriteRepository"]:
    map_cls(n, "com.hourslot.booking.repository")

# --- payment ---
map_cls("PaymentService", "com.hourslot.payment")
for n in ["Payment", "PaymentRefund"]:
    map_cls(n, "com.hourslot.payment.model")
for n in ["PaymentRepository", "PaymentRefundRepository"]:
    map_cls(n, "com.hourslot.payment.repository")

# --- media ---
for n in ["MediaAssetService", "ObjectStorageService"]:
    map_cls(n, "com.hourslot.media")
for n in ["MediaAsset", "BusinessMedia"]:
    map_cls(n, "com.hourslot.media.model")
for n in ["MediaAssetRepository", "BusinessMediaRepository"]:
    map_cls(n, "com.hourslot.media.repository")

# --- notification ---
for n in ["MailService", "NotificationService", "NotificationPreferenceService"]:
    map_cls(n, "com.hourslot.notification")
for n in ["Notification", "NotificationPreference"]:
    map_cls(n, "com.hourslot.notification.model")
for n in ["NotificationRepository", "NotificationPreferenceRepository"]:
    map_cls(n, "com.hourslot.notification.repository")

# --- web ---
CONTROLLERS = [
    "AdminController", "AuthController", "AvailabilityController", "BookingController",
    "BusinessController", "CategoryController", "CustomerPackageController", "DiscoveryController",
    "GeoController", "MediaController", "NotificationController", "OrganizationController",
    "PaymentController", "ProbeIgnoreController", "ReviewController", "UserProfileController",
    "VerificationDocumentController",
]
for n in CONTROLLERS:
    map_cls(n, "com.hourslot.web.controller")
for n in ["SecurityConfig", "JacksonConfig", "WebConfig"]:
    map_cls(n, "com.hourslot.web.config")
map_cls("GlobalExceptionHandler", "com.hourslot.web.exception")
map_cls("CustomUserDetailsService", "com.hourslot.web.security")

# HourSlotApplication stays at com.hourslot
map_cls("HourSlotApplication", "com.hourslot")

# Old package prefixes that may appear as wildcards / imports
OLD_PREFIXES = [
    "com.hourslot.model",
    "com.hourslot.repository",
    "com.hourslot.service",
    "com.hourslot.controller",
    "com.hourslot.security",
    "com.hourslot.dto.geo",
    "com.hourslot.dto",
    "com.hourslot.jdbc",
    "com.hourslot.util",
    "com.hourslot.config",
    "com.hourslot.exception",
]

PACKAGE_INFOS = {
    "com.hourslot.shared": "Shared kernel: JDBC, config loaders, errors, utils.",
    "com.hourslot.identity": "Auth identity: users, JWT, login DTOs.",
    "com.hourslot.organization": "Tenancy, businesses, staff, RBAC, plans, KYC.",
    "com.hourslot.catalog": "Categories, services, packages, peak pricing.",
    "com.hourslot.geo": "Country / region / currency catalog.",
    "com.hourslot.availability": "Working hours, holidays, slot generation and locks.",
    "com.hourslot.booking": "Bookings, reviews, favorites, discovery DTOs.",
    "com.hourslot.payment": "Stripe and payment records.",
    "com.hourslot.media": "Object storage and media assets.",
    "com.hourslot.notification": "In-app notifications and email.",
    "com.hourslot.web": "HTTP adapters: controllers, security wiring, exception handler.",
}


def pkg_to_path(pkg: str) -> Path:
    return Path(*pkg.split("."))


def collect_java_files() -> list[tuple[Path, bool]]:
    files: list[tuple[Path, bool]] = []
    for mod in sorted(ROOT.glob("hourslot-*")):
        for p in (mod / "src" / "main" / "java").rglob("*.java"):
            if p.name == "package-info.java":
                continue
            files.append((p, False))
        for p in (mod / "src" / "test" / "java").rglob("*.java"):
            files.append((p, True))
    return files


def rewrite_content(text: str, class_name: str, new_pkg: str) -> str:
    # Replace package declaration
    text = re.sub(r"^package\s+[\w.]+;", f"package {new_pkg};", text, count=1, flags=re.M)

    # Replace FQCN imports of known classes
    for cls, pkg in CLASS_PKG.items():
        # import com.hourslot....Class;
        text = re.sub(
            rf"import\s+com\.hourslot(?:\.[\w]+)*\.{cls}\s*;",
            f"import {pkg}.{cls};",
            text,
        )

    # Replace wildcard imports with expanded imports for classes we know lived there
    # import com.hourslot.model.*;
    # import com.hourslot.repository.*;
    # import com.hourslot.service.*;
    def expand_wildcard(match: re.Match) -> str:
        old = match.group(1)
        needed = []
        # Find simple type usages in the file that belong to that old package conceptually
        # Safer: emit imports for every CLASS_PKG class whose old package prefix matches
        # We don't have old package stored; instead expand based on common old roots
        for cls, pkg in sorted(CLASS_PKG.items()):
            # Heuristic: if the class is referenced as a simple name in the file, import it
            if re.search(rf"\b{cls}\b", text):
                needed.append(f"import {pkg}.{cls};")
        # Deduplicate while preserving order
        seen = set()
        lines = []
        for line in needed:
            if line not in seen and not line.endswith(f".{class_name};"):
                # Don't import self
                if f".{class_name};" in line and pkg_of(class_name) == new_pkg:
                    continue
                seen.add(line)
                lines.append(line)
        return "\n".join(lines) if lines else ""

    text = re.sub(r"import\s+(com\.hourslot\.(?:model|repository|service))\.\*\s*;", expand_wildcard, text)

    # Fix remaining old-style single imports that used intermediate packages we changed
    # e.g. import com.hourslot.service.BookingService;
    for cls, pkg in CLASS_PKG.items():
        text = re.sub(
            rf"import\s+com\.hourslot\.(?:model|repository|service|controller|security|jdbc|util|config|exception|dto(?:\.geo)?)\.{cls}\s*;",
            f"import {pkg}.{cls};",
            text,
        )

    # Static / type references with FQCN in code (rare)
    for cls, pkg in CLASS_PKG.items():
        text = text.replace(f"com.hourslot.model.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.repository.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.service.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.controller.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.security.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.jdbc.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.util.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.config.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.exception.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.dto.geo.{cls}", f"{pkg}.{cls}")
        text = text.replace(f"com.hourslot.dto.{cls}", f"{pkg}.{cls}")

    # Clean duplicate imports
    lines = text.splitlines(keepends=True)
    out = []
    seen_imports = set()
    for line in lines:
        if line.startswith("import "):
            key = line.strip()
            if key in seen_imports:
                continue
            seen_imports.add(key)
        out.append(line)
    text = "".join(out)

    # Remove blank import-only leftovers from empty expand
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


def pkg_of(class_name: str) -> str:
    return CLASS_PKG[class_name]


def write_package_info(pkg: str, doc: str) -> None:
    path = SRC_MAIN / pkg_to_path(pkg) / "package-info.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"/** {doc} */\npackage {pkg};\n", encoding="utf-8")


def merge_resources() -> None:
    SRC_RES.mkdir(parents=True, exist_ok=True)
    app_res = ROOT / "hourslot-app" / "src" / "main" / "resources"
    if not app_res.exists():
        raise SystemExit("hourslot-app resources missing")
    for item in app_res.iterdir():
        dest = SRC_RES / item.name
        if dest.exists():
            if dest.is_dir():
                shutil.rmtree(dest)
            else:
                dest.unlink()
        shutil.copytree(item, dest) if item.is_dir() else shutil.copy2(item, dest)


def process_file(src: Path, is_test: bool) -> None:
    class_name = src.stem
    if class_name not in CLASS_PKG:
        # Tests named differently
        if class_name == "BookingStatusTransitionTest":
            new_pkg = "com.hourslot.booking"
        elif class_name == "JwtTokenTypeTest":
            new_pkg = "com.hourslot.identity.security"
        else:
            raise SystemExit(f"Unmapped class: {src}")
    else:
        new_pkg = CLASS_PKG[class_name]

    text = src.read_text(encoding="utf-8")
    text = rewrite_content(text, class_name, new_pkg)

    base = SRC_TEST if is_test else SRC_MAIN
    dest = base / pkg_to_path(new_pkg) / f"{class_name}.java"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text, encoding="utf-8")


def delete_modules() -> None:
    for mod in ROOT.glob("hourslot-*"):
        shutil.rmtree(mod, ignore_errors=True)


def second_pass_fix_imports() -> None:
    """Expand any remaining star imports and ensure referenced classes are imported."""
    for java in list(SRC_MAIN.rglob("*.java")) + list(SRC_TEST.rglob("*.java")):
        if java.name == "package-info.java":
            continue
        text = java.read_text(encoding="utf-8")
        orig = text

        # Expand remaining star imports
        def expand(m: re.Match) -> str:
            body = java.read_text(encoding="utf-8") if False else text
            needed = []
            for cls, pkg in sorted(CLASS_PKG.items()):
                if re.search(rf"(?<![\w.]){cls}(?![\w])", body):
                    needed.append(f"import {pkg}.{cls};")
            seen = set()
            lines = []
            for line in needed:
                if line not in seen:
                    seen.add(line)
                    lines.append(line)
            return "\n".join(lines)

        text = re.sub(r"import\s+com\.hourslot\.(?:model|repository|service)\.\*\s*;", expand, text)

        # Fix any leftover old imports
        for cls, pkg in CLASS_PKG.items():
            text = re.sub(
                rf"import\s+com\.hourslot\.(?:model|repository|service|controller|security|jdbc|util|config|exception|dto(?:\.geo)?)\.{cls}\s*;",
                f"import {pkg}.{cls};",
                text,
            )

        # Deduplicate imports
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

        if text != orig:
            java.write_text(text, encoding="utf-8")


def main() -> None:
    if (ROOT / "src").exists():
        shutil.rmtree(ROOT / "src")

    files = collect_java_files()
    print(f"Processing {len(files)} Java files...")
    for src, is_test in files:
        process_file(src, is_test)

    merge_resources()

    for pkg, doc in PACKAGE_INFOS.items():
        write_package_info(pkg, doc)

    second_pass_fix_imports()
    delete_modules()
    print("Done. Single-src modular monolith ready.")


if __name__ == "__main__":
    main()
