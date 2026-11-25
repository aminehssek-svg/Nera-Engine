
package nera.Core;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import nera.DynaScript.Compiler.Main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

public class EngineCore extends SimpleApplication {

    // EDITOR SCENE
    private Node sceneRoot;
    private Node sceneContent;
    private Node viewerRoot;

    private ModelViewer modelViewer;
    private AnimationViewer animationViewer;
    private CompiledPlayer2D player2DPreview;

    // LEGACY GIF PLAYERS
    private final List<GifSpritePlayer> gifPlayers = new ArrayList<>();

    // GAME WINDOW
    private EngineCore gameInstance;

    // DYNASCRIPT PLAYER RUNTIME
    private CompiledPlayer2D runtimePlayer2D;
    private Spatial runtimePlayer3D;
    private AnimationViewer runtimePlayer3DAnimations;
    private Node runtimePlayerNode;

    private Main.PlayerRuntimeConfig runtimePlayerConfig;

    private final List<Spatial> runtimeEnemies = new ArrayList<>();
    private final List<Spatial> runtimeCollectibles = new ArrayList<>();

    private boolean moveLeft;
    private boolean moveRight;
    private boolean jumpRequested;
    private boolean grounded = true;
    private boolean collectedObject;
    private boolean playerDead;
    private boolean collisionEnabled;

    private float verticalVelocity;
    private float runtimeFloorY;
    private float hurtTimer;

    private String currentRuntimeAnimation = "";

    private static final String INPUT_LEFT = "NERA_PLAYER_LEFT";
    private static final String INPUT_RIGHT = "NERA_PLAYER_RIGHT";
    private static final String INPUT_JUMP = "NERA_PLAYER_JUMP";

    private final ActionListener runtimeInputListener = (name, pressed, tpf) -> {
        if (INPUT_LEFT.equals(name)) {
            moveLeft = pressed;
        } else if (INPUT_RIGHT.equals(name)) {
            moveRight = pressed;
        } else if (INPUT_JUMP.equals(name) && pressed) {
            jumpRequested = true;
        }
    };

    // --------------------------------------------------
    // INITIALIZATION
    // --------------------------------------------------

    @Override
    public void simpleInitApp() {

        sceneRoot = new Node("NeraScene");
        sceneContent = new Node("Scene3DContent");
        viewerRoot = new Node("ModelViewerRoot");

        sceneRoot.attachChild(sceneContent);
        sceneRoot.attachChild(viewerRoot);
        rootNode.attachChild(sceneRoot);

        viewPort.setBackgroundColor(
            new ColorRGBA(0.12f, 0.13f, 0.16f, 1f)
        );

        cam.setLocation(new Vector3f(0f, 1f, 6f));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        cam.update();

        flyCam.setMoveSpeed(10f);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.7f));
        rootNode.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(
            new Vector3f(-1f, -2f, -1f).normalizeLocal()
        );
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);

        modelViewer = new ModelViewer(assetManager, viewerRoot);
        animationViewer = new AnimationViewer();

        System.out.println("Nera Engine 3D initialized.");
    }

    @Override
    public void simpleUpdate(float tpf) {

        for (GifSpritePlayer player : gifPlayers) {
            player.update(tpf);
        }

        if (player2DPreview != null) {
            player2DPreview.update(tpf);
        }

        if (runtimePlayer2D != null) {
            runtimePlayer2D.update(tpf);
        }

        if (runtimePlayerNode != null && runtimePlayerConfig != null) {
            updateRuntimePlayer(tpf);
        }
    }

    // --------------------------------------------------
    // MODEL PREVIEW
    // --------------------------------------------------

    
