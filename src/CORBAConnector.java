import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import pdf.PDFService;
import pdf.PDFServiceHelper;
import java.util.concurrent.*;

public class CORBAConnector {

    private PDFService service;
    private ORB orb;
    private final String host;
    private final int port;
    private volatile boolean connected = false;
    private int reconnectCount = 0;

    public CORBAConnector(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
        startWatchdog();
    }

    private void connect() {
        try {
            System.setProperty("org.omg.CORBA.ORBInitialPort", String.valueOf(port));
            System.setProperty("org.omg.CORBA.ORBInitialHost", host);
            orb = ORB.init(new String[]{}, null);
            NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
            service = PDFServiceHelper.narrow(nc.resolve_str("PDFService"));
            connected = true;
            Logger.ok("CORBA connecté → " + host + ":" + port);
        } catch (Exception e) {
            connected = false;
            Logger.error("CORBA connexion échouée: " + e.getMessage());
        }
    }

    private void startWatchdog() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (!connected || !isAlive()) {
                reconnectCount++;
                Logger.warn("CORBA déconnecté, tentative de reconnexion #" + reconnectCount);
                connect();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private boolean isAlive() {
        try {
            if (service == null) return false;
            // ping léger
            service.createPDF("ping", "/tmp/ping_test.pdf");
            new java.io.File("/tmp/ping_test.pdf").delete();
            return true;
        } catch (Exception e) {
            connected = false;
            return false;
        }
    }

    public PDFService getService() throws Exception {
        if (!connected || service == null)
            throw new Exception("Serveur CORBA non disponible (reconnexion en cours...)");
        return service;
    }

    public boolean isConnected() { return connected; }
    public int getReconnectCount() { return reconnectCount; }
    public String getHost() { return host; }
    public int getPort() { return port; }
}
