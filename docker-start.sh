#!/bin/sh
export APP_HOME=/app
JAVA=java
JAR="lib/pdfbox-app-2.0.30.jar:lib/nanohttpd-2.3.1.jar"

# Créer /tmp/pdfs et copier les PDFs de demo
mkdir -p /tmp/pdfs
cp /app/pdfs/*.pdf /tmp/pdfs/ 2>/dev/null || true
echo "PDFs copiés dans /tmp/pdfs: $(ls /tmp/pdfs/ | wc -l) fichiers"

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
APP_HOME=/app RENDER=true $JAVA -cp "build:$JAR" \
    -Dorg.omg.CORBA.ORBInitialPort=1050 \
    -Dorg.omg.CORBA.ORBInitialHost=localhost \
    WebServer
