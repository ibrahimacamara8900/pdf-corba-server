import fi.iki.elonen.NanoHTTPD;
import pdf.PDFService;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WebServer extends NanoHTTPD {

    private final CORBAConnector corba;
    private static final String HOME = System.getProperty("user.home") + "/pdf-corba-server/pdfs/";
    private final long startTime = System.currentTimeMillis();

    public WebServer(CORBAConnector corba) throws IOException {
        super(8080);
        this.corba = corba;
        new File(HOME).mkdirs();
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Logger.ok("Serveur Web démarré → http://localhost:8080");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String ip  = session.getHeaders().getOrDefault("http-client-ip", "?");
        Logger.info("REQ " + session.getMethod() + " " + uri + " ← " + ip);
        try {
            if (uri.equals("/") || uri.equals("/index.html"))
                return html(getHTML());
            if (uri.equals("/status"))
                return html(getStatusHTML());
            if (uri.startsWith("/api/"))
                return handleAPI(session, uri);
            if (uri.startsWith("/download/"))
                return handleDownload(uri);
            if (uri.equals("/upload") && Method.POST.equals(session.getMethod()))
                return handleUpload(session);
            if (uri.equals("/api/logs"))
                return json("{\"logs\":" + Logger.getLogsJSON() + "}");
        } catch (Validator.ValidationException e) {
            Logger.warn("Validation: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            Logger.error("Erreur: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404");
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
                String content = Validator.requireText(p.get("content"), "Contenu");
                String out = HOME + "created_" + t + ".pdf";
                String r = svc.createPDF(content, out);
                Logger.ok("createPDF → " + new File(out).getName());
                return fileResult(r, out);
            }
            case "/api/extract-text": {
                String path = Validator.requireFile(p.get("path"));
                String r = svc.extractText(path);
                Logger.ok("extractText ← " + p.get("path"));
                if (r.startsWith("ERROR:")) return json("{\"error\":\"" + esc(r) + "\"}");
                return json("{\"success\":true,\"result\":\"" + esc(r.replace("SUCCESS:","")) + "\"}");
            }
            case "/api/merge": {
                String f1 = Validator.requireFile(p.get("file1"));
                String f2 = Validator.requireFile(p.get("file2"));
                String out = HOME + "merged_" + t + ".pdf";
                String r = svc.mergePDF(f1, f2, out);
                Logger.ok("mergePDF → " + new File(out).getName());
                return fileResult(r, out);
            }
            case "/api/password": {
                String path = Validator.requireFile(p.get("path"));
                String pass = Validator.requireText(p.get("password"), "Mot de passe");
                String out  = HOME + "secured_" + t + ".pdf";
                String r = svc.addPassword(path, pass, out);
                Logger.ok("addPassword → " + new File(out).getName());
                return fileResult(r, out);
            }
            case "/api/split": {
                String path  = Validator.requireFile(p.get("path"));
                int pages    = Validator.requireInt(p.get("pages"), "Pages", 1, 1000);
                String outDir = HOME + "split_" + t;
                String r = svc.splitPDF(path, pages, outDir);
                Logger.ok("splitPDF → " + outDir);
                return json("{\"success\":true,\"result\":\"" + esc(r) + "\"}");
            }
            case "/api/extract-pages": {
                String path = Validator.requireFile(p.get("path"));
                int from = Validator.requireInt(p.get("from"), "Page début", 1, 9999);
                int to   = Validator.requireInt(p.get("to"),   "Page fin",   1, 9999);
                if (from > to) throw new Validator.ValidationException("Page début > Page fin");
                String out = HOME + "extracted_" + t + ".pdf";
                String r = svc.extractPages(path, from, to, out);
                Logger.ok("extractPages → " + new File(out).getName());
                return fileResult(r, out);
            }
            case "/api/delete-pages": {
                String path = Validator.requireFile(p.get("path"));
                int from = Validator.requireInt(p.get("from"), "Page début", 1, 9999);
                int to   = Validator.requireInt(p.get("to"),   "Page fin",   1, 9999);
                if (from > to) throw new Validator.ValidationException("Page début > Page fin");
                String out = HOME + "deleted_" + t + ".pdf";
                String r = svc.deletePages(path, from, to, out);
                Logger.ok("deletePages → " + new File(out).getName());
                return fileResult(r, out);
            }
            case "/api/convert-images": {
                String path = Validator.requireFile(p.get("path"));
                String outDir = HOME + "images_" + t;
                String r = svc.convertToImages(path, outDir);
                Logger.ok("convertToImages → " + outDir);
                return json("{\"success\":true,\"result\":\"" + esc(r) + "\"}");
            }
            case "/api/list": {
                File dir = new File(HOME);
                StringBuilder sb = new StringBuilder("[");
                File[] files = dir.listFiles((d,n) -> n.endsWith(".pdf"));
                if (files != null) {
                    Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int i=0;i<files.length;i++){
                        sb.append("{\"name\":\"").append(files[i].getName())
                          .append("\",\"size\":").append(files[i].length()).append("}");
                        if (i<files.length-1) sb.append(",");
                    }
                }
                return json("{\"files\":" + sb.append("]") + "}");
            }
            case "/api/status": {
                long uptime = (System.currentTimeMillis() - startTime) / 1000;
                return json("{\"corba\":" + corba.isConnected()
                    + ",\"reconnects\":" + corba.getReconnectCount()
                    + ",\"uptime\":" + uptime + "}");
            }
        }
        return json("{\"error\":\"Route inconnue: " + uri + "\"}");
    }

    private Response fileResult(String r, String out) {
        if (r.startsWith("ERROR:"))
            return json("{\"error\":\"" + esc(r) + "\"}");
        String fname = new File(out).getName();
        return json("{\"success\":true,\"result\":\"✅ Fichier créé\",\"file\":\"" + fname + "\"}");
    }

    private Response handleDownload(String uri) throws IOException {
        String fname = uri.replace("/download/", "");
        if (fname.contains("..") || fname.contains("/"))
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Interdit");
        File f = new File(HOME + fname);
        if (!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Fichier introuvable");
        byte[] data = Files.readAllBytes(f.toPath());
        Response r = newFixedLengthResponse(Response.Status.OK, "application/pdf", new ByteArrayInputStream(data), data.length);
        r.addHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");
        Logger.info("Download: " + fname + " (" + data.length + " bytes)");
        return r;
    }

    private Response handleUpload(IHTTPSession session) throws Exception {
        Map<String,String> body = new HashMap<>();
        session.parseBody(body);
        String tmpPath = body.get("file");
        String fname = session.getParms().getOrDefault("filename", "upload_" + System.currentTimeMillis() + ".pdf");
        if (fname.contains("..") || !fname.endsWith(".pdf"))
            return json("{\"error\":\"Fichier PDF uniquement\"}");
        if (tmpPath != null) {
            Files.copy(new File(tmpPath).toPath(), new File(HOME + fname).toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logger.ok("Upload: " + fname);
            return json("{\"success\":true,\"file\":\"" + fname + "\"}");
        }
        return json("{\"error\":\"Upload échoué\"}");
    }

    private Response json(String s) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", s);
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private Response html(String s) {
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", s);
    }

    private String esc(String s) {
        if (s==null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");
    }

    private Map<String,String> parseJSON(String json) {
        Map<String,String> map = new HashMap<>();
        json = json.trim().replaceAll("[{}]","");
        for (String part : json.split(",")) {
            String[] kv = part.split(":",2);
            if (kv.length==2) map.put(kv[0].trim().replaceAll("\"",""), kv[1].trim().replaceAll("\"",""));
        }
        return map;
    }

    private String getStatusHTML() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        String corbaStatus = corba.isConnected() ? "🟢 Connecté" : "🔴 Déconnecté";
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Status</title>"
        +"<meta http-equiv='refresh' content='5'>"
        +"<style>body{font-family:monospace;background:#1e1e2e;color:#cdd6f4;padding:30px}"
        +"h1{color:#89b4fa;margin-bottom:20px}.box{background:#313244;padding:20px;border-radius:10px;margin-bottom:15px}"
        +"a{color:#89b4fa}</style></head><body>"
        +"<h1>📊 Statut du Serveur</h1>"
        +"<div class='box'><b>Serveur Web</b><br>🟢 En ligne · Port 8080<br>⏱️ Uptime: "+uptime+"s</div>"
        +"<div class='box'><b>Serveur CORBA</b><br>"+corbaStatus+"<br>🔄 Reconnexions: "+corba.getReconnectCount()
        +"<br>📡 "+corba.getHost()+":"+corba.getPort()+"</div>"
        +"<div class='box'><b>Logs récents</b><br><pre style='color:#a6e3a1;font-size:11px;max-height:300px;overflow:auto'>"
        + String.join("\n", Logger.getLogs().subList(Math.max(0, Logger.getLogs().size()-20), Logger.getLogs().size()))
        +"</pre></div>"
        +"<a href='/'>← Retour</a></body></html>";
    }

    private String getHTML() {
        return "<!DOCTYPE html><html lang='fr'><head><meta charset='UTF-8'>"
        +"<meta name='viewport' content='width=device-width,initial-scale=1'>"
        +"<title>PDF CORBA Server</title><style>"
        +"*{margin:0;padding:0;box-sizing:border-box}"
        +"body{font-family:Arial,sans-serif;background:#1e1e2e;color:#cdd6f4;min-height:100vh}"
        +"header{background:#313244;padding:18px 30px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px}"
        +"header h1{font-size:20px;color:#89b4fa}"
        +".badges{display:flex;gap:8px;align-items:center}"
        +".badge{padding:4px 12px;border-radius:20px;font-size:12px;font-weight:bold}"
        +".b-green{background:#a6e3a1;color:#1e1e2e} .b-blue{background:#89b4fa;color:#1e1e2e}"
        +".container{max-width:1100px;margin:25px auto;padding:0 20px}"
        +".files-box{background:#313244;border-radius:12px;padding:20px;margin-bottom:25px}"
        +".files-box h3{color:#89b4fa;margin-bottom:12px;font-size:15px;display:flex;align-items:center;gap:10px}"
        +".file-list{display:flex;flex-wrap:wrap;gap:8px;min-height:38px}"
        +".ftag{background:#45475a;padding:6px 14px;border-radius:20px;font-size:12px;cursor:pointer;display:flex;align-items:center;gap:6px;transition:.15s}"
        +".ftag:hover{background:#89b4fa;color:#1e1e2e}"
        +".ftag .sz{opacity:.6;font-size:10px}"
        +"a.dlbtn{color:inherit;text-decoration:none;padding:2px 6px;border-radius:4px;background:rgba(255,255,255,.1)}"
        +"a.dlbtn:hover{background:#89b4fa;color:#1e1e2e}"
        +".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(290px,1fr));gap:18px;margin-bottom:25px}"
        +".card{background:#313244;border-radius:12px;padding:18px}"
        +".card h3{color:#89b4fa;margin-bottom:14px;font-size:14px}"
        +"input,textarea{width:100%;padding:9px 12px;background:#45475a;border:1.5px solid #585b70;border-radius:8px;color:#cdd6f4;margin-bottom:8px;font-size:13px;outline:none;transition:.2s}"
        +"input:focus,textarea:focus{border-color:#89b4fa;background:#313244}"
        +"textarea{height:75px;resize:vertical}"
        +".row{display:flex;gap:8px}"
        +"button.btn{width:100%;padding:10px;background:#89b4fa;color:#1e1e2e;border:none;border-radius:8px;font-weight:bold;cursor:pointer;font-size:13px;transition:.2s}"
        +"button.btn:hover{background:#b4befe} button.btn:active{transform:scale(.98)}"
        +"button.btn:disabled{background:#45475a;color:#6c7086;cursor:not-allowed}"
        +".result{background:#181825;border-radius:8px;padding:10px 12px;margin-top:10px;font-size:12px;font-family:monospace;min-height:38px;word-break:break-all;line-height:1.6;transition:.3s}"
        +".ok{color:#a6e3a1} .err{color:#f38ba8} .info{color:#89dceb}"
        +"a.dl{display:inline-block;margin-top:6px;padding:5px 14px;background:#313244;border:1px solid #45475a;border-radius:6px;font-size:11px;color:#cdd6f4;text-decoration:none}"
        +"a.dl:hover{background:#89b4fa;color:#1e1e2e;border-color:#89b4fa}"
        +".pulse{width:8px;height:8px;border-radius:50%;background:#a6e3a1;animation:p 2s infinite;display:inline-block}"
        +"@keyframes p{0%,100%{opacity:1}50%{opacity:.3}}"
        +"#logBox{background:#181825;border-radius:10px;padding:15px;font-family:monospace;font-size:11px;color:#a6e3a1;height:160px;overflow-y:auto;margin-top:8px}"
        +"</style></head><body>"
        +"<header>"
        +"  <div style='display:flex;align-items:center;gap:12px'><div class='pulse'></div><h1>📄 PDF CORBA Server</h1></div>"
        +"  <div class='badges'><span class='badge b-green' id='corbaBadge'>⏳ CORBA...</span><span class='badge b-blue'>Port 8080</span>"
        +"  <a href='/status' style='color:#cdd6f4;font-size:12px;text-decoration:none'>📊 Statut</a></div>"
        +"</header>"
        +"<div class='container'>"
        +"<div class='files-box'>"
        +"  <h3>📁 Fichiers PDF"
        +"    <button class='btn' onclick='listFiles()' style='width:auto;padding:5px 12px;font-size:11px'>🔄</button>"
        +"    <button class='btn' onclick='document.getElementById(\"fu\").click()' style='width:auto;padding:5px 12px;font-size:11px'>⬆️ Upload</button>"
        +"  </h3>"
        +"  <input type='file' id='fu' accept='.pdf' style='display:none' onchange='uploadFile(this)'>"
        +"  <div class='file-list' id='fileList'><span style='color:#6c7086;font-size:13px'>Cliquez 🔄</span></div>"
        +"</div>"
        +"<div class='grid'>"
        // Créer
        +"<div class='card'><h3>✨ Créer PDF</h3>"
        +"<textarea id='cc' placeholder='Contenu du PDF...'>Bonjour CORBA!\nLigne 2\nLigne 3</textarea>"
        +"<button class='btn' onclick='act(\"create\",{content:g(\"cc\").value},\"cr\")'>Créer</button>"
        +"<div class='result' id='cr'></div></div>"
        // Extraire texte
        +"<div class='card'><h3>📝 Extraire Texte</h3>"
        +"<input id='tp' placeholder='Nom du fichier (ex: test.pdf)'>"
        +"<button class='btn' onclick='act(\"extract-text\",{path:g(\"tp\").value},\"tr\")'>Extraire</button>"
        +"<div class='result' id='tr'></div></div>"
        // Fusionner
        +"<div class='card'><h3>🔗 Fusionner PDFs</h3>"
        +"<input id='mf1' placeholder='Fichier 1'>"
        +"<input id='mf2' placeholder='Fichier 2'>"
        +"<button class='btn' onclick='act(\"merge\",{file1:g(\"mf1\").value,file2:g(\"mf2\").value},\"mr\")'>Fusionner</button>"
        +"<div class='result' id='mr'></div></div>"
        // Extraire pages
        +"<div class='card'><h3>📑 Extraire Pages</h3>"
        +"<input id='ep' placeholder='Nom du fichier'>"
        +"<div class='row'><input id='ef' placeholder='De' value='1'><input id='et' placeholder='À' value='1'></div>"
        +"<button class='btn' onclick='act(\"extract-pages\",{path:g(\"ep\").value,from:g(\"ef\").value,to:g(\"et\").value},\"epr\")'>Extraire</button>"
        +"<div class='result' id='epr'></div></div>"
        // Supprimer pages
        +"<div class='card'><h3>🗑️ Supprimer Pages</h3>"
        +"<input id='dp' placeholder='Nom du fichier'>"
        +"<div class='row'><input id='df' placeholder='De' value='1'><input id='dt' placeholder='À' value='1'></div>"
        +"<button class='btn' onclick='act(\"delete-pages\",{path:g(\"dp\").value,from:g(\"df\").value,to:g(\"dt\").value},\"dpr\")'>Supprimer</button>"
        +"<div class='result' id='dpr'></div></div>"
        // Mot de passe
        +"<div class='card'><h3>🔒 Mot de Passe</h3>"
        +"<input id='pp' placeholder='Nom du fichier'>"
        +"<input id='pw' placeholder='Mot de passe' type='password'>"
        +"<button class='btn' onclick='act(\"password\",{path:g(\"pp\").value,password:g(\"pw\").value},\"pwr\")'>Sécuriser</button>"
        +"<div class='result' id='pwr'></div></div>"
        // Découper
        +"<div class='card'><h3>✂️ Découper PDF</h3>"
        +"<input id='sp' placeholder='Nom du fichier'>"
        +"<input id='spg' placeholder='Pages par partie' value='1'>"
        +"<button class='btn' onclick='act(\"split\",{path:g(\"sp\").value,pages:g(\"spg\").value},\"spr\")'>Découper</button>"
        +"<div class='result' id='spr'></div></div>"
        // Images
        +"<div class='card'><h3>🖼️ PDF → Images</h3>"
        +"<input id='ip' placeholder='Nom du fichier'>"
        +"<button class='btn' onclick='act(\"convert-images\",{path:g(\"ip\").value},\"ir\")'>Convertir</button>"
        +"<div class='result' id='ir'></div></div>"
        +"</div>"
        // Logs
        +"<div class='files-box'>"
        +"<h3>📋 Logs en temps réel <button class='btn' onclick='fetchLogs()' style='width:auto;padding:5px 12px;font-size:11px'>🔄</button></h3>"
        +"<div id='logBox'>Chargement...</div>"
        +"</div>"
        +"</div>"
        +"<script>"
        +"function g(x){return document.getElementById(x);}"
        +"async function act(r,p,rid){"
        +"  const el=g(rid); el.className='result info'; el.innerHTML='⏳ En cours...';"
        +"  const qs=Object.entries(p).map(([k,v])=>k+'='+encodeURIComponent(v)).join('&');"
        +"  try{"
        +"    const d=await (await fetch('/api/'+r+'?'+qs)).json();"
        +"    if(d.error){el.className='result err';el.innerHTML='❌ '+d.error;}"
        +"    else{el.className='result ok';"
        +"      let h='✅ '+( d.result||'OK');"
        +"      if(d.file) h+='<br><a class=\"dl\" href=\"/download/'+d.file+'\" download>⬇️ Télécharger '+d.file+'</a>';"
        +"      el.innerHTML=h; listFiles();}"
        +"  }catch(e){el.className='result err';el.innerHTML='❌ Erreur réseau: '+e.message;}"
        +"  fetchLogs();"
        +"}"
        +"async function listFiles(){"
        +"  try{"
        +"    const d=await (await fetch('/api/list')).json();"
        +"    const el=g('fileList');"
        +"    if(d.files&&d.files.length){"
        +"      el.innerHTML=d.files.map(f=>"
        +"        '<span class=\"ftag\" onclick=\"fill(\\''+f.name+'\\')\">'+'📄 '+f.name+"
        +"        ' <span class=\"sz\">'+fmt(f.size)+'</span>'"
        +"        +' <a class=\"dlbtn\" href=\"/download/'+f.name+'\" download>⬇️</a></span>'"
        +"      ).join('');"
        +"    } else el.innerHTML='<span style=\"color:#6c7086\">Aucun fichier</span>';"
        +"  }catch(e){}"
        +"}"
        +"function fmt(b){return b>1024*1024?(b/1024/1024).toFixed(1)+'MB':b>1024?(b/1024).toFixed(0)+'KB':b+'B';}"
        +"function fill(n){"
        +"  ['tp','pp','sp','ep','dp','ip','mf1'].forEach(i=>{const e=g(i);if(e&&!e.value)e.value=n;});"
        +"}"
        +"async function uploadFile(input){"
        +"  const file=input.files[0]; if(!file) return;"
        +"  const fd=new FormData(); fd.append('file',file);"
        +"  try{"
        +"    const d=await (await fetch('/upload?filename='+encodeURIComponent(file.name),{method:'POST',body:fd})).json();"
        +"    if(d.success){listFiles();} else alert('❌ '+d.error);"
        +"  }catch(e){alert('❌ Upload échoué');}"
        +"  input.value='';"
        +"}"
        +"async function fetchLogs(){"
        +"  try{"
        +"    const d=await (await fetch('/api/logs')).json();"
        +"    const box=g('logBox');"
        +"    box.innerHTML=d.logs.slice(-30).reverse().join('<br>');"
        +"  }catch(e){}"
        +"}"
        +"async function checkStatus(){"
        +"  try{"
        +"    const d=await (await fetch('/api/status')).json();"
        +"    const b=g('corbaBadge');"
        +"    b.textContent=d.corba?'✅ CORBA Connecté':'🔴 CORBA Déconnecté';"
        +"    b.className='badge '+(d.corba?'b-green':'');"
        +"    b.style.background=d.corba?'#a6e3a1':'#f38ba8';"
        +"  }catch(e){}"
        +"}"
        +"listFiles(); fetchLogs(); checkStatus();"
        +"setInterval(fetchLogs,5000); setInterval(checkStatus,10000); setInterval(listFiles,15000);"
        +"</script></body></html>";
    }

    public static void main(String[] args) throws IOException {
        Logger.info("=== Démarrage PDF CORBA Server ===");
        CORBAConnector corba = new CORBAConnector("172.26.20.100", 1050);
        new WebServer(corba);
        Logger.ok("Accès: http://localhost:8080 | http://172.26.20.100:8080");
        try { Thread.currentThread().join(); } catch (InterruptedException e) {}
    }
}
