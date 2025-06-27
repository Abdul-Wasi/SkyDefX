// SoundPlayer.java (FIXED: Removed LineEvent.Type.END for broader compatibility)

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;

public class SoundPlayer {

    private static Clip startupClip = null; // Dedicated Clip for the background startup sound

    public static void playSound(String filename) {
        new Thread(() -> {
            try {
                URL soundURL = SoundPlayer.class.getResource("/res/sounds/" + filename); // Prioritize for 'res' in 'src'

                if (soundURL == null) {
                    soundURL = SoundPlayer.class.getResource("/sounds/" + filename); // Fallback for 'res' at root
                }

                if (soundURL == null) {
                    System.err.println("Sound file not found: " + filename + ". Make sure it's in 'src/res/sounds/' or 'res/sounds/' and your project is rebuilt.");
                    return;
                }

                System.out.println("SoundPlayer: Attempting to load sound from: " + soundURL);

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundURL);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);

                if (filename.equals("system_startup.wav")) {
                    // If a startup sound is already playing, stop it first
                    if (startupClip != null && startupClip.isRunning()) {
                        startupClip.stop();
                        startupClip.close();
                    }
                    startupClip = clip; // Store reference to the new startup clip
                }

                clip.start();

                // Changed: Removed '|| event.getType() == LineEvent.Type.END'
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        try {
                            audioStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // Clear reference if it was the startup clip that stopped
                        if (clip == startupClip) {
                            startupClip = null;
                        }
                    }
                });

            } catch (UnsupportedAudioFileException e) {
                System.err.println("Unsupported audio file format for " + filename + ". The javax.sound.sampled API primarily supports WAV files: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Error reading sound file " + filename + ": " + e.getMessage());
                e.printStackTrace();
            } catch (LineUnavailableException e) {
                System.err.println("Audio line unavailable for " + filename + ": " + e.getMessage());
            }
        }).start();
    }

    public static void stopStartupSound() {
        if (startupClip != null && startupClip.isRunning()) {
            startupClip.stop();
            startupClip.close();
            startupClip = null;
            System.out.println("SoundPlayer: Stopped system_startup.wav.");
        } else {
            System.out.println("SoundPlayer: system_startup.wav not found or not playing to stop.");
        }
    }

    public static void stopSound(String filename) {
        if (filename.equals("system_startup.wav")) {
            stopStartupSound();
        }
    }
    
    public static void stopAllSounds() {
        stopStartupSound();
        System.out.println("SoundPlayer: All managed sounds stopped.");
    }
}