public void previewModel(File file, Consumer<List<String>> callback) {

    if (file == null || !file.isFile()) {
        return;
    }

    enqueue(() -> {

        try {

            clearPreviewInternal();
            sceneContent.detachAllChildren();
            gifPlayers.clear();

            String fileName = file.getName().toLowerCase();

            // PLAYER 2D
            if (fileName.endsWith(".player2d")) {

                player2DPreview = new CompiledPlayer2D(
                    assetManager,
                    file.toPath(),
                    file.getName(),
                    2f
                );

                Spatial sprite = player2DPreview.getGeometry();

                viewerRoot.attachChild(sprite);
                sprite.updateGeometricState();

                focusCameraOnModel(sprite);

                List<String> animations =
                    player2DPreview.getAnimationNames();

                System.out.println(
                    "Player2D loaded: " + file.getName()
                );

                if (callback != null) {
                    SwingUtilities.invokeLater(
                        () -> callback.accept(animations)
                    );
                }

                return null;
            }

            // 3D MODEL
            Path modelPath = file.toPath()
                .toAbsolutePath()
                .normalize();

            if (!Files.isRegularFile(modelPath)) {
                throw new IOException(
                    "Model file does not exist: " + modelPath
                );
            }

            String assetPath = registerAndGetAssetPath(modelPath);

            System.out.println(
                "Nera: Loading 3D model: " + modelPath
            );

            Spatial model = assetManager.loadModel(assetPath);

            if (model == null) {
                throw new IOException(
                    "The 3D model loader returned null."
                );
            }

            model.setLocalTranslation(Vector3f.ZERO);
            model.setLocalScale(1f);

            viewerRoot.attachChild(model);

            model.updateGeometricState();

            List<String> animations =
                animationViewer.detectAnimations(model);

            focusCameraOnModel(model);

            System.out.println(
                "Nera: 3D model loaded: " + file.getName()
            );

            System.out.println(
                "Nera: Animations detected: " + animations
            );

            if (callback != null) {
                SwingUtilities.invokeLater(
                    () -> callback.accept(animations)
                );
            }

        } catch (Exception e) {

            System.err.println(
                "Nera: Failed to load model: " + file
            );

            e.printStackTrace();

            if (callback != null) {
                SwingUtilities.invokeLater(
                    () -> callback.accept(new ArrayList<>())
                );
            }
        }

        return null;
    });
}


    private void focusCameraOnModel(Spatial model) {

        if (model == null) return;

        model.updateGeometricState();

        BoundingVolume bounds = model.getWorldBound();

        if (!(bounds instanceof BoundingBox)) {
            System.out.println("Camera: Model has no BoundingBox.");
            return;
        }

        BoundingBox box = (BoundingBox) bounds;

        Vector3f center = box.getCenter().clone();

        float extent = Math.max(
            box.getXExtent(),
            Math.max(box.getYExtent(), box.getZExtent())
        );

        if (extent <= 0.0001f) {
            System.out.println("Camera: Invalid model dimensions.");
            return;
        }

        float distance = extent * 3.5f + 1f;

        Vector3f cameraPosition =
            center.add(0f, extent * 0.35f, distance);

        cam.setLocation(cameraPosition);
        cam.lookAt(center, Vector3f.UNIT_Y);
        cam.update();
    }

    public void playAnimation(String animationName) {

        if (animationName == null) return;

        enqueue(() -> {

            if (player2DPreview != null) {
                player2DPreview.play(animationName);
                return null;
            }

            if (animationViewer != null) {
                animationViewer.play(animationName);
            }

            return null;
        });
    }

    public void clearPreview() {
        enqueue(() -> {
            clearPreviewInternal();
            return null;
        });
    }

   
private void clearPreviewInternal() {

    if (player2DPreview != null) {
        player2DPreview = null;
    }

    if (modelViewer != null) {
        modelViewer.clear();
    }

    if (animationViewer != null) {
        animationViewer.clear();
    }

    if (viewerRoot != null) {
        viewerRoot.detachAllChildren();
    }

    viewerRoot.updateGeometricState();
}

    public void setMouseCaptured(boolean captured) {

        enqueue(() -> {

            if (inputManager == null || flyCam == null) {
                return null;
            }

            flyCam.setEnabled(captured);
            inputManager.setCursorVisible(!captured);

            System.out.println(
                captured
                    ? "Nera: Mouse capture enabled"
                    : "Nera: Mouse capture disabled"
            );

            return null;
        });
    }

    // --------------------------------------------------
    // GAME PLAY / STOP
    // --------------------------------------------------

    
