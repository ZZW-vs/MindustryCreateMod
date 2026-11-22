<#
.SYNOPSIS
  本地化 key 检查: 扫描 src 中所有 bundle 文本读取点, 与 assets/bundles 的两份 bundle 比对。

.DESCRIPTION
  - 静态 key: bundle.get / getOrNull / format / has 的第一个字符串字面量参数
  - 三目/多字面量: 取该参数内全部字符串字面量
  - 动态拼接: 如 "faction." + name, 检查是否存在以该前缀开头的 key
  - Ability 类: 校验 ability.<类名去掉 Ability 后小写> 是否存在 (含匿名 Ability 检测)
  - 参照原版 bundle (参考/Mindustry-159.7源码) 作为白名单, 原版键不会误报
  - 额外检查两份 bundle 之间的键一致性

  仅做检查, 不接入 Gradle 构建流程, 不会打断正常打包。

.EXAMPLE
  tools\check-bundles.bat
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-bundles.ps1 -ShowVanilla
#>
[CmdletBinding()]
param(
    # 同时把命中原版白名单的 key 也列出来 (默认不显示)
    [switch]$ShowVanilla,
    # 从参考源码的 bundle.properties 重新生成 tools/vanilla-bundle-keys.txt
    [switch]$RefreshVanillaKeys
)

$ErrorActionPreference = 'Stop'
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

$root = Split-Path -Parent $PSScriptRoot
$srcDir = Join-Path $root 'src'
$baseBundle = Join-Path $root 'assets\bundles\bundle.properties'
$zhBundle = Join-Path $root 'assets\bundles\bundle_zh_CN.properties'
$vanillaBundle = Join-Path $root '参考\Mindustry-159.7源码\core\assets\bundles\bundle.properties'
$vanillaKeyFile = Join-Path $PSScriptRoot 'vanilla-bundle-keys.txt'

if (-not (Test-Path $srcDir)) { throw "找不到 src 目录: $srcDir" }
if (-not (Test-Path $baseBundle)) { throw "找不到 bundle.properties: $baseBundle" }
if (-not (Test-Path $zhBundle)) { throw "找不到 bundle_zh_CN.properties: $zhBundle" }

function Read-BundleKeys([string]$path) {
    $keys = New-Object 'System.Collections.Generic.HashSet[string]'
    if (-not (Test-Path $path)) { return ,$keys }
    foreach ($line in [IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)) {
        $t = $line.Trim()
        if ($t.Length -eq 0) { continue }
        if ($t.StartsWith('#') -or $t.StartsWith('!')) { continue }
        # properties: key=value 或 key:value, 跳过续行
        $m = [regex]::Match($t, '^([^=:\s]+)\s*[=:]')
        if ($m.Success) { [void]$keys.Add($m.Groups[1].Value) }
    }
    return ,$keys
}

function Get-LineNumber([string]$text, [int]$index) {
    $count = 0
    for ($i = 0; $i -lt $index; $i++) { if ($text[$i] -eq "`n") { $count++ } }
    return $count + 1
}

