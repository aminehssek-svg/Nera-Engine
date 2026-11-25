
package nera;

import java.io.File;
import java.util.Set;
import java.util.Locale;

public final class AssetFormats {

    private AssetFormats() {}

    public static final Set<String> CHARACTER_MODELS =
        Set.of("gltf");

    public static final Set<String> MAP_MODELS =
        Set.of("glb");

    public static final Set<String> OBJECT_MODELS =
        Set.of("obj");

    public static final Set<String> VIDEOS =
        Set.of("mp4");

    public static final Set<String> AUDIO =
        Set.of("mid", "midi", "mp3", "ogg", "wav");

    public static final Set<String> TEXTURES =
        Set.of("png", "jpg", "jpeg", "gif", "bmp");

    public static final Set<String> DYNASCRIPT =
        Set.of("dyna");

    public static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');

        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }

        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isCharacterModel(File file) {
        return CHARACTER_MODELS.contains(getExtension(file));
    }

    public static boolean isMapModel(File file) {
        return MAP_MODELS.contains(getExtension(file));
    }

    public static boolean isObjectModel(File file) {
        return OBJECT_MODELS.contains(getExtension(file));
    }

    public static boolean isVideo(File file) {
        return VIDEOS.contains(getExtension(file));
    }

    public static boolean isAudio(File file) {
        return AUDIO.contains(getExtension(file));
    }

    public static boolean isTexture(File file) {
        return TEXTURES.contains(getExtension(file));
    }

    public static boolean isDynaScript(File file) {
        return DYNASCRIPT.contains(getExtension(file));
    }

    public static boolean isSupported(File file) {
        String ext = getExtension(file);

        return CHARACTER_MODELS.contains(ext)
            || MAP_MODELS.contains(ext)
            || OBJECT_MODELS.contains(ext)
            || VIDEOS.contains(ext)
            || AUDIO.contains(ext)
            || TEXTURES.contains(ext)
            || DYNASCRIPT.contains(ext);
    }
}
