
package nera;

import nera.Core.EngineCore;
import nera.Core.Scene3DManager;
import nera.Core.Player2DCompiler;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Component;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

public class EditorWindow {

    private void show3DView() {

        mainViewCards.show(mainViewPanel, "3d");
        mainViewPanel.setBorder(
            BorderFactory.createTitledBorder("3D View")
        );

        assetCards.show(assetCardPanel, "assets");

        if (workspaceTabs.getTabCount() > 0) {
            workspaceTabs.setSelectedIndex(0);
        }

        mainViewPanel.revalidate();
        mainViewPanel.repaint();
    }

    private final JFrame window;
    private final Canvas previewCanvas;
    private final EngineCore engine;

    private JTree assetTree;
    private File projectDirectory;

    private File clipboardFile;
    private boolean clipboardIsCut = false;

    private boolean mouseCaptured = false;

    private JPanel assetCardPanel;
    private java.awt.CardLayout assetCards;

    private JPanel mainViewPanel;
    private java.awt.CardLayout mainViewCards;
    private JTabbedPane scriptTabs;

    private DefaultListModel<String> animationListModel;
    private JList<String> animationList;
    private JLabel animationMessage;

    private JTabbedPane workspaceTabs;

    private JLabel statusBar;

    private JButton playButton;
    private JButton stopButton;

    private final Map<File, DynaScriptEditor> openScripts =
        new HashMap<>();

    private final KeyEventDispatcher globalKeyDispatcher =
        this::dispatchGlobalKeyEvent;

    public EditorWindow(Canvas previewCanvas, EngineCore engine) {

    this.previewCanvas = previewCanvas;
    this.engine = engine;

    window = new JFrame("Nera Engine");

    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    window.setSize(1100, 700);
    window.setMinimumSize(new Dimension(800, 500));
    window.setLocationRelativeTo(null);

    createLayout();

    // Disable mouse capture when the window loses focus
    window.addWindowFocusListener(new java.awt.event.WindowAdapter() {

        @Override
        public void windowLostFocus(java.awt.event.WindowEvent e) {

            if (mouseCaptured) {

                mouseCaptured = false;

                engine.setMouseCaptured(false);

                statusBar.setText("Mouse capture disabled");
            }
        }
    });

    // Global keyboard shortcuts
    KeyboardFocusManager
        .getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(globalKeyDispatcher);
}

    

    // =====================================================
    // INTERFACE PRINCIPALE
    // =====================================================

    private void createLayout() {

        window.setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());

        topBar.setBorder(
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        );

        topBar.add(
            new JLabel("NERA ENGINE"),
            BorderLayout.WEST
        );

        topBar.add(
            new JLabel("Scene Editor"),
            BorderLayout.EAST
        );

        JPanel gameControls =
            new JPanel(new FlowLayout(FlowLayout.RIGHT));

        playButton = new JButton("▶ Play");
        stopButton = new JButton("■ Stop");

        stopButton.setEnabled(false);

        playButton.addActionListener(e -> startGame());
        stopButton.addActionListener(e -> stopGame());

        gameControls.add(playButton);
        gameControls.add(stopButton);

        topBar.add(gameControls, BorderLayout.CENTER);

        window.add(topBar, BorderLayout.NORTH);

        JPanel projectPanel = new JPanel(new BorderLayout());

        projectPanel.setPreferredSize(new Dimension(210, 0));

        projectPanel.setBorder(
            BorderFactory.createTitledBorder("Project")
        );

        projectPanel.add(
            new JLabel(
                "<html>Maps<br><br>Scripts<br><br>Textures</html>"
            ),
            BorderLayout.NORTH
        );

        // GRANDE ZONE CENTRALE : 3DView OU éditeur DynaScript

        mainViewCards = new java.awt.CardLayout();
        mainViewPanel = new JPanel(mainViewCards);
        mainViewPanel.setBorder(
            BorderFactory.createTitledBorder("3D View")
        );

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(previewCanvas, BorderLayout.CENTER);
        mainViewPanel.add(previewPanel, "3d");

        // Les scripts occupent exactement la même zone que 3DView.
        scriptTabs = new JTabbedPane();
        mainViewPanel.add(scriptTabs, "scripts");
        mainViewCards.show(mainViewPanel, "3d");

        // ASSETS

        JPanel assetsPanel = new JPanel(new BorderLayout());

        assetsPanel.setBorder(
            BorderFactory.createTitledBorder("Assets")
        );

        JPanel assetsToolbar = new JPanel(new BorderLayout());

        JPanel toolbarButtons = new JPanel();

        JButton openProjectButton =
            new JButton("Open Project Folder");

        JButton newFolderButton =
            new JButton("New Folder");

        JButton newDynaScriptButton =
            new JButton("New DynaScript");

        JButton saveAllButton =
            new JButton("Save All");

