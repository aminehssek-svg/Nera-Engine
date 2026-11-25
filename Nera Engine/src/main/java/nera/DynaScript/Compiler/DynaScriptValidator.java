package nera.DynaScript.Compiler;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks references that can be verified against files in a Nera project.
 *
 * This class deliberately does not enforce a fixed DynaScript template.
 * Unknown/custom syntax is left alone.
 */
public final class DynaScriptValidator {

    private static final String MAGIC = "NERA_PLAYER2D";
    private static final int VERSION = 1;

    private static final Pattern ANIMATION_CALL =
            Pattern.compile("\\banim\\s*\\.\\s*play\\s*\\(\\s*\"([^\"]+)\"\\s*\\)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern ANIMATION_BASE =
            Pattern.compile("^\\s*Animation\\.BasedOn\\.(.+?)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern LOAD_SCENE =
            Pattern.compile("^\\s*Load\\s+Scene\\s+\"([^\"]+)\"\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private DynaScriptValidator() {
    }

    public enum Severity {
        ERROR, WARNING
    }

    public static final class Issue {
        private final Severity severity;
        private final int line;
        private final String message;

        public Issue(Severity severity, int line, String message) {
            this.severity = severity;
            this.line = line;
            this.message = message;
        }

        public Severity getSeverity() {
            return severity;
        }

        public int getLine() {
            return line;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            String location = line > 0 ? "Ligne " + line + " : " : "";
            return severity + " — " + location + message;
        }
    }

    /**
     * Validates only project references that can be established from the
     * script and the project's actual files.
     */
    public static List<Issue> validate(Path scriptFile, Path projectRoot) {
        List<Issue> issues = new ArrayList<>();

        if (scriptFile == null || !Files.isRegularFile(scriptFile)) {
            issues.add(new Issue(Severity.ERROR, 0,
                    "Le fichier DynaScript est introuvable."));
            return issues;
        }

        Path script = scriptFile.toAbsolutePath().normalize();
        Path root = projectRoot == null
                ? script.getParent()
                : projectRoot.toAbsolutePath().normalize();

        if (root == null || !Files.isDirectory(root)) {
            issues.add(new Issue(Severity.ERROR, 0,
                    "Le dossier du projet est introuvable."));
            return issues;
        }

        final List<String> lines;
        try {
            lines = Files.readAllLines(script);
        } catch (IOException ex) {
            issues.add(new Issue(Severity.ERROR, 0,
                    "Impossible de lire le script : " + ex.getMessage()));
            return issues;
        }

        String declaredPlayer2D = null;
        List<AnimationReference> animationReferences = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String code = removeComment(lines.get(i)).trim();
            if (code.isEmpty()) {
                continue;
            }

            Matcher baseMatcher = ANIMATION_BASE.matcher(code);
            if (baseMatcher.matches()) {
                declaredPlayer2D = baseMatcher.group(1).trim();
            }

            Matcher animationMatcher = ANIMATION_CALL.matcher(code);
            while (animationMatcher.find()) {
                animationReferences.add(
                        new AnimationReference(i + 1, animationMatcher.group(1)));
            }

            Matcher sceneMatcher = LOAD_SCENE.matcher(code);
            if (sceneMatcher.matches()) {
                Path scene = resolveProjectFile(root, script.getParent(),
                        sceneMatcher.group(1));
                if (scene == null || !Files.isRegularFile(scene)) {
                    issues.add(new Issue(Severity.ERROR, i + 1,
                            "Scène introuvable : " + sceneMatcher.group(1)));
                }
            }
        }

        if (!animationReferences.isEmpty()) {
            Path player2D = resolvePlayer2D(root, script, declaredPlayer2D, issues);

            if (player2D != null) {
                try {
                    Set<String> availableAnimations = readAnimationNames(player2D);

                    for (AnimationReference reference : animationReferences) {
                        if (!availableAnimations.contains(reference.name)) {
                            issues.add(new Issue(Severity.ERROR, reference.line,
                                    "Animation \"" + reference.name
                                    + "\" absente de " + player2D.getFileName()
                                    + ". Animations disponibles : "
                                    + (availableAnimations.isEmpty()
                                        ? "(aucune)" : String.join(", ", availableAnimations))));
                        }
                    }
                } catch (IOException ex) {
                    issues.add(new Issue(Severity.ERROR, 0,
                            "Fichier Player2D illisible : " + player2D
                            + " (" + ex.getMessage() + ")"));
                }
            }
        }

        return issues;
    }

    private static Path resolvePlayer2D(
            Path root,
            Path script,
            String declaredName,
            List<Issue> issues) {

        List<Path> candidates = new ArrayList<>();

        boolean explicitFileReference = declaredName != null
                && !declaredName.isBlank()
                && declaredName.toLowerCase().endsWith(".player2d");

        if (explicitFileReference) {
            Path direct = resolveProjectFile(root, script.getParent(), declaredName);
            if (direct != null && Files.isRegularFile(direct)) {
                return direct;
            }

            String wantedFileName = Path.of(declaredName).getFileName().toString();
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString()
                                .equalsIgnoreCase(wantedFileName))
                        .forEach(candidates::add);
            } catch (IOException ignored) {
            }
        } else {
            // Names such as "Player.2DPlayer" describe the animation source,
            // not necessarily a filename. In that case use the matching
            // script/Player2D basename convention used by Nera's compiler.
            String scriptName = script.getFileName().toString();
            int dot = scriptName.lastIndexOf('.');
            String baseName = dot > 0 ? scriptName.substring(0, dot) : scriptName;
            String wanted = baseName + ".Player2D";

            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString()
                                .equalsIgnoreCase(wanted))
                        .forEach(candidates::add);
            } catch (IOException ignored) {
            }
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (candidates.size() > 1) {
            issues.add(new Issue(Severity.WARNING, 0,
                    "Plusieurs fichiers Player2D correspondent. "
                    + "Précise Animation.BasedOn avec le nom du fichier voulu."));
        } else {
            issues.add(new Issue(Severity.ERROR, 0,
                    declaredName == null
                    ? "Aucun fichier .Player2D correspondant au nom du script n'a été trouvé."
                    : "Fichier .Player2D introuvable : " + declaredName));
        }

        return null;
    }

    /**
     * Reads the same binary layout written by Player2DCompiler.
     * It reads animation names and safely skips the encoded PNG frames.
     */
    private static Set<String> readAnimationNames(Path file) throws IOException {
        Set<String> names = new LinkedHashSet<>();

        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            String magic = input.readUTF();
            if (!MAGIC.equals(magic)) {
                throw new IOException("signature NERA_PLAYER2D invalide");
            }

            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("version Player2D non prise en charge : " + version);
            }

            input.readUTF(); // Player name
            int animationCount = input.readInt();

            if (animationCount < 1 || animationCount > 1000) {
                throw new IOException("nombre d'animations invalide");
            }

            for (int a = 0; a < animationCount; a++) {
                String animationName = input.readUTF();
                names.add(animationName);

                input.readInt(); // width
                input.readInt(); // height

                int frameCount = input.readInt();
                if (frameCount < 1 || frameCount > 100000) {
                    throw new IOException("nombre d'images invalide pour " + animationName);
                }

                for (int f = 0; f < frameCount; f++) {
                    input.readInt(); // duration
                    int imageLength = input.readInt();

                    if (imageLength <= 0 || imageLength > 100_000_000) {
                        throw new IOException("taille d'image invalide");
                    }

                    int remaining = imageLength;
                    while (remaining > 0) {
                        int skipped = input.skipBytes(remaining);
                        if (skipped <= 0) {
                            throw new IOException("fichier Player2D incomplet");
                        }
                        remaining -= skipped;
                    }
                }
            }
        }

        return names;
    }

    private static Path resolveProjectFile(Path root, Path scriptFolder, String reference) {
        try {
            Path requested = Path.of(reference);
            if (requested.isAbsolute()) {
                Path normalized = requested.normalize();
                return Files.exists(normalized) ? normalized : null;
            }

            Path fromScript = scriptFolder == null
                    ? null : scriptFolder.resolve(requested).normalize();
            if (fromScript != null && fromScript.startsWith(root)
                    && Files.exists(fromScript)) {
                return fromScript;
            }

            Path fromRoot = root.resolve(requested).normalize();
            if (fromRoot.startsWith(root) && Files.exists(fromRoot)) {
                return fromRoot;
            }
        } catch (RuntimeException ignored) {
            // Invalid path text is reported as a missing project reference.
        }
        return null;
    }

    private static String removeComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '/' && i + 1 < line.length()
                    && line.charAt(i + 1) == '/') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static final class AnimationReference {
        final int line;
        final String name;

        AnimationReference(int line, String name) {
            this.line = line;
            this.name = name;
        }
    }

}
