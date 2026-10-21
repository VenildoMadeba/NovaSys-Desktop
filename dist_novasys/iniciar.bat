@echo off
title NovaSys Desktop - Sistema de Gestao de Retalho
color 0B
echo ========================================================
echo          A INICIAR O NOVASYS DESKTOP...
echo ========================================================
java --module-path "lib" --add-modules javafx.controls,javafx.fxml -jar novasys-desktop.jar
if errorlevel 1 (
    echo.
    echo [ERRO] Ocorreu uma falha ao iniciar a aplicacao.
    echo Certifique-se de que o Java (JRE/JDK 17+) esta instalado no PC.
    echo.
    pause
)