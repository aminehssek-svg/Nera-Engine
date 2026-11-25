
package nera.Core;

import com.jme3.anim.AnimComposer;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

public class AnimationViewer {

    private AnimComposer composer;

    public List<String> detectAnimations(Spatial model) {

        composer = null;

        if (model == null) {
            return new ArrayList<>();
        }

        composer = findComposer(model);

        List<String> animations = new ArrayList<>();

        if (composer != null) {
            animations.addAll(
                composer.getAnimClipsNames()
            );
        }

        System.out.println(
            "AnimationViewer: " + animations
        );

        return animations;
    }

    private AnimComposer findComposer(Spatial spatial) {

        AnimComposer found =
            spatial.getControl(AnimComposer.class);

        if (found != null) {
            return found;
        }

        if (spatial instanceof Node) {

            for (Spatial child : ((Node) spatial).getChildren()) {

                found = findComposer(child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public boolean play(String animationName) {

        if (composer == null || animationName == null) {
            return false;
        }

        if (!composer.hasAnimClip(animationName)) {
            return false;
        }

        composer.setCurrentAction(animationName);

        System.out.println(
            "Playing animation: " + animationName
        );

        return true;
    }

    public boolean hasAnimations() {
        return composer != null
            && !composer.getAnimClipsNames().isEmpty();
    }

    public void clear() {
        composer = null;
    }
}
