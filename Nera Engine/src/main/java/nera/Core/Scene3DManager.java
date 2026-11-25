
package nera.Core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages Nera scenes.
 *
 * New format: .nera
 * Old .3D files can still be loaded.
 */
public final class Scene3DManager {

    public static final class SceneObject {

        private String category;
        private int id;
        private String model;
        private String name;
        private double x;
        private double y;
        private double z;

        private SceneObject(
                String category,
                int id,
                String model,
                String name,
                double x,
                double y,
                double z
        ) {
            this.category = category;
            this.id = id;
            this.model = model;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getCategory() {
            return category;
        }

        public int getId() {
            return id;
        }

        public String getModel() {
            return model;
        }

        public String getName() {
            return name;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        private void setPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final String FORMAT_HEADER = "NERA_SCENE\t1";

    public static final String CATEGORY_PLAYER_2D = "Player2D";

    private static final Pattern INSTANCE_LINE =
            Pattern.compile("^(\\d+)\\s+(.+)$");

    private final Map<String, LinkedHashMap<Integer, SceneObject>> objects =
            new LinkedHashMap<>();

    private Path sceneFile;
    private String mapModel = "";

    private Consumer<Scene3DManager> changeListener;

    public void setChangeListener(Consumer<Scene3DManager> listener) {
        changeListener = listener;
    }

    public Path getSceneFile() {
        return sceneFile;
    }

    public String getMapModel() {
        return mapModel;
    }

    public void setMapModel(String model) {
        mapModel = model == null ? "" : model.trim();
        changed();
    }

    // --------------------------------------------------
    // LOADING
    // --------------------------------------------------

    public void load(Path file) throws IOException {

        Objects.requireNonNull(file, "Scene file cannot be null");

        List<String> lines = Files.readAllLines(
                file,
                StandardCharsets.UTF_8
        );

        objects.clear();
        mapModel = "";

        sceneFile = file.toAbsolutePath().normalize();

        if (!lines.isEmpty()
                && lines.get(0).trim().equals(FORMAT_HEADER)) {

            loadNeraFormat(lines, file);
        } else {
            loadLegacyFormat(lines, file);
        }
    }

    private void loadNeraFormat(
            List<String> lines,
            Path file
    ) throws IOException {

        for (int i = 1; i < lines.size(); i++) {

            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\t", -1);

            try {

                if (parts[0].equals("MAP")) {

                    if (parts.length != 2) {
                        throw new IOException("Invalid MAP entry.");
                    }

                    mapModel = decode(parts[1]);

                } else if (parts[0].equals("OBJECT")) {

                    if (parts.length != 8) {
                        throw new IOException("Invalid OBJECT entry.");
                    }

                    String category = decode(parts[1]);
                    int id = Integer.parseInt(parts[2]);
                    String model = decode(parts[3]);
                    String name = decode(parts[4]);

                    double x = parseFinite(parts[5]);
                    double y = parseFinite(parts[6]);
                    double z = parseFinite(parts[7]);

                    if (category.isBlank() || model.isBlank()) {
                        throw new IOException(
                                "Object category or model is empty."
                        );
                    }

                    LinkedHashMap<Integer, SceneObject> entries =
                            objects.computeIfAbsent(
                                    category,
                                    key -> new LinkedHashMap<>()
                            );

                    if (entries.containsKey(id)) {
                        throw new IOException(
                                "Duplicate object ID: " + id
                        );
                    }

                    entries.put(
                            id,
                            new SceneObject(
                                    category,
                                    id,
                                    model,
                                    name,
                                    x,
                                    y,
                                    z
                            )
                    );

                } else {
                    throw new IOException(
                            "Unknown scene entry: " + parts[0]
                    );
                }

            } catch (RuntimeException ex) {

                throw new IOException(
                        "Invalid scene data at line "
                                + (i + 1)
                                + " in "
                                + file.getFileName(),
                        ex
                );
            }
        }
    }

    /**
     * Reads the previous .3D format.
     * This keeps older projects compatible.
     */
    private void loadLegacyFormat(
            List<String> lines,
            Path file
    ) throws IOException {

        String currentCategory = null;
        SceneObject currentObject = null;

        for (String raw : lines) {

            String trimmed = raw.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")
                    || trimmed.startsWith("//")) {
                continue;
            }

            if (trimmed.startsWith("Map in 3D:")) {

                mapModel = trimmed
                        .substring("Map in 3D:".length())
                        .trim();

                currentCategory = null;
                currentObject = null;
                continue;
            }

            if (trimmed.endsWith(":")) {

                currentCategory = trimmed.substring(
                        0,
                        trimmed.length() - 1
                ).trim();

                if (currentCategory.isEmpty()) {
                    throw new IOException(
                            "Empty category name in "
                                    + file.getFileName()
                    );
                }

                objects.computeIfAbsent(
                        currentCategory,
                        key -> new LinkedHashMap<>()
                );

                currentObject = null;
                continue;
            }

            Matcher instance = INSTANCE_LINE.matcher(trimmed);

            if (instance.matches() && currentCategory != null) {

                int id;

                try {
                    id = Integer.parseInt(instance.group(1));
                } catch (NumberFormatException ex) {
                    throw new IOException(
                            "Invalid instance number: " + trimmed,
                            ex
                    );
                }

                String model = instance.group(2).trim();

                if (model.isEmpty()) {
                    throw new IOException(
                            "Missing model for "
                                    + currentCategory
                                    + " "
                                    + id
                    );
                }

                LinkedHashMap<Integer, SceneObject> category =
                        objects.get(currentCategory);

                if (category.containsKey(id)) {
                    throw new IOException(
                            "Duplicate instance "
                                    + currentCategory
                                    + " "
                                    + id
                    );
                }

                currentObject = new SceneObject(
                        currentCategory,
                        id,
                        model,
                        currentCategory + "_" + id,
                        0,
                        0,
                        0
                );

                category.put(id, currentObject);
                continue;
            }

            if (currentObject != null && trimmed.contains("=")) {

                int equals = trimmed.indexOf('=');

                String key = trimmed
                        .substring(0, equals)
                        .trim()
                        .toLowerCase();

                String value = trimmed
                        .substring(equals + 1)
                        .trim();

                switch (key) {

                    case "name":
                        currentObject.name = unquote(value);
                        break;

                    case "x":
                        currentObject.x = parseLegacyNumber(
                                value, "X", currentObject
                        );
                        break;

                    case "y":
                        currentObject.y = parseLegacyNumber(
                                value, "Y", currentObject
                        );
                        break;

                    case "z":
                        currentObject.z = parseLegacyNumber(
                                value, "Z", currentObject
                        );
                        break;

                    default:
                        break;
                }
            }
        }
    }

    // --------------------------------------------------
    // SAVING
    // --------------------------------------------------

    public void save() throws IOException {

        if (sceneFile == null) {
            throw new IOException(
                    "No scene file is associated with this scene."
            );
        }

        save(sceneFile);
    }

    public void save(Path file) throws IOException {

        Objects.requireNonNull(
                file,
                "Scene file cannot be null"
        );

        StringBuilder out = new StringBuilder();

        out.append(FORMAT_HEADER)
                .append(System.lineSeparator());

        out.append("MAP\t")
                .append(encode(mapModel))
                .append(System.lineSeparator());

        for (Map.Entry<String, LinkedHashMap<Integer, SceneObject>> entry
                : objects.entrySet()) {

            List<SceneObject> sorted = new ArrayList<>(
                    entry.getValue().values()
            );

            sorted.sort(
                    Comparator.comparingInt(SceneObject::getId)
            );

            for (SceneObject object : sorted) {

                out.append("OBJECT\t")
                        .append(encode(object.category)).append('\t')
                        .append(object.id).append('\t')
                        .append(encode(object.model)).append('\t')
                        .append(encode(object.name)).append('\t')
                        .append(format(object.x)).append('\t')
                        .append(format(object.y)).append('\t')
                        .append(format(object.z))
                        .append(System.lineSeparator());
            }
        }

        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(
                absolute,
                out.toString(),
                StandardCharsets.UTF_8
        );

        sceneFile = absolute;
    }

    // --------------------------------------------------
    // OBJECT MANAGEMENT
    // --------------------------------------------------

    public SceneObject spawn(
            String category,
            String model,
            double x,
            double y,
            double z
    ) {

        String safeCategory = requireText(category, "category");
        String safeModel = requireText(model, "model");

        checkPosition(x, y, z);

        LinkedHashMap<Integer, SceneObject> entries =
                objects.computeIfAbsent(
                        safeCategory,
                        key -> new LinkedHashMap<>()
                );

        int id = 1;

        while (entries.containsKey(id)) {
            id++;
        }

        SceneObject object = new SceneObject(
                safeCategory,
                id,
                safeModel,
                safeCategory + "_" + id,
                x,
                y,
                z
        );

        entries.put(id, object);

        changed();

        return object;
    }

    public boolean move(
            String category,
            int id,
            double x,
            double y,
            double z
    ) {

        checkPosition(x, y, z);

        SceneObject object = getObject(category, id);

        if (object == null) {
            return false;
        }

        object.setPosition(x, y, z);

        changed();

        return true;
    }

    public boolean changePosition(
            String category,
            int id,
            String axis,
            double value
    ) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Position must be a finite number."
            );
        }

