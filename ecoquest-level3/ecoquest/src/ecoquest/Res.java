package ecoquest;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Finds data files (data/trash/*.csv, assets/...) even when the game is started from the wrong folder. */
final class Res {
    private Res() {
    }

    static Path find(String relative) {
        Path start = Path.of("").toAbsolutePath();
        for (Path p = start; p != null && start.relativize(p).getNameCount() <= 4; p = p.getParent()) {
            Path f = p.resolve(relative);
            if (Files.exists(f)) return f;
        }
        try { // next to the compiled classes: <project>/out/ecoquest -> <project>
            Path code = Path.of(Res.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path p = code; p != null; p = p.getParent()) {
                Path f = p.resolve(relative);
                if (Files.exists(f)) return f;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // fall through
        }
        return Path.of(relative);
    }
}
