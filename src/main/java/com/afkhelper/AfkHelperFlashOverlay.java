package com.afkhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AfkHelperFlashOverlay extends Overlay
{
    private final AfkHelperConfig config;

    private long flashStarted;
    private boolean flashing;

    public AfkHelperFlashOverlay(AfkHelperConfig config)
    {
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(false);
        setResettable(false);
    }

    public void triggerFlash()
    {
        if (!config.enabled() || !config.screenFlash())
        {
            return;
        }

        flashStarted = System.currentTimeMillis();
        flashing = true;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enabled() || !config.screenFlash() || !flashing)
        {
            return null;
        }

        long elapsed = System.currentTimeMillis() - flashStarted;
        int duration = Math.max(1, config.flashDuration());

        if (elapsed >= duration)
        {
            flashing = false;
            return null;
        }

        int opacity = Math.max(0, Math.min(100, config.flashOpacity()));

        if (opacity == 0)
        {
            return null;
        }

        Rectangle clip = graphics.getClipBounds();

        if (clip == null)
        {
            return null;
        }

        int alpha = opacity * 255 / 100;
        Color originalColor = graphics.getColor();

        graphics.setColor(new Color(255, 220, 0, alpha));
        graphics.fillRect(clip.x, clip.y, clip.width, clip.height);

        graphics.setColor(originalColor);

        return null;
    }
}