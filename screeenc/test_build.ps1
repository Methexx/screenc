# Build Verification Script for Windows
# Run this script to verify the project builds correctly

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Screen Receiver - Build Test Suite" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$ErrorCount = 0

# Test 1: Check Flutter Installation
Write-Host "[1/7] Checking Flutter installation..." -ForegroundColor Yellow
try {
    $flutterVersion = flutter --version 2>&1 | Select-String "Flutter" | Select-Object -First 1
    if ($flutterVersion) {
        Write-Host "  ✓ Flutter is installed: $flutterVersion" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Flutter not found" -ForegroundColor Red
        $ErrorCount++
    }
} catch {
    Write-Host "  ✗ Flutter not found in PATH" -ForegroundColor Red
    $ErrorCount++
}

# Test 2: Check Dart SDK
Write-Host "[2/7] Checking Dart SDK..." -ForegroundColor Yellow
try {
    $dartVersion = dart --version 2>&1 | Out-String
    if ($dartVersion -match "Dart") {
        Write-Host "  ✓ Dart SDK is available" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Dart SDK not found" -ForegroundColor Red
        $ErrorCount++
    }
} catch {
    Write-Host "  ✗ Dart SDK not found" -ForegroundColor Red
    $ErrorCount++
}

# Test 3: Check pubspec.yaml
Write-Host "[3/7] Verifying pubspec.yaml..." -ForegroundColor Yellow
if (Test-Path "pubspec.yaml") {
    Write-Host "  ✓ pubspec.yaml exists" -ForegroundColor Green
    $pubspecContent = Get-Content "pubspec.yaml" -Raw
    if ($pubspecContent -match "permission_handler") {
        Write-Host "  ✓ Required dependencies configured" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Missing required dependencies" -ForegroundColor Red
        $ErrorCount++
    }
} else {
    Write-Host "  ✗ pubspec.yaml not found" -ForegroundColor Red
    $ErrorCount++
}

# Test 4: Flutter Clean & Get Dependencies
Write-Host "[4/7] Running flutter clean and pub get..." -ForegroundColor Yellow
try {
    flutter clean | Out-Null
    Write-Host "  ✓ Flutter clean completed" -ForegroundColor Green
    
    $pubGetOutput = flutter pub get 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Dependencies fetched successfully" -ForegroundColor Green
    } else {
        Write-Host "  ✗ flutter pub get failed" -ForegroundColor Red
        Write-Host $pubGetOutput -ForegroundColor Red
        $ErrorCount++
    }
} catch {
    Write-Host "  ✗ Error during flutter pub get" -ForegroundColor Red
    $ErrorCount++
}

# Test 5: Flutter Analyze
Write-Host "[5/7] Running flutter analyze..." -ForegroundColor Yellow
try {
    $analyzeOutput = flutter analyze 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0 -or $analyzeOutput -match "No issues found") {
        Write-Host "  ✓ No analysis issues found" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ Analysis found issues:" -ForegroundColor Yellow
        Write-Host $analyzeOutput -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ✗ flutter analyze failed" -ForegroundColor Red
    $ErrorCount++
}

# Test 6: Run Flutter Tests
Write-Host "[6/7] Running flutter test..." -ForegroundColor Yellow
try {
    $testOutput = flutter test 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ All tests passed" -ForegroundColor Green
        # Extract test count
        if ($testOutput -match "All tests passed!") {
            Write-Host "  $($Matches[0])" -ForegroundColor Green
        }
    } else {
        Write-Host "  ✗ Some tests failed" -ForegroundColor Red
        Write-Host $testOutput -ForegroundColor Red
        $ErrorCount++
    }
} catch {
    Write-Host "  ✗ flutter test failed" -ForegroundColor Red
    $ErrorCount++
}

# Test 7: Build APK (Debug)
Write-Host "[7/7] Building debug APK..." -ForegroundColor Yellow
try {
    Write-Host "  This may take a few minutes..." -ForegroundColor Gray
    $buildOutput = flutter build apk --debug 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ APK built successfully" -ForegroundColor Green
        $apkPath = "build\app\outputs\flutter-apk\app-debug.apk"
        if (Test-Path $apkPath) {
            $apkSize = (Get-Item $apkPath).Length / 1MB
            Write-Host "  APK Size: $([math]::Round($apkSize, 2)) MB" -ForegroundColor Cyan
            Write-Host "  Location: $apkPath" -ForegroundColor Cyan
        }
    } else {
        Write-Host "  ✗ APK build failed" -ForegroundColor Red
        Write-Host $buildOutput -ForegroundColor Red
        $ErrorCount++
    }
} catch {
    Write-Host "  ✗ Build process failed" -ForegroundColor Red
    $ErrorCount++
}

# Summary
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Build Verification Summary" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

if ($ErrorCount -eq 0) {
    Write-Host "✓ ALL CHECKS PASSED - Project builds successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Connect Android device via USB" -ForegroundColor White
    Write-Host "  2. Run: adb forward tcp:27183 tcp:27183" -ForegroundColor White
    Write-Host "  3. Run: flutter run" -ForegroundColor White
    exit 0
} else {
    Write-Host "✗ BUILD VERIFICATION FAILED - $ErrorCount error(s) found" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please fix the above errors and run this script again." -ForegroundColor Yellow
    exit 1
}
