#!/bin/bash
echo "🛑 Arrêt de tous les services..."
pkill -f WebServer && echo "  ✅ WebServer arrêté"
pkill -f "Server " && echo "  ✅ Serveur CORBA arrêté"
pkill orbd && echo "  ✅ NameService arrêté"
echo "✅ Tout arrêté."
