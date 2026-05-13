@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Switch Language / 切换语言
echo   Interastral Peace Casino
echo ============================================
echo(

cd /d "%~dp0"
if errorlevel 1 (echo [ERROR] Cannot access mod directory. & pause & exit /b 1)

set "DATA_DIR=data\config"
set "RULES_DIR=data\campaign"

if not exist "%DATA_DIR%\strings.json" (
    echo [ERROR] %DATA_DIR%\strings.json not found.
    pause
    exit /b 1
)

cd "%DATA_DIR%"
if errorlevel 1 (echo [ERROR] Cannot access %DATA_DIR%. & pause & exit /b 1)

REM --- Clean up temp file from interrupted previous run ---
if exist "strings.tmp" (
    echo [WARN] Found leftover strings.tmp from a previous interrupted switch.
    echo        Restoring it as strings.json...
    move /y "strings.tmp" "strings.json" >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Could not restore strings.tmp. Delete it manually and retry.
        pause
        exit /b 1
    )
    echo   [OK] Recovered.
    echo(
)

REM --- Detect current language from backup file state ---
set "CURRENT="
set "TARGET_FILE="

if exist "strings-EN.json" (
    if not exist "strings-CN.json" (
        set "CURRENT=CN"
        set "TARGET_FILE=strings-EN.json"
    )
)
if not defined CURRENT (
    if exist "strings-CN.json" (
        set "CURRENT=EN"
        set "TARGET_FILE=strings-CN.json"
    )
)

if not defined CURRENT (
    if exist "strings-EN.json" if exist "strings-CN.json" (
        echo [ERROR] Both strings-EN.json and strings-CN.json found.
        echo        Cannot determine current language.
        echo        Delete one backup file and retry.
        pause
        exit /b 1
    )
    echo [ERROR] No backup language file found (strings-EN.json or strings-CN.json).
    echo        Place the alternate language file in %DATA_DIR% and retry.
    pause
    exit /b 1
)

if "%CURRENT%"=="EN" (
    set "CURRENT_NAME=English"
    set "TARGET_NAME=Chinese / 中文"
) else (
    set "CURRENT_NAME=Chinese / 中文"
    set "TARGET_NAME=English"
)

echo Current language / 当前语言: %CURRENT_NAME%
echo(
echo Switch to / 切换为: %TARGET_NAME%?
echo(
set /p CONFIRM= [Y/N]:
if /i "!CONFIRM!" neq "Y" (
    echo Cancelled / 已取消。
    pause
    exit /b
)

echo(
echo Switching... / 切换中...
echo(

REM --- Three-step atomic swap ---
REM Step 1: Move current strings.json to temp
echo [1/6] strings.json --^> strings.tmp ...
move /y "strings.json" "strings.tmp" >nul 2>&1
if errorlevel 1 (echo   [FAILED] & pause & exit /b 1)
echo   [OK]

REM Step 2: Move target language file to strings.json
echo [2/6] %TARGET_FILE% --^> strings.json ...
move /y "%TARGET_FILE%" "strings.json" >nul 2>&1
if errorlevel 1 (
    echo   [FAILED] Rolling back...
    move /y "strings.tmp" "strings.json" >nul 2>&1
    pause
    exit /b 1
)
echo   [OK]

REM Step 3: Move temp to backup
echo [3/6] strings.tmp --^> %TARGET_FILE% ...
move /y "strings.tmp" "%TARGET_FILE%" >nul 2>&1
if errorlevel 1 (
    echo   [FAILED] Rolling back...
    move /y "strings.json" "%TARGET_FILE%" >nul 2>&1
    move /y "strings.tmp" "strings.json" >nul 2>&1
    pause
    exit /b 1
)
echo   [OK]

REM --- Switch rules.csv in parallel ---
cd /d "%~dp0%RULES_DIR%"
if errorlevel 1 (
    echo [WARN] Cannot access %RULES_DIR%. Skipping rules switch.
    cd /d "%~dp0%DATA_DIR%"
    goto rules_done
)

if exist "rules.tmp" (
    echo [WARN] Found leftover rules.tmp from a previous interrupted switch.
    move /y "rules.tmp" "rules.csv" >nul 2>&1
    echo   [OK] Recovered rules.csv.
)

set "RULES_TARGET="
if "%CURRENT%"=="EN" set "RULES_TARGET=rules-CN.csv"
if "%CURRENT%"=="CN" set "RULES_TARGET=rules-EN.csv"

if not defined RULES_TARGET (
    echo [WARN] Cannot determine rules target. Skipping rules switch.
    cd /d "%~dp0%DATA_DIR%"
    goto rules_done
)

if not exist "%RULES_TARGET%" (
    echo [WARN] %RULES_TARGET% not found. Skipping rules switch.
    cd /d "%~dp0%DATA_DIR%"
    goto rules_done
)

echo [4/6] rules.csv --^> rules.tmp ...
move /y "rules.csv" "rules.tmp" >nul 2>&1
if errorlevel 1 (echo   [FAILED] Skipping rules. & cd /d "%~dp0%DATA_DIR%" & goto rules_done)
echo   [OK]

echo [5/6] %RULES_TARGET% --^> rules.csv ...
move /y "%RULES_TARGET%" "rules.csv" >nul 2>&1
if errorlevel 1 (
    echo   [FAILED] Rolling back rules...
    move /y "rules.tmp" "rules.csv" >nul 2>&1
    cd /d "%~dp0%DATA_DIR%"
    goto rules_done
)
echo   [OK]

echo [6/6] rules.tmp --^> %RULES_TARGET% ...
move /y "rules.tmp" "%RULES_TARGET%" >nul 2>&1
if errorlevel 1 (
    echo   [FAILED] Rolling back rules...
    move /y "rules.csv" "%RULES_TARGET%" >nul 2>&1
    move /y "rules.tmp" "rules.csv" >nul 2>&1
)
echo   [OK]

:rules_done
cd /d "%~dp0%DATA_DIR%"

echo(
echo ============================================
echo  Done! / 完成！
echo  Language switched from %CURRENT_NAME% to %TARGET_NAME%
echo  已从 %CURRENT_NAME% 切换为 %TARGET_NAME%
echo ============================================
echo(
pause
