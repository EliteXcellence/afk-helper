package com.afkhelper;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays the short synthesised alert sounds through RuneLite's AudioPlayer.
 * The whole sound (tones and the gaps between them) is rendered into one
 * in-memory WAV file, so nothing needs to sleep, and playback is handed to
 * RuneLite's shared executor rather than a thread of our own. Only one
 * alert plays at a time; a new one requested while another is still
 * sounding is skipped.
 */
@Slf4j
@Singleton
public class AfkHelperAudioPlayer
{
    private static final int SAMPLE_RATE = 44100;
    private static final int GAP_MS = 70;

    private final AudioPlayer audioPlayer;
    private final ScheduledExecutorService executor;

    private volatile boolean stopped;
    private volatile long soundingUntilMillis;

    @Inject
    public AfkHelperAudioPlayer(
            AudioPlayer audioPlayer,
            ScheduledExecutorService executor)
    {
        this.audioPlayer = audioPlayer;
        this.executor = executor;
    }

    /** Allow sounds to play (call when the plugin starts). */
    public void start()
    {
        stopped = false;
    }

    /** Refuse new sounds (call when the plugin stops). */
    public void stop()
    {
        stopped = true;
    }

    public void play(AfkHelperConfig.AlertSound sound, int volume)
    {
        if (sound == null || stopped || volume <= 0)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now < soundingUntilMillis)
        {
            return;
        }

        int[][] tones = getTones(sound);
        soundingUntilMillis = now + getDurationMillis(tones);

        // Volume is applied as gain (dB) by the AudioPlayer: 100% = 0 dB.
        float gain = (float) (20.0 * Math.log10(
                Math.min(100, volume) / 100.0));

        try
        {
            executor.submit(() -> playSound(tones, gain));
        }
        catch (RuntimeException ex)
        {
            soundingUntilMillis = 0;
            log.debug("Could not schedule alert sound", ex);
        }
    }

    private void playSound(int[][] tones, float gain)
    {
        if (stopped)
        {
            return;
        }

        // Audio is optional; alerts still work without sound.
        try
        {
            audioPlayer.play(
                    new ByteArrayInputStream(toWav(render(tones))),
                    gain);
        }
        catch (Exception ex)
        {
            log.debug("Could not play alert sound", ex);
        }
    }

    private int getDurationMillis(int[][] tones)
    {
        int total = 0;

        for (int i = 0; i < tones.length; i++)
        {
            total += tones[i][1];

            if (i < tones.length - 1)
            {
                total += GAP_MS;
            }
        }

        return total;
    }

    /** Renders every tone, separated by short silences, as 16-bit PCM. */
    private byte[] render(int[][] tones)
    {
        int gapSamples = SAMPLE_RATE * GAP_MS / 1000;
        int totalSamples = 0;

        for (int i = 0; i < tones.length; i++)
        {
            totalSamples += SAMPLE_RATE * tones[i][1] / 1000;

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
            int samples = SAMPLE_RATE * tones[t][1] / 1000;

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

    /** Wraps raw 16-bit mono PCM in a standard 44-byte WAV header. */
    private byte[] toWav(byte[] pcm)
    {
        ByteBuffer wav = ByteBuffer
                .allocate(44 + pcm.length)
                .order(ByteOrder.LITTLE_ENDIAN);

        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(36 + pcm.length);
        wav.put("WAVE".getBytes(StandardCharsets.US_ASCII));

        wav.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(16);                    // fmt chunk size
        wav.putShort((short) 1);           // PCM
        wav.putShort((short) 1);           // mono
        wav.putInt(SAMPLE_RATE);
        wav.putInt(SAMPLE_RATE * 2);       // byte rate
        wav.putShort((short) 2);           // block align
        wav.putShort((short) 16);          // bits per sample

        wav.put("data".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(pcm.length);
        wav.put(pcm);

        return wav.array();
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
}