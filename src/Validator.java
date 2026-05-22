import java.io.File;

public class Validator {

    private static final String HOME = System.getenv("APP_HOME") != null ? "/tmp/pdfs/" : System.getProperty("user.home") + "/pdf-corba-server/pdfs/";

    public static String requireFile(String name) throws ValidationException {
        if (name == null || name.trim().isEmpty())
            throw new ValidationException("Nom de fichier vide");
        if (name.contains("..") || name.contains("/") || name.contains("\\"))
            throw new ValidationException("Nom de fichier invalide: " + name);
        if (!name.endsWith(".pdf"))
            throw new ValidationException("Le fichier doit être un PDF: " + name);
        String path = HOME + name;
        if (!new File(path).exists())
            throw new ValidationException("Fichier introuvable: " + name);
        return path;
    }

    public static int requireInt(String val, String field, int min, int max) throws ValidationException {
        if (val == null || val.trim().isEmpty())
            throw new ValidationException(field + " est requis");
        try {
            int v = Integer.parseInt(val.trim());
            if (v < min || v > max)
                throw new ValidationException(field + " doit être entre " + min + " et " + max);
            return v;
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " doit être un nombre entier");
        }
    }

    public static String requireText(String val, String field) throws ValidationException {
        if (val == null || val.trim().isEmpty())
            throw new ValidationException(field + " est requis");
        return val;
    }

    public static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
}
