# ============================================================
# 教务管理平台 - 数据库一键重置脚本
# 使用方法：.\sql\reset-all.ps1
# 前提：MySQL 已启动，root 密码 123456
# ============================================================

$mysqlPassword = "123456"
$sqlDir = "$PSScriptRoot"

Write-Host "=== 重置数据库 edu_admin ===" -ForegroundColor Cyan

# 1. DROP + CREATE
Write-Host "[1/3] 删除并重建空库..." -ForegroundColor Yellow
Get-Content "$sqlDir\reset.sql" | mysql -uroot -p"$mysqlPassword" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：重置失败，请检查 MySQL 是否运行" -ForegroundColor Red
    exit 1
}

# 2. Schema
Write-Host "[2/3] 建表..." -ForegroundColor Yellow
Get-Content "$sqlDir\schema.sql" | mysql -uroot -p"$mysqlPassword" -D edu_admin 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：建表失败" -ForegroundColor Red
    exit 1
}

# 3. Data
Write-Host "[3/3] 填充初始数据..." -ForegroundColor Yellow
Get-Content "$sqlDir\data.sql" | mysql -uroot -p"$mysqlPassword" -D edu_admin 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：数据插入失败" -ForegroundColor Red
    exit 1
}

Write-Host "完成！数据库 edu_admin 已重建，可以使用 admin/123456 登录" -ForegroundColor Green
