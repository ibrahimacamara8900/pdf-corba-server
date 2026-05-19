import pdf.PDFServicePOA;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class PDFServiceImpl extends PDFServicePOA {

    @Override
    public String mergePDF(String path1, String path2, String output) {
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.addSource(path1); merger.addSource(path2);
            merger.setDestinationFileName(output);
            merger.mergeDocuments(null);
            return "SUCCESS:" + output;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String splitPDF(String path, int pages, String outputDir) {
        try {
            PDDocument doc = PDDocument.load(new File(path));
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(pages);
            List<PDDocument> parts = splitter.split(doc);
            new File(outputDir).mkdirs();
            for (int i = 0; i < parts.size(); i++) {
                parts.get(i).save(outputDir + "/part_" + i + ".pdf");
                parts.get(i).close();
            }
            doc.close();
            return "SUCCESS:" + parts.size() + " fichiers dans " + outputDir;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String extractPages(String path, int fromPage, int toPage, String output) {
        try {
            PDDocument source = PDDocument.load(new File(path));
            PDDocument result = new PDDocument();
            for (int i = fromPage - 1; i < toPage; i++)
                result.addPage(source.getPage(i));
            result.save(output); result.close(); source.close();
            return "SUCCESS:" + output;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String deletePages(String path, int fromPage, int toPage, String output) {
        try {
            PDDocument doc = PDDocument.load(new File(path));
            for (int i = toPage - 1; i >= fromPage - 1; i--)
                doc.removePage(i);
            doc.save(output); doc.close();
            return "SUCCESS:" + output;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String addPassword(String path, String password, String output) {
        try {
            PDDocument doc = PDDocument.load(new File(path));
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy); doc.save(output); doc.close();
            return "SUCCESS:" + output;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String convertToImages(String path, String outputDir) {
        try {
            PDDocument doc = PDDocument.load(new File(path));
            PDFRenderer renderer = new PDFRenderer(doc);
            new File(outputDir).mkdirs();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 150);
                ImageIO.write(img, "PNG", new File(outputDir + "/page_" + i + ".png"));
            }
            doc.close();
            return "SUCCESS:" + doc.getNumberOfPages() + " images dans " + outputDir;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String extractText(String path) {
        try {
            PDDocument doc = PDDocument.load(new File(path));
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            doc.close();
            return "SUCCESS:" + text;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }

    @Override
    public String createPDF(String content, String output) {
        try {
            PDDocument doc = new PDDocument();
            PDPage page = new PDPage();
            doc.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(doc, page);
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(50, 700);
            for (String line : content.split("\n")) {
                stream.showText(line);
                stream.newLineAtOffset(0, -15);
            }
            stream.endText(); stream.close();
            doc.save(output); doc.close();
            return "SUCCESS:" + output;
        } catch (Exception e) { return "ERROR:" + e.getMessage(); }
    }
}
