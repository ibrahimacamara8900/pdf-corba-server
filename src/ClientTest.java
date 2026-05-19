import org.omg.CosNaming.*;
import org.omg.CORBA.*;
import pdf.PDFService;
import pdf.PDFServiceHelper;

public class ClientTest {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
            PDFService service = PDFServiceHelper.narrow(nc.resolve_str("PDFService"));

            System.out.println("=== Test createPDF ===");
            String r1 = service.createPDF("Bonjour CORBA!\nLigne 2\nLigne 3",
                System.getProperty("user.home") + "/pdf-corba-server/pdfs/test.pdf");
            System.out.println(r1);

            System.out.println("=== Test extractText ===");
            String r2 = service.extractText(
                System.getProperty("user.home") + "/pdf-corba-server/pdfs/test.pdf");
            System.out.println(r2);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
