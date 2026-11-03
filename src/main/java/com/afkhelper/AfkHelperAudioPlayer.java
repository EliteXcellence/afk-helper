package com.afkhelper;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays the short synthesised alert sounds.
 *
 * The whole sound (tones and the gaps between them) is rendered into one
 * buffer up front, so nothing needs to sleep, and playback runs on
 * RuneLite's shared executor instead of a thread of our own. Only one alert
 * plays at a time; a new one requested while another is playing is skipped.
 */
@Slf4j
@Singleton
public class AfkHelperAudioPlayer
{
    private static final float SAMPLE_RATE = 44100f;
    private static final int GAP_MS = 70;

    private final ScheduledExecutorService executor;
    private final AtomicBoolean playing = new AtomicBoolean();

    private volatile boolean stopped;
    private volatile SourceDataLine currentLine;

    @Inject
    public AfkHelperAudioPlayer(ScheduledExecutorService executor)
    {
        this.executor = executor;
    }

    /** Allow sounds to play (call when the plugin starts). */
    public void start()
    {
        stopped = false;
    }

    public void play(AfkHelperConfig.AlertSound sound, int volume)
    {
        if (sound == null || stopped)
        {
            return;
        }

        if (!playing.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            executor.submit(() ->
            {
                try
                {
                    playSound(sound, volume);
                }
                finally
                {
                    playing.set(false);
                }
            });
        }
        catch (RuntimeException ex)
        {
            playing.set(false);
            log.debug("Could not schedule alert sound", ex);
        }
    }

    private void playSound(AfkHelperConfig.AlertSound sound, int volume)
    {
        byte[] audio = render(
                getTones(sound),
                Math.max(0, Math.min(100, volume)) / 100f);

        if (audio.length == 0)
        {
            return;
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

        // Audio is optional; alerts still work without sound.
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format))
        {
            currentLine = line;

            if (stopped)
            {
                return;
            }

            line.open(format);
            line.start();
            line.write(audio, 0, audio.length);
            line.drain();
            line.stop();
        }
        catch (Exception ex)
        {
            log.debug("Could not play alert sound", ex);
        }
        finally
        {
            currentLine = null;
        }
    }

    /** Renders every tone, separated by short silences, into one buffer. */
    private byte[] render(int[][] tones, float volume)
    {
        int gapSamples = (int) (SAMPLE_RATE * GAP_MS / 1000);
        int totalSamples = 0;

        for (int i = 0; i < tones.length; i++)
        {
            totalSamples += (int) (SAMPLE_RATE * tones[i][1] / 1000);

            if (i < tones.length - 1)
            {
                totalSamples += gapSamples;
            }
        }

        byte[] buffer = new byte[totalSamples * 2];
        int offset = 0;

        for (int t = 0; t < tones.length; t++)
        {
            int frequency = tones[t][0];
            int samples = (int) (SAMPLE_RATE * tones[t][1] / 1000);

            for (int i = 0; i < samples; i++)
            {
                double angle = 2.0 * Math.PI * frequency * i / SAMPLE_RATE;

                double envelope = Math.min(
                        1.0,
                        Math.min(
                                i / (SAMPLE_RATE * 0.01),
                                (samples - i) / (SAMPLE_RATE * 0.02)
                        )
                );

                short value = (short) (
                        Math.sin(angle)
                                * Short.MAX_VALUE
                                * volume
                                * Math.max(0, envelope)
                );

                buffer[(offset + i) * 2] = (byte) (value & 0xff);
                buffer[(offset + i) * 2 + 1] = (byte) ((value >> 8) & 0xff);
            }

            // The gap after a tone is left as zeroes, i.e. silence.
            offset += samples + (t < tones.length - 1 ? gapSamples : 0);
        }

        return buffer;
    }

    private int[][] getTones(AfkHelperConfig.AlertSound sound)
    {
        switch (sound)
        {
            case DOUBLE_BEEP:
                return new int[][] {
                        {880, 140},
                        {880, 140}
                };

            case BELL:
                return new int[][] {
                        {1046, 350}
                };

            case CHIME:
                return new int[][] {
                        {784, 160},
                        {988, 160},
                        {1175, 260}
                };

            case SIREN:
                return new int[][] {
                        {700, 180},
                        {1100, 180},
                        {700, 180},
                        {1100, 180}
                };

            case BEEP:
            default:
                return new int[][] {
                        {880, 180}
                };
        }
    }

    /** Stop any sound that is playing and refuse new ones. */
    public void stop()
    {
        stopped = true;

        SourceDataLine line = currentLine;

        if (line != null)
        {
            // Closing also unblocks a drain() waiting on the playback thread.
            line.close();
        }
    }
}