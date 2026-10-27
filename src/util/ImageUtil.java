package util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public final class ImageUtil {

    public static final int SIZE = 256;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private ImageUtil() {
    }

    public static byte[] toAvatarPng(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Choose a picture file.");
        }
        if (file.length() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The picture is too big (at most 10 MB).");
        }
        try {
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                throw new IllegalArgumentException("This file is not a picture. Use a PNG, JPG, GIF or BMP file.");
            }
            int side = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - side) / 2;
            int y = (source.getHeight() - side) / 2;
            BufferedImage square = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = square.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(square, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("The picture could not be read: " + e.getMessage());
        }
    }
}
