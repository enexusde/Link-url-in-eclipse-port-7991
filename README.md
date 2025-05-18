What?
-
You need eclipse to jump quick to a specific file and line? Use this eclipse-plug-in.

In eclipse, if you have a project "foobar" and a file "pom.xml" you could use this weblink to open the editor in line 12:

    http://localhost:7991/foobar/pom.xml?12

The file will be opened immediately and eclipse gain the focus so you can write on directly.

Install
-
Just open the eclipse-installation-folder. There will be a folder named "plugins" place the jar right inside the folder having the correct ownership. Restart eclipse and the links should work immediately. For debugging purposes you could check the /.metadata/.log-file.
