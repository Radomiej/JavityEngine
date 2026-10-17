@echo off
setlocal
cd /d "%~dp0"
set "GAME_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "GAME_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%~dp0runtime\bin\java.exe" set "GAME_JAVA=%~dp0runtime\bin\java.exe"
"%GAME_JAVA%" --enable-native-access=ALL-UNNAMED -jar "%~dp0Straznicy-Bram.jar" %*
set "GAME_EXIT=%ERRORLEVEL%"
if not "%GAME_EXIT%"=="0" (
  echo.
  echo Gra wymaga 64-bitowej Javy 25 i sterownika OpenGL.
  echo W paczce z katalogiem runtime nie musisz instalowac Javy osobno.
  echo Zachowaj komunikat bledu powyzej do diagnostyki.
  pause
)
exit /b %GAME_EXIT%
