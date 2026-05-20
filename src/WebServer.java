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
        super(8080); this.corba = corba;
        new File(HOME).mkdirs();
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Logger.ok("Serveur Web → http://localhost:8080");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String ip = session.getHeaders().getOrDefault("http-client-ip","127.0.0.1");
        Logger.info("REQ " + session.getMethod() + " " + uri + " \u2190 " + ip);
        try {
            if (uri.equals("/") || uri.equals("/index.html")) return html(getHTML());
            if (uri.equals("/status")) return html(getStatusHTML());
            if (uri.startsWith("/api/")) return handleAPI(session, uri);
            if (uri.startsWith("/download/")) return handleDownload(uri);
            if (uri.equals("/upload") && Method.POST.equals(session.getMethod())) return handleUpload(session);
        } catch (Validator.ValidationException e) {
            Logger.warn("Validation: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            Logger.error("Erreur: " + e.getMessage());
            return json("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","404");
    }

    private Response handleAPI(IHTTPSession session, String uri) throws Exception {
        PDFService svc = corba.getService();
        Map<String,String> p = session.getParms();
        if (Method.POST.equals(session.getMethod())) {
            Map<String,String> body = new HashMap<>(); session.parseBody(body);
            String raw = body.get("postData"); if (raw!=null) p = parseJSON(raw);
        }
        long t = System.currentTimeMillis();
        switch(uri) {
            case "/api/create": { String c=Validator.requireText(p.get("content"),"Contenu"); String o=HOME+"created_"+t+".pdf"; return fileResult(svc.createPDF(c,o),o); }
            case "/api/extract-text": { String path=Validator.requireFile(p.get("path")); String r=svc.extractText(path); Logger.ok("extractText \u2190 "+p.get("path")); return r.startsWith("ERROR:")?json("{\"error\":\""+esc(r)+"\"}"):json("{\"success\":true,\"result\":\""+esc(r.replace("SUCCESS:",""))+"\"}"); }
            case "/api/merge": { String f1=Validator.requireFile(p.get("file1")); String f2=Validator.requireFile(p.get("file2")); String o=HOME+"merged_"+t+".pdf"; return fileResult(svc.mergePDF(f1,f2,o),o); }
            case "/api/password": { String path=Validator.requireFile(p.get("path")); String pass=Validator.requireText(p.get("password"),"Mot de passe"); String o=HOME+"secured_"+t+".pdf"; return fileResult(svc.addPassword(path,pass,o),o); }
            case "/api/split": { String path=Validator.requireFile(p.get("path")); int pg=Validator.requireInt(p.get("pages"),"Pages",1,1000); String od=HOME+"split_"+t; return json("{\"success\":true,\"result\":\""+esc(svc.splitPDF(path,pg,od))+"\"}"); }
            case "/api/extract-pages": { String path=Validator.requireFile(p.get("path")); int f=Validator.requireInt(p.get("from"),"Début",1,9999); int to=Validator.requireInt(p.get("to"),"Fin",1,9999); if(f>to) throw new Validator.ValidationException("Page début > fin"); String o=HOME+"extracted_"+t+".pdf"; return fileResult(svc.extractPages(path,f,to,o),o); }
            case "/api/delete-pages": { String path=Validator.requireFile(p.get("path")); int f=Validator.requireInt(p.get("from"),"Début",1,9999); int to=Validator.requireInt(p.get("to"),"Fin",1,9999); if(f>to) throw new Validator.ValidationException("Page début > fin"); String o=HOME+"deleted_"+t+".pdf"; return fileResult(svc.deletePages(path,f,to,o),o); }
            case "/api/convert-images": { String path=Validator.requireFile(p.get("path")); String od=HOME+"images_"+t; return json("{\"success\":true,\"result\":\""+esc(svc.convertToImages(path,od))+"\"}"); }
            case "/api/list": { File dir=new File(HOME); StringBuilder sb=new StringBuilder("["); File[] files=dir.listFiles((d,n)->n.endsWith(".pdf")); if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified())); for(int i=0;i<files.length;i++){sb.append("{\"name\":\"").append(files[i].getName()).append("\",\"size\":").append(files[i].length()).append("}"); if(i<files.length-1)sb.append(",");}} return json("{\"files\":"+sb.append("]")+"}"); }
            case "/api/logs": return json("{\"logs\":"+Logger.getLogsJSON()+"}");
            case "/api/status": { long up=(System.currentTimeMillis()-startTime)/1000; return json("{\"corba\":"+corba.isConnected()+",\"reconnects\":"+corba.getReconnectCount()+",\"uptime\":"+up+"}"); }
        }
        return json("{\"error\":\"Route inconnue\"}");
    }

    private Response fileResult(String r, String out) {
        if(r.startsWith("ERROR:")) return json("{\"error\":\""+esc(r)+"\"}");
        String fname=new File(out).getName(); Logger.ok("Fichier créé: "+fname);
        return json("{\"success\":true,\"result\":\"Fichier créé avec succès\",\"file\":\""+fname+"\"}");
    }

    private Response handleDownload(String uri) throws IOException {
        String fname=uri.replace("/download/",""); if(fname.contains("..")||fname.contains("/")) return newFixedLengthResponse(Response.Status.FORBIDDEN,"text/plain","Interdit");
        File f=new File(HOME+fname); if(!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Introuvable");
        byte[] data=Files.readAllBytes(f.toPath()); Response r=newFixedLengthResponse(Response.Status.OK,"application/pdf",new ByteArrayInputStream(data),data.length);
        r.addHeader("Content-Disposition","attachment; filename=\""+fname+"\""); Logger.info("Download: "+fname); return r;
    }

    private Response handleUpload(IHTTPSession session) throws Exception {
        Map<String,String> body=new HashMap<>(); session.parseBody(body); String tmpPath=body.get("file");
        String fname=session.getParms().getOrDefault("filename","upload_"+System.currentTimeMillis()+".pdf");
        if(fname.contains("..")||!fname.endsWith(".pdf")) return json("{\"error\":\"PDF uniquement\"}");
        if(tmpPath!=null){Files.copy(new File(tmpPath).toPath(),new File(HOME+fname).toPath(),StandardCopyOption.REPLACE_EXISTING); Logger.ok("Upload: "+fname); return json("{\"success\":true,\"file\":\""+fname+"\"}");}
        return json("{\"error\":\"Upload échoué\"}");
    }

    private Response json(String s){Response r=newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",s);r.addHeader("Access-Control-Allow-Origin","*");return r;}
    private Response html(String s){return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",s);}
    private String esc(String s){if(s==null)return "";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
    private Map<String,String> parseJSON(String json){Map<String,String> map=new HashMap<>();json=json.trim().replaceAll("[{}]","");for(String part:json.split(",")){String[] kv=part.split(":",2);if(kv.length==2)map.put(kv[0].trim().replaceAll("\"",""),kv[1].trim().replaceAll("\"",""));}return map;}

    private String getStatusHTML(){
        long up = (System.currentTimeMillis()-startTime)/1000;
        String cs = corba.isConnected() ? "ok" : "err";
        String cl = corba.isConnected() ? "Connecte" : "Deconnecte";
        String lg = String.join("\n", Logger.getLogs().subList(Math.max(0,Logger.getLogs().size()-25),Logger.getLogs().size()));
        return "<!DOCTYPE html><html><head><meta charset=UTF-8><title>Statut</title><meta http-equiv=refresh content=5>"
        +"<style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:system-ui;background:#f8f8f6;color:#1a1a18;padding:30px}h1{font-size:20px;font-weight:500;margin-bottom:20px;color:#185fa5}.box{background:#fff;border:1px solid #eee;border-radius:12px;padding:18px;margin-bottom:14px}.lbl{font-size:11px;color:#888780;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px}.val{font-size:15px;font-weight:500}.ok{color:#3b6d11}.err{color:#a32d2d}pre{font-size:11px;color:#5f5e5a;line-height:1.7;overflow:auto;max-height:250px}a{color:#185fa5;text-decoration:none;font-size:13px}</style></head><body>"
        +"<h1>Statut du serveur</h1>"
        +"<div class=box><div class=lbl>Serveur Web</div><div class=val ok>En ligne - Port 8080</div><div style=font-size:12px;color:#888780;margin-top:4px>Uptime: "+up+"s</div></div>"
        +"<div class=box><div class=lbl>Serveur CORBA</div><div class=val style=color:"+(corba.isConnected()?"#3b6d11":"#a32d2d")+">"+cl+"</div>"
        +"<div style=font-size:12px;color:#888780;margin-top:4px>"+corba.getHost()+":"+corba.getPort()+" - Reconnexions: "+corba.getReconnectCount()+"</div></div>"
        +"<div class=box><div class=lbl>Logs recents</div><pre>"+lg+"</pre></div>"
        +"<a href=/>← Retour</a></body></html>";
    }

    private String getHTML(){
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("user.home")+"/pdf-corba-server/resources/index.html"));
            return new String(bytes, "UTF-8");
        } catch(Exception e){ return "<h1>Erreur chargement HTML: "+e.getMessage()+"</h1>"; }
    }
    private String getHTML_unused(){return '<!DOCTYPE html><html lang=\'fr\'><head><meta charset=\'UTF-8\'><meta name=\'viewport\' content=\'width=device-width,initial-scale=1\'><title>PDF CORBA Server</title><style>*{margin:0;padding:0;box-sizing:border-box;transition:background .25s,color .25s,border-color .25s}body{font-family:system-ui,-apple-system,sans-serif}#app{--bg:#f0f4f8;--sf:#ffffff;--sf2:#e8edf2;--bd:#d1dae3;--tx:#1a2332;--mu:#64748b;--ac:#2563eb;--ac2:#1d4ed8;--ac-bg:#eff6ff;--ac-tx:#1e40af;--ok:#16a34a;--ok-bg:#f0fdf4;--er:#dc2626;--er-bg:#fef2f2;--btn:#2563eb;--btn-tx:#ffffff;--hdr:#1e3a5f;background:var(--bg);color:var(--tx);min-height:100vh}#app.dark{--bg:#0f172a;--sf:#1e293b;--sf2:#273548;--bd:#334155;--tx:#e2e8f0;--mu:#94a3b8;--ac:#60a5fa;--ac2:#3b82f6;--ac-bg:#1e3a5f;--ac-tx:#93c5fd;--ok:#4ade80;--ok-bg:#052e16;--er:#f87171;--er-bg:#450a0a;--btn:#3b82f6;--btn-tx:#ffffff;--hdr:#020617}.hdr{background:var(--hdr);padding:16px 24px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:10}.hicon{width:38px;height:38px;background:rgba(255,255,255,.15);border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:20px}.htitle{color:#fff;font-size:16px;font-weight:600;margin-left:12px}.hsub{color:rgba(255,255,255,.55);font-size:12px;margin-left:12px}.badge-on{background:rgba(74,222,128,.2);color:#4ade80;border:1px solid rgba(74,222,128,.3);padding:4px 12px;border-radius:20px;font-size:12px;font-weight:500;display:flex;align-items:center;gap:6px}.dot{width:6px;height:6px;border-radius:50%;background:#4ade80;animation:blink 2s infinite}@keyframes blink{0%,100%{opacity:1}50%{opacity:.3}}.tbtn{background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.2);color:#fff;border-radius:8px;padding:7px 14px;font-size:12px;font-weight:500;cursor:pointer;display:flex;align-items:center;gap:6px}.tbtn:hover{background:rgba(255,255,255,.2)}.stlink{color:rgba(255,255,255,.7);font-size:12px;text-decoration:none;display:flex;align-items:center;gap:5px}.stlink:hover{color:#fff}.body{padding:22px;max-width:1100px;margin:0 auto}.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:14px;margin-bottom:22px}.stat{background:var(--sf);border:1px solid var(--bd);border-radius:12px;padding:16px 18px}.slbl{font-size:11px;font-weight:600;color:var(--mu);text-transform:uppercase;letter-spacing:.8px;margin-bottom:8px}.sval{font-size:22px;font-weight:700;color:var(--tx)}.sval.g{color:var(--ok)}.sval.r{color:var(--er)}.ssub{font-size:12px;color:var(--mu);margin-top:4px}.fcard{background:var(--sf);border:1px solid var(--bd);border-radius:12px;padding:18px;margin-bottom:18px}.sec-title{font-size:13px;font-weight:600;color:var(--tx);margin-bottom:14px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}.sec-sub{color:var(--mu);font-weight:400;font-size:12px}.ftags{display:flex;flex-wrap:wrap;gap:8px;min-height:34px}.ftag{background:var(--ac-bg);color:var(--ac-tx);border:1px solid #bfdbfe;border-radius:8px;padding:6px 14px;font-size:13px;display:flex;align-items:center;gap:7px;cursor:pointer;font-weight:500}.ftag:hover{background:var(--btn);color:#fff;border-color:var(--btn)}.sz{opacity:.6;font-size:11px;font-weight:400}.dlsmall{color:inherit;text-decoration:none;opacity:.7;font-size:12px}.dlsmall:hover{opacity:1}.hbtn{padding:6px 14px;background:var(--sf2);border:1px solid var(--bd);border-radius:8px;color:var(--mu);font-size:12px;font-weight:500;cursor:pointer}.hbtn:hover{border-color:var(--ac);color:var(--ac);background:var(--ac-bg)}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:14px;margin-bottom:18px}.card{background:var(--sf);border:1px solid var(--bd);border-radius:12px;padding:18px}.card-hd{font-size:13px;font-weight:600;color:var(--tx);margin-bottom:14px;display:flex;align-items:center;gap:8px;padding-bottom:12px;border-bottom:1px solid var(--bd)}.card-ico{width:28px;height:28px;background:var(--ac-bg);border-radius:7px;display:flex;align-items:center;justify-content:center;font-size:14px}input,textarea{width:100%;padding:9px 12px;background:var(--sf2);border:1px solid var(--bd);border-radius:8px;color:var(--tx);font-size:13px;margin-bottom:9px;outline:none;font-family:inherit}input:focus,textarea:focus{border-color:var(--ac);background:var(--sf);box-shadow:0 0 0 3px rgba(37,99,235,.1)}textarea{height:70px;resize:vertical}.row2{display:flex;gap:8px}.btn{width:100%;padding:10px;background:var(--btn);color:var(--btn-tx);border:none;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;letter-spacing:.2px}.btn:hover{background:var(--ac2)}.btn:active{transform:scale(.98)}.result{border-radius:8px;padding:10px 13px;margin-top:10px;font-size:12px;font-family:monospace;min-height:36px;color:var(--mu);line-height:1.6;background:var(--sf2);border:1px solid var(--bd)}.result.ok{color:var(--ok);background:var(--ok-bg);border-color:rgba(22,163,74,.2)}.result.er{color:var(--er);background:var(--er-bg);border-color:rgba(220,38,38,.2)}.result.ld{color:var(--ac);background:var(--ac-bg);border-color:rgba(37,99,235,.2)}.dl{display:inline-flex;align-items:center;gap:5px;margin-top:7px;padding:5px 12px;background:var(--sf);border:1px solid var(--bd);border-radius:6px;font-size:11px;color:var(--tx);text-decoration:none;font-weight:500}.dl:hover{background:var(--ac-bg);color:var(--ac-tx);border-color:var(--ac)}.logcard{background:var(--sf);border:1px solid var(--bd);border-radius:12px;padding:18px}.logbox{background:var(--sf2);border:1px solid var(--bd);border-radius:8px;padding:13px;font-family:monospace;font-size:11px;color:var(--mu);height:130px;overflow-y:auto;line-height:1.8}.lok{color:var(--ok);font-weight:500}.linf{color:var(--ac)}.ler{color:var(--er);font-weight:500}</style></head><body><div id=\'app\'><header class=\'hdr\'><div style=\'display:flex;align-items:center\'><div class=\'hicon\'>📄</div><div><div class=\'htitle\'>PDF CORBA Server</div><div class=\'hsub\'>Services distribues · Java 8 · PDFBox 2.0</div></div></div><div style=\'display:flex;align-items:center;gap:12px\'><div class=\'badge-on\' id=\'corbaBadge\'><div class=\'dot\'></div> Serveur en ligne</div><a href=\'/status\' class=\'stlink\'>📊 Statut</a><button class=\'tbtn\' onclick=\'toggleTheme()\'>🌙 <span id=\'tlbl\'>Sombre</span></button></div></header><div class=\'body\'><div class=\'stats\'><div class=\'stat\'><div class=\'slbl\'>Statut CORBA</div><div class=\'sval g\' id=\'sCorba\'>Connecte</div><div class=\'ssub\' id=\'sHost\'>172.26.20.100:1050</div></div><div class=\'stat\'><div class=\'slbl\'>Port Web</div><div class=\'sval\'>8080</div><div class=\'ssub\'>NanoHTTPD</div></div><div class=\'stat\'><div class=\'slbl\'>Uptime</div><div class=\'sval\' id=\'sUp\'>--</div><div class=\'ssub\'>Depuis demarrage</div></div><div class=\'stat\'><div class=\'slbl\'>Fichiers PDF</div><div class=\'sval\' id=\'sCount\'>--</div><div class=\'ssub\'>Dans pdfs/</div></div></div><div class=\'fcard\'><div class=\'sec-title\'>📁 Fichiers disponibles <span class=\'sec-sub\'>Cliquer pour remplir les champs</span><div style=\'margin-left:auto;display:flex;gap:8px\'><button class=\'hbtn\' onclick=\'listFiles()\'>🔄 Actualiser</button><button class=\'hbtn\' onclick=\'document.getElementById("fu").click()\'>⬆️ Upload</button></div></div><input type=\'file\' id=\'fu\' accept=\'.pdf\' style=\'display:none\' onchange=\'uploadFile(this)\'><div class=\'ftags\' id=\'fileList\'><span style=\'color:var(--mu);font-size:13px\'>Chargement...</span></div></div><div class=\'grid\'><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>✨</div> Creer un PDF</div><textarea id=\'cc\' placeholder=\'Contenu du PDF...\'>Bonjour CORBA !
Ligne 2
Ligne 3</textarea><button class=\'btn\' onclick="act(\'create\',{content:g(\'cc\').value},\'cr\')">Creer le PDF</button><div class=\'result\' id=\'cr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>📝</div> Extraire le texte</div><input id=\'tp\' placeholder=\'Nom du fichier (ex: test.pdf)\'><button class=\'btn\' onclick="act(\'extract-text\',{path:g(\'tp\').value},\'tr\')">Extraire le texte</button><div class=\'result\' id=\'tr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>🔗</div> Fusionner des PDFs</div><input id=\'mf1\' placeholder=\'Fichier 1 (ex: a.pdf)\'><input id=\'mf2\' placeholder=\'Fichier 2 (ex: b.pdf)\'><button class=\'btn\' onclick="act(\'merge\',{file1:g(\'mf1\').value,file2:g(\'mf2\').value},\'mr\')">Fusionner</button><div class=\'result\' id=\'mr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>📑</div> Extraire des pages</div><input id=\'ep\' placeholder=\'Nom du fichier\'><div class=\'row2\'><input id=\'ef\' value=\'1\' placeholder=\'Page de\'><input id=\'et\' value=\'1\' placeholder=\'Page a\'></div><button class=\'btn\' onclick="act(\'extract-pages\',{path:g(\'ep\').value,from:g(\'ef\').value,to:g(\'et\').value},\'epr\')">Extraire les pages</button><div class=\'result\' id=\'epr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>🗑️</div> Supprimer des pages</div><input id=\'dp\' placeholder=\'Nom du fichier\'><div class=\'row2\'><input id=\'df\' value=\'1\' placeholder=\'Page de\'><input id=\'dt\' value=\'1\' placeholder=\'Page a\'></div><button class=\'btn\' onclick="act(\'delete-pages\',{path:g(\'dp\').value,from:g(\'df\').value,to:g(\'dt\').value},\'dpr\')">Supprimer les pages</button><div class=\'result\' id=\'dpr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>🔒</div> Proteger par mot de passe</div><input id=\'pp\' placeholder=\'Nom du fichier\'><input id=\'pw\' type=\'password\' placeholder=\'Mot de passe\'><button class=\'btn\' onclick="act(\'password\',{path:g(\'pp\').value,password:g(\'pw\').value},\'pwr\')">Securiser le PDF</button><div class=\'result\' id=\'pwr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>✂️</div> Decouper le PDF</div><input id=\'sp\' placeholder=\'Nom du fichier\'><input id=\'spg\' value=\'1\' placeholder=\'Pages par partie\'><button class=\'btn\' onclick="act(\'split\',{path:g(\'sp\').value,pages:g(\'spg\').value},\'spr\')">Decouper</button><div class=\'result\' id=\'spr\'></div></div><div class=\'card\'><div class=\'card-hd\'><div class=\'card-ico\'>🖼️</div> Convertir en images</div><input id=\'ip\' placeholder=\'Nom du fichier\'><button class=\'btn\' onclick="act(\'convert-images\',{path:g(\'ip\').value},\'ir\')">Convertir en PNG</button><div class=\'result\' id=\'ir\'></div></div></div><div class=\'logcard\'><div class=\'sec-title\'>🖥️ Logs en temps reel <span class=\'sec-sub\'>Actualisation toutes les 5 secondes</span></div><div class=\'logbox\' id=\'logBox\'>Chargement...</div></div></div></div><script>function g(x){return document.getElementById(x)}let dark=false;function toggleTheme(){dark=!dark;g(\'app\').className=dark?\'dark\':\'\';g(\'tlbl\').textContent=dark?\'Clair\':\'Sombre\'}async function act(route,params,rid){const el=g(rid);el.className=\'result ld\';el.innerHTML=\'⏳ En cours...\';const qs=Object.entries(params).map(([k,v])=>k+\'=\'+encodeURIComponent(v)).join(\'&\');try{const d=await(await fetch(\'/api/\'+route+\'?\'+qs)).json();if(d.error){el.className=\'result er\';el.innerHTML=\'❌ \'+d.error}else{el.className=\'result ok\';let h=\'✅ \'+(d.result||\'OK\');if(d.file)h+=\'<br><a class="dl" href="/download/\'+d.file+\'" download>⬇️ Telecharger \'+d.file+\'</a>\';el.innerHTML=h;listFiles()}}catch(e){el.className=\'result er\';el.innerHTML=\'❌ Erreur reseau\'}fetchLogs()}async function listFiles(){try{const d=await(await fetch(\'/api/list\')).json();const el=g(\'fileList\');if(d.files&&d.files.length){g(\'sCount\').textContent=d.files.length;el.innerHTML=d.files.map(f=>\'<span class="ftag" onclick="fill(\\'\'+f.name+\'\\')">📄 \'+f.name+\' <span class="sz">\'+fmt(f.size)+\'</span> <a class="dlsmall" href="/download/\'+f.name+\'" download>⬇️</a></span>\').join(\'\')}else{g(\'sCount\').textContent=\'0\';el.innerHTML=\'<span style="color:var(--mu)">Aucun fichier PDF</span>\'}}catch(e){}}function fmt(b){return b>1048576?(b/1048576).toFixed(1)+\'MB\':b>1024?(b/1024).toFixed(0)+\'KB\':b+\'B\'}function fill(n){[\'tp\',\'pp\',\'sp\',\'ep\',\'dp\',\'ip\',\'mf1\'].forEach(i=>{const e=g(i);if(e&&!e.value)e.value=n})}async function uploadFile(input){const file=input.files[0];if(!file)return;const fd=new FormData();fd.append(\'file\',file);try{const d=await(await fetch(\'/upload?filename=\'+encodeURIComponent(file.name),{method:\'POST\',body:fd})).json();if(d.success)listFiles();else alert(\'Erreur: \'+d.error)}catch(e){alert(\'Upload echoue\')}input.value=\'\'}async function fetchLogs(){try{const d=await(await fetch(\'/api/logs\')).json();const box=g(\'logBox\');const cls=(l)=>l.includes(\'[OK\')?\' lok\':l.includes(\'[INFO\')?\' linf\':l.includes(\'[ERR\')?\' ler\':\'\';box.innerHTML=d.logs.slice(-40).reverse().map(l=>\'<div class="\'+cls(l)+\'">\'+l+\'</div>\').join(\'\')}catch(e){}}async function checkStatus(){try{const d=await(await fetch(\'/api/status\')).json();const b=g(\'corbaBadge\'),s=g(\'sCorba\');if(d.corba){b.innerHTML=\'<div class="dot"></div> Serveur en ligne\';b.style.cssText=\'background:rgba(74,222,128,.2);color:#4ade80;border:1px solid rgba(74,222,128,.3);padding:4px 12px;border-radius:20px;font-size:12px;font-weight:500;display:flex;align-items:center;gap:6px\';s.textContent=\'Connecte\';s.className=\'sval g\'}else{b.innerHTML=\'⚠️ Hors ligne\';b.style.cssText=\'background:rgba(248,113,113,.2);color:#f87171;border:1px solid rgba(248,113,113,.3);padding:4px 12px;border-radius:20px;font-size:12px;font-weight:500\';s.textContent=\'Deconnecte\';s.className=\'sval r\'}const up=d.uptime;g(\'sUp\').textContent=up>3600?Math.floor(up/3600)+\'h \'+Math.floor(up%3600/60)+\'m\':up>60?Math.floor(up/60)+\'m \'+up%60+\'s\':up+\'s\'}catch(e){}}listFiles();fetchLogs();checkStatus();setInterval(fetchLogs,5000);setInterval(checkStatus,10000);setInterval(listFiles,30000);</script></body></html>';}
"
+"async function act(route,params,rid){"
+" const el=g(rid);el.className='result ld';el.innerHTML='<i class=\"ti ti-loader-2\"></i> En cours...';"
+" const qs=Object.entries(params).map(([k,v])=>k+'='+encodeURIComponent(v)).join('&');"
+" try{"
+"  const d=await (await fetch('/api/'+route+'?'+qs)).json();"
+"  if(d.error){el.className='result er';el.innerHTML='<i class=\"ti ti-alert-circle\"></i> '+d.error;}"
+"  else{el.className='result ok';"
+"   let h='<i class=\"ti ti-check\"></i> '+(d.result||'OK');"
+"   if(d.file) h+='<br><a class=\"dl\" href=\"/download/'+d.file+'\" download><i class=\"ti ti-download\"></i> '+d.file+'</a>';"
+"   el.innerHTML=h;listFiles();}"
+" }catch(e){el.className='result er';el.innerHTML='<i class=\"ti ti-wifi-off\"></i> Erreur réseau';}"
+" fetchLogs();"
+"}"
+"async function listFiles(){"
+" try{"
+"  const d=await (await fetch('/api/list')).json();"
+"  const el=g('fileList');"
+"  if(d.files&&d.files.length){"
+"   g('sCount').textContent=d.files.length;"
+"   el.innerHTML=d.files.map(f=>"
+"    '<span class=\"ftag\" onclick=\"fill(\\''+f.name+'\\')\">'+'<i class=\"ti ti-file-type-pdf\" style=\"font-size:13px\"></i> '+f.name+"
+"    ' <span class=\"sz\">'+fmt(f.size)+'</span>'"
+"    +' <a class=\"dl\" href=\"/download/'+f.name+'\" download style=\"margin-top:0;padding:2px 8px;font-size:11px\"><i class=\"ti ti-download\" style=\"font-size:12px\"></i></a></span>'"
+"   ).join('');"
+"  } else {g('sCount').textContent='0';el.innerHTML='<span style=\"color:var(--mu);font-size:13px\">Aucun fichier PDF</span>';}"
+" }catch(e){}"
+"}"
+"function fmt(b){return b>1048576?(b/1048576).toFixed(1)+'MB':b>1024?(b/1024).toFixed(0)+'KB':b+'B';}"
+"function fill(n){['tp','pp','sp','ep','dp','ip','mf1'].forEach(i=>{const e=g(i);if(e&&!e.value)e.value=n;});}"
+"async function uploadFile(input){"
+" const file=input.files[0];if(!file)return;"
+" const fd=new FormData();fd.append('file',file);"
+" try{const d=await (await fetch('/upload?filename='+encodeURIComponent(file.name),{method:'POST',body:fd})).json();"
+"  if(d.success)listFiles(); else alert('Erreur: '+d.error);"
+" }catch(e){alert('Upload échoué');} input.value='';}"
+"async function fetchLogs(){"
+" try{const d=await (await fetch('/api/logs')).json();"
+"  const box=g('logBox');"
+"  const cls=(l)=>l.includes('[OK')?' lok':l.includes('[INFO')?' linf':l.includes('[ERR')?' lerr':'';"
+"  box.innerHTML=d.logs.slice(-40).reverse().map(l=>'<div class=\"'+cls(l)+'\">'+l+'</div>').join('');"
+" }catch(e){}"
+"}"
+"async function checkStatus(){"
+" try{const d=await (await fetch('/api/status')).json();"
+"  const b=g('corbaBadge'),s=g('sCorba');"
+"  if(d.corba){b.innerHTML='<i class=\"ti ti-circle-check\"></i> En ligne';b.style.background='var(--ok-bg)';b.style.color='var(--ok)';s.textContent='Connecté';s.style.color='var(--ok)';}"
+"  else{b.innerHTML='<i class=\"ti ti-circle-x\"></i> Hors ligne';b.style.background='var(--er-bg)';b.style.color='var(--er)';s.textContent='Déconnecté';s.style.color='var(--er)';}"
+"  const up=d.uptime;g('sUp').textContent=up>3600?Math.floor(up/3600)+'h'+(Math.floor(up%3600/60))+'m':up>60?Math.floor(up/60)+'m'+(up%60)+'s':up+'s';"
+" }catch(e){}"
+"}"
+"listFiles();fetchLogs();checkStatus();"
+"setInterval(fetchLogs,5000);setInterval(checkStatus,10000);setInterval(listFiles,30000);"
+"</script></body></html>";}

    private static String card(String icon, String title, String inputs, String onclick, String label, String rid){
        return "<div class='card'><div class='cttl'><i class='ti ti-"+icon+"' aria-hidden='true'></i> "+title+"</div>"
        +inputs+"<button class='btn' onclick=\""+onclick+"\">"+label+"</button>"
        +"<div class='result' id='"+rid+"'></div></div>";
    }

    public static void main(String[] args) throws IOException {
        Logger.info("=== PDF CORBA Server démarrage ===");
        CORBAConnector corba = new CORBAConnector("172.26.20.100", 1050);
        new WebServer(corba);
        Logger.ok("Interface: http://localhost:8080 | http://172.26.20.100:8080");
        try { Thread.currentThread().join(); } catch (InterruptedException e) {}
    }
}
