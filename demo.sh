#!/bin/bash
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
JAR=~/pdf-corba-server/lib/pdfbox-app-2.0.30.jar
OPTS="-cp build:$JAR -Dorg.omg.CORBA.ORBInitialPort=1050 -Dorg.omg.CORBA.ORBInitialHost=localhost"

echo "▶ Démarrage NameService..."
orbd -ORBInitialPort 1050 -ORBInitialHost localhost &
sleep 2

echo "▶ Démarrage Serveur..."
cd ~/pdf-corba-server && $JAVA $OPTS Server &
sleep 2

echo "▶ Lancement 3 clients..."
$JAVA -cp "build:$JAR" ClientFX ClientA &
sleep 1
$JAVA -cp "build:$JAR" ClientFX ClientB &
sleep 1
$JAVA -cp "build:$JAR" ClientFX ClientN &

echo "✅ Démo lancée ! 3 clients connectés au serveur."