# 返回 [pscustomobject] 列表: Kind = static|dynamic|unresolved; Value; File; Line
function Get-BundleReferences([string]$file) {
    $refs = New-Object System.Collections.ArrayList
    $text = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
    $regex = [regex]'\bbundle\s*\.\s*(getOrNull|get|format|has)\s*\('
    foreach ($m in $regex.Matches($text)) {
        $argStart = $m.Index + $m.Length
        # 解析第一个实参: 到顶层逗号或右括号为止 (忽略字符串内的括号/逗号)
        $depth = 0; $inStr = $false; $esc = $false; $end = -1
        for ($j = $argStart; $j -lt $text.Length; $j++) {
            $c = $text[$j]
            if ($inStr) {
                if ($esc) { $esc = $false }
                elseif ($c -eq '\') { $esc = $true }
                elseif ($c -eq '"') { $inStr = $false }
            } else {
                if ($c -eq '"') { $inStr = $true }
                elseif ($c -eq '(' -or $c -eq '[' -or $c -eq '{') { $depth++ }
                elseif ($c -eq ')' -or $c -eq ']' -or $c -eq '}') {
                    if ($depth -eq 0) { $end = $j; break } else { $depth-- }
                }
                elseif ($c -eq ',' -and $depth -eq 0) { $end = $j; break }
            }
        }
        if ($end -lt 0) { continue }
        $arg = $text.Substring($argStart, $end - $argStart)
        $line = Get-LineNumber $text $m.Index

        $lits = @()
        foreach ($lm in [regex]::Matches($arg, '"((?:[^"\\]|\\.)*)"')) { $lits += $lm.Groups[1].Value }

        # 判断顶层是否存在字符串外的 '+'
        $hasPlus = $false; $inStr = $false; $esc = $false
        foreach ($ch in $arg.ToCharArray()) {
            if ($inStr) {
                if ($esc) { $esc = $false }
                elseif ($ch -eq '\') { $esc = $true }
                elseif ($ch -eq '"') { $inStr = $false }
            } else {
                if ($ch -eq '"') { $inStr = $true }
                elseif ($ch -eq '+') { $hasPlus = $true }
            }
        }

        if ($lits.Count -eq 0) {
            $refs.Add([pscustomobject]@{ Kind = 'unresolved'; Value = $arg.Trim(); File = $file; Line = $line }) | Out-Null
        } elseif ($hasPlus) {
            $refs.Add([pscustomobject]@{ Kind = 'dynamic'; Value = $lits[0]; File = $file; Line = $line }) | Out-Null
        } else {
            foreach ($k in $lits) {
                $refs.Add([pscustomobject]@{ Kind = 'static'; Value = $k; File = $file; Line = $line }) | Out-Null
            }
        }
    }
    return $refs
}

$baseKeys = Read-BundleKeys $baseBundle
$zhKeys = Read-BundleKeys $zhBundle

# 原版 key 白名单: 优先用随仓库携带的快照 (参考/ 目录不进 Git, 新克隆也能用)
$vanillaKeys = New-Object 'System.Collections.Generic.HashSet[string]'
if ($RefreshVanillaKeys -and (Test-Path $vanillaBundle)) {
    $vSet = Read-BundleKeys $vanillaBundle
    $vSorted = @($vSet | Sort-Object)
    [IO.File]::WriteAllLines($vanillaKeyFile, $vSorted, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ("已刷新原版 key 快照: {0} 个 -> {1}" -f $vSorted.Count, $vanillaKeyFile) -ForegroundColor DarkGray
}
if (Test-Path $vanillaKeyFile) {
    foreach ($l in [IO.File]::ReadAllLines($vanillaKeyFile, [Text.Encoding]::UTF8)) {
        $t = $l.Trim()
        if ($t.Length -gt 0) { [void]$vanillaKeys.Add($t) }
    }
} elseif (Test-Path $vanillaBundle) {
    $vanillaKeys = Read-BundleKeys $vanillaBundle
} else {
    Write-Host '警告: 未找到原版 key 快照或参考 bundle, 原版键可能被误报为缺失。' -ForegroundColor Yellow
}

$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter '*.java' | Sort-Object FullName
$refs = New-Object System.Collections.ArrayList
foreach ($f in $javaFiles) {
    foreach ($r in (Get-BundleReferences $f.FullName)) { $refs.Add($r) | Out-Null }
}

$rel = {
    param($p)
    $full = [IO.Path]::GetFullPath($p)
    if ($full.StartsWith($root + '\')) { return $full.Substring($root.Length + 1) } else { return $full }
}

$missingBase = @{}
$missingZh = @{}
$vanillaSkipped = 0
$dynamicPrefixes = @{}

foreach ($r in $refs) {
    if ($r.Kind -eq 'static') {
        $k = $r.Value
        $inBase = $baseKeys.Contains($k)
        $inZh = $zhKeys.Contains($k)
        if ($inBase -and $inZh) { continue }
        if ($vanillaKeys.Contains($k)) {
            $vanillaSkipped++
            if ($ShowVanilla) {
                Write-Host ("  [原版键] " + $k + "  (" + (& $rel $r.File) + ":" + $r.Line + ")") -ForegroundColor DarkGray
            }
            continue
        }
        $where = (& $rel $r.File) + ':' + $r.Line
        if (-not $inBase) {
            if (-not $missingBase.ContainsKey($k)) { $missingBase[$k] = New-Object System.Collections.ArrayList }
            $missingBase[$k].Add($where) | Out-Null
        }
        if (-not $inZh) {
            if (-not $missingZh.ContainsKey($k)) { $missingZh[$k] = New-Object System.Collections.ArrayList }
            $missingZh[$k].Add($where) | Out-Null
        }
    } elseif ($r.Kind -eq 'dynamic') {
        $prefix = $r.Value
        if ($prefix.Length -eq 0) { continue }
        $matched = $false
        foreach ($k in $baseKeys) { if ($k.StartsWith($prefix)) { $matched = $true; break } }
        if (-not $matched) { foreach ($k in $vanillaKeys) { if ($k.StartsWith($prefix)) { $matched = $true; break } } }
        if (-not $matched) {
            $where = (& $rel $r.File) + ':' + $r.Line
            if (-not $dynamicPrefixes.ContainsKey($prefix)) { $dynamicPrefixes[$prefix] = New-Object System.Collections.ArrayList }
            $dynamicPrefixes[$prefix].Add($where) | Out-Null
        }
    }
}

# Ability 本地化 key 检查 (Mindustry 的 Ability.localized 用 ability.<类名小写>)
$abilityProblems = New-Object System.Collections.ArrayList
$classRegex = [regex]'class\s+(\w+)\s+extends\s+(?:[\w\.]*\.)?\w*Ability\b'
$anonRegex = [regex]'new\s+(?:[\w\.]*\.)?Ability\s*\(\s*\)\s*\{'
foreach ($f in $javaFiles) {
    $text = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
    foreach ($m in $classRegex.Matches($text)) {
        $cls = $m.Groups[1].Value
        $key = 'ability.' + ($cls -replace 'Ability$', '').ToLowerInvariant()
        if (-not ($baseKeys.Contains($key) -or $vanillaKeys.Contains($key))) {
            $abilityProblems.Add(("缺少 $key (类 $cls)  " + (& $rel $f.FullName) + ':' + (Get-LineNumber $text $m.Index))) | Out-Null
        }
    }
    foreach ($m in $anonRegex.Matches($text)) {
        $abilityProblems.Add(("匿名 Ability 会显示 ???ability.ability???  " + (& $rel $f.FullName) + ':' + (Get-LineNumber $text $m.Index))) | Out-Null
    }
}

# 两份 bundle 之间的键一致性
$onlyBase = @($baseKeys | Where-Object { -not $zhKeys.Contains($_) } | Sort-Object)
$onlyZh = @($zhKeys | Where-Object { -not $baseKeys.Contains($_) } | Sort-Object)

# ---------- 输出 ----------
Write-Host ''
Write-Host '===== Bundle 本地化 key 检查 =====' -ForegroundColor Cyan
Write-Host ("源码: {0} 个 .java, 引用点 {1} 个 (静态 {2} / 动态 {3} / 无法解析 {4})" -f `
        $javaFiles.Count, $refs.Count,
        (@($refs | Where-Object { $_.Kind -eq 'static' }).Count),
        (@($refs | Where-Object { $_.Kind -eq 'dynamic' }).Count),
        (@($refs | Where-Object { $_.Kind -eq 'unresolved' }).Count))
Write-Host ("bundle.properties: {0} 键 | bundle_zh_CN.properties: {1} 键 | 原版参考: {2} 键" -f `
        $baseKeys.Count, $zhKeys.Count, $vanillaKeys.Count)
if ($vanillaSkipped -gt 0) { Write-Host ("命中原版键 {0} 个, 已按原版白名单跳过 (加 -ShowVanilla 可查看)" -f $vanillaSkipped) -ForegroundColor DarkGray }
Write-Host ''

$issues = 0

function Print-Missing($title, $map) {
    if ($map.Count -eq 0) { return }
    Write-Host ("[缺失] " + $title + " 缺少 {0} 个 key:" -f $map.Count) -ForegroundColor Red
    foreach ($k in ($map.Keys | Sort-Object)) {
        Write-Host ("  - " + $k) -ForegroundColor Yellow
        foreach ($w in $map[$k]) { Write-Host ("      " + $w) -ForegroundColor Gray }
    }
    Write-Host ''
}

if ($missingBase.Count -gt 0) { $issues += $missingBase.Count; Print-Missing 'bundle.properties' $missingBase }
if ($missingZh.Count -gt 0) { $issues += $missingZh.Count; Print-Missing 'bundle_zh_CN.properties' $missingZh }

if ($dynamicPrefixes.Count -gt 0) {
    Write-Host ("[动态 key] {0} 个拼接前缀在 bundle 中无匹配:" -f $dynamicPrefixes.Count) -ForegroundColor Red
    foreach ($p in ($dynamicPrefixes.Keys | Sort-Object)) {
        Write-Host ('  - "' + $p + '" + ...') -ForegroundColor Yellow
        foreach ($w in $dynamicPrefixes[$p]) { Write-Host ('      ' + $w) -ForegroundColor Gray }
    }
    Write-Host ''
    $issues += $dynamicPrefixes.Count
}

if ($abilityProblems.Count -gt 0) {
    Write-Host ("[Ability] {0} 个 Ability 本地化问题:" -f $abilityProblems.Count) -ForegroundColor Red
    foreach ($p in $abilityProblems) { Write-Host ('  - ' + $p) -ForegroundColor Yellow }
    Write-Host ''
    $issues += $abilityProblems.Count
}

if ($onlyBase.Count -gt 0) {
    Write-Host ("[一致性] 仅存在于 bundle.properties 的 key ({0} 个):" -f $onlyBase.Count) -ForegroundColor DarkYellow
    foreach ($k in $onlyBase) { Write-Host ('  - ' + $k) -ForegroundColor Gray }
    Write-Host ''
    $issues += $onlyBase.Count
}
if ($onlyZh.Count -gt 0) {
    Write-Host ("[一致性] 仅存在于 bundle_zh_CN.properties 的 key ({0} 个):" -f $onlyZh.Count) -ForegroundColor DarkYellow
    foreach ($k in $onlyZh) { Write-Host ('  - ' + $k) -ForegroundColor Gray }
    Write-Host ''
    $issues += $onlyZh.Count
}

if ($issues -eq 0) {
    Write-Host '结果: 未发现缺失或明显的动态 key 问题。' -ForegroundColor Green
    exit 0
} else {
    Write-Host ("结果: 共发现 {0} 处问题 (缺失 key / 动态前缀 / Ability 文案 / 两 bundle 不一致)。" -f $issues) -ForegroundColor Red
    exit 1
}
