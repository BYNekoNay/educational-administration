# EduAdmin server startup
# Usage: cd backend && .\start.ps1

$env:JWT_SECRET = "demo-secret-key-change-in-prod"
$env:DB_PASSWORD = "123456"

Write-Host "[>] Starting backend on port 8080..."
mvn.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8080"