        JButton saveProjectButton =
            new JButton("Save Project");

        openProjectButton.addActionListener(
            e -> chooseProjectFolder()
        );

        newFolderButton.addActionListener(
            e -> createNewFolder()
        );

        newDynaScriptButton.addActionListener(
            e -> createNewDynaScript()
        );

        saveAllButton.addActionListener(
            e -> saveAllScripts()
        );

        saveProjectButton.addActionListener(
            e -> saveProject()
        );

        toolbarButtons.add(openProjectButton);
        toolbarButtons.add(newFolderButton);
        toolbarButtons.add(newDynaScriptButton);
        toolbarButtons.add(saveAllButton);
        toolbarButtons.add(saveProjectButton);

        assetsToolbar.add(toolbarButtons, BorderLayout.WEST);

        // ARBRE DES FICHIERS

        DefaultMutableTreeNode root =
            new DefaultMutableTreeNode("No project selected");

        assetTree = new JTree(root);

        assetTree.setCellRenderer(new AssetTreeRenderer());

        assetTree.setRootVisible(true);
        assetTree.setDragEnabled(true);
        assetTree.setDropMode(DropMode.ON);

        assetTree.setTransferHandler(
            new AssetTransferHandler()
        );

        createAssetContextMenu();
        setupKeyboardShortcuts();

        assetTree.addTreeSelectionListener(e -> {

            TreePath path = assetTree.getSelectionPath();

            if (path == null) {
                return;
            }

            DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) path.getLastPathComponent();

            if (!(node.getUserObject() instanceof AssetNode)) {
                return;
            }

            File file =
                ((AssetNode) node.getUserObject()).file;

            if (file.isDirectory()) {

                show3DView();
                engine.clearPreview();

                return;
            }

            previewAsset(file);
        });

        assetTree.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {

                if (e.getClickCount() != 2) {
                    return;
                }

                TreePath path = assetTree.getPathForLocation(
                    e.getX(),
                    e.getY()
                );

                if (path == null) {
                    return;
                }

                DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode) path.getLastPathComponent();

                if (!(node.getUserObject() instanceof AssetNode)) {
                    return;
                }

                File file =
                    ((AssetNode) node.getUserObject()).file;

                if (AssetFormats.isDynaScript(file)) {
                    openDynaScript(file);
                } else if (file.isFile()
                        && !isModelFile(file)
                        && !isScene3DFile(file)) {

                    openExternalFile(file);
                }
            }
        });

        JScrollPane assetScroll = new JScrollPane(assetTree);

        // CARTES ASSETS

        assetCards = new java.awt.CardLayout();

        assetCardPanel = new JPanel(assetCards);

        JPanel browserPanel = new JPanel(new BorderLayout());

        browserPanel.add(
            assetsToolbar,
            BorderLayout.NORTH
        );

        browserPanel.add(
            assetScroll,
            BorderLayout.CENTER
        );

        assetCardPanel.add(browserPanel, "assets");

        // CARTE ANIMATIONS

        JPanel animationPanel = new JPanel(new BorderLayout());

        JButton backButton = new JButton("← Assets");

        backButton.addActionListener(
            e -> showAssetBrowser()
        );

        animationPanel.add(
            backButton,
            BorderLayout.NORTH
        );

        animationListModel = new DefaultListModel<>();

        animationList = new JList<>(animationListModel);

        animationList.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION
        );

        animationList.addListSelectionListener(e -> {

            if (e.getValueIsAdjusting()) {
                return;
            }

            String selected = animationList.getSelectedValue();

            if (selected != null) {
                engine.playAnimation(selected);
            }
        });

        animationPanel.add(
            new JScrollPane(animationList),
            BorderLayout.CENTER
        );

        animationMessage = new JLabel(
            "Select an animation to play it.",
            SwingConstants.CENTER
        );

        animationPanel.add(
            animationMessage,
            BorderLayout.SOUTH
        );

        assetCardPanel.add(animationPanel, "animations");

        showAssetBrowser();

        // ONGLET ASSETS

        workspaceTabs = new JTabbedPane();

        workspaceTabs.addTab("Assets", assetCardPanel);

        assetsPanel.add(
            workspaceTabs,
            BorderLayout.CENTER
        );

        // SÉPARATION VERTICALE

        JSplitPane rightSplit = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            mainViewPanel,
            assetsPanel
        );

        rightSplit.setResizeWeight(0.75);
        rightSplit.setDividerLocation(450);

        // SÉPARATION HORIZONTALE

        JSplitPane mainSplit = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            projectPanel,
            rightSplit
        );

        mainSplit.setDividerLocation(210);
        mainSplit.setResizeWeight(0);

        window.add(mainSplit, BorderLayout.CENTER);

        // BARRE DE STATUT

        statusBar = new JLabel("Ready");

        statusBar.setBorder(
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        );

        window.add(statusBar, BorderLayout.SOUTH);
    }


    // =====================================================
    // SAUVEGARDE
    // =====================================================

    private boolean saveAllScripts() {

        for (DynaScriptEditor editor : openScripts.values()) {

            if (!editor.save()) {
                JOptionPane.showMessageDialog(
                    window,
                    "Unable to save a DynaScript file.",
                    "Save error",
                    JOptionPane.ERROR_MESSAGE
                );

                return false;
            }
        }

        statusBar.setText("All scripts saved.");

        return true;
    }

    
