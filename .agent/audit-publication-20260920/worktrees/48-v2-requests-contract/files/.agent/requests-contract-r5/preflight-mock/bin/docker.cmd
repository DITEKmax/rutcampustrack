@echo off
if "%~1"=="run" exit /b 0
if "%~1"=="compose" (
  if not "%MOCK_COMPOSE_EXIT%"=="0" (
    echo synthetic compose failure without error keyword 1>&2
    exit /b %MOCK_COMPOSE_EXIT%
  )
  exit /b 0
)
echo unexpected synthetic docker invocation 1>&2
exit /b 99