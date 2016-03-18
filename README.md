# javaWSproxy
Quick Java implementation of dewebsockify (https://github.com/encharm/dewebsockify)

## Overview
This is a quick and dirty implementation of dewebsockify (https://github.com/encharm/dewebsockify) written in Java.  I couldn't find anything similar in Java, so I took a stab at building it myself.  It may not be pretty, but it does work!  Currently, it will only handle binary encoding.  It may be worth adding base-64 someday, but I dont need it right now.  I'm putting it here in case it can help anyone else.

I know a little Java, but I would hardly call myself a Java Developer, so I hope this is in a useful format.  I originally built this using IntelliJ, but I didn't want to track the whole project for only 3 Java files.  If the pom.xml file is not setup properly, or if you just don't want to use Maven, you need a copy of tyrus-standalone-client-jdk-1.12.jar, which is available in the "jar" branch or from the [Maven Repository] (http://mvnrepository.com/artifact/org.glassfish.tyrus.bundles/tyrus-standalone-client-jdk).

Since I am also not sure how to properly build a jar file, I simply added my classes into the tyrus jar and included "Main-Class" in the manifest.  The working Jar that I am currently using, wsp.jar, is also available in the "jar" branch of this project.

## Resources
* The original websocket proxy class implementation was based on this [Stack Overflow post] (http://stackoverflow.com/questions/26452903/javax-websocket-client-simple-example)
* The NIO implementation was based on information from the [Rox Java NIO Tutorial] (http://rox-xmlrpc.sourceforge.net/niotut/)
* [Tyrus Documentation] (https://tyrus.java.net/documentation/1.12/index/)
* [Tyrus API] (https://tyrus.java.net/apidocs/1.12/index.html)
* [javax.websocket API] (https://docs.oracle.com/javaee/7/api/index.html?javax/websocket/package-summary.html)
* [java.net API] (https://docs.oracle.com/javase/7/docs/api/index.html?java/net/package-summary.html)
