Place `nvdaControllerClient.dll` in this directory.

It comes from the NVDA source tree at `nvda_source/miscDeps/server/x64/nvdaControllerClient.dll`
(also included in the NVDA Controller Client zip on the NV Access downloads page).
Recent NVDA releases dropped the separate `nvdaControllerClient32.dll` /
`nvdaControllerClient64.dll` names in favor of this single 64-bit `nvdaControllerClient.dll`.
It is freely redistributable with third-party applications, which is exactly what
it's for.

At runtime, `com.nibblenerds.unitedminecraft.client.speech.NvdaController` reads it
from the classpath (`/nvda/nvdaControllerClient.dll`) and copies it out to a temp
file, since Windows can only `LoadLibrary` a DLL from disk, not from memory.

If the DLL is missing, or NVDA isn't running, the mod silently falls back to
Minecraft's normal narrator (SAPI/text2speech) — this file is not required for the
mod to work.
