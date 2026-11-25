
package nera.Core;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GifSpritePlayer {

    private final List<BufferedImage> frames = new ArrayList<>();
    private final List<Float> durations = new ArrayList<>();

    private final Texture2D texture;
    private final Geometry geometry;
    private final AWTLoader awtLoader = new AWTLoader();

    private int currentFrame = 0;
    private float elapsed = 0f;

    public GifSpritePlayer(
            AssetManager assetManager,
            Path gifPath,
            String name,
            float targetHeight
    ) throws IOException {

        if (targetHeight <= 0f) {
            throw new IllegalArgumentException(
                    "Sprite height must be positive."
            );
        }

        loadGif(gifPath);

        BufferedImage firstFrame = frames.get(0);

        float aspectRatio =
                (float) firstFrame.getWidth()
                / firstFrame.getHeight();

        float width = targetHeight * aspectRatio;

        texture = new Texture2D();
        texture.setImage(
                awtLoader.load(firstFrame, false)
        );

        Material material = new Material(
                assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md"
        );

        material.setTexture("ColorMap", texture);
        material.setTransparent(true);

        material.getAdditionalRenderState().setBlendMode(
                RenderState.BlendMode.Alpha
        );

        material.getAdditionalRenderState().setDepthWrite(false);

        geometry = new Geometry(
                name,
                new Quad(width, targetHeight)
        );

        geometry.setMaterial(material);
        geometry.setQueueBucket(
                RenderQueue.Bucket.Transparent
        );

        geometry.setLocalTranslation(-width / 2f, 0f, 0f);
    }

    private void loadGif(Path path) throws IOException {

        try (ImageInputStream input =
                     ImageIO.createImageInputStream(path.toFile())) {

            if (input == null) {
                throw new IOException(
                        "Cannot open GIF: " + path
                );
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IOException(
                        "No GIF reader available: " + path
                );
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input, false, false);

                int count = reader.getNumImages(true);

                if (count <= 0) {
                    throw new IOException(
                            "GIF contains no frames: " + path
                    );
                }

                for (int i = 0; i < count; i++) {

                    BufferedImage frame = reader.read(i);

                    if (frame == null) {
                        throw new IOException(
                                "Cannot read GIF frame " + i
                        );
                    }

                    frames.add(frame);

                    float duration = readFrameDuration(
                            reader.getImageMetadata(i)
                    );

                    durations.add(duration);
                }

            } finally {
                reader.dispose();
            }
        }

        System.out.println(
                "Nera: GIF loaded: "
                        + path.getFileName()
                        + " (" + frames.size() + " frames)"
        );
    }

    private float readFrameDuration(IIOMetadata metadata) {

        try {

            Node root = metadata.getAsTree(
                    "javax_imageio_gif_image_1.0"
            );

            Node control = findNode(
                    root,
                    "GraphicControlExtension"
            );

            if (control != null) {

                NamedNodeMap attributes =
                        control.getAttributes();

                Node delay = attributes.getNamedItem(
                        "delayTime"
                );

                if (delay != null) {

                    int hundredths = Integer.parseInt(
                            delay.getNodeValue()
                    );

                    return Math.max(
                            hundredths / 100f,
                            0.02f
                    );
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "Nera: Could not read GIF frame duration."
            );
        }

        return 0.1f;
    }

    private Node findNode(Node parent, String name) {

        if (parent == null) {
            return null;
        }

        if (name.equals(parent.getNodeName())) {
            return parent;
        }

        for (Node child = parent.getFirstChild();
             child != null;
             child = child.getNextSibling()) {

            Node result = findNode(child, name);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public void update(float tpf) {

        if (frames.size() <= 1) {
            return;
        }

        elapsed += tpf;

        while (elapsed >= durations.get(currentFrame)) {

            elapsed -= durations.get(currentFrame);

            currentFrame++;

            if (currentFrame >= frames.size()) {
                currentFrame = 0;
            }

            texture.setImage(
                    awtLoader.load(
                            frames.get(currentFrame),
                            false
                    )
            );
        }
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public void reset() {

        currentFrame = 0;
        elapsed = 0f;

        texture.setImage(
                awtLoader.load(frames.get(0), false)
        );
    }
}
