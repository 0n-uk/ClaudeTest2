import javax.sound.sampled.*;
import java.io.File;

public class MusicPlayer {

    private static Clip clip;

    public static void play() {
        if (clip != null) {
            if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
            return;
        }
        try {
            AudioInputStream audio = AudioSystem.getAudioInputStream(new File("Simple Scales.wav"));
            clip = AudioSystem.getClip();
            clip.open(audio);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored) {}
    }

    public static void stop() {
        if (clip != null && clip.isRunning()) clip.stop();
    }
}
