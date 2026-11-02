# United Minecraft installer (prototype)

Console installer for the official Minecraft launcher on Windows. Installs or updates Fabric Loader,
Fabric API and United Minecraft, and creates a "United Minecraft" launcher profile.

Build (MinGW):

    gcc -O2 -c third_party/miniz.c -I third_party -o miniz.o
    g++ -std=c++20 -Os -municode -Wall -I third_party -I . main.cpp http.cpp hash.cpp miniz.o -o united-installer.exe -static -lwinhttp -lbcrypt
    strip united-installer.exe

Options: `--dry-run` (show the plan, change nothing), `--yes` (skip the prompt), `--mc <version>`,
`--game-dir <folder>` (default `%APPDATA%\.minecraft`).

If a Fabric profile for the same Minecraft version already exists, the installer asks whether to use
it or create a separate "United Minecraft" profile (with `--yes` it creates the separate one). The
choice is remembered in `united-installer.json` in the Minecraft folder, so updates don't ask again.

It works with either launcher profile file (`launcher_profiles.json`, or
`launcher_profiles_microsoft_store.json` for the Microsoft Store launcher) and asks when both exist
(`--launcher win32|microsoft_store` skips the question). It creates the `mods` folder if missing and
pre-downloads Fabric's libraries into `libraries/`, as the Fabric installer does.