private void saveProject() {

    if (projectDirectory == null) {
        JOptionPane.showMessageDialog(
            window,
            "No project is currently open."
        );
        return;
    }

    if (!saveAllScripts()) {
        return;
    }

    try {

        List<Path> compiledPlayers =
            Player2DCompiler.compileProject(
                projectDirectory.toPath()
            );

        refreshAssets();

        statusBar.setText(
            "Project saved. "
            + compiledPlayers.size()
            + " Player2D asset(s) compiled."
        );

    } catch (IOException ex) {

        JOptionPane.showMessageDialog(
            window,
            "Unable to compile Player2D assets:\n"
                + ex.getMessage(),
            "Compilation error",
            JOptionPane.ERROR_MESSAGE
        );

        ex.printStackTrace();
    }
}


    // =====================================================
    // AFFICHAGE DES DYNA SCRIPTS
    // =====================================================

    private void showDynaScriptView(DynaScriptEditor editor) {

        int index = scriptTabs.indexOfComponent(editor);

        if (index >= 0) {
            scriptTabs.setSelectedIndex(index);
        }

        mainViewPanel.setBorder(
            BorderFactory.createTitledBorder("DynaScript")
        );
        mainViewCards.show(mainViewPanel, "scripts");
        mainViewPanel.revalidate();
        mainViewPanel.repaint();
    }

    
