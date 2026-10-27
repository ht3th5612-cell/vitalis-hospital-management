package util;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;

public final class Mailer {

    public interface Sender {
        void send(String to, String subject, String body) throws IOException;
    }

    private static final Properties SETTINGS = new Properties();

    static {
        try (InputStream in = Mailer.class.getResourceAsStream("/mail.properties")) {
            if (in != null) {
                SETTINGS.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Unable to read mail.properties: " + e.getMessage());
        }
    }

    private static Sender sender = Mailer::sendBySmtp;

    private Mailer() {
    }

    public static void setSender(Sender replacement) {
        sender = replacement == null ? Mailer::sendBySmtp : replacement;
    }

    public static void send(String to, String subject, String body) {
        try {
            sender.send(to, subject, body);
        } catch (IOException e) {
            throw new IllegalStateException("The e-mail could not be sent. Check the internet connection and src/mail.properties. ("
                    + e.getMessage() + ")");
        }
    }

    private static void sendBySmtp(String to, String subject, String body) throws IOException {
        String host = setting("mail.host", "smtp.gmail.com");
        int port = Integer.parseInt(setting("mail.port", "587"));
        String user = setting("mail.user", "");
        String password = setting("mail.password", "").replace(" ", "");
        if (user.isBlank() || password.isBlank()) {
            throw new IOException("mail.user / mail.password are not set");
        }
        String from = setting("mail.from", "VITALIS Hospital");

        SSLSocketFactory ssl = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket plain = null;
        Socket socket = null;
        try {
            if (port == 465) {
                socket = ssl.createSocket(host, port);
            } else {
                plain = new Socket();
                plain.connect(new InetSocketAddress(host, port), 15_000);
                socket = plain;
            }
            socket.setSoTimeout(15_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expect(in, 220);
            command(out, in, "EHLO vitalis", 250);
            if (port != 465) {
                command(out, in, "STARTTLS", 220);
                socket = ssl.createSocket(plain, host, port, true);
                socket.setSoTimeout(15_000);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                command(out, in, "EHLO vitalis", 250);
            }
            command(out, in, "AUTH LOGIN", 334);
            command(out, in, base64(user), 334);
            command(out, in, base64(password), 235);
            command(out, in, "MAIL FROM:<" + user + ">", 250);
            command(out, in, "RCPT TO:<" + to + ">", 250);
            command(out, in, "DATA", 354);

            StringBuilder message = new StringBuilder();
            message.append("From: ").append(from).append(" <").append(user).append(">\r\n");
            message.append("To: <").append(to).append(">\r\n");
            message.append("Subject: ").append(subject).append("\r\n");
            message.append("Date: ").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))).append("\r\n");
            message.append("MIME-Version: 1.0\r\n");
            message.append("Content-Type: text/plain; charset=UTF-8\r\n");
            message.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            for (String line : body.split("\\R", -1)) {
                message.append(line.startsWith(".") ? "." + line : line).append("\r\n");
            }
            message.append(".");
            command(out, in, message.toString(), 250);

            out.write("QUIT\r\n");
            out.flush();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static void command(BufferedWriter out, BufferedReader in, String text, int expectedCode) throws IOException {
        out.write(text + "\r\n");
        out.flush();
        expect(in, expectedCode);
    }

    private static void expect(BufferedReader in, int expectedCode) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new IOException("the mail server closed the connection");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
        int code;
        try {
            code = Integer.parseInt(line.substring(0, 3));
        } catch (RuntimeException e) {
            throw new IOException("unexpected answer of the mail server: " + line);
        }
        if (code != expectedCode) {
            throw new IOException("the mail server answered: " + line);
        }
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String setting(String name, String defaultValue) {
        return System.getProperty(name, SETTINGS.getProperty(name, defaultValue)).trim();
    }
}
