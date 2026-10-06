import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays the game's music: the menu track and the battle track, one at a time.
 * Songs are loaded on a background thread so the screen never waits for them,
 * and each one is loaded only once.
 */
public class MusicPlayer {

    private static final Map<String, Clip> clips = new HashMap<>();
    private static Clip current;

    // One background thread, so requests run in the order they were made
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "music");
        t.setDaemon(true);
        return t;
    });

    /** Loops the menu music, carrying on from where it was paused. */
    public static void playMenu() {
        worker.execute(() -> play(GamePaths.MENU_MUSIC, false));
    }

    /** Loops the battle music from the start. */
    public static void playBattle() {
        worker.execute(() -> play(GamePaths.BATTLE_MUSIC, true));
    }

    /** Pauses whatever is playing. */
    public static void stop() {
        worker.execute(() -> {
            if (current != null) current.stop();
        });
    }

    private static void play(String path, boolean fromStart) {
        Clip clip = load(path);
        if (current != null && current != clip) current.stop();
        current = clip;
        if (clip == null) return;
        if (fromStart) clip.setFramePosition(0);
        if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    /** Loads a song the first time it's asked for. Null (silence) if it can't be read. */
    private static Clip load(String path) {
        if (clips.containsKey(path)) return clips.get(path);
        Clip clip = null;
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new File(path))) {
            clip = AudioSystem.getClip();
            clip.open(audio);
        } catch (Exception e) {
            System.err.println("Could not load music " + path + ": " + e.getMessage());
            clip = null;
        }
        clips.put(path, clip);
        return clip;
    }
}
