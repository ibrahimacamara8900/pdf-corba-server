#!/bin/bash
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
JAR=~/pdf-corba-server/lib/pdfbox-app-2.0.30.jar

echo "▶ Démarrage NameService..."
orbd -ORBInitialPort 1050 -ORBInitialHost localhost &
sleep 2 && echo "✅ NameService OK"

echo "▶ Démarrage Serveur PDF CORBA..."
cd ~/pdf-corba-server && $JAVA \
    -cp "build:$JAR" \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=localhost \
    Server
