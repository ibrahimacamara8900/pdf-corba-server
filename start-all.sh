#!/bin/bash
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
JAR=~/pdf-corba-server/lib/pdfbox-app-2.0.30.jar
NANO=~/pdf-corba-server/lib/nanohttpd-2.3.1.jar
HOST=172.26.20.100
PORT=1050

echo "🧹 Nettoyage des anciens processus..."
pkill -f WebServer 2>/dev/null
pkill -f "Server " 2>/dev/null
pkill orbd 2>/dev/null
sleep 2

echo "▶ [1/3] Démarrage NameService CORBA..."
orbd -ORBInitialPort $PORT -ORBInitialHost $HOST &
sleep 3

echo "▶ [2/3] Démarrage Serveur PDF CORBA..."
cd ~/pdf-corba-server && $JAVA \
    -cp "build:$JAR" \
    -Dorg.omg.CORBA.ORBInitialPort=$PORT \
    -Dorg.omg.CORBA.ORBInitialHost=$HOST \
    Server >> ~/pdf-corba-server/server.log 2>&1 &
sleep 3

echo "▶ [3/3] Démarrage Serveur Web..."
cd ~/pdf-corba-server && $JAVA \
    -cp "build:$JAR:$NANO" \
    WebServer &
sleep 2

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   ✅ PDF CORBA SERVER EN LIGNE !     ║"
echo "╠══════════════════════════════════════╣"
echo "║  🌐 Local  : http://localhost:8080   ║"
echo "║  🌐 Réseau : http://$HOST:8080  ║"
echo "║  📊 Statut : http://localhost:8080/status ║"
echo "╚══════════════════════════════════════╝"
