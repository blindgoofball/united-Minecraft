@echo off
setlocal EnableExtensions EnableDelayedExpansion

title United Minecraft - Setup and Build

echo ============================================================
echo         United Minecraft - Automated Setup ^& Build
echo ============================================================
echo.

cd /d "%~dp0"

:: ------------------------------------------------------------------
:: 1. Dynamic Java / JDK Detection across all available drives
:: ------------------------------------------------------------------
echo [1/4] Checking Java environment...

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] Java not found in PATH. Scanning available drives for JDK...
    set "FOUND_JAVA="
    for %%D in (C D E F G H I) do (
        if exist "%%D:\" (
            for /d %%J in ("%%D:\Program Files\Java\jdk*" "%%D:\Program Files\Eclipse Adoptium\jdk*" "%%D:\Program Files\Microsoft\jdk*" "%%D:\Java\jdk*") do (
                if exist "%%J\bin\java.exe" (
                    set "JAVA_HOME=%%J"
                    set "PATH=%%J\bin;!PATH!"
                    set "FOUND_JAVA=1"
                    echo [OK] Found JDK at: %%J
                    goto :java_checked
                )
            )
        )
    )
    if not defined FOUND_JAVA (
        echo [ERROR] Java [JDK 25 recommended] could not be found automatically.
        echo Please install JDK 25 and add it to your PATH or set JAVA_HOME.
        exit /b 1
    )
) else (
    echo [OK] Java detected in PATH.
)

:java_checked
java -version 2>&1 | findstr /i "version"
echo.

:: ------------------------------------------------------------------
:: 2. Dynamic Drive and .gradle Junction / Path Resolution
:: ------------------------------------------------------------------
echo [2/4] Verifying Gradle user directory and drive junctions...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$userGradle = Join-Path $env:USERPROFILE '.gradle';" ^
    "if (Test-Path $userGradle) {" ^
    "    $item = Get-Item -Force $userGradle;" ^
    "    if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {" ^
    "        $target = $item.Target;" ^
    "        if ($target) {" ^
    "            $targetPath = $target.Trim('{', '}');" ^
    "            if (-not (Test-Path $targetPath)) {" ^
    "                Write-Host '[INFO] Junction target does not exist. Auto-creating:' $targetPath;" ^
    "                New-Item -ItemType Directory -Path $targetPath -Force | Out-Null;" ^
    "            }" ^
    "        }" ^
    "    }" ^
    "} else {" ^
    "    try {" ^
    "        New-Item -ItemType Directory -Path $userGradle -Force | Out-Null;" ^
    "    } catch {" ^
    "        Write-Host '[WARN] Could not create .gradle in user profile. Selecting dynamic drive fallback...';" ^
    "        $drives = Get-PSDrive -PSProvider FileSystem | Sort-Object Free -Descending;" ^
    "        $bestDrive = $drives[0].Root;" ^
    "        $fallback = Join-Path $bestDrive '.gradle';" ^
    "        New-Item -ItemType Directory -Path $fallback -Force | Out-Null;" ^
    "        [Environment]::SetEnvironmentVariable('GRADLE_USER_HOME', $fallback, 'Process');" ^
    "    }" ^
    "}"

if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Setting GRADLE_USER_HOME to local fallback directory...
    if not exist "%~dp0.gradle-home" mkdir "%~dp0.gradle-home"
    set "GRADLE_USER_HOME=%~dp0.gradle-home"
)
echo [OK] Gradle user home directory verified.
echo.

:: ------------------------------------------------------------------
:: 3. Gradle Wrapper Verification & Auto-Recovery
:: ------------------------------------------------------------------
echo [3/4] Checking Gradle wrapper distribution...

call gradlew.bat --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] Gradle wrapper needs distribution download. Initializing auto-download...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$propsFile = 'gradle/wrapper/gradle-wrapper.properties';" ^
        "if (Test-Path $propsFile) {" ^
        "    $content = Get-Content $propsFile -Raw;" ^
        "    if ($content -match 'distributionUrl=(.+)') {" ^
        "        $url = $matches[1].Replace('\', '').Trim();" ^
        "        $zipName = [System.IO.Path]::GetFileName($url);" ^
        "        $distName = [System.IO.Path]::GetFileNameWithoutExtension($zipName);" ^
        "        $userHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' };" ^
        "        $wrapperDistDir = Join-Path $userHome ('wrapper/dists/' + $distName);" ^
        "        if (-not (Test-Path $wrapperDistDir)) { New-Item -ItemType Directory -Path $wrapperDistDir -Force | Out-Null };" ^
        "        $hashDirs = Get-ChildItem -Directory -Path $wrapperDistDir -ErrorAction SilentlyContinue;" ^
        "        $targetHashDir = if ($hashDirs) { $hashDirs[0].FullName } else { Join-Path $wrapperDistDir 'auto_download' };" ^
        "        if (-not (Test-Path $targetHashDir)) { New-Item -ItemType Directory -Path $targetHashDir -Force | Out-Null };" ^
        "        $targetZip = Join-Path $targetHashDir $zipName;" ^
        "        if (-not (Test-Path $targetZip) -or ((Get-Item $targetZip).Length -lt 1000000)) {" ^
        "            Write-Host '[INFO] Downloading Gradle distribution from:' $url;" ^
        "            curl.exe -L $url -o $targetZip;" ^
        "        }" ^
        "    }" ^
        "}"
)
echo [OK] Gradle wrapper ready.
echo.

:: ------------------------------------------------------------------
:: 4. Build Execution
:: ------------------------------------------------------------------
echo [4/4] Executing build...

if "%~1"=="" (
    echo [INFO] No arguments specified. Defaulting to: gradlew.bat build
    call gradlew.bat build
) else (
    echo [INFO] Running: gradlew.bat %*
    call gradlew.bat %*
)

set "BUILD_STATUS=%ERRORLEVEL%"
echo.
if %BUILD_STATUS% EQU 0 (
    echo ============================================================
    echo [SUCCESS] Build finished successfully!
    echo Output jars are located in: build\libs\
    echo ============================================================
) else (
    echo ============================================================
    echo [ERROR] Build failed with exit code %BUILD_STATUS%.
    echo ============================================================
)

exit /b %BUILD_STATUS%