        SceneObject object = getObject(category, id);

        if (object == null || axis == null) {
            return false;
        }

        switch (axis.toUpperCase()) {

            case "X":
                object.x = value;
                break;

            case "Y":
                object.y = value;
                break;

            case "Z":
                object.z = value;
                break;

            default:
                return false;
        }

        changed();

        return true;
    }

    public SceneObject spawnPlayer2D(
        String sprite,
        double x,
        double y,
        double z
) {
    return spawn(
            CATEGORY_PLAYER_2D,
            sprite,
            x,
            y,
            z
    );
}

    public boolean remove(String category, int id) {

        LinkedHashMap<Integer, SceneObject> entries =
                objects.get(category);

        if (entries == null || entries.remove(id) == null) {
            return false;
        }

        changed();

        return true;
    }

    public boolean rename(
            String category,
            int id,
            String name
    ) {

        SceneObject object = getObject(category, id);

        if (object == null) {
            return false;
        }

        object.name = requireText(name, "name");

        changed();

        return true;
    }

    public SceneObject getObject(String category, int id) {

        Map<Integer, SceneObject> entries = objects.get(category);

        return entries == null ? null : entries.get(id);
    }

    public Collection<SceneObject> getObjects() {

        List<SceneObject> result = new ArrayList<>();

        for (Map<Integer, SceneObject> category : objects.values()) {
            result.addAll(category.values());
        }

        return List.copyOf(result);
    }

    public List<String> getCategories() {
        return List.copyOf(objects.keySet());
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private void changed() {

        if (changeListener != null) {
            changeListener.accept(this);
        }
    }

    private static String encode(String value) {

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)
                );
    }

    private static String decode(String value) {

        return new String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }

    private static double parseFinite(String value) {

        double number = Double.parseDouble(value);

        if (!Double.isFinite(number)) {
            throw new NumberFormatException(
                    "Number must be finite."
            );
        }

        return number;
    }

    private static double parseLegacyNumber(
            String value,
            String axis,
            SceneObject object
    ) throws IOException {

        try {

            return parseFinite(value);

        } catch (NumberFormatException ex) {

            throw new IOException(
                    "Invalid "
                            + axis
                            + " coordinate for "
                            + object.category
                            + " "
                            + object.id
                            + ": "
                            + value,
                    ex
            );
        }
    }

    private static void checkPosition(
            double x,
            double y,
            double z
    ) {

        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {

            throw new IllegalArgumentException(
                    "Object position must contain finite numbers."
            );
        }
    }

    private static String unquote(String value) {

        if (value.length() >= 2
                && value.startsWith("\"")
                && value.endsWith("\"")) {

            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        return value;
    }

    private static String format(double value) {

        return value == (long) value
                ? Long.toString((long) value)
                : Double.toString(value);
    }

    private static String requireText(
            String value,
            String label
    ) {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    label + " cannot be empty"
            );
        }

        return value.trim();
    }
}
