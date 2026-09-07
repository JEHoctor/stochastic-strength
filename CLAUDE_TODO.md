# CLAUDE TODO

Bugs / cleanup ideas noticed out of scope. Triage and address when convenient.

## Open — intended / accepted-by-design (no action needed, kept for visibility)

## Open — needs triage

### From explicit-workout-control (2026-09-07) final review, deferred
- app/build.gradle.kts: androidTest-only `resolutionStrategy.force` on kotlinx-serialization 1.8.1 (Room 2.8.4 MigrationTestHelper vs Compose BOM). Revisit when bumping either. Re-checked 2026-09-07: without the force, kotlinx-serialization-core resolves to 1.7.3 (down from Room's declared 1.8.1) and `Migration19To20Test` fails with `AbstractMethodError: GeneratedSerializer.typeParametersSerializers()`, so the force is still required.

