# ============================================================
# Create 模组一键部署脚本
#
# 用途: 构建模组并部署到 Mindustry 的 mods 目录。
#       部署前自动检测游戏是否在运行 —— 游戏运行中覆盖 jar 会导致
#       类加载器懒加载失败 (NoClassDefFoundError), 必须先退游戏。
#
# 用法:
#   .\deploy.ps1            # 构建 + 部署
#   .\deploy.ps1 -SkipBuild # 跳过构建, 只部署现有 jar
#
# 注意: 需要在 PowerShell 5.1+ 下运行 (Windows 自带版本即可)。
# ============================================================
param(
    [switch]$SkipBuild,  # 传此开关则跳过 gradle 构建, 直接部署
    [switch]$NoPause     # 传此开关则结束时不等待回车 (脚本/自动化调用用)
)

$ErrorActionPreference = "Stop"
$project = $PSScriptRoot
$jar     = Join-Path $project "build\libs\CreateDesktop.jar"
$modsDir = Join-Path $env:APPDATA "Mindustry\mods"

Write-Host "=== Create 模组部署 ===" -ForegroundColor Cyan

# ----- 1. 检测游戏是否在运行 (关键防线) -----
# 游戏运行时持有 jar 文件句柄, 覆盖后已加载类正常、未加载类全部读取失败,
# 症状为 NoClassDefFoundError / ClassNotFoundException, 极难排查 —— 所以直接禁止。
$game = Get-Process -Name "mindustry*" -ErrorAction SilentlyContinue
if ($game) {
    Write-Host ""
    Write-Host "[X] 检测到 Mindustry 正在运行 (进程: $($game.Id -join ', '))!" -ForegroundColor Red
    Write-Host "    游戏运行中替换 jar 会导致懒加载崩溃 (NoClassDefFoundError)。" -ForegroundColor Yellow
    Write-Host "    请先完全退出游戏, 再重新运行本脚本。" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] 未检测到运行中的 Mindustry" -ForegroundColor Green

# ----- 2. 构建 (可跳过) -----
if (-not $SkipBuild) {
    Write-Host "[..] 正在构建 (gradlew build) ..." -ForegroundColor Cyan
    Push-Location $project
    try {
        & .\gradlew.bat build --console=plain
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[X] 构建失败, 已取消部署" -ForegroundColor Red
            exit 1
        }
    } finally {
        Pop-Location
    }
    Write-Host "[OK] 构建成功" -ForegroundColor Green
}

# ----- 3. 校验 jar 存在 -----
if (-not (Test-Path $jar)) {
    Write-Host "[X] 找不到 $jar, 请先构建 (去掉 -SkipBuild)" -ForegroundColor Red
    exit 1
}

# ----- 4. 检查 mods 目录里的旧版本残留 -----
# 多个可加载的 Create jar 并存时, 游戏可能加载到旧版本, 造成"改了没生效"。
# .mmd 后缀不会被加载器识别, 无需处理。
$stale = Get-ChildItem -Path $modsDir -Filter "Create*" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne "CreateDesktop.jar" -and $_.Extension -in ".jar", ".zip" }
if ($stale) {
    Write-Host "[!] mods 目录存在其他 Create 相关文件, 建议手动清理:" -ForegroundColor Yellow
    $stale | ForEach-Object { Write-Host "    $($_.Name)  ($($_.LastWriteTime))" -ForegroundColor Yellow }
}

# ----- 5. 部署 -----
Copy-Item $jar $modsDir -Force
$deployed = Get-Item (Join-Path $modsDir "CreateDesktop.jar")
Write-Host "[OK] 已部署: $($deployed.FullName)" -ForegroundColor Green
Write-Host "     大小: $([math]::Round($deployed.Length / 1MB, 1)) MB  时间: $($deployed.LastWriteTime)" -ForegroundColor Gray
Write-Host "=== 完成, 现在可以启动游戏了 ===" -ForegroundColor Cyan

# 双击/右键运行时窗口停留, 方便看结果; 脚本化调用传 -NoPause 跳过
if (-not $NoPause) {
    Read-Host "按回车退出"
}
