
package nera;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynaScriptAnalyzer {

    public static class Issue {

        private final int line;
        private final String message;

        public Issue(int line, String message) {
            this.line = line;
            this.message = message;
        }

        public int getLine() {
            return line;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "Line " + line + ": " + message;
        }
    }

    private static final Pattern FUNCTION_PATTERN =
        Pattern.compile(
            "^Function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\((.*)\\))?\\s*:?$"
        );

    private static final Pattern BLOCK_PATTERN =
        Pattern.compile("^(If|While)\\s+(.+):$");

    private static final Pattern VARIABLE_PATTERN =
        Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*\\s*:\\s*.+$"
        );

    private static final Pattern PLAYER_PATTERN =
        Pattern.compile("^Player\\s*:$");

    private static final Pattern COLLISION_ANIMATION_PATTERN =
        Pattern.compile(
            "^(Collision\\.BasedOn\\.AnimationOf\\.|CollisionBasedOn\\.AnimationOf\\.).+$",
            Pattern.CASE_INSENSITIVE
        );

    private static final Pattern ANIMATION_BASED_PATTERN =
        Pattern.compile(
            "^(Animation\\.BasedOn\\.|AnimationsBasedOn\\.|AnimationBasedOn\\.).+$",
            Pattern.CASE_INSENSITIVE
        );

    private static final Pattern ANIM_PLAY_PATTERN =
        Pattern.compile(
            "^anim\\.play\\s*\\(.*\\)\\s*$",
            Pattern.CASE_INSENSITIVE
        );

    public List<Issue> analyze(String source) {

        List<Issue> issues = new ArrayList<>();

        if (source == null || source.isEmpty()) {
            return issues;
        }

        String[] lines = source.split("\\R", -1);

        Set<String> functions = new HashSet<>();

        int[] blockIndent = new int[lines.length + 1];
        int blockCount = 0;
        int playerIndent = -1;

        for (int i = 0; i < lines.length; i++) {

            String original = lines[i];
            String line = original.trim();

            int number = i + 1;
            int indent = indentation(original);

            if (line.isEmpty()
                    || line.startsWith("#")
                    || line.startsWith("//")) {
                continue;
            }

            if (playerIndent >= 0 && indent <= playerIndent) {
                playerIndent = -1;
            }

            // Player uses its own syntax.
            if (playerIndent >= 0) {
                continue;
            }

            while (blockCount > 0
                    && indent <= blockIndent[blockCount - 1]) {
                blockCount--;
            }

            if (line.equals("On Start:")) {
                blockIndent[blockCount++] = indent;
                continue;
            }

            if (PLAYER_PATTERN.matcher(line).matches()) {
                playerIndent = indent;
                blockIndent[blockCount++] = indent;
                continue;
            }

            // Player Dies
            if (line.equalsIgnoreCase("Player Dies")) {
                continue;
            }

            if (line.equalsIgnoreCase("Player Dies:")) {
                blockIndent[blockCount++] = indent;
                continue;
            }

            // Animation model declarations
            if (ANIMATION_BASED_PATTERN.matcher(line).matches()) {
                continue;
            }

            // Animation playback
            if (ANIM_PLAY_PATTERN.matcher(line).matches()) {
                continue;
            }

            // Collision animation model
            if (COLLISION_ANIMATION_PATTERN.matcher(line).matches()) {
                continue;
            }

            Matcher functionMatcher =
                FUNCTION_PATTERN.matcher(line);

            if (functionMatcher.matches()) {

                String name = functionMatcher.group(1);

                if (!functions.add(name)) {
                    add(
                        issues,
                        number,
                        "Function already declared: " + name
                    );
                }

                blockIndent[blockCount++] = indent;
                continue;
            }

            Matcher blockMatcher =
                BLOCK_PATTERN.matcher(line);

            if (blockMatcher.matches()) {
                blockIndent[blockCount++] = indent;
                continue;
            }

            if (line.equals("Else:")) {
                continue;
            }

            if (line.startsWith("Call ")) {

                if (!line.matches(
                        "^Call\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)$")) {

                    add(
                        issues,
                        number,
                        "Invalid function call."
                    );
                }

                continue;
            }

            if (line.startsWith("Print:")) {

                if (line.substring(6).trim().isEmpty()) {
                    add(
                        issues,
                        number,
                        "Print requires a value."
                    );
                }

                continue;
            }

            if (line.startsWith("Return:")) {
                continue;
            }

            if (line.startsWith("Wait:")) {

                checkNumber(
                    line.substring(5).trim(),
                    number,
                    "Invalid duration.",
                    issues
                );

                continue;
            }

            if (line.startsWith("Add ")
                    || line.startsWith("Remove ")
                    || line.startsWith("Multiply ")
                    || line.startsWith("Change ")) {

                if (!line.contains(":")) {
                    add(
                        issues,
                        number,
                        "Incomplete command: missing ':'."
                    );
                }

                continue;
            }

            if (VARIABLE_PATTERN.matcher(line).matches()) {
                continue;
            }

            if (line.endsWith(":")) {
                add(
                    issues,
                    number,
                    "Unknown command or block: " + line
                );
                continue;
            }

            add(
                issues,
                number,
                "Unrecognized instruction: " + line
            );
        }

        return issues;
    }

    private void checkNumber(
            String value,
            int line,
            String message,
            List<Issue> issues) {

        try {

            double number = Double.parseDouble(value);

            if (!Double.isFinite(number)) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            add(issues, line, message);
        }
    }

    private void add(
            List<Issue> issues,
            int line,
            String message) {

        issues.add(new Issue(line, message));
    }

    private int indentation(String line) {

        int count = 0;

        while (count < line.length()) {

            char c = line.charAt(count);

            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 4;
            } else {
                break;
            }
        }

        return count;
    }
}
