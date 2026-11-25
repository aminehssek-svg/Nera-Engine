
package nera;

import java.io.File;
import java.io.IOException;

public final class ProjectSecurity {

    private static final String MARKER_NAME = "project.nera";

    private ProjectSecurity() {
    }

    // Vérifie si un dossier est un projet Nera
    public static boolean isNeraProject(File directory) {

        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        File marker = new File(directory, MARKER_NAME);

        return marker.isFile();
    }

    // Crée le fichier de reconnaissance d'un nouveau projet
    public static boolean createProjectMarker(File directory)
            throws IOException {

        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        File marker = new File(directory, MARKER_NAME);

        if (marker.exists()) {
            return marker.isFile();
        }

        return marker.createNewFile();
    }
}
