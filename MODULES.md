# HourSlot backend — single-src modular monolith

One Maven project, one `pom.xml`, one `src`, one main class. Domain boundaries are **Java packages**, not separate JARs.

```text
HourSlot-Backend/
  pom.xml
  src/main/java/com/hourslot/
    HourSlotApplication.java     # only main
    shared/                      # JDBC, config loaders, errors, utils
    identity/                    # users, JWT, auth DTOs
    organization/                # tenancy, businesses, staff, RBAC, plans, KYC
      services/ model/ repository/
    catalog/                     # categories, services, packages, peak pricing
      services/ model/ repository/
    geo/
      services/ dto/
    availability/                # hours, holidays, slots, Redis locks
      services/ model/ repository/
    booking/                     # bookings, reviews, favorites
      services/ model/ repository/ dto/
    payment/
      services/ model/ repository/
    media/
      services/ model/ repository/
    notification/
      services/ model/ repository/
    web/                         # controllers, SecurityConfig, exception handler
  src/main/resources/            # application.yml, Flyway, log4j2
```

```text
HourSlotApplication
        │
        ├── web (HTTP)
        ├── identity
        ├── organization
        ├── catalog
        ├── geo
        ├── availability
        ├── booking
        ├── payment
        ├── media
        └── notification
                │
                └── shared
```

Allowed dependency direction (do not reverse):

```text
web → domain packages → shared
booking → availability → catalog → organization → identity → shared
payment → notification, booking models
```

`web` is the composition root for HTTP. Domain packages must not depend on controllers.

## Run

```text
./mvnw spring-boot:run
```

JAR: `target/backend.jar` (`mainClass` = `com.hourslot.HourSlotApplication`).

## Add a future domain (request, quote, job)

1. Create `src/main/java/com/hourslot/<name>/` with a `package-info.java`.
2. Put application services in `<name>.services`; models in `<name>.model`; repos in `<name>.repository`.
3. Put REST under `web.controller`.
4. Depend only downward (not on `web`).
