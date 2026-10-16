@echo off
cd /d "%~dp0"

echo === Compilando proyecto ===
call mvn clean package
if errorlevel 1 goto error

echo === Preparando archivos ===
if not exist dist-input mkdir dist-input

copy /Y target\robotarm-1.0-SNAPSHOT.jar dist-input\
if errorlevel 1 goto error

echo === Eliminando version anterior ===
if exist dist\RobotArm (
    rmdir /S /Q dist\RobotArm
    if errorlevel 1 goto error
)

echo === Generando aplicacion ===
jpackage --type app-image ^
  --name RobotArm ^
  --input dist-input ^
  --main-jar robotarm-1.0-SNAPSHOT.jar ^
  --main-class garbi.com.robotarm.Launcher ^
  --add-modules java.base,java.desktop,java.logging,java.xml,jdk.unsupported ^
  --dest dist

if errorlevel 1 goto error

echo.
echo === ACTUALIZACION COMPLETADA ===
echo Aplicacion disponible en dist\RobotArm
pause
exit /b 0

:error
echo.
echo ERROR: No se pudo actualizar la aplicacion.
pause
exit /b 1