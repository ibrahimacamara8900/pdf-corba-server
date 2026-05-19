#!/bin/bash
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
JAR=~/pdf-corba-server/lib/pdfbox-app-2.0.30.jar
HOST=172.26.20.100

echo "▶ Démarrage NameService sur $HOST:1050..."
orbd -ORBInitialPort 1050 -ORBInitialHost $HOST &
sleep 2 && echo "✅ NameService OK"

echo "▶ Démarrage Serveur PDF CORBA..."
cd ~/pdf-corba-server && $JAVA \
    -cp "build:$JAR" \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=$HOST \
    Server
