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
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompiledPlayer2D {

    private static final String MAGIC = "NERA_PLAYER2D";
    private static final int VERSION = 1;

    private static class Frame {
        BufferedImage image;
        float duration;
        Frame(BufferedImage image, float duration) {
            this.image = image;
            this.duration = duration;
        }
    }

    private static class Animation {
        String name;
        int width;
        int height;
        List<Frame> frames = new ArrayList<>();
    }

    private final Map<String, Animation> animations = new LinkedHashMap<>();
    private final Texture2D texture;
    private final Geometry geometry;
    private final AWTLoader loader = new AWTLoader();
    private Animation currentAnimation;
    private int currentFrame;
    private float elapsed;
    private final float targetHeight;

    public CompiledPlayer2D(AssetManager assetManager, Path file, String name, float targetHeight) throws IOException {
        if (targetHeight <= 0f) throw new IllegalArgumentException("Sprite height must be positive.");
        this.targetHeight = targetHeight;
        load(file);
        if (animations.isEmpty()) throw new IOException("Player2D contains no animations.");

        currentAnimation = animations.values().iterator().next();
        BufferedImage first = currentAnimation.frames.get(0).image;
        texture = new Texture2D();
        texture.setImage(loader.load(flipVertically(first), false));

        float ratio = (float) first.getWidth() / first.getHeight();
        float width = targetHeight * ratio;
        geometry = new Geometry(name, new Quad(width, targetHeight));

        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.setTransparent(true);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        geometry.setLocalTranslation(-width / 2f, 0f, 0f);
    }

    private BufferedImage flipVertically(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = flipped.createGraphics();
        AffineTransform transform = new AffineTransform();
        transform.translate(0, image.getHeight());
        transform.scale(1, -1);
        graphics.drawImage(image, transform, null);
        graphics.dispose();
        return flipped;
    }

    private void load(Path file) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            String magic = input.readUTF();
            if (!MAGIC.equals(magic)) throw new IOException("Invalid Player2D file.");
            int version = input.readInt();
            if (version != VERSION) throw new IOException("Unsupported Player2D version: " + version);
            String playerName = input.readUTF();
            int animationCount = input.readInt();
            if (animationCount < 1 || animationCount > 1000) throw new IOException("Invalid animation count.");

            for (int a = 0; a < animationCount; a++) {
                Animation animation = new Animation();
                animation.name = input.readUTF();
                animation.width = input.readInt();
                animation.height = input.readInt();
                int frameCount = input.readInt();
                if (animation.width <= 0 || animation.height <= 0 || frameCount <= 0 || frameCount > 100000) {
                    throw new IOException("Invalid animation data: " + animation.name);
                }
                for (int f = 0; f < frameCount; f++) {
                    int durationMs = input.readInt();
                    int imageLength = input.readInt();
                    if (durationMs < 0 || imageLength <= 0 || imageLength > 100_000_000) {
                        throw new IOException("Invalid frame data.");
                    }
                    byte[] bytes = input.readNBytes(imageLength);
                    if (bytes.length != imageLength) throw new IOException("Incomplete frame data.");
                    BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    if (image == null) throw new IOException("Invalid PNG frame.");
                    animation.frames.add(new Frame(image, Math.max(durationMs / 1000f, 0.01f)));
                }
                animations.put(animation.name, animation);
            }
            System.out.println("Nera: Player2D loaded: " + playerName + " (" + animations.size() + " animations)");
        }
    }

    public List<String> getAnimationNames() {
        return new ArrayList<>(animations.keySet());
    }

    public boolean hasAnimation(String animationName) {
        return animations.containsKey(animationName);
    }

    public String getCurrentAnimationName() {
        return currentAnimation == null ? "" : currentAnimation.name;
    }

    public void play(String animationName) {
        Animation animation = animations.get(animationName);
        if (animation == null || animation == currentAnimation) return;
        currentAnimation = animation;
        currentFrame = 0;
        elapsed = 0f;
        updateTexture();
        updateGeometrySize();
    }

    public void update(float tpf) {
        if (currentAnimation == null || currentAnimation.frames.size() <= 1) return;
        elapsed += tpf;
        while (elapsed >= currentAnimation.frames.get(currentFrame).duration) {
            elapsed -= currentAnimation.frames.get(currentFrame).duration;
            currentFrame++;
            if (currentFrame >= currentAnimation.frames.size()) currentFrame = 0;
            updateTexture();
        }
    }

    private void updateTexture() {
        BufferedImage image = currentAnimation.frames.get(currentFrame).image;
        texture.setImage(loader.load(flipVertically(image), false));
    }

    private void updateGeometrySize() {
        float ratio = (float) currentAnimation.width / currentAnimation.height;
        float width = targetHeight * ratio;
        geometry.setMesh(new Quad(width, targetHeight));
        geometry.setLocalTranslation(-width / 2f, 0f, 0f);
    }

    public Geometry getGeometry() { return geometry; }

    public void reset() {
        currentFrame = 0;
        elapsed = 0f;
        updateTexture();
    }
}
