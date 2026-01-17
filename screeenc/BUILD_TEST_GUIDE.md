# Build Test Documentation

## Overview
This document provides comprehensive build testing procedures for the Screen Receiver Android application.

---

## Quick Build Test

Run the automated build verification script:

```powershell
# Windows
.\test_build.ps1

# Or manually:
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

---

## Test Categories

### 1. **Compilation Tests**

#### Check for Syntax Errors
```bash
flutter analyze
```

**Expected Output:**
```
Analyzing screeenc...
No issues found!
```

#### Build Dart Code
```bash
flutter pub get
dart compile exe lib/main.dart
```

---

### 2. **Unit & Widget Tests**

#### Run All Tests
```bash
flutter test
```

**Test Coverage:**
- ✅ App initialization
- ✅ Widget building
- ✅ Platform channel configuration
- ✅ UI element presence
- ✅ Button interactions
- ✅ State management
- ✅ Instructions display

#### Run Specific Test
```bash
flutter test test/widget_test.dart
```

#### Run with Coverage
```bash
flutter test --coverage
```

View coverage report:
```bash
# Install genhtml (part of lcov)
# Windows: Install from http://ltp.sourceforge.net/coverage/lcov.php

genhtml coverage/lcov.info -o coverage/html
start coverage/html/index.html
```

---

### 3. **Android Native Code Tests**

#### Compile Kotlin Code
```bash
cd android
./gradlew compileDebugKotlin
```

**Expected Output:**
```
BUILD SUCCESSFUL
```

#### Check for Android Lint Issues
```bash
cd android
./gradlew lint
```

#### Build Android AAR
```bash
cd android
./gradlew assembleDebug
```

---

### 4. **Build Tests**

#### Build Debug APK
```bash
flutter build apk --debug
```

**Output Location:** `build/app/outputs/flutter-apk/app-debug.apk`

**Expected Size:** ~40-60 MB

#### Build Release APK (Requires Signing)
```bash
flutter build apk --release
```

#### Build App Bundle
```bash
flutter build appbundle
```

**Output Location:** `build/app/outputs/bundle/release/app-release.aab`

#### Build for Specific ABI
```bash
# ARM64 only (smaller size)
flutter build apk --target-platform android-arm64

# Multiple ABIs
flutter build apk --split-per-abi
```

---

### 5. **Integration Tests**

#### Check Platform Channel Communication
```bash
flutter test integration_test/platform_channel_test.dart
```

#### Run on Connected Device
```bash
# List devices
flutter devices

# Run on specific device
flutter run -d <device-id>
```

---

### 6. **Performance Tests**

#### Profile Build
```bash
flutter build apk --profile
flutter run --profile
```

#### Measure Build Time
```bash
# Clean build
flutter clean
Measure-Command { flutter build apk --debug }
```

#### Check APK Size
```bash
flutter build apk --analyze-size
```

---

## Build Verification Checklist

### Pre-Build Checks
- [ ] Flutter SDK installed (3.10.1+)
- [ ] Dart SDK available
- [ ] Android SDK configured
- [ ] Gradle wrapper executable
- [ ] Internet connection (for dependencies)

### Build Process Checks
- [ ] `pubspec.yaml` valid
- [ ] Dependencies resolved (`flutter pub get`)
- [ ] No analysis errors (`flutter analyze`)
- [ ] All tests pass (`flutter test`)
- [ ] Android manifest valid
- [ ] Kotlin code compiles
- [ ] APK builds successfully

### Post-Build Verification
- [ ] APK file exists in output directory
- [ ] APK size is reasonable (~40-60 MB debug)
- [ ] APK can be installed on device
- [ ] App launches without crashes
- [ ] UI renders correctly
- [ ] Platform channels functional

---

## Common Build Issues

### Issue 1: "Unable to resolve dependency"
**Solution:**
```bash
flutter clean
flutter pub cache repair
flutter pub get
```

### Issue 2: "Gradle build failed"
**Solution:**
```bash
cd android
./gradlew clean
cd ..
flutter clean
flutter build apk --debug
```

### Issue 3: "Kotlin compiler error"
**Solution:**
- Check Kotlin version in `android/build.gradle.kts`
- Ensure Android Gradle plugin is compatible
- Verify Java 17 is being used

### Issue 4: "Out of memory during build"
**Solution:**
Edit `android/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError
```

### Issue 5: "SDK location not found"
**Solution:**
Create `android/local.properties`:
```properties
sdk.dir=C:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
```

---

## Continuous Integration (CI) Tests

### GitHub Actions Example
```yaml
name: Build Test
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: subosito/flutter-action@v2
        with:
          flutter-version: '3.10.1'
      - run: flutter pub get
      - run: flutter analyze
      - run: flutter test
      - run: flutter build apk --debug
