
package nera.Core;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.io.File;
import java.nio.file.Path;

public class ModelViewer {

    private final AssetManager assetManager;
    private final Node viewerRoot;

    private Spatial currentModel;

    private static final float TARGET_SIZE = 4f;

    public ModelViewer(AssetManager assetManager, Node viewerRoot) {
        this.assetManager = assetManager;
        this.viewerRoot = viewerRoot;
    }

    public Spatial loadModel(File file) {

        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException(
                "Model file does not exist."
            );
        }

        clear();

        Path path = file.toPath()
            .toAbsolutePath()
            .normalize();

        Path parent = path.getParent();

        if (parent != null) {
            assetManager.registerLocator(
                parent.toString(),
                FileLocator.class
            );
        }

        Spatial model = assetManager.loadModel(
            path.getFileName().toString()
        );

        if (model == null) {
            throw new IllegalStateException(
                "Unable to load model: " + file.getName()
            );
        }

        model.setName(file.getName());

        currentModel = model;

        viewerRoot.attachChild(currentModel);

        centerModel(currentModel);

        viewerRoot.updateGeometricState();

        System.out.println(
            "ModelViewer: Model loaded: " + file.getName()
        );

        printModelInfo(currentModel);

        return currentModel;
    }

    public void centerModel(Spatial model) {

        if (model == null) {
            return;
        }

        model.setLocalScale(1f);
        model.setLocalTranslation(Vector3f.ZERO);

        model.updateGeometricState();

        BoundingVolume bounds = model.getWorldBound();

        if (!(bounds instanceof BoundingBox)) {
            return;
        }

        BoundingBox box = (BoundingBox) bounds;

        Vector3f center = new Vector3f();
        box.getCenter(center);

        float largest = Math.max(
            box.getXExtent(),
            Math.max(box.getYExtent(), box.getZExtent())
        );

        if (largest <= 0.0001f) {
            return;
        }

        float scale = TARGET_SIZE / largest;

        model.setLocalScale(scale);

        model.setLocalTranslation(
            center.negate().mult(scale)
        );

        model.updateGeometricState();
    }

    private void printModelInfo(Spatial spatial) {

        System.out.println("Spatial: " + spatial.getName());

        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                printModelInfo(child);
            }
        }

        if (spatial instanceof Geometry geometry) {
            System.out.println("Geometry: " + geometry.getName());
            System.out.println("Material: " + geometry.getMaterial());
            System.out.println("Mesh: " + geometry.getMesh());
        }
    }

    public void clear() {

        if (currentModel != null) {
            currentModel.removeFromParent();
            currentModel = null;
        }
    }

    public Spatial getCurrentModel() {
        return currentModel;
    }

    public Node getViewerRoot() {
        return viewerRoot;
    }
}