private void openDynaScript(File file) {
    try {
        final File scriptFile = file.getCanonicalFile();

        DynaScriptEditor existing = openScripts.get(scriptFile);

        if (existing != null) {
            showDynaScriptView(existing);
            return;
        }

        DynaScriptEditor editor = new DynaScriptEditor(
    scriptFile,
    projectDirectory,
    () -> updateScriptTabTitle(scriptFile)
);

        openScripts.put(scriptFile, editor);
        scriptTabs.addTab(scriptFile.getName(), editor);

        int index = scriptTabs.indexOfComponent(editor);

        JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tab.setOpaque(false);

        JLabel title = new JLabel(scriptFile.getName());

        JButton closeButton = new JButton("×");
        closeButton.setMargin(new Insets(0, 4, 0, 4));
        closeButton.setFocusable(false);
        closeButton.addActionListener(e -> closeDynaScript(scriptFile));

        tab.add(title);
        tab.add(closeButton);

        scriptTabs.setTabComponentAt(index, tab);
        scriptTabs.setSelectedIndex(index);
        showDynaScriptView(editor);

        statusBar.setText("Opened: " + scriptFile.getName());

    } catch (IOException ex) {
        JOptionPane.showMessageDialog(
            window,
            "Unable to open this DynaScript:\n" + ex.getMessage(),
            "Open error",
            JOptionPane.ERROR_MESSAGE
        );
    }
}

    // =====================================================
    // IDENTIFICATION DES SCRIPTS
    // =====================================================

    private boolean isPlayerScript(File file) {

        String name = file.getName().toLowerCase();

        return name.contains("player")
            || name.contains("dynabot");
    }

    private boolean isEnemyScript(File file) {

        String name = file.getName().toLowerCase();

        return name.contains("enemy")
            || name.contains("badbot");
    }

    private boolean isMapScript(File file) {

        String name = file.getName();

        int dot = name.lastIndexOf('.');

        if (dot > 0) {
            name = name.substring(0, dot);
        }

        File glb = new File(file.getParentFile(), name + ".glb");
        File scene = new File(file.getParentFile(), name + ".scene");

        return glb.isFile() || scene.isFile();
    }

    // =====================================================
    // MODÈLES DE DYNA SCRIPT
    // =====================================================

    private String getDynaScriptTemplate(File file) {

        if (isPlayerScript(file)) {

            return """
                # Player Script

                object Player

                start
                    speed = 5

                update
                    move
                """;
        }

        if (isEnemyScript(file)) {

            return """
                # Enemy Script

                object Enemy

                start
                    speed = 3

                update
                    follow Player
                """;
        }

        if (isMapScript(file)) {

            return getSceneDynaScriptTemplate(file);
        }

        return """
            # DynaScript

            object NewObject

            start
                print "Hello Nera"

            update
                pass
            """;
    }

    private File findMatchingScene3D(File scriptFile) {

        String name = scriptFile.getName();

        int dot = name.lastIndexOf('.');

        if (dot > 0) {
            name = name.substring(0, dot);
        }

        File scene = new File(
            scriptFile.getParentFile(),
            name + ".scene"
        );

        return scene.isFile() ? scene : null;
    }

    private String getSceneDynaScriptTemplate(File scriptFile) {

        File sceneFile = findMatchingScene3D(scriptFile);

        if (sceneFile == null) {

            return """
                # Map Script

                map NewMap

                start
                    print "Map loaded"

                update
                    pass
                """;
        }

        try {

            List<String> lines = Files.readAllLines(
                sceneFile.toPath()
            );

            for (String line : lines) {

                if (line.startsWith("Map in Scene:")) {

                    String modelName =
                        line.substring("Map in Scene:".length()).trim();

                    return """
                        # Map Script

                        map %s

                        start
                            print "Map loaded"

                        update
                            pass
                        """.formatted(modelName);
                }
            }

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return """
            # Map Script

            map NewMap

            start
                print "Map loaded"

            update
                pass
            """;
    }

    private void updateScriptTabTitle(File file) {

        DynaScriptEditor editor = openScripts.get(file);

        if (editor == null) {
            return;
        }

        int index = scriptTabs.indexOfComponent(editor);

        if (index >= 0) {
            scriptTabs.setTitleAt(index, file.getName());
        }
    }

    private void closeDynaScript(File file) {

        DynaScriptEditor editor = openScripts.get(file);

        if (editor == null) {
            return;
        }

        if (editor.isModified()) {

            int choice = JOptionPane.showConfirmDialog(
                window,
                "Save changes to " + file.getName() + "?",
                "Unsaved changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (choice == JOptionPane.CANCEL_OPTION
                    || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }

            if (choice == JOptionPane.YES_OPTION) {

                if (!editor.save()) {
                    return;
                }
            }
        }

        scriptTabs.remove(editor);
        openScripts.remove(file);

        if (scriptTabs.getTabCount() == 0) {
            show3DView();
        } else {
            int selected = scriptTabs.getSelectedIndex();
            if (selected >= 0) {
                Component selectedEditor = scriptTabs.getComponentAt(selected);
                if (selectedEditor instanceof DynaScriptEditor) {
                    showDynaScriptView((DynaScriptEditor) selectedEditor);
                }
            }
        }
    }

    // =====================================================
    // MENU CONTEXTUEL
    // =====================================================

    private void createAssetContextMenu() {

        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem cutItem = new JMenuItem("Cut");
        JMenuItem pasteItem = new JMenuItem("Paste");
        JMenuItem renameItem = new JMenuItem("Rename");
        JMenuItem deleteItem = new JMenuItem("Delete");

        openItem.addActionListener(e -> {

            File file = getSelectedAsset();

            if (file == null) {
                return;
            }

            if (AssetFormats.isDynaScript(file)) {
                openDynaScript(file);
            } else if (isModelFile(file) || isScene3DFile(file)) {
                previewAsset(file);
            } else {
                openExternalFile(file);
            }
        });

        copyItem.addActionListener(e -> copySelectedAsset());
        cutItem.addActionListener(e -> cutSelectedAsset());
        pasteItem.addActionListener(e -> pasteAsset());
        renameItem.addActionListener(e -> renameSelectedAsset());
        deleteItem.addActionListener(e -> deleteSelectedAsset());

        menu.add(openItem);
        menu.addSeparator();
        menu.add(copyItem);
        menu.add(cutItem);
        menu.add(pasteItem);
        menu.addSeparator();
        menu.add(renameItem);
        menu.add(deleteItem);

        assetTree.setComponentPopupMenu(menu);
    }

    // =====================================================
    // RACCOURCIS CLAVIER
    // =====================================================

    private void setupKeyboardShortcuts() {

        assetTree.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
            "deleteAsset"
        );

        assetTree.getActionMap().put(
            "deleteAsset",
            new AbstractAction() {

                @Override
                public void actionPerformed(
                    java.awt.event.ActionEvent e
                ) {
                    deleteSelectedAsset();
                }
            }
        );

        assetTree.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
            "renameAsset"
        );

        assetTree.getActionMap().put(
            "renameAsset",
            new AbstractAction() {

                @Override
                public void actionPerformed(
                    java.awt.event.ActionEvent e
                ) {
                    renameSelectedAsset();
                }
            }
        );
    }

    private boolean dispatchGlobalKeyEvent(KeyEvent e) {

    if (e.getID() != KeyEvent.KEY_PRESSED) {
        return false;
    }

    if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
            && (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0
            && e.getKeyCode() == KeyEvent.VK_S) {

        saveAllScripts();
        return true;
    }

    if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
            && e.getKeyCode() == KeyEvent.VK_D) {

        mouseCaptured = !mouseCaptured;

        engine.setMouseCaptured(mouseCaptured);

        if (mouseCaptured) {
            previewCanvas.requestFocusInWindow();
        }

        statusBar.setText(
            mouseCaptured
                ? "Mouse capture enabled"
                : "Mouse capture disabled"
        );

        return true;
    }

    return false;
}


    // =====================================================
    // PLAY / STOP
    // =====================================================

    private void startGame() {

        if (projectDirectory == null) {

            JOptionPane.showMessageDialog(
                window,
                "Open a project before starting the game.",
                "No project",
                JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        if (!saveAllScripts()) {
            return;
        }

        File selected = getSelectedAsset();

        if (selected == null) {

            JOptionPane.showMessageDialog(
                window,
                "Select a 3D scene or a map model first.",
                "No scene selected",
                JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        File sceneFile = null;

        if (isScene3DFile(selected)) {

            sceneFile = selected;

        } else if (AssetFormats.isMapModel(selected)) {

            String name = selected.getName();

            int dot = name.lastIndexOf('.');

            if (dot > 0) {
                name = name.substring(0, dot);
            }

            File matchingScene = new File(
                selected.getParentFile(),
                name + ".scene"
            );

            if (matchingScene.isFile()) {
                sceneFile = matchingScene;
            }
        }

        if (sceneFile == null) {

            JOptionPane.showMessageDialog(
                window,
                "No matching .3D scene was found.\n"
                    + "Select a .3D scene or a map model with a matching scene file.",
                "Scene not found",
                JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        try {

            Scene3DManager scene = new Scene3DManager();

            scene.load(sceneFile.toPath());

            engine.startGame(
                scene,
                projectDirectory,
                message -> SwingUtilities.invokeLater(() -> {

                    if (message != null && !message.isBlank()) {
                        statusBar.setText(message);
                    }

                    playButton.setEnabled(false);
                    stopButton.setEnabled(true);
                })
            );

            statusBar.setText(
                "Starting game: " + sceneFile.getName()
            );

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to start the game:\n" + ex.getMessage(),
                "Play error",
                JOptionPane.ERROR_MESSAGE
            );

            ex.printStackTrace();
        }
    }

    private void stopGame() {

        engine.stopGame();

        playButton.setEnabled(true);
        stopButton.setEnabled(false);

        statusBar.setText("Game stopped.");

        show3DView();
    }

    // =====================================================
    // FICHIERS ET MODÈLES
    // =====================================================

    private boolean isModelFile(File file) {

    String name = file.getName().toLowerCase();

    return name.endsWith(".obj")
        || name.endsWith(".gltf")
        || name.endsWith(".glb")
        || name.endsWith(".j3o")
        || name.endsWith(".fbx")
        || name.endsWith(".player2d");
}

    private void previewScene3D(File file) {

        try {

            Scene3DManager scene = new Scene3DManager();

            scene.load(file.toPath());

            engine.loadScene(
    scene,
    projectDirectory,
    message -> SwingUtilities.invokeLater(() -> {
        if (message != null && !message.isBlank()) {
            statusBar.setText(message);
        }
    })
);

            show3DView();

            statusBar.setText(
                "Scene loaded: " + file.getName()
            );

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to load scene:\n" + ex.getMessage(),
                "Scene error",
                JOptionPane.ERROR_MESSAGE
            );

            ex.printStackTrace();
        }
    }

    private boolean isScene3DFile(File file) {

        return file.isFile()
            && file.getName().toLowerCase().endsWith(".3d");
    }

    private void previewAsset(File file) {

        if (file == null || !file.isFile()) {
            return;
        }

        if (isScene3DFile(file)) {

            previewScene3D(file);
            return;
        }

        if (isModelFile(file)) {

            try {

                engine.previewModel(
    file,
    animations -> SwingUtilities.invokeLater(
        () -> showAnimations(animations)
    )
);

                show3DView();

                statusBar.setText(
                    "Model preview: " + file.getName()
                );

            } catch (Exception ex) {

                JOptionPane.showMessageDialog(
                    window,
                    "Unable to preview model:\n" + ex.getMessage(),
                    "Preview error",
                    JOptionPane.ERROR_MESSAGE
                );

                ex.printStackTrace();
            }

            return;
        }

        show3DView();
    }

    // =====================================================
    // ANIMATIONS
    // =====================================================

    private void showAnimations(List<String> animations) {

        animationListModel.clear();

        if (animations == null || animations.isEmpty()) {

            animationMessage.setText(
                "No animations found."
            );

        } else {

            for (String animation : animations) {
                animationListModel.addElement(animation);
            }

            animationMessage.setText(
                "Select an animation to play it."
            );
        }

        assetCards.show(assetCardPanel, "animations");
    }

    private void showAssetBrowser() {

        assetCards.show(assetCardPanel, "assets");
    }

    // =====================================================
    // OUVERTURE DE FICHIERS EXTERNES
    // =====================================================

    private void openExternalFile(File file) {

        try {

            if (!Desktop.isDesktopSupported()) {
                return;
            }

            Desktop.getDesktop().open(file);

        } catch (IOException ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to open file:\n" + ex.getMessage(),
                "Open error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // OUVERTURE D'UN PROJET
    // =====================================================

    private void chooseProjectFolder() {

        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle("Open Nera Project");

        chooser.setFileSelectionMode(
            JFileChooser.DIRECTORIES_ONLY
        );

        int result = chooser.showOpenDialog(window);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();

        if (!ProjectSecurity.isNeraProject(selected)) {

            JOptionPane.showMessageDialog(
                window,
                "This folder is not a valid Nera project.",
                "Invalid project",
                JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        for (File file : new ArrayList<>(openScripts.keySet())) {
            closeDynaScript(file);
        }

        projectDirectory = selected;

        refreshAssets();

        engine.clearPreview();

        statusBar.setText(
            "Project opened: " + projectDirectory.getName()
        );
    }

    // =====================================================
    // ACTUALISATION DES ASSETS
    // =====================================================

    private void refreshAssets() {

        if (projectDirectory == null) {
            return;
        }

        createMissing3DFiles();

        DefaultMutableTreeNode root =
            createFileTree(projectDirectory);

        assetTree.setModel(
            new DefaultTreeModel(root)
        );

        statusBar.setText("Assets refreshed.");
    }

    private void createMissing3DFiles() {

        if (projectDirectory == null) {
            return;
        }

        createMissing3DFilesInDirectory(projectDirectory);
    }

    private void createMissing3DFilesInDirectory(File directory) {

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                createMissing3DFilesInDirectory(file);
                continue;
            }

            if (!file.getName().toLowerCase().endsWith(".glb")) {
                continue;
            }

            String baseName = file.getName();

            int dot = baseName.lastIndexOf('.');

            if (dot > 0) {
                baseName = baseName.substring(0, dot);
            }

            File sceneFile = new File(
                file.getParentFile(),
                baseName + ".3D"
            );

            if (!sceneFile.exists()) {

                try {

                    Files.writeString(
                        sceneFile.toPath(),
                        "Map in 3D: " + file.getName()
                            + System.lineSeparator()
                    );

                } catch (IOException ex) {

                    ex.printStackTrace();
                }
            }

            if (!hasMatchingDynaScript(file)) {

                File scriptFile = new File(
                    file.getParentFile(),
                    baseName + ".dyna"
                );

                try {

                    Files.writeString(
                        scriptFile.toPath(),
                        getDynaScriptTemplate(scriptFile)
                    );

                } catch (IOException ex) {

                    ex.printStackTrace();
                }
            }
        }
    }

    private boolean hasMatchingDynaScript(File modelFile) {

        String name = modelFile.getName();

        int dot = name.lastIndexOf('.');

        if (dot > 0) {
            name = name.substring(0, dot);
        }

        File script = new File(
            modelFile.getParentFile(),
            name + ".dyna"
        );

        return script.isFile();
    }


    // =====================================================
    // CRÉATION DE L'ARBRE DES FICHIERS
    // =====================================================

    private DefaultMutableTreeNode createFileTree(File directory) {

        DefaultMutableTreeNode root =
            new DefaultMutableTreeNode(
                new AssetNode(directory)
            );

        File[] files = directory.listFiles();

        if (files == null) {
            return root;
        }

        Arrays.sort(files, (a, b) -> {

            if (a.isDirectory() && !b.isDirectory()) {
                return -1;
            }

            if (!a.isDirectory() && b.isDirectory()) {
                return 1;
            }

            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {

            if (file.isDirectory()) {

                root.add(createFileTree(file));

            } else {

                root.add(
                    new DefaultMutableTreeNode(
                        new AssetNode(file)
                    )
                );
            }
        }

        return root;
    }

    // =====================================================
    // SÉLECTION DES ASSETS
    // =====================================================

    private File getSelectedAsset() {

        TreePath path = assetTree.getSelectionPath();

        if (path == null) {
            return null;
        }

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode) path.getLastPathComponent();

        if (!(node.getUserObject() instanceof AssetNode)) {
            return null;
        }

        return ((AssetNode) node.getUserObject()).file;
    }

    private File getSelectedFolderDestination() {

        File selected = getSelectedAsset();

        if (selected == null) {
            return projectDirectory;
        }

        if (selected.isDirectory()) {
            return selected;
        }

        return selected.getParentFile();
    }

    private File getDropDestination() {

        TreePath path = assetTree.getSelectionPath();

        if (path == null) {
            return projectDirectory;
        }

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode) path.getLastPathComponent();

        if (!(node.getUserObject() instanceof AssetNode)) {
            return projectDirectory;
        }

        File selected =
            ((AssetNode) node.getUserObject()).file;

        return selected.isDirectory()
            ? selected
            : selected.getParentFile();
    }

    // =====================================================
    // COPIER / COUPER / COLLER
    // =====================================================

    private void copySelectedAsset() {

        File selected = getSelectedAsset();

        if (selected == null) {
            return;
        }

        clipboardFile = selected;
        clipboardIsCut = false;

        statusBar.setText(
            "Copied: " + selected.getName()
        );
    }

    private void cutSelectedAsset() {

        File selected = getSelectedAsset();

        if (selected == null) {
            return;
        }

        clipboardFile = selected;
        clipboardIsCut = true;

        statusBar.setText(
            "Cut: " + selected.getName()
        );
    }

    private void pasteAsset() {

        if (clipboardFile == null || !clipboardFile.exists()) {
            return;
        }

        File destination = getSelectedFolderDestination();

        if (destination == null) {
            return;
        }

        File target = new File(
            destination,
            clipboardFile.getName()
        );

        if (target.equals(clipboardFile)) {
            return;
        }

        if (target.exists()) {

            target = createUniqueFile(
                destination,
                clipboardFile.getName()
            );
        }

        try {

            if (clipboardIsCut) {

                Files.move(
                    clipboardFile.toPath(),
                    target.toPath()
                );

                statusBar.setText(
                    "Moved: " + target.getName()
                );

                clipboardFile = null;
                clipboardIsCut = false;

            } else {

                copyRecursively(
                    clipboardFile.toPath(),
                    target.toPath()
                );

                statusBar.setText(
                    "Pasted: " + target.getName()
                );
            }

            refreshAssets();

        } catch (IOException ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to paste asset:\n" + ex.getMessage(),
                "Paste error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void copyRecursively(Path source, Path destination)
            throws IOException {

        if (Files.isDirectory(source)) {

            Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(
                        Path dir,
                        BasicFileAttributes attrs
                    ) throws IOException {

                        Path target = destination.resolve(
                            source.relativize(dir)
                        );

                        Files.createDirectories(target);

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attrs
                    ) throws IOException {

                        Files.copy(
                            file,
                            destination.resolve(source.relativize(file)),
                            StandardCopyOption.REPLACE_EXISTING
                        );

                        return FileVisitResult.CONTINUE;
                    }
                }
            );

        } else {

            Files.copy(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    // =====================================================
    // RENOMMER
    // =====================================================

    private void renameSelectedAsset() {

        File selected = getSelectedAsset();

        if (selected == null) {
            return;
        }

        String newName = JOptionPane.showInputDialog(
            window,
            "New name:",
            selected.getName()
        );

        if (newName == null || newName.isBlank()) {
            return;
        }

        File target = new File(
            selected.getParentFile(),
            newName.trim()
        );

        if (target.exists()) {

            JOptionPane.showMessageDialog(
                window,
                "A file with this name already exists."
            );

            return;
        }

        try {

            Files.move(
                selected.toPath(),
                target.toPath()
            );

            refreshAssets();

            statusBar.setText(
                "Renamed: " + target.getName()
            );

        } catch (IOException ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to rename:\n" + ex.getMessage(),
                "Rename error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // SUPPRIMER
    // =====================================================

    private void deleteSelectedAsset() {

        File selected = getSelectedAsset();

        if (selected == null || selected.equals(projectDirectory)) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            window,
            "Delete " + selected.getName() + "?",
            "Confirm deletion",
            JOptionPane.YES_NO_OPTION
        );

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {

            deleteRecursively(selected.toPath());

            refreshAssets();

            statusBar.setText(
                "Deleted: " + selected.getName()
            );

        } catch (IOException ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to delete:\n" + ex.getMessage(),
                "Delete error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void deleteRecursively(Path path) throws IOException {

        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(
            path,
            new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs
                ) throws IOException {

                    Files.delete(file);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                    Path dir,
                    IOException exc
                ) throws IOException {

                    if (exc != null) {
                        throw exc;
                    }

                    Files.delete(dir);

                    return FileVisitResult.CONTINUE;
                }
            }
        );
    }


    // =====================================================
    // TRANSFERT DE FICHIERS
    // =====================================================

    private class AssetTransferHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {

            return support.isDataFlavorSupported(
                DataFlavor.javaFileListFlavor
            );
        }

        @Override
        public boolean importData(TransferSupport support) {

            if (!canImport(support)) {
                return false;
            }

            try {

                Transferable transferable =
                    support.getTransferable();

                List<File> files = (List<File>)
                    transferable.getTransferData(
                        DataFlavor.javaFileListFlavor
                    );

                File destination = getDropDestination();

                if (destination == null) {
                    return false;
                }

                for (File file : files) {
                    importDirectory(file, destination);
                }

                refreshAssets();

                return true;

            } catch (Exception ex) {

                JOptionPane.showMessageDialog(
                    window,
                    "Unable to import files:\n" + ex.getMessage(),
                    "Import error",
                    JOptionPane.ERROR_MESSAGE
                );

                return false;
            }
        }
    }

    private void moveAsset(File source, File destination)
            throws IOException {

        Files.move(
            source.toPath(),
            destination.toPath()
        );
    }

    private void importDirectory(File source, File destination)
            throws IOException {

        File target = new File(
            destination,
            source.getName()
        );

        if (target.exists()) {
            target = createUniqueFile(
                destination,
                source.getName()
            );
        }

        copyRecursively(
            source.toPath(),
            target.toPath()
        );
    }

    private void copyFile(File source, File destination)
            throws IOException {

        Files.copy(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    // =====================================================
    // NOMS UNIQUES
    // =====================================================

    private File createUniqueDirectory(
        File parent,
        String name
    ) {

        File target = new File(parent, name);

        int index = 1;

        while (target.exists()) {

            target = new File(
                parent,
                name + " (" + index + ")"
            );

            index++;
        }

        return target;
    }

    private File createUniqueFile(
        File parent,
        String name
    ) {

        File original = new File(parent, name);

        if (!original.exists()) {
            return original;
        }

        int dot = name.lastIndexOf('.');

        String baseName =
            dot > 0 ? name.substring(0, dot) : name;

        String extension =
            dot > 0 ? name.substring(dot) : "";

        int index = 1;

        File target;

        do {

            target = new File(
                parent,
                baseName + " (" + index + ")" + extension
            );

            index++;

        } while (target.exists());

        return target;
    }

    // =====================================================
    // NOUVEAU DOSSIER
    // =====================================================

    private void createNewFolder() {

        if (projectDirectory == null) {
            return;
        }

        File destination = getSelectedFolderDestination();

        if (destination == null) {
            destination = projectDirectory;
        }

        String name = JOptionPane.showInputDialog(
            window,
            "Folder name:"
        );

        if (name == null || name.isBlank()) {
            return;
        }

        File folder = createUniqueDirectory(
            destination,
            name.trim()
        );

        if (folder.mkdir()) {

            refreshAssets();

            statusBar.setText(
                "Folder created: " + folder.getName()
            );

        } else {

            JOptionPane.showMessageDialog(
                window,
                "Unable to create folder."
            );
        }
    }

    // =====================================================
    // NOUVEAU DYNA SCRIPT
    // =====================================================

    private void createNewDynaScript() {

        if (projectDirectory == null) {

            JOptionPane.showMessageDialog(
                window,
                "Open a project first."
            );

            return;
        }

        File destination = getSelectedFolderDestination();

        if (destination == null) {
            destination = projectDirectory;
        }

        String name = JOptionPane.showInputDialog(
            window,
            "DynaScript name:"
        );

        if (name == null || name.isBlank()) {
            return;
        }

        name = name.trim();

        if (!name.toLowerCase().endsWith(".dyna")) {
            name += ".dyna";
        }

        File script = createUniqueFile(
            destination,
            name
        );

        try {

            Files.writeString(
                script.toPath(),
                getDynaScriptTemplate(script)
            );

            refreshAssets();

            openDynaScript(script);

            statusBar.setText(
                "DynaScript created: " + script.getName()
            );

        } catch (IOException ex) {

            JOptionPane.showMessageDialog(
                window,
                "Unable to create DynaScript:\n" + ex.getMessage(),
                "Creation error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================
    // FENÊTRE
    // =====================================================

    public void showWindow() {

        window.setVisible(true);
    }

    // =====================================================
    // OBJET ASSET
    // =====================================================

    private static class AssetNode {

        private final File file;

        private AssetNode(File file) {
            this.file = file;
        }

        @Override
        public String toString() {
            return file.getName();
        }
    }

    // =====================================================
    // RENDU DE L'ARBRE
    // =====================================================

    private class AssetTreeRenderer
            extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {

            super.getTreeCellRendererComponent(
                tree,
                value,
                selected,
                expanded,
                leaf,
                row,
                hasFocus
            );

            DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) value;

            Object object = node.getUserObject();

            if (!(object instanceof AssetNode)) {
                return this;
            }

            File file = ((AssetNode) object).file;

            setText(file.getName());

            if (file.isDirectory()) {

                setIcon(UIManager.getIcon("FileView.directoryIcon"));

            } else {

                setIcon(UIManager.getIcon("FileView.fileIcon"));

                if (isModelFile(file)) {

                    try {

                        BufferedImage image =
                            ImageIO.read(file);

                        if (image != null) {

                            Image thumbnail = image.getScaledInstance(
                                24,
                                24,
                                Image.SCALE_SMOOTH
                            );

                            setIcon(new ImageIcon(thumbnail));
                        }

                    } catch (Exception ignored) {
                        // Certains formats 3D ne sont pas des images.
                    }
                }
            }

            return this;
        }
    }
}
