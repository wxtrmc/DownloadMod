@echo off
setlocal
if not exist dist mkdir dist
for %%v in (1.21.1 1.21.4 1.21.5 1.21.8 1.21.11) do (
  echo ===== Building for %%v =====
  call gradlew.bat dist -Pmc=%%v
  if errorlevel 1 (
    echo FAILED for %%v
    exit /b 1
  )
)
echo Done. The jars are in the dist folder.
