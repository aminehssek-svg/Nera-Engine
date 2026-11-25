
package nera;

import com.jme3.system.JmeCanvasContext;
import nera.Core.EngineCore;

import java.awt.Canvas;

public class Preview3D {

    private final EngineCore engine;
    private JmeCanvasContext context;
    private Canvas canvas;

    public Preview3D(EngineCore engine) {
        this.engine = engine;
    }

    public Canvas createCanvas() {

        engine.createCanvas();

        context = (JmeCanvasContext) engine.getContext();

        context.setSystemListener(engine);

        canvas = context.getCanvas();

        canvas.setFocusable(true);
        canvas.setIgnoreRepaint(true);

        return canvas;
    }

    public void start() {

        if (context == null) {
            throw new IllegalStateException(
                "The 3D Canvas has not been created."
            );
        }

        engine.startCanvas();
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
