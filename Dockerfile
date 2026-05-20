FROM eclipse-temurin:8-jdk

WORKDIR /app

COPY . .

RUN mkdir -p build pdfs

RUN cd src && idlj -fall PDFService.idl && cd ..

RUN javac -cp "lib/pdfbox-app-2.0.30.jar:lib/nanohttpd-2.3.1.jar:src" \
    -d build \
    src/Logger.java \
    src/Validator.java \
    src/CORBAConnector.java \
    src/PDFServiceImpl.java \
    src/Server.java \
    src/WebServer.java \
    src/pdf/*.java

EXPOSE 8080

CMD ["sh", "docker-start.sh"]
