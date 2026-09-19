# Releasing

Notes for cutting a release, not needed just to play with or build the mod -
see [README.md](README.md) for that.

## Versioning

`mod_version` in [`gradle.properties`](gradle.properties) is plain
[SemVer](https://semver.org/) - `MAJOR.MINOR.PATCH`. Bump `MINOR` for new
features and `PATCH` for fixes, same as normal SemVer practice.

This version number is deliberately independent of the Minecraft version -
see below for how the two combine in a release's tag and jar filenames. It is
also shared by both loaders: a release is one version of the mod, shipped as
one jar per loader, never a separate version number per loader.

## Tag and jar filenames

Every release targets exactly one Minecraft version (`minecraft_version` in
`gradle.properties`) and stops working once that version is superseded. Both
the git tag and the built jars' filenames bake that in directly, and the jars
also carry the loader - so a jar sitting alone in someone's downloads folder
still answers both questions a person holding it has to answer:

- Git tag: `v{mod_version}+mc{minecraft_version}` - e.g. `v1.2.0+mc26.3`
- Fabric jar: `united-minecraft-{mod_version}+mc{minecraft_version}-fabric.jar`
- NeoForge jar: `united-minecraft-{mod_version}+mc{minecraft_version}-neoforge.jar`

There is one tag per release, not one per loader. Both jars are attached to
that single GitHub release.

The filenames are produced automatically by the `jar` task in each loader
module - running `./gradlew build` already names them correctly, no manual
renaming needed. The `version` field in `fabric.mod.json` and
`neoforge.mods.toml` stays the plain mod version (no `+mc...` suffix, no
loader suffix) - only the tag and filenames carry those.

## When NeoForge hasn't caught up

Fabric is tier-1: a release goes out the day a new Minecraft version does,
without waiting for anything else. NeoForge trails each Minecraft release by
days to weeks, because NeoForge itself does.

Do not hold a release for it. Set `neoforge_enabled=false` in
`gradle.properties` and everything - `./gradlew build`, CI, the steps below -
works exactly as it would with one loader, producing the Fabric jar alone.
Ship that, with a line in the release notes saying a NeoForge build will
follow.

When NeoForge catches up, set the flag back to `true`, bump
`neoforge_version`, and cut a normal release that ships both jars again. The
Minecraft version's first NeoForge-capable release is an ordinary release,
not a special one.

## GitHub release

1. Bump `mod_version` in `gradle.properties` if this release includes changes
   since the last one (it usually will).
2. Commit that version bump.
3. Tag the commit: `git tag v{mod_version}+mc{minecraft_version}`, then
   `git push origin v{mod_version}+mc{minecraft_version}`.
4. Run `./gradlew build` and collect both jars (not the `-sources.jar` files -
   those are for IDEs, not for players):
   - `fabric/build/libs/united-minecraft-{mod_version}+mc{minecraft_version}-fabric.jar`
   - `neoforge/build/libs/united-minecraft-{mod_version}+mc{minecraft_version}-neoforge.jar`

   If `neoforge_enabled` is `false`, there is no NeoForge jar and that is
   expected - see above.
5. Create the GitHub release from that tag:
   - **Title**: `United Minecraft {mod_version} for Minecraft {minecraft_version}`
     - e.g. `United Minecraft 1.2.0 for Minecraft 26.3`
   - **Notes**: a short "What's new" list, plus the loader versions this build
     was built and tested against (from `gradle.properties`) so players
     installing via the README's Installation steps know exactly what to grab:
     Fabric Loader and Fabric API for the Fabric jar, NeoForge for the
     NeoForge jar. Say which loaders this release includes, especially when it
     is Fabric-only.
   - Attach both jars from step 4.
