# Releasing

Notes for cutting a release, not needed just to play with or build the mod -
see [README.md](README.md) for that.

## Versioning

`mod_version` in [`gradle.properties`](gradle.properties) is plain
[SemVer](https://semver.org/) - `MAJOR.MINOR.PATCH` - and stays under `1.0.0`
(`0.y.z`) until the mod's feature set and behavior are stable enough to call
it 1.0. Within `0.y.z`, bump `y` for new features and `z` for fixes, same as
normal SemVer practice.

This version number is deliberately independent of the Minecraft version -
see below for how the two combine in a release's tag and jar filename.

## Tag and jar filename

Every release targets exactly one Minecraft version (`minecraft_version` in
`gradle.properties`) and stops working once that version is superseded, so
both the git tag and the built jar's filename bake the Minecraft version in
directly - self-describing even with zero other context, e.g. a jar sitting
alone in someone's downloads folder:

- Git tag: `v{mod_version}+mc{minecraft_version}` - e.g. `v0.1.0+mc26.2`
- Jar filename: `united-minecraft-{mod_version}+mc{minecraft_version}.jar` -
  e.g. `united-minecraft-0.1.0+mc26.2.jar`

The jar filename is produced automatically by the `jar` task in
`build.gradle` - running `./gradlew build` already names it correctly, no
manual renaming needed. `fabric.mod.json`'s own `version` field stays the
plain mod version (no `+mc...` suffix) - only the tag and filename carry it.

## GitHub release

1. Bump `mod_version` in `gradle.properties` if this release includes changes
   since the last one (it usually will).
2. Commit that version bump.
3. Tag the commit: `git tag v{mod_version}+mc{minecraft_version}`, then
   `git push origin v{mod_version}+mc{minecraft_version}`.
4. Run `./gradlew build` and grab
   `build/libs/united-minecraft-{mod_version}+mc{minecraft_version}.jar`
   (not the `-sources.jar` - that's for IDEs, not for players).
5. Create the GitHub release from that tag:
   - **Title**: `United Minecraft {mod_version} for Minecraft {minecraft_version}`
     - e.g. `United Minecraft 0.1.0 for Minecraft 26.2`
   - **Mark as pre-release** while the mod is still under `1.0.0` - visible
     as a badge, doesn't hide the release or stop it from being downloaded.
   - **Notes**: a short "What's new" list, plus the Fabric Loader and Fabric
     API versions this build was built/tested against (from
     `gradle.properties`) so players installing via the README's
     Installation steps know exactly what to grab.
   - Attach the jar from step 4.
