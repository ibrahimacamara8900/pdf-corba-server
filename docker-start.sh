#!/bin/sh
export APP_HOME=/app
JAVA=java
JAR="lib/pdfbox-app-2.0.30.jar:lib/nanohttpd-2.3.1.jar"

mkdir -p /app/pdfs

echo "▶ Démarrage NameService CORBA..."
orbd -ORBInitialPort 1050 -ORBInitialHost localhost &
sleep 3

echo "▶ Démarrage Serveur PDF CORBA..."
$JAVA -cp "build:lib/pdfbox-app-2.0.30.jar" \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=localhost \
    Server &
sleep 3

echo "▶ Démarrage Serveur Web..."
$JAVA -cp "build:$JAR" \
    -DAPP_HOME=/app \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=localhost \
    WebServer
