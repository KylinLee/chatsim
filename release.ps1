# 发布构建脚本
#
# 版本号来源：当前分支最新一条 release 提交的消息，格式固定为
#     release(<major.minor.patch>): <description>
# 该提交必须是当前 HEAD（发布时先提交一条 release 提交作为标记），脚本据此：
#   1. 校验工作区干净、HEAD 即最新 release 提交；
#   2. 构建 <flavor>Release APK（版本号由 Gradle 构建时从该提交动态读取），复制到 dist/ 并打印 SHA-256；
#   3. 输出自上次发布以来的提交列表，可选 -Tag 创建 v<version> 标签。
#
# 用法：
#   .\release.ps1 [-Flavor core|fdroid] [-Tag] [-DryRun]
#
# 示例：
#   git commit --allow-empty -m "release(1.2.0): rule engine"
#   .\release.ps1 -Tag

[CmdletBinding()]
param(
    [ValidateSet('core', 'fdroid')]
    [string]$Flavor = 'core',

    [switch]$Tag,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)

    $output = & git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') 失败（exit $LASTEXITCODE）"
    }
    return $output
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $dirty = @(Invoke-Git status --porcelain)
    if ($dirty.Count -gt 0) {
        throw "工作区不干净，请先提交或暂存改动：`n$($dirty -join "`n")"
    }

    $log = @(Invoke-Git log '--format=%H%x09%s')
    $releasePattern = '^([0-9a-f]{40})\trelease\(v?(\d+)\.(\d+)\.(\d+)\): (.+)$'
    $releases = @(
        foreach ($line in $log) {
            if ($line -match $releasePattern) {
                [pscustomobject]@{
                    Hash    = $Matches[1]
                    Version = "$($Matches[2]).$($Matches[3]).$($Matches[4])"
                    Major   = [int]$Matches[2]
                    Minor   = [int]$Matches[3]
                    Patch   = [int]$Matches[4]
                    Subject = $Matches[5]
                }
            }
        }
    )

    if ($releases.Count -eq 0) {
        throw '未找到 release 提交，请使用 release(<major.minor.patch>): <description> 格式标记发布点'
    }

    $current = $releases[0]
    $head = (Invoke-Git rev-parse HEAD | Select-Object -First 1).Trim()
    if ($head -ne $current.Hash) {
        throw "最新的 release 提交 $($current.Hash.Substring(0, 8)) 不是当前 HEAD（$($head.Substring(0, 8))），请将发布提交置于分支末端"
    }

    Write-Host "发布版本：$($current.Version)（HEAD=$($head.Substring(0, 8))）"
    Write-Host "构建变体：$Flavor"

    if ($releases.Count -gt 1) {
        $previous = $releases[1]
        Write-Host "自上次发布 $($previous.Version)（$($previous.Hash.Substring(0, 8))）以来的提交："
        @(Invoke-Git log --oneline "$($previous.Hash)..$($current.Hash)") | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host '首个 release 提交，无历史对比'
    }

    if ($DryRun) {
        Write-Host '[DryRun] 版本解析与校验完成，跳过构建'
        return
    }

    $variant = $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)
    & .\gradlew.bat "assemble${variant}Release" --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle 构建失败（exit $LASTEXITCODE）"
    }

    $apkPath = "app\build\outputs\apk\$Flavor\release\chatsim-$Flavor-release.apk"
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "未找到 APK：$apkPath"
    }

    $distDir = Join-Path $PSScriptRoot 'dist'
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    $distPath = Join-Path $distDir "chatsim-$Flavor-$($current.Version).apk"
    Copy-Item -LiteralPath $apkPath -Destination $distPath -Force

    $sha256 = (Get-FileHash -LiteralPath $distPath -Algorithm SHA256).Hash
    Write-Host "APK：$distPath"
    Write-Host "SHA-256：$sha256"

    if ($Tag) {
        $tagName = "v$($current.Version)"
        & git rev-parse -q --verify "refs/tags/$tagName" *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "标签 $tagName 已存在"
        }
        Invoke-Git tag -a $tagName -m "release $($current.Version)" | Out-Null
        Write-Host "已创建标签：$tagName"
    }
} finally {
    Pop-Location
}
