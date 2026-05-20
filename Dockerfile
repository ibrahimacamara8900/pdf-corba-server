FROM openjdk:8-jdk-alpine

WORKDIR /app

# Copier tout le projet
COPY . .

# Créer les dossiers nécessaires
RUN mkdir -p build pdfs resources

# Compiler IDL
RUN cd src && idlj -fall PDFService.idl && cd ..

# Compiler Java
RUN javac -cp "lib/pdfbox-app-2.0.30.jar:lib/nanohttpd-2.3.1.jar:src" \
    -d build \
    src/Logger.java \
    src/Validator.java \
    src/CORBAConnector.java \
    src/PDFServiceImpl.java \
    src/Server.java \
    src/WebServer.java \
    src/pdf/*.java

# Exposer le port web
EXPOSE 8080

# Script de démarrage
CMD ["sh", "docker-start.sh"]
