# HourSlot backend — single-src modular monolith

One Maven project, one `pom.xml`, one `src`, one main class. Domain boundaries are **Java packages**, not separate JARs.

```text
HourSlot-Backend/
  pom.xml
  src/main/java/com/hourslot/
    HourSlotApplication.java
    shared/
    identity/
    organization/      # tenancy, businesses, staff, RBAC, plans, KYC, provider ops, usage
    catalog/
    geo/
    availability/
    booking/
    request/           # marketplace service requests + matching
    quote/             # quotes against requests
    job/               # jobs, disputes, notes, media
    community/         # local communities
    payment/
    media/
    notification/      # in-app + WhatsApp channel stubs
    web/
```

Allowed dependency direction (do not reverse):

```text
web → domain packages → shared
quote/request/job → booking → availability → catalog → organization → identity → shared
community → identity → shared
```

## Run

```text
./mvnw spring-boot:run
```
