
package nera;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import nera.DynaScript.Compiler.DynaScriptValidator;

import javax.swing.event.UndoableEditListener;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class DynaScriptEditor extends JPanel {

    private static final String DEFAULT_CODE =
        "On Start:\n" +
        "    Player :\n" +
        "        (gravity, max jump height, max speed)\n" +
        "        // Input\n" +
        "        Up : Z / ⬆️ / W\n" +
        "        Down : S / ⬇️\n" +
        "        Left: Q / A / ⬅️\n" +
        "        Right: D / ➡️\n" +
        "        Jump: Space\n" +
        "        //Animations\n" +
        "        Animation.BasedOn.Player.gltf\n" +
        "        If Player Run (Up, Down, Left or Right):\n" +
        "            anim.play (\"Walk\") or (\"Run\") in huge speed\n" +
        "        If Player Jump:\n" +
        "            anim.play (\"Jump\")\n" +
        "        If Player touch Enemy:\n" +
        "            anim.play (\"Hurt1\") or (\"Hurt2\") for the last try\n" +
        "        Else:\n" +
        "            anim.play (\"Idle\")\n";
    
    private static class EditorIssue {

    private final int line;
    private final String message;
    private final boolean warning;

    public EditorIssue(int line, String message, boolean warning) {
        this.line = line;
        this.message = message;
        this.warning = warning;
    }

    public int getLine() {
        return line;
    }

    public boolean isWarning() {
        return warning;
    }

    @Override
    public String toString() {
        return (warning ? "[WARNING] " : "[ERROR] ") + message;
    }
}


    private final File file;
    private final Runnable changeListener;

    private final JTextArea codeArea;
    private final UndoManager undoManager = new UndoManager();
    private final JTextArea lineNumbers;

    private final DynaScriptAnalyzer analyzer =
        new DynaScriptAnalyzer();

    private final DefaultListModel<EditorIssue> errorModel =
    new DefaultListModel<>();

private final JList<EditorIssue> errorList =
    new JList<>(errorModel);

private final Path projectRoot;

    private final JLabel errorTitle =
        new JLabel("ERRORS");

    private boolean modified = false;
    private boolean loading = false;
    

    



    public DynaScriptEditor(
        File file,
        File projectDirectory,
        Runnable changeListener
) throws IOException {

    this.file = file;
    this.changeListener = changeListener;

    this.projectRoot = projectDirectory != null
        ? projectDirectory.toPath()
        : file.getParentFile().toPath();

        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        codeArea = new JTextArea();

        codeArea.setFont(new Font("Consolas", Font.PLAIN, 17));
        codeArea.setBackground(new Color(30, 30, 30));
        codeArea.setForeground(Color.WHITE);
        codeArea.setCaretColor(Color.WHITE);
        codeArea.setSelectionColor(new Color(65, 95, 145));
        codeArea.setSelectedTextColor(Color.WHITE);
        codeArea.setTabSize(4);
        codeArea.setLineWrap(false);
        codeArea.setMargin(new Insets(12, 12, 12, 12));

        lineNumbers = new JTextArea("1");
        lineNumbers.setEditable(false);
        lineNumbers.setFocusable(false);
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 17));
        lineNumbers.setBackground(new Color(38, 38, 38));
        lineNumbers.setForeground(new Color(130, 130, 130));
        lineNumbers.setMargin(new Insets(12, 8, 12, 8));

        // LOAD FILE

        loading = true;

        if (file.exists()) {

            String existingCode = Files.readString(
                file.toPath(),
                StandardCharsets.UTF_8
            );

            if (existingCode.contains("# DYNA SCRIPT - PLAYER")
                    || existingCode.contains("Object \"Player\"")) {

                codeArea.setText(DEFAULT_CODE);

                Files.writeString(
                    file.toPath(),
                    DEFAULT_CODE,
                    StandardCharsets.UTF_8
                );

            } else {
                codeArea.setText(existingCode);
            }

        } else {

            codeArea.setText(DEFAULT_CODE);

            File parent = file.getParentFile();

            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            Files.writeString(
                file.toPath(),
                DEFAULT_CODE,
                StandardCharsets.UTF_8
            );
        }

        updateLineNumbers();

        // Loading is finished. Future edits can now be analyzed.
        loading = false;
        codeArea.getDocument().addUndoableEditListener(
    e -> undoManager.addEdit(e.getEdit())
);

        // EDITOR

        JScrollPane codeScrollPane =
            new JScrollPane(codeArea);

        codeScrollPane.setRowHeaderView(lineNumbers);

        codeScrollPane.getViewport().setBackground(
            new Color(30, 30, 30)
        );

        codeScrollPane.setBorder(
            BorderFactory.createEmptyBorder()
        );

        // ERRORS PANEL

        JPanel errorsPanel = new JPanel(new BorderLayout());

        errorsPanel.setBackground(new Color(35, 35, 35));
        errorsPanel.setPreferredSize(new Dimension(100, 145));

        errorTitle.setForeground(new Color(220, 220, 220));
        errorTitle.setFont(new Font("Consolas", Font.BOLD, 13));
        errorTitle.setBorder(
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        );

        errorList.setBackground(new Color(35, 35, 35));
        errorList.setForeground(new Color(230, 120, 120));
        errorList.setFont(new Font("Consolas", Font.PLAIN, 13));
        errorList.setSelectionBackground(new Color(75, 55, 55));
        errorList.setSelectionForeground(Color.WHITE);
        errorList.setFixedCellHeight(23);

        JScrollPane errorScrollPane =
            new JScrollPane(errorList);

        errorScrollPane.setBorder(
            BorderFactory.createEmptyBorder()
        );

        errorScrollPane.getViewport().setBackground(
            new Color(35, 35, 35)
        );

        errorsPanel.add(errorTitle, BorderLayout.NORTH);
        errorsPanel.add(errorScrollPane, BorderLayout.CENTER);

        JSplitPane editorSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            codeScrollPane,
            errorsPanel
        );

        editorSplit.setResizeWeight(0.82);
        editorSplit.setDividerSize(5);
        editorSplit.setBorder(BorderFactory.createEmptyBorder());

        add(editorSplit, BorderLayout.CENTER);

        // CLICK AN ERROR TO GO TO ITS LINE

        errorList.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {

                int index =
                    errorList.locationToIndex(e.getPoint());

                if (index < 0) {
                    return;
                }

                Rectangle bounds =
                    errorList.getCellBounds(index, index);

                if (bounds == null || !bounds.contains(e.getPoint())) {
                    return;
                }

                EditorIssue issue = errorModel.getElementAt(index);

goToLine(issue.getLine());
            }
        });

        // DOCUMENT LISTENER

        codeArea.getDocument().addDocumentListener(
            new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent e) {
                    documentChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    documentChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    documentChanged();
                }
            }
        );

        // INITIAL ANALYSIS

        analyzeCode();

        // CTRL + S

        KeyStroke saveKey = KeyStroke.getKeyStroke(
            KeyEvent.VK_S,
            InputEvent.CTRL_DOWN_MASK
        );

        // CTRL + Z : UNDO

