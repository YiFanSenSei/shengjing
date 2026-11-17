param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$assets = @(
    @{Name='ESC-50-33c8ce9.zip';Url='https://codeload.github.com/karolpiczak/ESC-50/zip/33c8ce9eb2cf0b1c2f8bcf322eb349b6be34dbb6';Algorithm='SHA256';Hash='661183a6f53ef04f12c9bd618fed0ddc1713280d6c94a5a5431e844ba6f6a21f'},
    @{Name='DLIVING_16k.zip';Url='https://zenodo.org/records/1227121/files/DLIVING_16k.zip?download=1';Algorithm='MD5';Hash='46741384d9e434a0bd8b3ec1830b6052'},
    @{Name='OOFFICE_16k.zip';Url='https://zenodo.org/records/1227121/files/OOFFICE_16k.zip?download=1';Algorithm='MD5';Hash='7b61cc2d182d5a654cb9c3101ddd4041'},
    @{Name='DKITCHEN_16k.zip';Url='https://zenodo.org/records/1227121/files/DKITCHEN_16k.zip?download=1';Algorithm='MD5';Hash='7ffbf52d7f4699f96927846103dc8788'}
)
foreach ($asset in $assets) {
    $path = Join-Path $Destination $asset.Name
    if ((Test-Path -LiteralPath $path) -and (Get-FileHash -LiteralPath $path -Algorithm $asset.Algorithm).Hash -ieq $asset.Hash) { continue }
    $partial = $path + '.partial'
    Invoke-WebRequest -Uri $asset.Url -OutFile $partial -TimeoutSec 600
    if ((Get-FileHash -LiteralPath $partial -Algorithm $asset.Algorithm).Hash -ine $asset.Hash) { throw "Checksum mismatch: $($asset.Name)" }
    Move-Item -LiteralPath $partial -Destination $path -Force
    Write-Output "$($asset.Name): verified"
}
