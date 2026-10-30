$ErrorActionPreference = 'Stop'
$taskProject = $PSScriptRoot
$taskPreviousJavaHome = $env:JAVA_HOME
try {
    $taskCandidates = @()
    if ($env:JAVA_HOME) { $taskCandidates += $env:JAVA_HOME }
    foreach ($taskJdkRoot in @((Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:ProgramFiles 'Microsoft'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'))) {
        if (Test-Path -LiteralPath $taskJdkRoot) { $taskCandidates += Get-ChildItem -LiteralPath $taskJdkRoot -Directory | Select-Object -ExpandProperty FullName }
    }
    $taskJdk = $taskCandidates | Select-Object -Unique | Where-Object {
        $taskRelease = Join-Path $_ 'release'
        (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe')) -and
        (Test-Path -LiteralPath $taskRelease) -and
        (Get-Content -LiteralPath $taskRelease | Where-Object { $_ -match '^JAVA_VERSION="25(?:[.\-+]|").*' })
    } | Sort-Object { if ($_ -match 'loom|ea-') { 1 } else { 0 } } | Select-Object -First 1
    if (-not $taskJdk) { throw 'JDK 25 fehlt. In IntelliJ unter Project Structure > SDK ein JDK 25 installieren.' }
    $env:JAVA_HOME = $taskJdk
    Push-Location -LiteralPath $taskProject
    try { & '.\gradlew.bat' --no-daemon build; if ($LASTEXITCODE -ne 0) { throw 'TheMob-Build fehlgeschlagen.' } }
    finally { Pop-Location }
    Write-Host 'Fertig: build/libs/TheMob-1.19.0-MC1.18-26.3.jar'
    Write-Host 'Oeffentliche API mit Quellen, JavaDoc und Beispiel: build/distributions/TheMob-Developer-SDK-1.19.0.zip'
    Write-Host 'Serverpaket und Quellprojekt: build/distributions/'
} finally { $env:JAVA_HOME = $taskPreviousJavaHome }