public void startGame(
    Scene3DManager scene,
    File projectDirectory,
    Consumer<String> callback
) {
    if (scene == null || projectDirectory == null) {
        if (callback != null) {
            SwingUtilities.invokeLater(
                () -> callback.accept("Scene or project directory is missing.")
            );
        }
        return;
    }

    if (gameInstance != null) {
        if (callback != null) {
            SwingUtilities.invokeLater(
                () -> callback.accept("Game is already running.")
            );
        }
        return;
    }

    try {
        EngineCore runtime = new EngineCore();

        AppSettings gameSettings = new AppSettings(true);
        gameSettings.setTitle("Nera Game");
        gameSettings.setResolution(1280, 720);
        gameSettings.setVSync(true);
        gameSettings.setSamples(4);

        runtime.setSettings(gameSettings);
        runtime.setShowSettings(false);

        gameInstance = runtime;

        runtime.start();

        runtime.loadScene(scene, projectDirectory, error -> {
            if (error != null) {
                System.err.println(
                    "Could not load game scene: " + error
                );

                // Ne pas arrêter immédiatement GLFW ici.
                if (callback != null) {
                    callback.accept(error);
                }
            } else {
                System.out.println(
                    "Nera Game started successfully."
                );

                if (callback != null) {
                    callback.accept(null);
                }
            }
        });

    } catch (Exception e) {
        e.printStackTrace();

        gameInstance = null;

        if (callback != null) {
            SwingUtilities.invokeLater(
                () -> callback.accept(e.getMessage())
            );
        }
    }
}

