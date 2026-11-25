
package nera.DynaScript.Compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nera.Core.Scene3DManager;

public class Main {

    static Map<String, String> variables = new HashMap<>();
    static Map<String, FunctionData> functions = new HashMap<>();
    static List<String> programLines = new ArrayList<>();

    static boolean returning = false;
    static String returnValue = "";

    static class FunctionData {
        List<String> parameters = new ArrayList<>();
        List<String> body = new ArrayList<>();
    }

    static class PlayerConfig {
        double gravity = 20;
        double jumpHeight = 4;
        double maxSpeed = 12;

        String model = "";

        Map<String, List<String>> inputs = new LinkedHashMap<>();
        Map<String, AnimationRule> animations = new LinkedHashMap<>();
    }

    static class AnimationRule {
        String condition = "";
        List<String> animationChoices = new ArrayList<>();
        String speed = "normal";
        boolean lastTry = false;
    }

    /** Immutable-enough runtime snapshot consumed by EngineCore. */
    public static final class PlayerRuntimeConfig {
        public final double gravity;
        public final double jumpHeight;
        public final double maxSpeed;
        public final String animationModel;
        public final boolean animationBasedCollision;
        public final Map<String, List<String>> inputs;
        public final Map<String, RuntimeAnimationRule> animations;

        private PlayerRuntimeConfig(PlayerConfig source, boolean animationBasedCollision) {
            gravity = source.gravity;
            jumpHeight = source.jumpHeight;
            maxSpeed = source.maxSpeed;
            animationModel = source.model;
            this.animationBasedCollision = animationBasedCollision;
            inputs = new LinkedHashMap<>();
            source.inputs.forEach((key, value) -> inputs.put(key, new ArrayList<>(value)));
            animations = new LinkedHashMap<>();
            source.animations.forEach((key, value) -> animations.put(key,
                    new RuntimeAnimationRule(value.condition, value.animationChoices,
                            value.speed, value.lastTry)));
        }
    }

    public static final class RuntimeAnimationRule {
        public final String condition;
        public final List<String> animationChoices;
        public final String speed;
        public final boolean lastTry;

        private RuntimeAnimationRule(String condition, List<String> choices,
                                     String speed, boolean lastTry) {
            this.condition = condition;
            this.animationChoices = new ArrayList<>(choices);
            this.speed = speed;
            this.lastTry = lastTry;
        }
    }

    private static boolean animationBasedCollision;

    static class SceneEntry {
        String category;
        String model = "";
        String name = "";
        double x = 0;
        double y = 0;
        double z = 0;

        SceneEntry(String category) {
            this.category = category;
        }
    }

    static PlayerConfig player = new PlayerConfig();

    static String attachedTo = "";
    static String attachedIn = "";

    static List<SceneEntry> sceneEntries = new ArrayList<>();

