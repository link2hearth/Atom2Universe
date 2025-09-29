@echo off
setlocal
set "EXIT_CODE=0"
set "SCRIPT_DIR=%~dp0"
set "IS_DOUBLE_CLICK=0"
echo %CMDCMDLINE% | find /I "%~f0" >nul 2>&1 && set "IS_DOUBLE_CLICK=1"
pushd "%SCRIPT_DIR%" >nul
if not "%~1"=="" (
  set "PORT=%~1"
)
where node >nul 2>&1
if errorlevel 1 (
  echo [MyLocalServ] Node.js doit etre installe et accessible via la commande ^"node^".
  echo Telechargez-le depuis https://nodejs.org/ et redemarrez cette fenetre.
  set "EXIT_CODE=1"
  goto :end
)
if defined PORT (
  echo [MyLocalServ] Demarrage du serveur sur le port %PORT%...
) else (
  echo [MyLocalServ] Demarrage du serveur sur le port par defaut (8080)...
)
node "%SCRIPT_DIR%MyLocalServ.js"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo [MyLocalServ] Le serveur s'est termine avec le code %EXIT_CODE%.
)
:end
popd >nul
if "%IS_DOUBLE_CLICK%"=="1" (
  echo.
  pause
)
endlocal & exit /b %EXIT_CODE%
