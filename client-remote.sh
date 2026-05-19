#!/bin/bash
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
JAR=~/pdf-corba-server/lib/pdfbox-app-2.0.30.jar
HOST=172.26.20.100   # ← IP du serveur

$JAVA -cp "build:$JAR" \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=$HOST \
    ClientFX ClientDistant
