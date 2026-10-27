package util;

import dao.MessageDAO;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class ChatFiles {

    public static final int PHOTO_SIDE = 1280;
    private static final long MAX_PHOTO_SOURCE = 25L * 1024 * 1024;
    private static final List<String> PHOTO_TYPES = List.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final List<String> VIDEO_TYPES = List.of("mp4", "m4v", "mov", "avi", "mkv", "webm", "wmv");

    private ChatFiles() {
    }

    public static File chooseMedia(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo or a video");
        List<String> pictures = PHOTO_TYPES.stream().map(t -> "*." + t).toList();
        List<String> videos = VIDEO_TYPES.stream().map(t -> "*." + t).toList();
        List<String> both = new java.util.ArrayList<>(pictures);
        both.addAll(videos);
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Photos and videos", both),
                new FileChooser.ExtensionFilter("Photos", pictures), new FileChooser.ExtensionFilter("Videos", videos));
        return chooser.showOpenDialog(owner);
    }

    public static boolean isPhoto(File file) {
        return PHOTO_TYPES.contains(extension(file.getName()));
    }

    public static byte[] photoBytes(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Choose a picture file.");
        }
        if (file.length() > MAX_PHOTO_SOURCE) {
            throw new IllegalArgumentException("The picture is too big (at most 25 MB).");
        }
        try {
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                throw new IllegalArgumentException("This file is not a picture. Use a PNG, JPG, GIF or BMP file.");
            }
            double scale = Math.min(1.0, (double) PHOTO_SIDE / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage flat = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = flat.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(flat, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("The picture could not be read: " + e.getMessage());
        }
    }

    public static String photoName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".jpg";
    }

    public static byte[] videoBytes(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Choose a video file.");
        }
        if (!VIDEO_TYPES.contains(extension(file.getName()))) {
            throw new IllegalArgumentException("Use a video file: " + String.join(", ", VIDEO_TYPES) + ".");
        }
        if (file.length() > MessageDAO.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The video is too big (at most " + MessageDAO.MAX_FILE_BYTES / (1024 * 1024) + " MB).");
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("The video could not be read: " + e.getMessage());
        }
    }

    public static void open(String fileName, byte[] content) {
        try {
            String ext = extension(fileName);
            File temp = File.createTempFile("vitalis-chat-", ext.isEmpty() ? "" : "." + ext);
            temp.deleteOnExit();
            Files.write(temp.toPath(), content);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IllegalStateException("This computer cannot open the file. Use Save and open it yourself.");
            }
            Desktop.getDesktop().open(temp);
        } catch (IOException e) {
            throw new IllegalStateException("The file could not be opened: " + e.getMessage());
        }
    }

    public static boolean save(Window owner, String fileName, byte[] content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save file");
        chooser.setInitialFileName(fileName);
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return false;
        }
        try {
            Files.write(target.toPath(), content);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("The file could not be saved: " + e.getMessage());
        }
    }

    public static String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}