public void stopGame() {
    if (gameInstance == null) {
        return;
    }

    EngineCore oldGame = gameInstance;
    gameInstance = null;

    try {
        oldGame.stop(true);
        System.out.println("Nera Game stopped.");
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public boolean isGameRunning() {
    return gameInstance != null;
}


    // --------------------------------------------------
    // SCENE LOADING
    // --------------------------------------------------

    public void loadScene(
        Scene3DManager scene,
        File projectDirectory,
        Consumer<String> callback
    ) {

        if (scene == null || projectDirectory == null) {

            if (callback != null) {
                SwingUtilities.invokeLater(
                    () -> callback.accept("Scene or project directory is missing.")
                );
            }

            return;
        }

        enqueue(() -> {

            clearPreviewInternal();
            sceneContent.detachAllChildren();
            gifPlayers.clear();
            resetRuntimePlayer();

            try {

                Path projectRoot = projectDirectory.toPath()
                    .toAbsolutePath()
                    .normalize();

                // MAP
                if (!scene.getMapModel().isBlank()) {

                    Path mapPath = resolveModelPath(
                        scene.getMapModel(),
                        scene,
                        projectRoot
                    );

                    Spatial map = assetManager.loadModel(
                        registerAndGetAssetPath(mapPath)
                    );

                    map.setName("NeraSceneMap");
                    sceneContent.attachChild(map);
                }

                // SCENE OBJECTS
                for (Scene3DManager.SceneObject object : scene.getObjects()) {

                    Node instance = new Node(object.getName());

                    instance.setLocalTranslation(
                        (float) object.getX(),
                        (float) object.getY(),
                        (float) object.getZ()
                    );

                    // PLAYER
                    if (Scene3DManager.CATEGORY_PLAYER_2D.equals(
                        object.getCategory()
                    )) {

                        Path assetPath = resolveModelPath(
                            object.getModel(),
                            scene,
                            projectRoot
                        );

                        Path compiledPlayerFile = findCompiledPlayer2D(
                            assetPath,
                            projectRoot,
                            object.getName()
                        );

                        Path scriptFile = findMatchingDynaScript(
                            compiledPlayerFile,
                            projectRoot,
                            object.getName()
                        );

                        runtimePlayerNode = instance;
                        runtimeFloorY = (float) object.getY();

                        if (scriptFile != null) {

                            runtimePlayerConfig =
                                Main.loadPlayerRuntimeConfig(scriptFile);

                            collisionEnabled =
                                runtimePlayerConfig.animationBasedCollision;

                            configureRuntimeInputs(runtimePlayerConfig);

                            System.out.println(
                                "DynaScript attached: "
                                + scriptFile.getFileName()
                            );

                            System.out.println(
                                "DynaScript animation rules: "
                                + runtimePlayerConfig.animations.keySet()
                            );

                        } else {

                            runtimePlayerConfig = null;
                            configureRuntimeInputs(null);

                            System.out.println(
                                "No matching .dyna script found for "
                                + object.getName()
                            );
                        }

                        // PRIORITY 1: 3D MODEL FROM DYNASCRIPT
                        if (runtimePlayerConfig != null
                            && runtimePlayerConfig.animationModel != null
                            && !runtimePlayerConfig.animationModel.isBlank()) {

                            Path modelPath = resolveModelPath(
                                runtimePlayerConfig.animationModel,
                                scene,
                                projectRoot
                            );

                            runtimePlayer3D = assetManager.loadModel(
                                registerAndGetAssetPath(modelPath)
                            );

                            instance.attachChild(runtimePlayer3D);

                            runtimePlayer3DAnimations = new AnimationViewer();

                            List<String> detectedAnimations =
                                runtimePlayer3DAnimations.detectAnimations(
                                    runtimePlayer3D
                                );

                            System.out.println(
                                "Nera: 3D Player loaded: "
                                + modelPath.getFileName()
                            );

                            System.out.println(
                                "Nera: 3D animations: "
                                + detectedAnimations
                            );

                            if (detectedAnimations.isEmpty()) {
                                System.out.println(
                                    "Nera warning: No 3D animations detected."
                                );
                            }

                            sceneContent.attachChild(instance);
                            continue;
                        }

                        // PRIORITY 2: COMPILED PLAYER2D
                        if (compiledPlayerFile != null) {

                            runtimePlayer2D = new CompiledPlayer2D(
                                assetManager,
                                compiledPlayerFile,
                                object.getName(),
                                1.5f
                            );

                            instance.attachChild(
                                runtimePlayer2D.getGeometry()
                            );

                            sceneContent.attachChild(instance);
                            continue;
                        }

                        // PRIORITY 3: LEGACY GIF
                        GifSpritePlayer gifPlayer = new GifSpritePlayer(
                            assetManager,
                            assetPath,
                            object.getName(),
                            1f
                        );

                        instance.attachChild(gifPlayer.getGeometry());
                        gifPlayers.add(gifPlayer);

                        sceneContent.attachChild(instance);
                        continue;
                    }

                    // OTHER OBJECTS
                    Path assetPath = resolveModelPath(
                        object.getModel(),
                        scene,
                        projectRoot
                    );

                    Spatial model = assetManager.loadModel(
                        registerAndGetAssetPath(assetPath)
                    );

                    instance.attachChild(model);

                    if ("Enemy".equalsIgnoreCase(object.getCategory())) {
                        runtimeEnemies.add(instance);
                    }

                    if ("Object".equalsIgnoreCase(object.getCategory())) {
                        runtimeCollectibles.add(instance);
                    }

                    sceneContent.attachChild(instance);
                }

                sceneContent.updateGeometricState();

                if (callback != null) {
                    SwingUtilities.invokeLater(
                        () -> callback.accept(null)
                    );
                }

            } catch (Exception e) {

                e.printStackTrace();

                if (callback != null) {
                    SwingUtilities.invokeLater(
                        () -> callback.accept(e.getMessage())
                    );
                }
            }

            return null;
        });
    }

    // --------------------------------------------------
    // PLAYER FILE SEARCH
    // --------------------------------------------------

    private Path findCompiledPlayer2D(
        Path original,
        Path projectRoot,
        String objectName
    ) {

        List<Path> candidates = new ArrayList<>();

        candidates.add(original);

        Path parent = original.getParent();

        String base = stripExtension(
            original.getFileName().toString()
        );

        if (parent != null) {
            candidates.add(parent.resolve(base + ".Player2D"));
        }

        candidates.add(projectRoot.resolve(objectName + ".Player2D"));
        candidates.add(projectRoot.resolve(base + ".Player2D"));

        if (Files.isDirectory(original)) {
            candidates.add(
                original.resolveSibling(
                    original.getFileName() + ".Player2D"
                )
            );
        }

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }

        return null;
    }

    private Path findMatchingDynaScript(
        Path playerFile,
        Path projectRoot,
        String objectName
    ) {

        List<Path> candidates = new ArrayList<>();

        if (playerFile != null) {

            String base = stripExtension(
                playerFile.getFileName().toString()
            );

            candidates.add(
                playerFile.resolveSibling(base + ".dyna")
            );
        }

        candidates.add(
            projectRoot.resolve(objectName + ".dyna")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }

        return null;
    }

    private String stripExtension(String name) {

        int dot = name.lastIndexOf('.');

        return dot > 0
            ? name.substring(0, dot)
            : name;
    }

    // --------------------------------------------------
    // INPUTS
    // --------------------------------------------------

    private void configureRuntimeInputs(
        Main.PlayerRuntimeConfig config
    ) {

        if (inputManager == null) return;

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);

        removeRuntimeMapping(INPUT_LEFT);
        removeRuntimeMapping(INPUT_RIGHT);
        removeRuntimeMapping(INPUT_JUMP);

        Set<Integer> leftKeys = new LinkedHashSet<>();
        Set<Integer> rightKeys = new LinkedHashSet<>();
        Set<Integer> jumpKeys = new LinkedHashSet<>();

        if (config != null) {

            addConfiguredKeys(
                leftKeys,
                config.inputs.get("Left")
            );

            addConfiguredKeys(
                rightKeys,
                config.inputs.get("Right")
            );

            addConfiguredKeys(
                jumpKeys,
                config.inputs.get("Jump")
            );
        }

        if (leftKeys.isEmpty()) {
            leftKeys.add(KeyInput.KEY_A);
            leftKeys.add(KeyInput.KEY_LEFT);
        }

        if (rightKeys.isEmpty()) {
            rightKeys.add(KeyInput.KEY_D);
            rightKeys.add(KeyInput.KEY_RIGHT);
        }

        if (jumpKeys.isEmpty()) {
            jumpKeys.add(KeyInput.KEY_SPACE);
        }

        addMapping(INPUT_LEFT, leftKeys);
        addMapping(INPUT_RIGHT, rightKeys);
        addMapping(INPUT_JUMP, jumpKeys);

        inputManager.addListener(
            runtimeInputListener,
            INPUT_LEFT,
            INPUT_RIGHT,
            INPUT_JUMP
        );
    }

    private void addConfiguredKeys(
        Set<Integer> target,
        List<String> keys
    ) {

        if (keys == null) return;

        for (String key : keys) {

            Integer code = keyCode(key);

            if (code != null) {
                target.add(code);
            }
        }
    }

    private Integer keyCode(String raw) {

        String key = raw.trim().toUpperCase();

        switch (key) {

            case "Q": return KeyInput.KEY_Q;
            case "A": return KeyInput.KEY_A;
            case "D": return KeyInput.KEY_D;
            case "Z": return KeyInput.KEY_Z;
            case "S": return KeyInput.KEY_S;

            case "SPACE": return KeyInput.KEY_SPACE;

            case "LEFT":
            case "⬅":
            case "⬅️":
                return KeyInput.KEY_LEFT;

            case "RIGHT":
            case "➡":
            case "➡️":
                return KeyInput.KEY_RIGHT;

            case "UP":
            case "⬆":
            case "⬆️":
                return KeyInput.KEY_UP;

            case "DOWN":
            case "⬇":
            case "⬇️":
                return KeyInput.KEY_DOWN;

            default:
                return null;
        }
    }

    private void addMapping(
        String name,
        Set<Integer> keys
    ) {

        if (keys.isEmpty()) return;

        com.jme3.input.controls.Trigger[] triggers =
            new com.jme3.input.controls.Trigger[keys.size()];

        int i = 0;

        for (Integer key : keys) {
            triggers[i++] = new KeyTrigger(key);
        }

        inputManager.addMapping(name, triggers);
    }

    private void removeRuntimeMapping(String name) {

        if (inputManager.hasMapping(name)) {
            inputManager.deleteMapping(name);
        }
    }

    // --------------------------------------------------
    // PLAYER MOVEMENT
    // --------------------------------------------------

    private void updateRuntimePlayer(float tpf) {

        if (runtimePlayerNode == null
            || runtimePlayerConfig == null
            || playerDead) {
            return;
        }

        if (hurtTimer > 0f) {
            hurtTimer = Math.max(0f, hurtTimer - tpf);
        }

        float horizontal =
            (moveRight ? 1f : 0f)
            - (moveLeft ? 1f : 0f);

        float speed = (float) runtimePlayerConfig.maxSpeed;

        Vector3f position =
            runtimePlayerNode.getLocalTranslation().clone();

        position.x += horizontal * speed * tpf;

        float gravity = (float) Math.max(
            0.01,
            runtimePlayerConfig.gravity
        );

        if (jumpRequested && grounded) {

            verticalVelocity = (float) Math.sqrt(
                2f * gravity
                * Math.max(0.01, runtimePlayerConfig.jumpHeight)
            );

            grounded = false;
        }

        jumpRequested = false;

        if (!grounded) {

            verticalVelocity -= gravity * tpf;
            position.y += verticalVelocity * tpf;

            if (position.y <= runtimeFloorY) {

                position.y = runtimeFloorY;
                verticalVelocity = 0f;
                grounded = true;
            }
        }

        runtimePlayerNode.setLocalTranslation(position);

        sceneContent.updateGeometricState();

        checkRuntimeCollectibles();
        checkRuntimeEnemyCollisions();

        updateRuntimeAnimation(horizontal != 0f);
    }

    // --------------------------------------------------
    // COLLISIONS
    // --------------------------------------------------

    private void checkRuntimeCollectibles() {

        if (runtimePlayerNode == null
            || runtimePlayerNode.getWorldBound() == null) {
            return;
        }

        for (int i = runtimeCollectibles.size() - 1; i >= 0; i--) {

            Spatial item = runtimeCollectibles.get(i);

            if (item.getParent() == null
                || item.getWorldBound() == null) {
                continue;
            }

            if (runtimePlayerNode.getWorldBound().intersects(
                item.getWorldBound()
            )) {

                collectedObject = true;

                item.removeFromParent();
                runtimeCollectibles.remove(i);

                System.out.println(
                    "Nera: Player collected an object."
                );
            }
        }
    }

    private void checkRuntimeEnemyCollisions() {

        if (!collisionEnabled
            || runtimePlayerNode == null
            || runtimePlayerNode.getWorldBound() == null
            || hurtTimer > 0f) {
            return;
        }

        for (Spatial enemy : runtimeEnemies) {

            if (enemy.getParent() == null
                || enemy.getWorldBound() == null) {
                continue;
            }

            if (runtimePlayerNode.getWorldBound().intersects(
                enemy.getWorldBound()
            )) {

                hurtTimer = 0.8f;
                playHurtAnimation();

                break;
            }
        }
    }

    // --------------------------------------------------
    // PLAYER ANIMATIONS
    // --------------------------------------------------

    private void playHurtAnimation() {

        if (runtimePlayerConfig == null
            || (runtimePlayer2D == null
                && runtimePlayer3DAnimations == null)) {
            return;
        }

        Main.RuntimeAnimationRule rule =
            findRule("touch enemy");

        if (rule == null || rule.animationChoices.isEmpty()) {
            return;
        }

        boolean fatalHit =
            rule.lastTry
            && !collectedObject
            && rule.animationChoices.size() >= 2;

        if (fatalHit) {

            playerDead = true;

            moveLeft = false;
            moveRight = false;

            System.out.println("Nera: Player died.");

            Main.RuntimeAnimationRule deathRule =
                findRule("player dies");

            if (deathRule != null
                && !deathRule.animationChoices.isEmpty()) {

                playRuleAnimation(deathRule, 0);

            } else {

                playRuntimeAnimation(
                    rule.animationChoices.get(1)
                );
            }

        } else {

            playRuntimeAnimation(
                rule.animationChoices.get(0)
            );
        }
    }

    private void updateRuntimeAnimation(boolean walking) {

        if (runtimePlayerConfig == null
            || (runtimePlayer2D == null
                && runtimePlayer3DAnimations == null)
            || hurtTimer > 0f
            || playerDead) {
            return;
        }

        // JUMP
        if (!grounded) {

            Main.RuntimeAnimationRule rule =
                findRule("jump");

            if (rule != null) {
                playRuleAnimation(rule, 0);
                return;
            }
        }

        // WALK / RUN
        if (walking) {

            Main.RuntimeAnimationRule rule =
                findMovementRule();

            if (rule != null) {
                playRuleAnimation(rule, 0);
                return;
            }
        }

        // IDLE / ELSE
        Main.RuntimeAnimationRule idle =
            runtimePlayerConfig.animations.get("else");

        if (idle != null) {
            playRuleAnimation(idle, 0);
        }
    }

    private Main.RuntimeAnimationRule findMovementRule() {

        if (runtimePlayerConfig == null) return null;

        for (Main.RuntimeAnimationRule rule :
            runtimePlayerConfig.animations.values()) {

            String condition = rule.condition.toLowerCase();

            if (condition.contains("walk")
                || condition.contains("run")
                || condition.contains("move")) {
                return rule;
            }
        }

        return null;
    }

    private Main.RuntimeAnimationRule findRule(String text) {

        if (runtimePlayerConfig == null) return null;

        for (Main.RuntimeAnimationRule rule :
            runtimePlayerConfig.animations.values()) {

            if (rule.condition.toLowerCase().contains(
                text.toLowerCase()
            )) {
                return rule;
            }
        }

        return null;
    }

    private void playRuntimeAnimation(String animation) {

        if (animation == null || animation.isBlank()) {
            return;
        }

        if (animation.equalsIgnoreCase("tout")) {

            System.out.println(
                "DynaScript: All detected animations are available."
            );

            return;
        }

        if (animation.equals(currentRuntimeAnimation)) {
            return;
        }

        // 3D ANIMATIONS
        if (runtimePlayer3DAnimations != null) {

            boolean played =
                runtimePlayer3DAnimations.play(animation);

            if (!played) {

                System.out.println(
                    "DynaScript error: 3D animation not found: "
                    + animation
                );

                return;
            }

            currentRuntimeAnimation = animation;
            return;
        }

        // 2D ANIMATIONS
        if (runtimePlayer2D != null) {

            if (!runtimePlayer2D.hasAnimation(animation)) {

                System.out.println(
                    "DynaScript error: Animation not found: "
                    + animation
                );

                System.out.println(
                    "Available animations: "
                    + runtimePlayer2D.getAnimationNames()
                );

                return;
            }

            runtimePlayer2D.play(animation);
            currentRuntimeAnimation = animation;
        }
    }

    private void playRuleAnimation(
        Main.RuntimeAnimationRule rule,
        int choiceIndex
    ) {

        if (rule == null
            || rule.animationChoices == null
            || rule.animationChoices.isEmpty()) {
            return;
        }

        int index = Math.max(
            0,
            Math.min(
                choiceIndex,
                rule.animationChoices.size() - 1
            )
        );

        String animation =
            rule.animationChoices.get(index);

        playRuntimeAnimation(animation);
    }

    // --------------------------------------------------
    // RESET
    // --------------------------------------------------

    private void resetRuntimePlayer() {

        runtimePlayer2D = null;
        runtimePlayer3D = null;
        runtimePlayer3DAnimations = null;
        runtimePlayerNode = null;
        runtimePlayerConfig = null;

        runtimeEnemies.clear();
        runtimeCollectibles.clear();

        moveLeft = false;
        moveRight = false;
        jumpRequested = false;

        grounded = true;
        collectedObject = false;
        playerDead = false;
        collisionEnabled = false;

        verticalVelocity = 0f;
        runtimeFloorY = 0f;
        hurtTimer = 0f;

        currentRuntimeAnimation = "";
    }

    // --------------------------------------------------
    // ASSET PATHS
    // --------------------------------------------------

    
