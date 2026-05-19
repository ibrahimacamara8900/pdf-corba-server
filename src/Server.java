import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import pdf.PDFServiceHelper;

public class Server {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootPOA.the_POAManager().activate();
            PDFServiceImpl impl = new PDFServiceImpl();
            org.omg.CORBA.Object ref = rootPOA.servant_to_reference(impl);
            NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
            NameComponent[] path = nc.to_name("PDFService");
            nc.rebind(path, ref);
            System.out.println("✅ Serveur PDF CORBA démarré sur port 1050...");
            orb.run();
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
