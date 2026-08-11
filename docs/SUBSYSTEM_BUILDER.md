# Subsystem Builder

The Subsystem Builder is an offline, project-backed editor under **Robot -> Subsystem Builder**. It
is available in normal student mode; it is not hidden with database/KDoc developer tools.

Each project stores subsystem definitions under `.ares/subsystems`. Saving creates immutable history
under `.ares/history/subsystems`, matching routine and controller-document behavior. **Save &
Generate** invokes the selected repository's Gradle wrapper without requiring a robot connection.

The screen has three working areas:

1. **Architecture** shows hardware, cached state, and output rules as one sensor-to-actuator flow.
2. **Inspector** edits the selected device, state value, or control rule and reports structural and
   mathematical validation errors immediately.
3. **Generated DSL + runtime** previews every robot and test source before anything is written.

Every target state becomes a typed action automatically (for example,
`subsystem.elevator.set.targetMeters`). The controls editor and routine builder discover these
derived actions from subsystem documents; students do not duplicate them in `action-catalog.json`
or write glue methods.

Generated robot sources are managed and overwritten on the next generation. Students who want full
ownership should copy the readable definition into a normal source package and implement a custom
`Subsystem`/`SubsystemIO`, then remove the visual document. Optional mechanisms can be marked
**Required at robot startup = false** so a missing optional device is reported and skipped; required
mechanisms fail robot initialization rather than running partially configured.

The generated `subsystem { ... }` file is intentionally readable. Beginners can remain visual,
intermediate students can learn the DSL, and advanced students retain direct access to
`SubsystemIO`, `Subsystem`, Redux, and custom controller code. See
`ARESLib-Kotlin/docs/subsystem-dsl.md` for the shared contract and examples.