private String registerAndGetAssetPath(Path path) {

    Path absolutePath = path.toAbsolutePath().normalize();

    if (!Files.isRegularFile(absolutePath)) {
        throw new IllegalArgumentException(
            "Asset file not found: " + absolutePath
        );
    }

    Path parent = absolutePath.getParent();

    if (parent == null) {
        throw new IllegalArgumentException(
            "Asset parent directory not found: " + absolutePath
        );
    }

    assetManager.registerLocator(
        parent.toString(),
        com.jme3.asset.plugins.FileLocator.class
    );

    return absolutePath.getFileName().toString();
}


    private Path resolveModelPath(
        String model,
        Scene3DManager scene,
        Path projectRoot
    ) throws Exception {

        Path relative = Path.of(
            model.replace('\\', File.separatorChar)
        );

        if (relative.isAbsolute()) {

            Path absolute = relative.normalize();

            if (!Files.exists(absolute)) {
                throw new IOException(
                    "Model not found: " + absolute
                );
            }

            return absolute;
        }

        Path fromProject =
            projectRoot.resolve(relative).normalize();

        if (fromProject.startsWith(projectRoot)
            && Files.exists(fromProject)) {
            return fromProject;
        }

        Path sceneFolder =
            scene.getSceneFile() == null
                ? projectRoot
                : scene.getSceneFile()
                    .toAbsolutePath()
                    .getParent();

        Path fromScene =
            sceneFolder.resolve(relative).normalize();

        if (fromScene.startsWith(sceneFolder)
            && Files.exists(fromScene)) {
            return fromScene;
        }

        throw new IOException("Model not found: " + model);
    }

    // --------------------------------------------------
    // GETTERS
    // --------------------------------------------------

    public Node getSceneRoot() {
        return sceneRoot;
    }

    public ViewPort getMainViewPort() {
        return viewPort;
    }
}
