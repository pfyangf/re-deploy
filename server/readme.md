```
$env:JAVA_HOME="C:\ProgramFiles\Java\jdk-17"                                    
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"                                        


jlink  --module-path "%JAVA_HOME%\jmods"  --add-modules java.base,java.desktop,java.sql,java.naming,java.management  --output runtime

jpackage  --name RedeployServer --input release --main-jar redeploy-server-0.1.0.jar --runtime-image runtime --type exe --app-version 0.1.0 --vendor "YangPengdfei" --win-shortcut --win-dir-chooser --java-options "-Xms512m -Xmx2048m"
```