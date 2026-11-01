$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$tools = Join-Path $project '.tools'
$jdk = (Get-ChildItem -LiteralPath "$tools/jdk" -Directory | Select-Object -First 1).FullName
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk/bin;$env:PATH"
$env:GRADLE_USER_HOME = "$tools/gradle-user"
$env:ANDROID_USER_HOME = "$tools/android-user"
$systemProxy = Get-ItemProperty -LiteralPath 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings' -ErrorAction SilentlyContinue
if ($systemProxy.ProxyEnable -eq 1 -and $systemProxy.ProxyServer -match '^([^:;]+):(\d+)$') {
    $env:GRADLE_OPTS = "-Dhttp.proxyHost=$($Matches[1]) -Dhttp.proxyPort=$($Matches[2]) -Dhttps.proxyHost=$($Matches[1]) -Dhttps.proxyPort=$($Matches[2]) -Dhttp.nonProxyHosts=repo.maven.apache.org"
}
# Keep AF_UNIX socket paths below the Windows pathname limit.
$env:TEMP = $tools
$env:TMP = $tools
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$tools"
Push-Location $project
try {
    & "$tools/gradle/gradle-8.13/bin/gradle.bat" --no-daemon --console=plain testDebugUnitTest assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed' }
} finally { Pop-Location }