KeyStroke undoKey = KeyStroke.getKeyStroke(
    KeyEvent.VK_Z,
    InputEvent.CTRL_DOWN_MASK
);

codeArea.getInputMap().put(undoKey, "undo");

codeArea.getActionMap().put("undo", new AbstractAction() {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        if (undoManager.canUndo()) {
            undoManager.undo();
        }
    }
});

// CTRL + Y : REDO

KeyStroke redoKey = KeyStroke.getKeyStroke(
    KeyEvent.VK_Y,
    InputEvent.CTRL_DOWN_MASK
);

codeArea.getInputMap().put(redoKey, "redo");

codeArea.getActionMap().put("redo", new AbstractAction() {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
    }
});

        codeArea.getInputMap().put(saveKey, "saveDynaScript");

        codeArea.getActionMap().put(
            "saveDynaScript",
            new AbstractAction() {

                @Override
                public void actionPerformed(
                        java.awt.event.ActionEvent e) {
                    save();
                }
            }
        );
    }
    

    private void documentChanged() {

        if (loading) {
            return;
        }

        modified = true;

        updateLineNumbers();
        analyzeCode();

        if (changeListener != null) {
            SwingUtilities.invokeLater(changeListener);
        }
    }

    private void analyzeCode() {

    errorModel.clear();

    int errors = 0;
    int warnings = 0;

    // Analyse de la syntaxe DynaScript
    List<DynaScriptAnalyzer.Issue> syntaxIssues =
        analyzer.analyze(codeArea.getText());

    for (DynaScriptAnalyzer.Issue issue : syntaxIssues) {

        errorModel.addElement(
            new EditorIssue(
                issue.getLine(),
                issue.toString(),
                false
            )
        );

        errors++;
    }

    // Vérification des références dans le projet
    List<DynaScriptValidator.Issue> projectIssues =
        DynaScriptValidator.validate(
            file.toPath(),
            projectRoot
        );

    for (DynaScriptValidator.Issue issue : projectIssues) {

        boolean warning =
            issue.getSeverity() == DynaScriptValidator.Severity.WARNING;

        errorModel.addElement(
            new EditorIssue(
                issue.getLine(),
                issue.getMessage(),
                warning
            )
        );

        if (warning) {
            warnings++;
        } else {
            errors++;
        }
    }

    // Affichage du résultat
    int total = errors + warnings;

    if (total == 0) {

        errorTitle.setText(
            "ERRORS (0) - No errors detected"
        );

        errorTitle.setForeground(
            new Color(130, 200, 140)
        );

    } else {

        errorTitle.setText(
            "ISSUES (" + errors + " errors, "
            + warnings + " warnings)"
        );

        errorTitle.setForeground(
            errors > 0
                ? new Color(230, 110, 110)
                : new Color(220, 180, 90)
        );
    }
}

    private void goToLine(int line) {

        try {

            int lineCount = codeArea.getLineCount();

            if (line < 1 || line > lineCount) {
                return;
            }

            int position =
                codeArea.getLineStartOffset(line - 1);

            codeArea.requestFocusInWindow();
            codeArea.setCaretPosition(position);

            Rectangle rectangle =
                codeArea.modelToView2D(position).getBounds();

            codeArea.scrollRectToVisible(rectangle);

        } catch (Exception e) {

            System.err.println(
                "Unable to go to line: " + e.getMessage()
            );
        }
    }

    private void updateLineNumbers() {

        int lineCount = codeArea.getLineCount();

        StringBuilder numbers = new StringBuilder();

        for (int i = 1; i <= lineCount; i++) {

            numbers.append(i);

            if (i < lineCount) {
                numbers.append("\n");
            }
        }

        lineNumbers.setText(numbers.toString());
    }

    public boolean save() {

        try {

            File parent = file.getParentFile();

            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            Files.writeString(
                file.toPath(),
                codeArea.getText(),
                StandardCharsets.UTF_8
            );

            modified = false;

            if (changeListener != null) {
                SwingUtilities.invokeLater(changeListener);
            }

            System.out.println(
                "DynaScript saved: " + file.getName()
            );

            return true;

        } catch (IOException e) {

            JOptionPane.showMessageDialog(
                this,
                "Unable to save DynaScript:\n" + e.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );

            return false;
        }
    }

    public File getFile() {
        return file;
    }

    public boolean isModified() {
        return modified;
    }

    public void focusEditor() {
        SwingUtilities.invokeLater(
            () -> codeArea.requestFocusInWindow()
        );
    }

    public String getCode() {
        return codeArea.getText();
    }

    public void setCode(String code) {
        codeArea.setText(code);
    }
}


