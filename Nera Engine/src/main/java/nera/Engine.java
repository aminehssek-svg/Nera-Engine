
package nera;

import com.jme3.system.AppSettings;
import nera.Core.EngineCore;

import javax.swing.SwingUtilities;
import java.awt.Canvas;

public class Engine {

    public static void main(String[] args) {

        AppSettings settings = new AppSettings(true);

        settings.setTitle("Nera Engine");
        settings.setResolution(1100, 700);
        settings.setVSync(true);
        settings.setSamples(4);

        EngineCore engine = new EngineCore();

        engine.setSettings(settings);
        engine.setShowSettings(false);

        Preview3D preview = new Preview3D(engine);
        Canvas canvas = preview.createCanvas();

        SwingUtilities.invokeLater(() -> {

            EditorWindow editor = new EditorWindow(canvas, engine);

            editor.showWindow();

            preview.start();
        });
    }
}
