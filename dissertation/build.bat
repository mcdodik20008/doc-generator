@echo off
REM =============================================================================
REM build.bat — Сборка диссертации (Windows)
REM Требуется: MiKTeX или TeX Live с XeLaTeX и Biber
REM =============================================================================

echo [1/4] Компиляция XeLaTeX (первый проход)...
xelatex -interaction=nonstopmode -output-directory=build main.tex
if errorlevel 1 goto :error

echo [2/4] Обработка библиографии (Biber)...
biber build/main
if errorlevel 1 goto :error

echo [3/4] Компиляция XeLaTeX (второй проход)...
xelatex -interaction=nonstopmode -output-directory=build main.tex
if errorlevel 1 goto :error

echo [4/4] Компиляция XeLaTeX (третий проход — финальный)...
xelatex -interaction=nonstopmode -output-directory=build main.tex
if errorlevel 1 goto :error

echo.
echo === Готово! PDF: build\main.pdf ===
goto :end

:error
echo.
echo === ОШИБКА при компиляции! Смотри логи в build\ ===
exit /b 1

:end
