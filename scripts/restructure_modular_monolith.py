"""One-shot relocation of HourSlot sources into Maven modules. Packages stay com.hourslot.*."""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD_MAIN = ROOT / "src" / "main" / "java" / "com" / "hourslot"
OLD_TEST = ROOT / "src" / "test" / "java" / "com" / "hourslot"
OLD_RES = ROOT / "src" / "main" / "resources"

# module -> list of paths relative to com/hourslot
MAPPING: dict[str, list[str]] = {
    "hourslot-common": [
        "jdbc/JdbcSupport.java",
        "util/MoneyAmounts.java",
        "util/TokenHashes.java",
        "exception/ApiError.java",
        "config/EnvFileLoader.java",
        "config/DatabaseUrlParser.java",
        "service/PlanLimitException.java",
    ],
    "hourslot-domain": [
        "jdbc/RowMappers.java",
        *[f"model/{p.name}" for p in sorted((OLD_MAIN / "model").glob("*.java"))],
        *[f"repository/{p.name}" for p in sorted((OLD_MAIN / "repository").glob("*.java"))],
    ],
    "hourslot-identity": [
        "security/AuthRateLimitFilter.java",
        "security/CustomUserDetails.java",
        "security/CustomUserDetailsService.java",
        "security/AuthEntryPointJwt.java",
        "security/AuthTokenFilter.java",
        "security/JwtUtils.java",
        "dto/RegisterRequest.java",
        "dto/CustomerView.java",
        "dto/TokenRefreshResponse.java",
        "dto/TokenRefreshRequest.java",
        "dto/LoginRequest.java",
        "dto/MessageResponse.java",
        "dto/LoginResponse.java",
    ],
    "hourslot-media": [
        "service/ObjectStorageService.java",
        "service/MediaAssetService.java",
    ],
    "hourslot-notification": [
        "service/MailService.java",
        "service/NotificationService.java",
        "service/NotificationPreferenceService.java",
    ],
    "hourslot-geo": [
        "service/GeoCatalogService.java",
        "dto/geo/CountryView.java",
        "dto/geo/RegionView.java",
        "dto/geo/CurrencyView.java",
    ],
    "hourslot-organization": [
        "service/TenancyService.java",
        "service/RbacService.java",
        "service/EntitlementService.java",
        "service/SystemSettingService.java",
        "service/VerificationDocumentService.java",
        "service/StaffInviteService.java",
    ],
    "hourslot-catalog": [
        "service/PricingService.java",
        "service/CatalogLocaleService.java",
    ],
    "hourslot-availability": [
        "service/AvailabilityService.java",
        "service/ScheduleService.java",
        "service/SlotLockService.java",
    ],
    "hourslot-booking": [
        "service/BookingService.java",
        "service/BookingStatusRules.java",
        "dto/DiscoverBranchResponse.java",
    ],
    "hourslot-payment": [
        "service/PaymentService.java",
    ],
    "hourslot-app": [
        "HourSlotApplication.java",
        "config/SecurityConfig.java",
        "config/JacksonConfig.java",
        "config/WebConfig.java",
        "exception/GlobalExceptionHandler.java",
        *[f"controller/{p.name}" for p in sorted((OLD_MAIN / "controller").glob("*.java"))],
    ],
}

TEST_MAPPING: dict[str, list[str]] = {
    "hourslot-booking": ["service/BookingStatusTransitionTest.java"],
    "hourslot-identity": ["security/JwtTokenTypeTest.java"],
}

PACKAGE_INFO = {
    "hourslot-common": ("com.hourslot.common", "Shared kernel: JDBC helpers, errors, config loaders. No domain services."),
    "hourslot-domain": ("com.hourslot.domain", "Shared persistence: entities, repositories, row mappers. One database for the monolith."),
    "hourslot-identity": ("com.hourslot.identity", "Auth, JWT, customer/user DTOs. Future identity service."),
    "hourslot-media": ("com.hourslot.media", "Object storage and media assets. Future media service."),
    "hourslot-notification": ("com.hourslot.notification", "In-app, email, preferences. Future notification service."),
    "hourslot-geo": ("com.hourslot.geo", "Countries, regions, currencies. Future geo service."),
    "hourslot-organization": ("com.hourslot.organization", "Orgs, businesses, staff, RBAC, plans, KYC. Future tenant service."),
    "hourslot-catalog": ("com.hourslot.catalog", "Categories, services, packages, peak pricing. Future catalog service."),
    "hourslot-availability": ("com.hourslot.availability", "Hours, holidays, slot generation and locks. Future availability service."),
    "hourslot-booking": ("com.hourslot.booking", "Bookings, reviews, favorites, discovery DTO. Future booking service."),
    "hourslot-payment": ("com.hourslot.payment", "Stripe and payment records. Future payment service."),
    "hourslot-app": ("com.hourslot.app", "Composition root: HTTP, security config, Flyway. Replaceable by an API gateway later."),
}


def java_dest(module: str, rel: str) -> Path:
    return ROOT / module / "src" / "main" / "java" / "com" / "hourslot" / rel


def test_dest(module: str, rel: str) -> Path:
    return ROOT / module / "src" / "test" / "java" / "com" / "hourslot" / rel


def write_package_info(module: str, package: str, doc: str) -> None:
    parts = package.split(".")
    path = ROOT / module / "src" / "main" / "java" / Path(*parts) / "package-info.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"/** {doc} */\npackage {package};\n", encoding="utf-8")


def move_file(src: Path, dest: Path) -> None:
    if not src.exists():
        raise SystemExit(f"Missing source file: {src}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dest))


def main() -> None:
    seen: set[str] = set()
    for module, rels in MAPPING.items():
        for rel in rels:
            key = rel.replace("\\", "/")
            if key in seen:
                raise SystemExit(f"Duplicate mapping: {key}")
            seen.add(key)
            move_file(OLD_MAIN / rel, java_dest(module, rel))

    leftover = [p.relative_to(OLD_MAIN).as_posix() for p in OLD_MAIN.rglob("*.java")]
    if leftover:
        raise SystemExit("Unmapped Java files:\n" + "\n".join(leftover))

    for module, rels in TEST_MAPPING.items():
        for rel in rels:
            move_file(OLD_TEST / rel, test_dest(module, rel))

    app_res = ROOT / "hourslot-app" / "src" / "main" / "resources"
    app_res.mkdir(parents=True, exist_ok=True)
    if OLD_RES.exists():
        for item in OLD_RES.iterdir():
            target = app_res / item.name
            if target.exists():
                if target.is_dir():
                    shutil.rmtree(target)
                else:
                    target.unlink()
            shutil.move(str(item), str(target))

    for module, (package, doc) in PACKAGE_INFO.items():
        write_package_info(module, package, doc)

    shutil.rmtree(ROOT / "src", ignore_errors=True)
    print("Relocation complete.")


if __name__ == "__main__":
    main()
