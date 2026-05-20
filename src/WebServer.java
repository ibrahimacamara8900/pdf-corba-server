import fi.iki.elonen.NanoHTTPD;
import pdf.PDFService;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WebServer extends NanoHTTPD {
    private final CORBAConnector corba;
    private static final String HOME = System.getenv("APP_HOME") != null ? System.getenv("APP_HOME") + "/pdfs/" : System.getProperty("user.home") + "/pdf-corba-server/pdfs/";
    private static final String RES  = System.getenv("APP_HOME") != null ? System.getenv("APP_HOME") + "/resources/" : System.getProperty("user.home") + "/pdf-corba-server/resources/";
    private final long startTime = System.currentTimeMillis();

    public WebServer(CORBAConnector corba) throws IOException {
        super(System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080);
        this.corba = corba;
        new File(HOME).mkdirs();
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Logger.ok("Serveur Web demarre");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String ip  = session.getHeaders().getOrDefault("http-client-ip", "127.0.0.1");
        Logger.info("REQ " + session.getMethod() + " " + uri + " <- " + ip);
        try {
            if (uri.equals("/") || uri.equals("/index.html")) return serveFile("index.html", "text/html");
            if (uri.equals("/status"))                        return serveFile("status.html", "text/html");
            if (uri.startsWith("/api/"))                      return handleAPI(session, uri);
            if (uri.startsWith("/download/"))                 return handleDownload(uri);
            if (uri.equals("/upload") && Method.POST.equals(session.getMethod())) return handleUpload(session);
        } catch (Validator.ValidationException e) {
            Logger.warn("Validation: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            Logger.error("Erreur: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404");
    }

    private Response serveFile(String name, String mime) throws IOException {
        File f = new File(RES + name);
        if (!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", name + " introuvable");
        String content = new String(Files.readAllBytes(f.toPath()), "UTF-8");
        // Injecter les variables dynamiques dans status.html
        if (name.equals("status.html")) {
            long up = (System.currentTimeMillis() - startTime) / 1000;
            String cs = corba.isConnected() ? "ok" : "err";
            String cl = corba.isConnected() ? "Connecte" : "Deconnecte";
            String lg = String.join("\n", Logger.getLogs().subList(Math.max(0, Logger.getLogs().size()-25), Logger.getLogs().size()));
            content = content
                .replace("{{UPTIME}}", up + "s")
                .replace("{{CORBA_CLASS}}", cs)
                .replace("{{CORBA_LABEL}}", cl)
                .replace("{{CORBA_HOST}}", corba.getHost() + ":" + corba.getPort())
                .replace("{{RECONNECTS}}", String.valueOf(corba.getReconnectCount()))
                .replace("{{LOGS}}", esc(lg));
        }
        return newFixedLengthResponse(Response.Status.OK, mime + "; charset=utf-8", content);
    }

    private Response handleAPI(IHTTPSession session, String uri) throws Exception {
        PDFService svc = corba.getService();
        Map<String, String> p = session.getParms();
        if (Method.POST.equals(session.getMethod())) {
            Map<String,String> body = new HashMap<>();
            session.parseBody(body);
            String raw = body.get("postData");
            if (raw != null) p = parseJSON(raw);
        }
        long t = System.currentTimeMillis();
        switch (uri) {
            case "/api/create": {
                String c = Validator.requireText(p.get("content"), "Contenu");
                String o = HOME + "created_" + t + ".pdf";
                return fileResult(svc.createPDF(c, o), o);
            }
            case "/api/extract-text": {
                String path = Validator.requireFile(p.get("path"));
                String r = svc.extractText(path);
                Logger.ok("extractText <- " + p.get("path"));
                if (r.startsWith("ERROR:")) return json("{\"error\":\"" + esc(r) + "\"}");
                return json("{\"success\":true,\"result\":\"" + esc(r.replace("SUCCESS:", "")) + "\"}");
            }
            case "/api/merge": {
                String f1 = Validator.requireFile(p.get("file1"));
                String f2 = Validator.requireFile(p.get("file2"));
                String o  = HOME + "merged_" + t + ".pdf";
                return fileResult(svc.mergePDF(f1, f2, o), o);
            }
            case "/api/password": {
                String path = Validator.requireFile(p.get("path"));
                String pass = Validator.requireText(p.get("password"), "Mot de passe");
                String o    = HOME + "secured_" + t + ".pdf";
                return fileResult(svc.addPassword(path, pass, o), o);
            }
            case "/api/split": {
                String path = Validator.requireFile(p.get("path"));
                int pg      = Validator.requireInt(p.get("pages"), "Pages", 1, 1000);
                String od   = HOME + "split_" + t;
                return json("{\"success\":true,\"result\":\"" + esc(svc.splitPDF(path, pg, od)) + "\"}");
            }
            case "/api/extract-pages": {
                String path = Validator.requireFile(p.get("path"));
                int from    = Validator.requireInt(p.get("from"), "Debut", 1, 9999);
                int to      = Validator.requireInt(p.get("to"),   "Fin",   1, 9999);
                if (from > to) throw new Validator.ValidationException("Page debut > fin");
                String o = HOME + "extracted_" + t + ".pdf";
                return fileResult(svc.extractPages(path, from, to, o), o);
            }
            case "/api/delete-pages": {
                String path = Validator.requireFile(p.get("path"));
                int from    = Validator.requireInt(p.get("from"), "Debut", 1, 9999);
                int to      = Validator.requireInt(p.get("to"),   "Fin",   1, 9999);
                if (from > to) throw new Validator.ValidationException("Page debut > fin");
                String o = HOME + "deleted_" + t + ".pdf";
                return fileResult(svc.deletePages(path, from, to, o), o);
            }
            case "/api/convert-images": {
                String path = Validator.requireFile(p.get("path"));
                String od   = HOME + "images_" + t;
                return json("{\"success\":true,\"result\":\"" + esc(svc.convertToImages(path, od)) + "\"}");
            }
            case "/api/list": {
                File dir = new File(HOME);
                StringBuilder sb = new StringBuilder("[");
                File[] files = dir.listFiles((d, n) -> n.endsWith(".pdf"));
                if (files != null) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int i = 0; i < files.length; i++) {
                        sb.append("{\"name\":\"").append(files[i].getName())
                          .append("\",\"size\":").append(files[i].length()).append("}");
                        if (i < files.length - 1) sb.append(",");
                    }
                }
                return json("{\"files\":" + sb.append("]") + "}");
            }
            case "/api/logs":   return json("{\"logs\":" + Logger.getLogsJSON() + "}");
            case "/api/status": {
                long up = (System.currentTimeMillis() - startTime) / 1000;
                return json("{\"corba\":" + corba.isConnected()
                    + ",\"reconnects\":" + corba.getReconnectCount()
                    + ",\"uptime\":" + up + "}");
            }
        }
        return json("{\"error\":\"Route inconnue\"}");
    }

    private Response fileResult(String r, String out) {
        if (r.startsWith("ERROR:")) return json("{\"error\":\"" + esc(r) + "\"}");
        String fname = new File(out).getName();
        Logger.ok("Fichier cree: " + fname);
        return json("{\"success\":true,\"result\":\"Fichier cree avec succes\",\"file\":\"" + fname + "\"}");
    }

    private Response handleDownload(String uri) throws IOException {
        String fname = uri.replace("/download/", "");
        if (fname.contains("..") || fname.contains("/"))
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Interdit");
        File f = new File(HOME + fname);
        if (!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Introuvable");
        byte[] data = Files.readAllBytes(f.toPath());
        Response r = newFixedLengthResponse(Response.Status.OK, "application/pdf", new ByteArrayInputStream(data), data.length);
        r.addHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
        Logger.info("Download: " + fname);
        return r;
    }

    private Response handleUpload(IHTTPSession session) throws Exception {
        Map<String,String> body = new HashMap<>();
        session.parseBody(body);
        String tmpPath = body.get("file");
        String fname = session.getParms().getOrDefault("filename", "upload_" + System.currentTimeMillis() + ".pdf");
        if (fname.contains("..") || !fname.endsWith(".pdf"))
            return json("{\"error\":\"PDF uniquement\"}");
        if (tmpPath != null) {
            Files.copy(new File(tmpPath).toPath(), new File(HOME + fname).toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logger.ok("Upload: " + fname);
            return json("{\"success\":true,\"file\":\"" + fname + "\"}");
        }
        return json("{\"error\":\"Upload echoue\"}");
    }

    private Response json(String s) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", s);
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private Map<String,String> parseJSON(String json) {
        Map<String,String> map = new HashMap<>();
        json = json.trim().replaceAll("[{}]", "");
        for (String part : json.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2)
                map.put(kv[0].trim().replaceAll("\"", ""), kv[1].trim().replaceAll("\"", ""));
        }
        return map;
    }

    public static void main(String[] args) throws IOException {
        Logger.info("=== PDF CORBA Server demarrage ===");
        String corbaHost = System.getenv("CORBA_HOST") != null ? System.getenv("CORBA_HOST") : "localhost";
        int corbaPort    = System.getenv("CORBA_PORT") != null ? Integer.parseInt(System.getenv("CORBA_PORT")) : 1050;
        CORBAConnector corba = new CORBAConnector(corbaHost, corbaPort);
        new WebServer(corba);
        Logger.ok("Interface: http://localhost:8080");
        try { Thread.currentThread().join(); } catch (InterruptedException e) {}
    }
}
