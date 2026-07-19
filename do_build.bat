@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "c:\Users\zeusk\AndroidStudioProjects\Navi-Link-zeus"
call gradlew.bat assembleDebug > build_log.txt 2> build_err.txt
echo EXIT=%ERRORLEVEL% >> build_log.txt
