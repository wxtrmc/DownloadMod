@echo off
setlocal
if not exist dist mkdir dist
for %%v in (26.1.2 26.2 26.3) do (
  echo ===== Building for %%v =====
  call gradlew.bat dist -Pmc=%%v
  if errorlevel 1 (
    echo FAILED for %%v
    exit /b 1
  )
)
echo Done. The jars are in the dist folder.
