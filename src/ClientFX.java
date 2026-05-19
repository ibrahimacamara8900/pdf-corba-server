import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import pdf.PDFService;
import pdf.PDFServiceHelper;

public class ClientFX extends JFrame {

    private PDFService service;
    private JTextArea logArea;
    private String clientName;
    private static final Color BG      = new Color(30, 30, 46);
    private static final Color PANEL   = new Color(49, 50, 68);
    private static final Color BTN     = new Color(69, 71, 90);
    private static final Color BTN_HOV = new Color(137, 180, 250);
    private static final Color TXT     = new Color(205, 214, 244);
    private static final Color GREEN   = new Color(166, 227, 161);

    public ClientFX(String name) {
        this.clientName = name;
        setTitle("📄 PDF CORBA Client — " + clientName);
        setSize(480, 620);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        // Header
        JPanel header = new JPanel();
        header.setBackground(PANEL);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel title = new JLabel("📄 PDF CORBA — " + clientName);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(TXT);
        header.add(title);

        // Grille boutons
        JPanel grid = new JPanel(new GridLayout(4, 2, 12, 12));
        grid.setBackground(BG);
        grid.setBorder(new EmptyBorder(20, 20, 10, 20));

        String[][] btns = {
            {"🔗 Fusionner PDFs",       "merge"},
            {"✂️ Découper PDF",          "split"},
            {"📑 Extraire Pages",        "extract"},
            {"🗑️ Supprimer Pages",       "delete"},
            {"🔒 Mot de Passe",          "password"},
            {"🖼️ PDF → Images",         "images"},
            {"📝 Extraire Texte",        "text"},
            {"✨ Créer PDF",             "create"}
        };

        for (String[] b : btns) {
            JButton btn = makeButton(b[0], b[1]);
            grid.add(btn);
        }

        // Zone logs
        logArea = new JTextArea();
        logArea.setBackground(new Color(24, 24, 37));
        logArea.setForeground(GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(PANEL), "📋 Logs",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 11), new Color(108, 112, 134)));
        scroll.setPreferredSize(new Dimension(460, 180));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BG);
        bottom.setBorder(new EmptyBorder(0, 20, 20, 20));
        bottom.add(scroll);

        add(header, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Connexion CORBA
        connectCORBA();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JButton makeButton(String label, String action) {
        JButton btn = new JButton(label);
        btn.setBackground(BTN);
        btn.setForeground(TXT);
        btn.setFont(new Font("Arial", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(BTN_HOV); btn.setForeground(BG); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(BTN); btn.setForeground(TXT); }
        });
        btn.addActionListener(e -> handleAction(action));
        return btn;
    }

    private void connectCORBA() {
        try {
            String[] args = {};
            System.setProperty("org.omg.CORBA.ORBInitialPort", "1050");
            System.setProperty("org.omg.CORBA.ORBInitialHost", "localhost");
            ORB orb = ORB.init(args, null);
            NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
            service = PDFServiceHelper.narrow(nc.resolve_str("PDFService"));
            log("✅ Connecté au serveur CORBA — " + clientName);
        } catch (Exception e) {
            log("❌ Connexion échouée: " + e.getMessage());
        }
    }

    private void handleAction(String action) {
        if (service == null) { log("❌ Non connecté !"); return; }
        String home = System.getProperty("user.home") + "/pdf-corba-server/pdfs/";
        new File(home).mkdirs();
        JFileChooser fc = new JFileChooser(new File(home));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF", "pdf"));

        switch (action) {
            case "create":
                String content = JOptionPane.showInputDialog(this, "Contenu du PDF:", "Créer PDF", JOptionPane.PLAIN_MESSAGE);
                if (content != null) {
                    String out = home + "created_" + System.currentTimeMillis() + ".pdf";
                    log("▶ createPDF..."); log(service.createPDF(content, out));
                }
                break;
            case "text":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    log("▶ extractText..."); log(service.extractText(fc.getSelectedFile().getAbsolutePath()));
                }
                break;
            case "merge":
                fc.setDialogTitle("PDF 1");
                if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) break;
                File f1 = fc.getSelectedFile();
                fc.setDialogTitle("PDF 2");
                if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) break;
                File f2 = fc.getSelectedFile();
                String outM = home + "merged_" + System.currentTimeMillis() + ".pdf";
                log("▶ mergePDF..."); log(service.mergePDF(f1.getAbsolutePath(), f2.getAbsolutePath(), outM));
                break;
            case "images":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String outDir = home + "images_" + System.currentTimeMillis();
                    log("▶ convertToImages..."); log(service.convertToImages(fc.getSelectedFile().getAbsolutePath(), outDir));
                }
                break;
            case "password":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String pass = JOptionPane.showInputDialog(this, "Mot de passe:", "Sécuriser PDF", JOptionPane.PLAIN_MESSAGE);
                    if (pass != null) {
                        String out = home + "secured_" + System.currentTimeMillis() + ".pdf";
                        log("▶ addPassword..."); log(service.addPassword(fc.getSelectedFile().getAbsolutePath(), pass, out));
                    }
                }
                break;
            case "split":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String p = JOptionPane.showInputDialog(this, "Pages par partie:", "1");
                    if (p != null) {
                        String outDir = home + "split_" + System.currentTimeMillis();
                        log("▶ splitPDF..."); log(service.splitPDF(fc.getSelectedFile().getAbsolutePath(), Integer.parseInt(p), outDir));
                    }
                }
                break;
            case "extract":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String from = JOptionPane.showInputDialog(this, "Page début:", "1");
                    String to   = JOptionPane.showInputDialog(this, "Page fin:", "1");
                    if (from != null && to != null) {
                        String out = home + "extracted_" + System.currentTimeMillis() + ".pdf";
                        log("▶ extractPages..."); log(service.extractPages(fc.getSelectedFile().getAbsolutePath(), Integer.parseInt(from), Integer.parseInt(to), out));
                    }
                }
                break;
            case "delete":
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String from = JOptionPane.showInputDialog(this, "Page début:", "1");
                    String to   = JOptionPane.showInputDialog(this, "Page fin:", "1");
                    if (from != null && to != null) {
                        String out = home + "deleted_" + System.currentTimeMillis() + ".pdf";
                        log("▶ deletePages..."); log(service.deletePages(fc.getSelectedFile().getAbsolutePath(), Integer.parseInt(from), Integer.parseInt(to), out));
                    }
                }
                break;
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "ClientA";
        SwingUtilities.invokeLater(() -> new ClientFX(name));
    }
}
