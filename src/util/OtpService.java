package util;

import dao.UserDAO;
import model.User;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class OtpService {

    public static final int CODE_LENGTH = 6;
    public static final int VALID_MINUTES = 5;
    public static final int MAX_TRIES = 3;
    public static final int WAIT_SECONDS = 60;

    private static final class Pending {
        final int userId;
        final String code;
        final Instant expires;
        final Instant sent;
        int triesLeft = MAX_TRIES;
        String token;

        Pending(int userId, String code, Instant sent) {
            this.userId = userId;
            this.code = code;
            this.sent = sent;
            this.expires = sent.plus(Duration.ofMinutes(VALID_MINUTES));
        }
    }

    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Clock clock = Clock.systemDefaultZone();

    private OtpService() {
    }

    public static void setClock(Clock replacement) {
        clock = replacement == null ? Clock.systemDefaultZone() : replacement;
        PENDING.clear();
    }

    public static synchronized void request(String email) {
        String key = key(email);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Enter your e-mail address.");
        }
        Validators.optionalEmail(key);
        User user = UserDAO.findForPasswordReset(key);
        if (user == null) {
            return;
        }
        Pending old = PENDING.get(key);
        Instant now = clock.instant();
        if (old != null && Duration.between(old.sent, now).getSeconds() < WAIT_SECONDS) {
            long wait = WAIT_SECONDS - Duration.between(old.sent, now).getSeconds();
            throw new IllegalArgumentException("A code was sent a moment ago. You can ask for a new one in " + wait + " seconds.");
        }
        String code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, CODE_LENGTH)));
        Mailer.send(user.getEmail(), "VITALIS password reset code",
                "Hello " + user.getFullName() + ",\n\n"
                        + "Your VITALIS password reset code is: " + code + "\n\n"
                        + "The code is valid for " + VALID_MINUTES + " minutes and can be used once.\n"
                        + "If you did not ask for it, ignore this e-mail: your password has not been changed.\n");
        PENDING.put(key, new Pending(user.getUserId(), code, now));
    }

    public static synchronized String verify(String email, String code) {
        String key = key(email);
        Pending pending = PENDING.get(key);
        if (pending == null) {
            throw new IllegalArgumentException("There is no valid code for this e-mail address. Ask for a new code.");
        }
        if (!clock.instant().isBefore(pending.expires)) {
            PENDING.remove(key);
            throw new IllegalArgumentException("The code has expired. Ask for a new code.");
        }
        String typed = code == null ? "" : code.trim();
        if (!MessageDigest.isEqual(typed.getBytes(), pending.code.getBytes())) {
            pending.triesLeft--;
            if (pending.triesLeft <= 0) {
                PENDING.remove(key);
                throw new IllegalArgumentException("Too many wrong codes. Ask for a new code.");
            }
            throw new IllegalArgumentException("Wrong code, please try again (" + pending.triesLeft + " tries left).");
        }
        if (pending.token == null) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            pending.token = java.util.HexFormat.of().formatHex(bytes);
        }
        return pending.token;
    }

    public static synchronized void setNewPassword(String email, String token, String newPassword) {
        String key = key(email);
        Pending pending = PENDING.get(key);
        if (pending == null || pending.token == null || token == null
                || !MessageDigest.isEqual(token.getBytes(), pending.token.getBytes())) {
            throw new IllegalArgumentException("The code was not confirmed. Ask for a new code.");
        }
        if (!clock.instant().isBefore(pending.expires)) {
            PENDING.remove(key);
            throw new IllegalArgumentException("The code has expired. Ask for a new code.");
        }
        PasswordUtil.checkPolicy(newPassword);
        UserDAO.resetPasswordAfterOtp(pending.userId, newPassword);
        PENDING.remove(key);
    }

    public static synchronized void confirm(String email, String code, String newPassword) {
        PasswordUtil.checkPolicy(newPassword);
        setNewPassword(email, verify(email, code), newPassword);
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
