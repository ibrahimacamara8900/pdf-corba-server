import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.util.*;

public class Logger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX = 200;
    private static PrintWriter fileWriter;

    static {
        try {
            String logFile = System.getProperty("user.home") + "/pdf-corba-server/server.log";
            fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void info(String msg)  { log("INFO ", msg); }
    public static void ok(String msg)    { log("OK   ", msg); }
    public static void warn(String msg)  { log("WARN ", msg); }
    public static void error(String msg) { log("ERROR", msg); }

    private static void log(String level, String msg) {
        String line = "[" + LocalDateTime.now().format(FMT) + "] [" + level + "] " + msg;
        System.out.println(line);
        synchronized (logs) {
            logs.add(line);
            if (logs.size() > MAX) logs.remove(0);
        }
        if (fileWriter != null) fileWriter.println(line);
    }

    public static List<String> getLogs() {
        synchronized (logs) { return new ArrayList<>(logs); }
    }

    public static String getLogsJSON() {
        List<String> l = getLogs();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            sb.append("\"").append(l.get(i).replace("\\","\\\\").replace("\"","\\\"")).append("\"");
            if (i < l.size()-1) sb.append(",");
        }
        return sb.append("]").toString();
    }
}