```

---

## Build Performance Benchmarks

### Expected Build Times (First Build)
- **Clean Debug APK**: 3-5 minutes
- **Clean Release APK**: 5-8 minutes
- **Incremental Build**: 30-60 seconds

### Expected APK Sizes
- **Debug APK**: 45-60 MB
- **Release APK**: 15-25 MB
- **Release APK (split-per-abi)**: 8-12 MB per ABI

---

## Test Commands Quick Reference

```bash
# Essential Tests
flutter pub get          # Get dependencies
flutter analyze          # Static analysis
flutter test            # Run unit tests
flutter build apk       # Build APK

# Advanced Tests
flutter test --coverage                    # With coverage
flutter build apk --analyze-size          # Analyze size
flutter build apk --split-per-abi         # Split by ABI
flutter run --profile                     # Profile mode

# Android Native
cd android && ./gradlew lint              # Lint check
cd android && ./gradlew test              # Unit tests
cd android && ./gradlew assembleDebug     # Build debug

# Cleanup
flutter clean                             # Clean build
flutter pub cache repair                  # Repair cache
cd android && ./gradlew clean            # Clean Android
```

---

## Automated Testing

Run the provided PowerShell script for comprehensive automated testing:

```powershell
# Set execution policy (if needed)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Run build tests
.\test_build.ps1
```

The script will:
1. ✅ Check Flutter installation
2. ✅ Verify Dart SDK
3. ✅ Validate pubspec.yaml
4. ✅ Run flutter clean & pub get
5. ✅ Run flutter analyze
6. ✅ Execute flutter test
7. ✅ Build debug APK

**Exit Codes:**
- `0` = All tests passed
- `1` = One or more tests failed

---

## Reporting Build Issues

When reporting build failures, include:
1. **Flutter doctor output**: `flutter doctor -v`
2. **Build command**: The exact command that failed
3. **Error output**: Full error message and stack trace
4. **Environment**: OS version, Flutter version, Android SDK version
5. **gradle.properties**: Contents of `android/gradle.properties`
6. **Logcat**: If runtime error, include `adb logcat` output

---

## Success Criteria

A successful build must:
- ✅ Pass `flutter analyze` with zero issues
- ✅ Pass all unit and widget tests
- ✅ Compile all Kotlin native code
- ✅ Generate a valid APK file
- ✅ APK can be installed on a real device
- ✅ App launches without crashes
- ✅ Platform channels work correctly

---

## Next Steps After Successful Build

1. **Install on Device**
   ```bash
   flutter install
   # Or manually:
   adb install build/app/outputs/flutter-apk/app-debug.apk
   ```

2. **Set Up ADB Forward**
   ```bash
   adb forward tcp:27183 tcp:27183
   ```

3. **Run & Test**
   ```bash
   flutter run
   ```

4. **Monitor Logs**
   ```bash
   adb logcat | grep "screeenc"
   ```

---

## Summary

Use `test_build.ps1` for quick verification, or follow manual test procedures for detailed analysis. All tests should pass before proceeding to device testing or Windows sender development.
