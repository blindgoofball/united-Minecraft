Native builds of [Prism](https://github.com/ethindp/prism) live here, one directory
per platform/architecture:

- `windows-x86_64/prism.dll` - the only platform wired up today.

To add another platform, drop its build (e.g. `macos-x86_64/libprism.dylib`,
`macos-aarch64/libprism.dylib`, `linux-x86_64/libprism.so`) in a matching directory
and add a case to `PrismController.NativeLibrary#detect()` - everything else
(method handles, speak/stop/shutdown) is already portable, since Prism's C ABI is
identical across platforms.

At runtime,
`com.nibblenerds.unitedminecraft.client.speech.PrismController` reads the
appropriate library from the classpath and copies it out to a temp file, since
none of Windows/macOS/Linux can load a native library from memory - it has to be a
real file on disk.

If no native library is available for the current platform, or Prism can't find a
usable speech backend at startup, the mod silently falls back to Minecraft's normal
narrator - this directory is not required for the mod to work.
