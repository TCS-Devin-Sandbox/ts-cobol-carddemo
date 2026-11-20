# CardDemo batch foundation

Run `mvn -B verify` from `java/` with JDK 21. The reactor parent uses Spring Boot
dependency management for Spring Batch, Flyway, PostgreSQL and Testcontainers.
This module provides configuration only: it does not launch a job or migrate a
database.

Spring Boot modules that depend on `carddemo-batch-common` can inject a `Clock`
fixed at 2022-07-05 10:00:00 UTC, matching the legacy parity fixtures.

Activate the Spring `docker` profile with `SPRING_PROFILES_ACTIVE=docker` when
running a batch application inside the `estate` service or another container
on its Docker Compose network. The profile uses `db:5432/carddemo`, the same
database as the legacy harness, and reads `PGUSER` and `PGPASSWORD` with the
existing Compose defaults. This profile is not for host-side connections.
