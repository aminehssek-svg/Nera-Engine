
package nera.Core;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

public class Player2DCompiler {

    private static final String MAGIC = "NERA_PLAYER2D";
    private static final int VERSION = 1;

    private static class Frame {
        byte[] image;
        int duration;

        Frame(byte[] image, int duration) {
            this.image = image;
            this.duration = duration;
        }
    }

    private static class GifData {
        int width;
        int height;
        List<Frame> frames = new ArrayList<>();
    }

    public static List<Path> compileProject(Path projectDirectory)
            throws IOException {

        Path root = projectDirectory.toAbsolutePath().normalize();

        if (!Files.isDirectory(root)) {
            throw new IOException("Invalid project directory.");
        }

        Path compiledRoot = root.resolve("Compiled");

        Files.createDirectories(compiledRoot);

        List<Path> directories = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isDirectory)
                .filter(path -> !path.startsWith(compiledRoot))
                .forEach(directories::add);
        }

        directories.sort(Comparator.comparing(Path::toString));

        List<Path> generated = new ArrayList<>();

        for (Path directory : directories) {

            List<Path> gifs = getGifFiles(directory);

            if (gifs.isEmpty()) {
                continue;
            }

            Path relativeParent;

            if (directory.equals(root)) {
                relativeParent = Paths.get("");
            } else {
                relativeParent = root.relativize(directory.getParent());
            }

            Path outputDirectory = compiledRoot.resolve(relativeParent);

            Files.createDirectories(outputDirectory);

            String playerName = directory.getFileName().toString();

            Path output = outputDirectory.resolve(
                playerName + ".Player2D"
            );

            compilePlayer(directory, gifs, output);

            generated.add(output);
        }

        return generated;
    }

    private static List<Path> getGifFiles(Path directory)
            throws IOException {

        List<Path> gifs = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {

            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName()
                    .toString()
                    .toLowerCase()
                    .endsWith(".gif"))
                .forEach(gifs::add);
        }

        gifs.sort(Comparator.comparing(
            path -> path.getFileName().toString(),
            String.CASE_INSENSITIVE_ORDER
        ));

        return gifs;
    }

    private static void compilePlayer(
        Path directory,
        List<Path> gifs,
        Path output
    ) throws IOException {

        Path temporary = output.resolveSibling(
            output.getFileName() + ".tmp"
        );

        try {

            try (DataOutputStream data = new DataOutputStream(
                    Files.newOutputStream(temporary))) {

                data.writeUTF(MAGIC);
                data.writeInt(VERSION);
                data.writeUTF(directory.getFileName().toString());
                data.writeInt(gifs.size());

                for (Path gif : gifs) {

                    GifData animation = readGif(gif);

                    String filename = gif.getFileName().toString();

                    String animationName = filename.substring(
                        0,
                        filename.lastIndexOf('.')
                    );

                    data.writeUTF(animationName);
                    data.writeInt(animation.width);
                    data.writeInt(animation.height);
                    data.writeInt(animation.frames.size());

                    for (Frame frame : animation.frames) {

                        data.writeInt(frame.duration);
                        data.writeInt(frame.image.length);
                        data.write(frame.image);
                    }
                }
            }

            try {
                Files.move(
                    temporary,
                    output,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );

            } catch (IOException ex) {

                Files.move(
                    temporary,
                    output,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static GifData readGif(Path path) throws IOException {

        try (ImageInputStream input =
                ImageIO.createImageInputStream(path.toFile())) {

            if (input == null) {
                throw new IOException("Cannot read GIF: " + path);
            }

            Iterator<ImageReader> readers =
                ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IOException("Unsupported GIF: " + path);
            }

            ImageReader reader = readers.next();

            try {

                reader.setInput(input, false, false);

                int frameCount = reader.getNumImages(true);

                if (frameCount <= 0) {
                    throw new IOException("GIF has no frames: " + path);
                }

                GifData result = new GifData();

                IIOMetadata streamMetadata =
                    reader.getStreamMetadata();

                Node streamRoot = streamMetadata == null
                    ? null
                    : streamMetadata.getAsTree(
                        "javax_imageio_gif_stream_1.0"
                    );

                Node screen = findNode(
                    streamRoot,
                    "LogicalScreenDescriptor"
                );

                result.width = getIntAttribute(
                    screen,
                    "logicalScreenWidth",
                    reader.getWidth(0)
                );

                result.height = getIntAttribute(
                    screen,
                    "logicalScreenHeight",
                    reader.getHeight(0)
                );

                BufferedImage canvas = new BufferedImage(
                    result.width,
                    result.height,
                    BufferedImage.TYPE_INT_ARGB
                );

                String previousDisposal = "none";
                BufferedImage previousCanvas = null;

                int previousX = 0;
                int previousY = 0;
                int previousWidth = 0;
                int previousHeight = 0;

                for (int i = 0; i < frameCount; i++) {

                    IIOMetadata metadata = reader.getImageMetadata(i);

                    Node metadataRoot = metadata.getAsTree(
                        "javax_imageio_gif_image_1.0"
                    );

                    Node descriptor = findNode(
                        metadataRoot,
                        "ImageDescriptor"
                    );

                    Node control = findNode(
                        metadataRoot,
                        "GraphicControlExtension"
                    );

                    int x = getIntAttribute(
                        descriptor, "imageLeftPosition", 0
                    );

                    int y = getIntAttribute(
                        descriptor, "imageTopPosition", 0
                    );

                    int width = getIntAttribute(
                        descriptor, "imageWidth", reader.getWidth(i)
                    );

                    int height = getIntAttribute(
                        descriptor, "imageHeight", reader.getHeight(i)
                    );

                    // Apply the previous frame's disposal.
                    if (i > 0) {

                        if ("restoreToBackgroundColor".equals(
                                previousDisposal)) {

                            Graphics2D clear = canvas.createGraphics();

                            clear.setComposite(AlphaComposite.Clear);

                            clear.fillRect(
                                previousX,
                                previousY,
                                previousWidth,
                                previousHeight
                            );

                            clear.dispose();

                        } else if ("restoreToPrevious".equals(
                                previousDisposal)
                                && previousCanvas != null) {

                            canvas = copyImage(previousCanvas);
                        }
                    }

                    String disposal = getStringAttribute(
                        control,
                        "disposalMethod",
                        "none"
                    );

                    previousCanvas =
                        "restoreToPrevious".equals(disposal)
                            ? copyImage(canvas)
                            : null;

                    BufferedImage frame = reader.read(i);

                    Graphics2D graphics = canvas.createGraphics();

                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(frame, x, y, null);

                    graphics.dispose();

                    ByteArrayOutputStream bytes =
                        new ByteArrayOutputStream();

                    if (!ImageIO.write(canvas, "png", bytes)) {
                        throw new IOException(
                            "Unable to encode GIF frame: " + path
                        );
                    }

                    int delay = getIntAttribute(
                        control,
                        "delayTime",
                        10
                    );

                    // GIF delays are in hundredths of a second.
                    int duration = Math.max(10, delay * 10);

                    result.frames.add(
                        new Frame(bytes.toByteArray(), duration)
                    );

                    previousDisposal = disposal;
                    previousX = x;
                    previousY = y;
                    previousWidth = width;
                    previousHeight = height;
                }

                return result;

            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage copyImage(BufferedImage source) {

        BufferedImage copy = new BufferedImage(
            source.getWidth(),
            source.getHeight(),
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();

        return copy;
    }

    private static Node findNode(Node parent, String name) {

        if (parent == null) {
            return null;
        }

        if (name.equals(parent.getNodeName())) {
            return parent;
        }

        for (Node child = parent.getFirstChild();
                child != null;
                child = child.getNextSibling()) {

            Node found = findNode(child, name);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private static int getIntAttribute(
        Node node,
        String name,
        int fallback
    ) {

        String value = getStringAttribute(node, name, null);

        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String getStringAttribute(
        Node node,
        String name,
        String fallback
    ) {

        if (node == null) {
            return fallback;
        }

        NamedNodeMap attributes = node.getAttributes();

        if (attributes == null) {
            return fallback;
        }

        Node attribute = attributes.getNamedItem(name);

        return attribute == null
            ? fallback
            : attribute.getNodeValue();
    }
}
