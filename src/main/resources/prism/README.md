Native builds of [Prism](https://github.com/ethindp/prism) live here, one directory
per platform/architecture:

- `windows-x86_64/prism.dll`
- `windows-aarch64/prism.dll`
- `macos-universal/libprism.dylib` - a single universal binary covering both
  Intel and Apple Silicon, so there's no separate `macos-x86_64`/`macos-aarch64`.
- `linux-x86_64/libprism.so`
- `linux-aarch64/libprism.so`

These are the `dynamic/release` builds from Prism's prebuilt SDK release for each
platform - not `static` (`.a`/`.lib` files are for linking into a C/C++ binary at
compile time, not for runtime loading) and not `debug`.

To add another platform/architecture, drop its native library in a matching
directory and add a case to `PrismController.NativeLibrary#detect()` - everything
else (method handles, speak/stop/shutdown) is already portable, since Prism's C
ABI is identical across platforms.

At runtime,
`com.nibblenerds.unitedminecraft.client.speech.PrismController` reads the
appropriate library from the classpath and copies it out to
`<game dir>/united_minecraft/prism-native/`, since none of Windows/macOS/Linux
can load a native library from memory - it has to be a real file on disk. This
is deliberately not a system temp directory: on Linux that's usually `/tmp`,
which many distros (and containers/Flatpak) mount `noexec`, silently breaking
the library load.

If no native library is available for the current platform, or Prism can't find a
usable speech backend at startup, the mod silently falls back to Minecraft's normal
narrator - this directory is not required for the mod to work.
