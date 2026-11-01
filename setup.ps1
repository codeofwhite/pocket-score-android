$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$tools = Join-Path $project '.tools'
New-Item -ItemType Directory -Path $tools -Force | Out-Null
function Get-Archive($url, $name, $destination) {
  $archive = Join-Path $tools $name
  $complete = $false
  $stalls = 0
  for ($attempt = 1; $attempt -le 40; $attempt++) {
    $previous = if (Test-Path -LiteralPath $archive) { (Get-Item -LiteralPath $archive).Length } else { 0 }
    try { Invoke-WebRequest -Uri $url -OutFile $archive -Resume -SslProtocol Tls12 -TimeoutSec 180; $complete = $true; break }
    catch {
      $current = if (Test-Path -LiteralPath $archive) { (Get-Item -LiteralPath $archive).Length } else { 0 }
      Write-Output "Download interrupted on attempt $attempt ($current bytes): $($_.Exception.Message)"
      if ($current -le $previous) { $stalls++ } else { $stalls = 0 }
      if ($stalls -ge 3) { throw }
    }
  }
  if (!$complete) { throw 'Download did not finish after 40 resumed transfers' }
  if (!(Test-Path -LiteralPath $destination)) { Expand-Archive -LiteralPath $archive -DestinationPath $destination }
}
$sdkProxyArgs = @()
$systemProxy = Get-ItemProperty -LiteralPath 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings' -ErrorAction SilentlyContinue
if ($systemProxy.ProxyEnable -eq 1 -and $systemProxy.ProxyServer -match '^([^:;]+):(\d+)$') {
  $sdkProxyArgs = @('--proxy=http', "--proxy_host=$($Matches[1])", "--proxy_port=$($Matches[2])")
}
Write-Output 'Downloading JDK 17' 
Get-Archive 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk17.zip' "$tools/jdk"
$jdk = (Get-ChildItem -LiteralPath "$tools/jdk" -Directory | Select-Object -First 1).FullName
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk/bin;$env:PATH"
Write-Output 'Downloading Gradle 8.13'
Get-Archive 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' 'gradle-8.13.zip' "$tools/gradle"
Write-Output 'Downloading Android command line tools'
Get-Archive 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' 'android-cli.zip' "$tools/android-cli"
$sdk = "$tools/android-sdk"
New-Item -ItemType Directory -Path $sdk -Force | Out-Null
$env:ANDROID_USER_HOME = "$tools/android-user"
$env:GRADLE_USER_HOME = "$tools/gradle-user"
1..100 | ForEach-Object { 'y' } | & "$tools/android-cli/cmdline-tools/bin/sdkmanager.bat" "--sdk_root=$sdk" @sdkProxyArgs --licenses
if ($LASTEXITCODE -ne 0) { throw 'SDK license acceptance failed' }
& "$tools/android-cli/cmdline-tools/bin/sdkmanager.bat" "--sdk_root=$sdk" @sdkProxyArgs 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
Set-Content -LiteralPath "$project/local.properties" -Value ('sdk.dir=' + $sdk.Replace('\','/')) -Encoding utf8
Write-Output 'Android build environment ready'