    private static Scene3DManager activeScene;
    private static Path activeScriptFile;
    private static Path activeProjectRoot;

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("DynaScript Interpreter");
            System.out.println("Usage: java Main file.dyna");
            return;
        }

        try {
            Path script = Path.of(args[0]).toAbsolutePath().normalize();
            String source = Files.readString(script);

            run(source, script, null, script.getParent());

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void run(String source) {
        run(source, null, null, null);
    }

    /** Reads only the player runtime declarations from a .dyna file. */
    public static PlayerRuntimeConfig loadPlayerRuntimeConfig(Path scriptFile) throws IOException {
        Path script = scriptFile.toAbsolutePath().normalize();
        String source = Files.readString(script);
        programLines = new ArrayList<>();
        for (String line : source.split("\\R")) {
            programLines.add(line);
        }
        player = new PlayerConfig();
        animationBasedCollision = false;
        parseOnStartPlayerConfig();
        return new PlayerRuntimeConfig(player, animationBasedCollision);
    }

    private static void parseOnStartPlayerConfig() {
        for (int i = 0; i < programLines.size(); i++) {
            String raw = programLines.get(i);
            if (!raw.trim().equalsIgnoreCase("On Start:")) continue;
            int end = findBlockEnd(i, programLines.size());
            parseRuntimePlayerBlock(i + 1, end, getIndent(raw) + 4);
            return;
        }
    }

    
private static void parseRuntimePlayerBlock(int start, int end, int indentLevel) {
    for (int i = start; i < end; i++) {
        String raw = programLines.get(i);
        String line = raw.trim();

        if (line.isEmpty() || line.startsWith("//")) continue;

        int indent = getIndent(raw);
        if (indent < indentLevel) continue;

        String lower = line.toLowerCase();

        // Animation model: new and legacy syntax
        if (lower.startsWith("animation.basedon.")
                || lower.startsWith("animationsbasedon.")
                || lower.startsWith("animationbasedon.")) {

            int dot = line.indexOf('.');
            player.model = line.substring(dot + 1).trim();

            // Remove the optional "BasedOn." prefix
            if (player.model.regionMatches(true, 0, "BasedOn.", 0, 8)) {
                player.model = player.model.substring(8).trim();
            }

            continue;
        }

        // Collision based on the model's animations
        if (lower.startsWith("collision.basedon.animationof.")
                || lower.startsWith("collisionbasedon.animationof.")) {
            animationBasedCollision = true;
            continue;
        }

        int colon = line.indexOf(':');

        // Player properties and inputs
        if (colon > 0) {
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();

            if (setPlayerProperty(name, value)) continue;

            if (isPlayerInput(name)) {
                List<String> keys = new ArrayList<>();

                for (String key : value.split("/")) {
                    if (!key.trim().isEmpty()) {
                        keys.add(key.trim());
                    }
                }

                player.inputs.put(capitalize(name), keys);
                continue;
            }
        }

        // Animation conditions
        if (line.startsWith("If ")) {

            // Block syntax must be checked BEFORE inline syntax
            if (line.endsWith(":")) {
                String condition = line.substring(3, line.length() - 1).trim();
                int blockEnd = findBlockEnd(i, end);

                AnimationRule rule = parseAnimationAction(
                        condition, i + 1, blockEnd
                );

                player.animations.put(normalizeCondition(condition), rule);
                i = blockEnd - 1;
                continue;
            }

            // Inline syntax
            if (colon > 3) {
                String condition = line.substring(3, colon).trim();
                String action = line.substring(colon + 1).trim();

                AnimationRule rule = parseAnimationLine(condition, action);
                player.animations.put(normalizeCondition(condition), rule);
                continue;
            }
        }

        // Player Dies condition
        if (lower.equals("player dies:")) {
            int blockEnd = findBlockEnd(i, end);

            AnimationRule rule = parseAnimationAction(
                    "Player Dies", i + 1, blockEnd
            );

            player.animations.put("player dies", rule);
            i = blockEnd - 1;
            continue;
        }

        // Else animation
        if (line.equalsIgnoreCase("Else:")) {
            int blockEnd = findBlockEnd(i, end);

            AnimationRule rule = parseAnimationAction(
                    "Else", i + 1, blockEnd
            );

            player.animations.put("else", rule);
            i = blockEnd - 1;
        }
    }
}


    private static AnimationRule parseAnimationLine(String condition, String action) {
        AnimationRule rule = new AnimationRule();
        rule.condition = condition;
        addAnimationNames(rule, action);
        if (action.toLowerCase().contains("for the last try")) rule.lastTry = true;
        if (action.toLowerCase().contains("huge speed")) rule.speed = "huge";
        else if (action.toLowerCase().contains("fast")) rule.speed = "fast";
        else if (action.toLowerCase().contains("slow")) rule.speed = "slow";
        return rule;
    }

    private static void addAnimationNames(AnimationRule rule, String action) {
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(action);
        while (matcher.find()) rule.animationChoices.add(matcher.group(1));
    }

    private static final Pattern ANIMATION_BASED_PATTERN =
    Pattern.compile(
        "^(Animation\\.BasedOn\\.|AnimationsBasedOn\\.|AnimationBasedOn\\.).+$",
        Pattern.CASE_INSENSITIVE
    );

private static final Pattern ANIM_PLAY_PATTERN =
    Pattern.compile(
        "^anim\\.play\\s*\\(.*\\).*$",
        Pattern.CASE_INSENSITIVE
    );

    private static String normalizeCondition(String condition) {
        return condition.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }

    public static void run(
            String source,
            Path scriptFile,
            Scene3DManager sceneManager,
            Path projectRoot) {

        variables.clear();
        functions.clear();
        programLines.clear();
        sceneEntries.clear();

        player = new PlayerConfig();
        animationBasedCollision = false;

        attachedTo = "";
        attachedIn = "";

        returning = false;
        returnValue = "";

        activeScriptFile = scriptFile == null
                ? null
                : scriptFile.toAbsolutePath().normalize();

        activeProjectRoot = projectRoot == null
                ? (activeScriptFile == null ? null : activeScriptFile.getParent())
                : projectRoot.toAbsolutePath().normalize();

        activeScene = sceneManager;

        String[] lines = source.split("\\R");

        for (String line : lines) {
            programLines.add(line);
        }

        configureSceneAttachment();
        registerFunctions();

        executeBlock(0, programLines.size(), 0);

        writeSceneDeclarations();
    }

    // --------------------------------------------------
    // SCENE ATTACHMENT
    // --------------------------------------------------

    private static void configureSceneAttachment() {

        String attachment = null;

        for (String raw : programLines) {

            String line = raw.trim();

            if (line.startsWith("On Start:")) {
                break;
            }

            if (line.startsWith("Attached in:")) {

                attachment = line.substring(
                        "Attached in:".length()
                ).trim();

                if (attachment.startsWith("\"")
                        && attachment.endsWith("\"")) {

                    attachment = attachment.substring(
                            1, attachment.length() - 1
                    );
                }

                break;
            }
        }

        if (attachment == null && activeScriptFile != null) {

            String scriptName = activeScriptFile.getFileName().toString();

            int dot = scriptName.lastIndexOf('.');

            String sceneName =
                    (dot > 0 ? scriptName.substring(0, dot) : scriptName)
                    + ".3D";

            Path base = activeProjectRoot != null
                    ? activeProjectRoot
                    : activeScriptFile.getParent();

            if (base != null
                    && Files.isRegularFile(base.resolve(sceneName))) {

                attachment = sceneName;
            }

            if (attachment == null
                    && activeScriptFile.getParent() != null
                    && Files.isRegularFile(
                            activeScriptFile.getParent().resolve(sceneName))) {

                attachment = sceneName;
            }
        }

        if (attachment == null || attachment.isBlank()) {
            return;
        }

        attachedIn = attachment;

        if (activeScene != null && activeScene.getSceneFile() != null) {
            return;
        }

        try {

            Path base = activeProjectRoot != null
                    ? activeProjectRoot
                    : (activeScriptFile == null
                        ? Path.of("").toAbsolutePath()
                        : activeScriptFile.getParent());

            Path scenePath = base.resolve(attachment).normalize();

            if (!Files.isRegularFile(scenePath)
                    && activeScriptFile != null) {

                Path besideScript = activeScriptFile
                        .getParent()
                        .resolve(attachment)
                        .normalize();

                if (Files.isRegularFile(besideScript)) {
                    scenePath = besideScript;
                }
            }

            if (Files.isRegularFile(scenePath)) {

                Scene3DManager loaded = new Scene3DManager();
                loaded.load(scenePath);
                activeScene = loaded;
            }

        } catch (Exception e) {

            System.out.println(
                    "Unable to load attached .3D scene: "
                    + e.getMessage()
            );
        }
    }

    // --------------------------------------------------
    // FUNCTIONS
    // --------------------------------------------------

    static void registerFunctions() {

        for (int i = 0; i < programLines.size(); i++) {

            String line = programLines.get(i).trim();

            if (!line.startsWith("Function ")
                    || !line.endsWith(":")) {

                continue;
            }

            String declaration = line.substring(
                    9, line.length() - 1
            ).trim();

            int opening = declaration.indexOf("(");
            int closing = declaration.lastIndexOf(")");

            String name;
            String parameters = "";

            if (opening >= 0 && closing > opening) {

                name = declaration.substring(0, opening).trim();

                parameters = declaration.substring(
                        opening + 1, closing
                ).trim();

            } else {

                name = declaration;
            }

            FunctionData function = new FunctionData();

            if (!parameters.isEmpty()) {

                for (String parameter : splitArguments(parameters)) {
                    function.parameters.add(parameter.trim());
                }
            }

            int end = findBlockEnd(i, programLines.size());

            for (int j = i + 1; j < end; j++) {
                function.body.add(programLines.get(j));
            }

            functions.put(name, function);
        }
    }

    // --------------------------------------------------
    // EXECUTION
    // --------------------------------------------------

    static void executeBlock(int start, int end, int baseIndent) {

        int i = start;

        while (i < end) {

            String raw = programLines.get(i);
            String line = raw.trim();

            if (line.isEmpty() || line.startsWith("//")) {
                i++;
                continue;
            }

            int indent = getIndent(raw);

            if (indent < baseIndent) {
                return;
            }

            if (line.startsWith("Function ")) {
                i = findBlockEnd(i, end);
                continue;
            }

            if (line.startsWith("Attached to:")
                    || line.startsWith("Attached in:")) {

                i++;
                continue;
            }

            if (line.equals("On Start:")) {

                int blockEnd = findBlockEnd(i, end);

                executeBlock(
                        i + 1,
                        blockEnd,
                        indent + 4
                );

                i = blockEnd;
                continue;
            }

            if (executeSceneCommand(line)) {
                i++;
                continue;
            }

            // SCENE DECLARATIONS

            if (line.matches("(?i)(Map|Player|Enemy|Object)\\s*:")) {

                int blockEnd = findBlockEnd(i, end);

                String category = line.substring(
                        0, line.length() - 1
                );

                SceneEntry entry = parseSceneEntry(
                        category,
                        i + 1,
                        blockEnd
                );

                if (entry.model.isBlank()) {

                    System.out.println(
                            "Missing Model in " + category + " block."
                    );

                } else {

                    if (entry.name.isBlank()) {
                        entry.name = entry.model;
                    }

                    sceneEntries.add(entry);
                }

                if (category.equals("Player")) {

                    parsePlayerBlock(
                            i + 1,
                            blockEnd,
                            indent
                    );
                }

                i = blockEnd;
                continue;
            }

            if (line.startsWith("If ") && line.endsWith(":")) {

                int blockEnd = findBlockEnd(i, end);

                String condition = line.substring(
                        3, line.length() - 1
                ).trim();

                boolean result = checkCondition(condition);

                int elseIndex = findElse(blockEnd, end, indent);

                if (result) {

                    executeBlock(
                            i + 1,
                            blockEnd,
                            indent + 4
                    );

                } else if (elseIndex >= 0) {

                    int elseEnd = findBlockEnd(elseIndex, end);

                    executeBlock(
                            elseIndex + 1,
                            elseEnd,
                            indent + 4
                    );

                    i = elseEnd;
                    continue;
                }

                if (elseIndex >= 0) {
                    i = findBlockEnd(elseIndex, end);
                } else {
                    i = blockEnd;
                }

                continue;
            }

            if (line.startsWith("While ") && line.endsWith(":")) {

                int blockEnd = findBlockEnd(i, end);

                String condition = line.substring(
                        6, line.length() - 1
                ).trim();

                int safety = 0;

                while (checkCondition(condition) && safety < 100000) {

                    executeBlock(
                            i + 1,
                            blockEnd,
                            indent + 4
                    );

                    if (returning) {
                        return;
                    }

                    safety++;
                }

                i = blockEnd;
                continue;
            }

            if (line.startsWith("Call ")) {

                executeFunction(line.substring(5).trim());

                i++;
                continue;
            }

            if (line.startsWith("Return:")) {

                returnValue = evaluate(
                        line.substring(7).trim()
                );

                returning = true;
                return;
            }

            if (line.startsWith("Print:")) {

                System.out.println(
                        evaluate(line.substring(6).trim())
                );

                i++;
                continue;
            }

            if (line.startsWith("Wait:")) {

                try {

                    double seconds = Double.parseDouble(
                            evaluate(line.substring(5).trim())
                    );

                    Thread.sleep((long) (seconds * 1000));

                } catch (Exception e) {

                    System.out.println("Invalid Wait value.");
                }

                i++;
                continue;
            }

            if (line.startsWith("Add ")) {
                executeMathCommand(line, "Add ", "+");
                i++;
                continue;
            }

            if (line.startsWith("Remove ")) {

                executeMathCommand(line, "Remove ", "-");
                i++;
                continue;
            }

            if (line.startsWith("Multiply ")) {

                executeMathCommand(line, "Multiply ", "*");
                i++;
                continue;
            }

            if (line.startsWith("Change ")) {

                int separator = line.indexOf(":");

                if (separator >= 0) {

                    String name = line.substring(
                            7, separator
                    ).trim();

                    String value = evaluate(
                            line.substring(separator + 1).trim()
                    );

                    variables.put(name, value);
                }

                i++;
                continue;
            }

            if (line.contains(":")) {

                int separator = line.indexOf(":");

                String name = line.substring(
                        0, separator
                ).trim();

                String value = evaluate(
                        line.substring(separator + 1).trim()
                );

                variables.put(name, value);

                i++;
                continue;
            }

            i++;
        }
    }

    // --------------------------------------------------
    // SCENE BLOCK PARSING
    // --------------------------------------------------

    private static SceneEntry parseSceneEntry(
            String category,
            int start,
            int end) {

        SceneEntry entry = new SceneEntry(category);

        for (int i = start; i < end; i++) {

            String line = programLines.get(i).trim();

            if (line.matches("(?i)Model\\s*=.*")) {

                int separator = line.indexOf("=");

                if (separator >= 0) {

                    entry.model = evaluate(
                            line.substring(separator + 1).trim()
                    );
                }
            }

            else if (line.startsWith("Name")) {

                int separator = line.indexOf("=");

                if (separator >= 0) {

                    entry.name = evaluate(
                            line.substring(separator + 1).trim()
                    );
                }
            }

            else if (line.startsWith("Position")) {

                int separator = line.indexOf("=");

                if (separator >= 0) {

                    String coordinates = line.substring(
                            separator + 1
                    ).trim();

                    if (coordinates.startsWith("(")
                            && coordinates.endsWith(")")) {

                        coordinates = coordinates.substring(
                                1, coordinates.length() - 1
                        );
                    }

                    double[] position = parseCoordinates(coordinates);

                    entry.x = position[0];
                    entry.y = position[1];
                    entry.z = position[2];
                }
            }
        }

        return entry;
    }

    // --------------------------------------------------
    // SCENE FILE GENERATION
    // --------------------------------------------------

    private static void writeSceneDeclarations() {

        if (sceneEntries.isEmpty()) {
            return;
        }

        if (attachedIn.isBlank()) {

            System.out.println(
                    "No .3D file attached. Cannot generate scene."
            );

            return;
        }

        try {

            Path base = activeProjectRoot != null
                    ? activeProjectRoot
                    : (activeScriptFile == null
                        ? Path.of("").toAbsolutePath()
                        : activeScriptFile.getParent());

            Path scenePath = base.resolve(attachedIn).normalize();

            if (!Files.isRegularFile(scenePath)
                    && activeScriptFile != null) {

                Path besideScript = activeScriptFile
                        .getParent()
                        .resolve(attachedIn)
                        .normalize();

                if (Files.isRegularFile(besideScript)
                        || besideScript.getParent() != null) {

                    scenePath = besideScript;
                }
            }

            StringBuilder output = new StringBuilder();

            // MAP

            for (SceneEntry entry : sceneEntries) {

                if (entry.category.equals("Map")) {

                    output.append("Map in 3D: ")
                            .append(entry.model)
                            .append("\n");

                    break;
                }
            }

            output.append("\n");

            // OTHER OBJECTS

            String[] categories = {
                "Player",
                "Enemy",
                "Object"
            };

            for (String category : categories) {

                List<SceneEntry> matching = new ArrayList<>();

                for (SceneEntry entry : sceneEntries) {

                    if (entry.category.equals(category)) {
                        matching.add(entry);
                    }
                }

                if (matching.isEmpty()) {
                    continue;
                }

                output.append(category).append(":\n");

                int id = 1;

                for (SceneEntry entry : matching) {

                    output.append("    ")
                            .append(id++)
                            .append(" ")
                            .append(entry.model)
                            .append("\n");

                    output.append("        Name = \"")
                            .append(entry.name)
                            .append("\"\n");

                    output.append("        X = ")
                            .append(formatNumber(entry.x))
                            .append("\n");

                    output.append("        Y = ")
                            .append(formatNumber(entry.y))
                            .append("\n");

                    output.append("        Z = ")
                            .append(formatNumber(entry.z))
                            .append("\n");
                }

                output.append("\n");
            }

            Files.writeString(scenePath, output.toString());

            activeScene = new Scene3DManager();
            activeScene.load(scenePath);

            System.out.println(
                    "3D Scene generated: " + scenePath
            );

            displaySceneInfo();

        } catch (Exception e) {

            System.out.println(
                    "Scene generation error: " + e.getMessage()
            );
        }
    }

    private static void displaySceneInfo() {

        if (activeScene == null) {
            return;
        }

        System.out.println();
        System.out.println("========== 3D SCENE ==========");

        System.out.println(
                "Map Model: " + activeScene.getMapModel()
        );

        for (Scene3DManager.SceneObject object
                : activeScene.getObjects()) {

            System.out.println();
            System.out.println("Name: " + object.getName());
            System.out.println("Model: " + object.getModel());

            System.out.println(
                    "Position: (" +
                    object.getX() + ", " +
                    object.getY() + ", " +
                    object.getZ() + ")"
            );
        }

        System.out.println("==============================");
    }

    // --------------------------------------------------
    // SCENE COMMANDS
    // --------------------------------------------------

    private static boolean executeSceneCommand(String line) {

        if (line.startsWith("Load Scene ")) {

            Matcher matcher = Pattern.compile(
                    "^Load Scene\\s+\"([^\"]+)\"$"
            ).matcher(line);

            if (!matcher.matches()) {

                System.out.println("Invalid Load Scene syntax.");
                return true;
            }

            try {

                String sceneName = matcher.group(1);

                Path base = activeProjectRoot != null
                        ? activeProjectRoot
                        : (activeScriptFile == null
                            ? Path.of("").toAbsolutePath()
                            : activeScriptFile.getParent());

                Path scenePath = base.resolve(sceneName).normalize();

                if (!Files.isRegularFile(scenePath)
                        && activeScriptFile != null) {

                    scenePath = activeScriptFile.getParent()
                            .resolve(sceneName)
                            .normalize();
                }

                if (!Files.isRegularFile(scenePath)) {

                    System.out.println(
                            "Scene file not found: " + scenePath
                    );

                    return true;
                }

                Scene3DManager loaded = new Scene3DManager();
                loaded.load(scenePath);

                activeScene = loaded;

                System.out.println(
                        "Scene loaded: " + sceneName
                );

            } catch (Exception e) {

                System.out.println(
                        "Unable to load scene: " + e.getMessage()
                );
            }

            return true;
        }

        if (line.startsWith("Spawn ")) {

            Matcher matcher = Pattern.compile(
                    "^Spawn\\s+(.+?)\\s+\\(\"([^\"]+)\"\\)\\s+at\\s*\\(([^)]*)\\)$"
            ).matcher(line);

            if (!matcher.matches()) {
                return false;
            }

            if (!requireScene()) {
                return true;
            }

            try {

                double[] xyz = parseCoordinates(matcher.group(3));

                activeScene.spawn(
                        matcher.group(1).trim(),
                        matcher.group(2).trim(),
                        xyz[0], xyz[1], xyz[2]
                );

                saveSceneAfterCommand();

            } catch (Exception e) {

                System.out.println("Spawn error: " + e.getMessage());
            }

            return true;
        }

        if (line.startsWith("Move ")) {

            Matcher matcher = Pattern.compile(
                    "^Move\\s+(.+?)\\s+(\\d+)\\s+to\\s*\\(([^)]*)\\)$"
            ).matcher(line);

            if (!matcher.matches()) {
                return false;
            }

            if (!requireScene()) {
                return true;
            }

            try {

                double[] xyz = parseCoordinates(matcher.group(3));

                if (!activeScene.move(
                        matcher.group(1).trim(),
                        Integer.parseInt(matcher.group(2)),
                        xyz[0], xyz[1], xyz[2])) {

                    System.out.println(
                            "Scene object not found: "
                            + matcher.group(1) + " "
                            + matcher.group(2)
                    );

                } else {

                    saveSceneAfterCommand();
                }

            } catch (Exception e) {

                System.out.println("Move error: " + e.getMessage());
            }

            return true;
        }

        if (line.startsWith("Change ")) {

            Matcher matcher = Pattern.compile(
                    "^Change\\s+(.+?)\\s+(\\d+)\\s+([XYZ])\\s*:\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE
            ).matcher(line);

            if (matcher.matches()) {

                if (!requireScene()) {
                    return true;
                }

                double value = toNumber(
                        evaluate(matcher.group(4).trim())
                );

                if (!activeScene.changePosition(
                        matcher.group(1).trim(),
                        Integer.parseInt(matcher.group(2)),
                        matcher.group(3),
                        value)) {

                    System.out.println(
                            "Scene object or axis not found: "
                            + matcher.group(1) + " "
                            + matcher.group(2)
                    );

                } else {

                    saveSceneAfterCommand();
                }

                return true;
            }
        }

        if (line.startsWith("Remove ")) {

            Matcher matcher = Pattern.compile(
                    "^Remove\\s+(.+?)\\s+(\\d+)$"
            ).matcher(line);

            if (matcher.matches()) {

                if (!requireScene()) {
                    return true;
                }

                if (!activeScene.remove(
                        matcher.group(1).trim(),
                        Integer.parseInt(matcher.group(2)))) {

                    System.out.println(
                            "Scene object not found: "
                            + matcher.group(1) + " "
                            + matcher.group(2)
                    );

                } else {

                    saveSceneAfterCommand();
                }

                return true;
            }
        }

        if (line.startsWith("Rename ")) {

            Matcher matcher = Pattern.compile(
                    "^Rename\\s+(.+?)\\s+(\\d+)\\s+to\\s+\"([^\"]+)\"$"
            ).matcher(line);

            if (matcher.matches()) {

                if (!requireScene()) {
                    return true;
                }

                if (!activeScene.rename(
                        matcher.group(1).trim(),
                        Integer.parseInt(matcher.group(2)),
                        matcher.group(3))) {

                    System.out.println(
                            "Scene object not found: "
                            + matcher.group(1) + " "
                            + matcher.group(2)
                    );

                } else {

                    saveSceneAfterCommand();
                }

                return true;
            }
        }

        return false;
    }

    private static boolean requireScene() {

        if (activeScene != null) {
            return true;
        }

        System.out.println(
                "No .3D scene is attached to this DynaScript."
        );

        return false;
    }

    private static double[] parseCoordinates(String text) {

        List<String> values = splitArguments(text);

        if (values.size() != 3) {

            throw new IllegalArgumentException(
                    "Expected three coordinates: X, Y, Z."
            );
        }

        return new double[] {
            toNumber(evaluate(values.get(0))),
            toNumber(evaluate(values.get(1))),
            toNumber(evaluate(values.get(2)))
        };
    }

    private static void saveSceneAfterCommand() {

        try {

            activeScene.save();

        } catch (IOException e) {

            System.out.println(
                    "Unable to save .3D scene: " + e.getMessage()
            );
        }
    }

    public static Scene3DManager getActiveScene() {
        return activeScene;
    }

    // --------------------------------------------------
    // FUNCTIONS EXECUTION
    // --------------------------------------------------

    static void executeFunction(String call) {

        int opening = call.indexOf("(");
        int closing = call.lastIndexOf(")");

        String name;
        List<String> arguments = new ArrayList<>();

        if (opening >= 0 && closing > opening) {

            name = call.substring(0, opening).trim();

            String argumentText = call.substring(
                    opening + 1, closing
            ).trim();

            if (!argumentText.isEmpty()) {
                arguments = splitArguments(argumentText);
            }

        } else {

            name = call.trim();
        }

        FunctionData function = functions.get(name);

        if (function == null) {

            System.out.println("Function not found: " + name);
            return;
        }

        Map<String, String> oldVariables = new HashMap<>(variables);

        for (int i = 0; i < function.parameters.size(); i++) {

            String parameter = function.parameters.get(i);

            String value = "";

            if (i < arguments.size()) {
                value = evaluate(arguments.get(i));
            }

            variables.put(parameter, value);
        }

        List<String> oldLines = new ArrayList<>(programLines);

        programLines = new ArrayList<>(function.body);

        boolean oldReturning = returning;
        String oldReturnValue = returnValue;

        returning = false;
        returnValue = "";

        executeBlock(0, programLines.size(), 0);

        programLines = oldLines;

        variables.clear();
        variables.putAll(oldVariables);

        returning = oldReturning;
        returnValue = oldReturnValue;
    }

    // --------------------------------------------------
    // MATH
    // --------------------------------------------------

    static void executeMathCommand(
            String line,
            String prefix,
            String operator) {

        String content = line.substring(prefix.length()).trim();

        int separator = content.indexOf(":");

        if (separator < 0) {
            return;
        }

        String name = content.substring(0, separator).trim();

        String amount = evaluate(
                content.substring(separator + 1).trim()
        );

        double current = toNumber(
                variables.getOrDefault(name, "0")
        );

        double value = toNumber(amount);

        double result;

        switch (operator) {

            case "+":
                result = current + value;
                break;

            case "-":
                result = current - value;
                break;

            case "*":
                result = current * value;
                break;

            default:
                return;
        }

        variables.put(name, formatNumber(result));
    }

    // --------------------------------------------------
    // PLAYER
    // --------------------------------------------------

    static void parsePlayerBlock(
            int start,
            int end,
            int playerIndent) {

        int i = start;

        while (i < end) {

            String raw = programLines.get(i);
            String line = raw.trim();

            if (line.isEmpty() || line.startsWith("//")) {
                i++;
                continue;
            }

            int indent = getIndent(raw);

            if (indent <= playerIndent) {
                break;
            }

            if (line.regionMatches(
                    true, 0, "Attached to:", 0, 12)) {

                attachedTo = line.substring(12).trim();

                if (attachedTo.startsWith("\"")
                        && attachedTo.endsWith("\"")) {

                    attachedTo = attachedTo.substring(
                            1, attachedTo.length() - 1
                    );
                }

                System.out.println("Attached to: " + attachedTo);

                i++;
                continue;
            }

            if (line.regionMatches(
                    true, 0, "Attached in:", 0, 12)) {

                attachedIn = line.substring(12).trim();

                if (attachedIn.startsWith("\"")
                        && attachedIn.endsWith("\"")) {

                    attachedIn = attachedIn.substring(
                            1, attachedIn.length() - 1
                    );
                }

                System.out.println("Attached in: " + attachedIn);

                i++;
                continue;
            }

            if (line.contains(":")) {

                int separator = line.indexOf(":");

                String name = line.substring(
                        0, separator
                ).trim();

                String value = line.substring(
                        separator + 1
                ).trim();

                if (setPlayerProperty(name, value)) {
                    i++;
                    continue;
                }

                if (indent == playerIndent + 4
                        && isPlayerInput(name)) {

                    List<String> choices = new ArrayList<>();

                    for (String choice : value.split("/")) {

                        if (!choice.trim().isEmpty()) {
                            choices.add(choice.trim());
                        }
                    }

                    player.inputs.put(name, choices);

                    i++;
                    continue;
                }
            }

            if (line.startsWith("(") && line.endsWith(")")) {

                String content = line.substring(
                        1, line.length() - 1
                );

                List<String> values = splitArguments(content);

                if (values.size() >= 3) {

                    try {

                        player.gravity = Double.parseDouble(
                                values.get(0).trim()
                        );

                        player.jumpHeight = Double.parseDouble(
                                values.get(1).trim()
                        );

                        player.maxSpeed = Double.parseDouble(
                                values.get(2).trim()
                        );

                    } catch (NumberFormatException ignored) {
                    }
                }

                i++;
                continue;
            }

            if (line.startsWith("Animation.BasedOn.")) {

                player.model = line.substring(
                        "Animation.BasedOn.".length()
                ).trim();

                i++;
                continue;
            }

            if (indent == playerIndent + 4
                    && line.startsWith("If ")
                    && line.endsWith(":")) {

                String condition = line.substring(
                        3, line.length() - 1
                ).trim();

                int blockEnd = findBlockEnd(i, end);

                AnimationRule rule = parseAnimationAction(
                        condition,
                        i + 1,
                        blockEnd
                );

                player.animations.put(condition, rule);

                i = blockEnd;
                continue;
            }

            if (indent == playerIndent + 4
                    && line.equals("Else:")) {

                int blockEnd = findBlockEnd(i, end);

                AnimationRule rule = parseAnimationAction(
                        "Else",
                        i + 1,
                        blockEnd
                );

                player.animations.put("Else", rule);

                i = blockEnd;
                continue;
            }

            i++;
        }

        displayPlayerConfig();
    }

    static boolean setPlayerProperty(String name, String value) {

        String property = name.trim().toLowerCase();

        try {

            double number = Double.parseDouble(evaluate(value));

            if (!Double.isFinite(number)) {
                return false;
            }

            switch (property) {

                case "gravity":
                    player.gravity = number;
                    return true;

                case "jump height":
                case "max jump height":
                    player.jumpHeight = number;
                    return true;

                case "max speed":
                    player.maxSpeed = number;
                    return true;

                default:
                    return false;
            }

        } catch (NumberFormatException e) {

            return false;
        }
    }

    static boolean isPlayerInput(String name) {

        return name.equals("Up")
                || name.equals("Down")
                || name.equals("Left")
                || name.equals("Right")
                || name.equals("Jump");
    }

    static AnimationRule parseAnimationAction(
        String condition,
        int start,
        int end) {

    AnimationRule rule = new AnimationRule();
    rule.condition = condition;

    for (int i = start; i < end; i++) {
        String line = programLines.get(i).trim();

        if (!line.regionMatches(true, 0, "anim.play", 0, 9)) {
            continue;
        }

        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(line);

        while (matcher.find()) {
            rule.animationChoices.add(matcher.group(1));
        }

        if (line.toLowerCase().contains("huge speed")) {
            rule.speed = "huge";
        } else if (line.toLowerCase().contains("fast")) {
            rule.speed = "fast";
        } else if (line.toLowerCase().contains("slow")) {
            rule.speed = "slow";
        }

        if (line.toLowerCase().contains("for the last try")) {
            rule.lastTry = true;
        }
    }

    return rule;
}

    static void displayPlayerConfig() {

        System.out.println();
        System.out.println("========== PLAYER ==========");

        System.out.println("Gravity: " + player.gravity);
        System.out.println("Jump height: " + player.jumpHeight);
        System.out.println("Max speed: " + player.maxSpeed);

        System.out.println("Model: " + player.model);

        System.out.println();
        System.out.println("Inputs:");

        for (String input : player.inputs.keySet()) {

            System.out.println(
                    input + " : "
                    + String.join(" / ", player.inputs.get(input))
            );
        }

        System.out.println();
        System.out.println("Animations:");

        for (String condition : player.animations.keySet()) {

            AnimationRule rule = player.animations.get(condition);

            System.out.println(
                    condition + " : "
                    + String.join(" or ", rule.animationChoices)
                    + " | Speed: " + rule.speed
                    + (rule.lastTry ? " | Last try" : "")
            );
        }

        System.out.println("============================");
        System.out.println();
    }

    // --------------------------------------------------
    // CONDITIONS
    // --------------------------------------------------

    static boolean checkCondition(String condition) {

        condition = condition.trim();

        if (condition.contains(" Or ")) {

            String[] parts = condition.split("\\s+Or\\s+");

            for (String part : parts) {

                if (checkCondition(part)) {
                    return true;
                }
            }

            return false;
        }

        if (condition.contains(" And ")) {

            String[] parts = condition.split("\\s+And\\s+");

            for (String part : parts) {

                if (!checkCondition(part)) {
                    return false;
                }
            }

            return true;
        }

        return checkComparison(condition);
    }

    static boolean checkComparison(String condition) {

        String[] operators = {
            ">=", "<=", "==", "!=", ">", "<"
        };

        for (String operator : operators) {

            int index = condition.indexOf(operator);

            if (index < 0) {
                continue;
            }

            String left = evaluate(
                    condition.substring(0, index).trim()
            );

            String right = evaluate(
                    condition.substring(
                            index + operator.length()
                    ).trim()
            );

            double a = toNumber(left);
            double b = toNumber(right);

            switch (operator) {

                case ">=":
                    return a >= b;

                case "<=":
                    return a <= b;

                case "==":
                    return left.equals(right) || a == b;

                case "!=":
                    return !left.equals(right) && a != b;

                case ">":
                    return a > b;

                case "<":
                    return a < b;
            }
        }

        return Boolean.parseBoolean(evaluate(condition));
    }

    // --------------------------------------------------
    // EXPRESSIONS
    // --------------------------------------------------

    static String evaluate(String expression) {

        expression = expression.trim();

        if (expression.isEmpty()) {
            return "";
        }

        if (expression.startsWith("\"")
                && expression.endsWith("\"")) {

            return expression.substring(
                    1, expression.length() - 1
            );
        }

        if (variables.containsKey(expression)) {
            return variables.get(expression);
        }

        try {

            return formatNumber(
                    new ExpressionParser(expression).parse()
            );

        } catch (Exception ignored) {

            return expression;
        }
    }

    static double toNumber(String value) {

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    static String formatNumber(double value) {

        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    static class ExpressionParser {

        String input;
        int position = -1;
        int character;

        ExpressionParser(String input) {
            this.input = input;
            nextCharacter();
        }

        void nextCharacter() {

            position++;

            character = position < input.length()
                    ? input.charAt(position)
                    : -1;
        }

        boolean eat(int expected) {

            while (character == ' ') {
                nextCharacter();
            }

            if (character == expected) {
                nextCharacter();
                return true;
            }

            return false;
        }

        double parse() {

            double result = parseExpression();

            if (position < input.length()) {
                throw new RuntimeException("Invalid expression");
            }

            return result;
        }

        double parseExpression() {

            double result = parseTerm();

            while (true) {

                if (eat('+')) {
                    result += parseTerm();

                } else if (eat('-')) {
                    result -= parseTerm();

                } else {
                    return result;
                }
            }
        }

        double parseTerm() {

            double result = parseFactor();

            while (true) {

                if (eat('*')) {
                    result *= parseFactor();

                } else if (eat('/')) {

                    double divisor = parseFactor();

                    if (divisor == 0) {
                        throw new RuntimeException("Division by zero");
                    }

                    result /= divisor;

                } else {
                    return result;
                }
            }
        }

        double parseFactor() {

            if (eat('+')) {
                return parseFactor();
            }

            if (eat('-')) {
                return -parseFactor();
            }

            double result;

            int start = position;

            if (eat('(')) {

                result = parseExpression();
                eat(')');

                return result;
            }

            if ((character >= '0' && character <= '9')
                    || character == '.') {

                while ((character >= '0' && character <= '9')
                        || character == '.') {

                    nextCharacter();
                }

                return Double.parseDouble(
                        input.substring(start, position)
                );
            }

            if (Character.isLetter(character)
                    || character == '_') {

                while (Character.isLetterOrDigit(character)
                        || character == '_') {

                    nextCharacter();
                }

                String name = input.substring(start, position);

                return toNumber(
                        variables.getOrDefault(name, "0")
                );
            }

            throw new RuntimeException("Unexpected character");
        }
    }

    // --------------------------------------------------
    // BLOCK HELPERS
    // --------------------------------------------------

    static int findBlockEnd(int start, int limit) {

        int headerIndent = getIndent(
                programLines.get(start)
        );

        for (int i = start + 1; i < limit; i++) {

            String line = programLines.get(i);

            if (line.trim().isEmpty()
                    || line.trim().startsWith("//")) {

                continue;
            }

            if (getIndent(line) <= headerIndent) {
                return i;
            }
        }

        return limit;
    }

    static int findElse(int start, int limit, int indent) {

        for (int i = start; i < limit; i++) {

            String line = programLines.get(i);

            if (line.trim().isEmpty()
                    || line.trim().startsWith("//")) {

                continue;
            }

            if (getIndent(line) < indent) {
                return -1;
            }

            if (getIndent(line) == indent
                    && line.trim().equals("Else:")) {

                return i;
            }

            if (getIndent(line) == indent) {
                return -1;
            }
        }

        return -1;
    }

    static int getIndent(String line) {

        int indent = 0;

        for (int i = 0; i < line.length(); i++) {

            if (line.charAt(i) == ' ') {
                indent++;

            } else if (line.charAt(i) == '\t') {
                indent += 4;

            } else {
                break;
            }
        }

        return indent;
    }

    static List<String> splitArguments(String text) {

        List<String> arguments = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        boolean insideQuotes = false;
        int parentheses = 0;

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (c == '"') {
                insideQuotes = !insideQuotes;
            }

            if (!insideQuotes) {

                if (c == '(') {
                    parentheses++;
                }

                if (c == ')') {
                    parentheses--;
                }

                if (c == ',' && parentheses == 0) {

                    arguments.add(current.toString().trim());

                    current.setLength(0);

                    continue;
                }
            }

            current.append(c);
        }

        if (!current.toString().trim().isEmpty()) {
            arguments.add(current.toString().trim());
        }

        return arguments;
    }
}
