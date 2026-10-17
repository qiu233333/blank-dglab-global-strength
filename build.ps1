param(
    [Parameter(Mandatory = $true)]
    [string]$OriginalJar,

    [Parameter(Mandatory = $true)]
    [string]$MinecraftJar,

    [Parameter(Mandatory = $true)]
    [string]$LibrariesDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ProcessedModsDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ModsDirectory,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSCommandPath
$buildRoot = Join-Path $projectRoot 'build'
$classes = Join-Path $buildRoot 'classes'
$webSocketJar = Join-Path $buildRoot 'Java-WebSocket-1.5.4.jar'
$patchedJar = Join-Path $buildRoot 'Blank-DG-LAB-global-strength-26.2-Fabric.jar'

$OriginalJar = (Resolve-Path -LiteralPath $OriginalJar).Path
$MinecraftJar = (Resolve-Path -LiteralPath $MinecraftJar).Path
$LibrariesDirectory = (Resolve-Path -LiteralPath $LibrariesDirectory).Path
$ProcessedModsDirectory = (Resolve-Path -LiteralPath $ProcessedModsDirectory).Path
$ModsDirectory = (Resolve-Path -LiteralPath $ModsDirectory).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path

$expectedBaseSha256 = '4D688A9743E35EF6D02E18A94EED1D3259C02A0A09A03FA4DEB164255701CF37'
$actualBaseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $OriginalJar).Hash
if ($actualBaseSha256 -ne $expectedBaseSha256) {
    throw "The base JAR differs from the version used for this patch. Expected SHA-256: $expectedBaseSha256; got: $actualBaseSha256"
}

$javac = Join-Path $JavaHome 'bin\javac.exe'
$jarTool = Join-Path $JavaHome 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $jarTool)) {
    throw 'JavaHome must point to a JDK with javac.exe and jar.exe (Java 25 for Minecraft 26.2).'
}

New-Item -ItemType Directory -Path $classes -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($OriginalJar)
try {
    $entry = $archive.GetEntry('META-INF/jars/Java-WebSocket-1.5.4.jar')
    if ($null -eq $entry) { throw 'The base JAR has no bundled Java-WebSocket-1.5.4.jar; check the base version.' }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $webSocketJar, $true)
} finally {
    $archive.Dispose()
}

$dependencies = @($MinecraftJar, $OriginalJar, $webSocketJar)
$dependencies += Get-ChildItem -LiteralPath $LibrariesDirectory, $ProcessedModsDirectory, $ModsDirectory -Recurse -Filter '*.jar' -File |
    Select-Object -ExpandProperty FullName
$classpath = ($dependencies | Select-Object -Unique) -join [IO.Path]::PathSeparator
$sources = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'source') -Recurse -Filter '*.java' -File |
    Select-Object -ExpandProperty FullName

& $javac -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }

Copy-Item -LiteralPath $OriginalJar -Destination $patchedJar -Force
& $jarTool uf $patchedJar -C $classes 'com/blankdglab'
if ($LASTEXITCODE -ne 0) { throw 'JAR update failed.' }
& $jarTool tf $patchedJar | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Patched JAR failed integrity check.' }
Write-Output $patchedJar
