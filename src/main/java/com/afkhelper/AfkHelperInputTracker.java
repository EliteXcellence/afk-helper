package com.afkhelper;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;

/**
 * Tracks the last time the player actually did something: a mouse click or a
 * key press. Moving the mouse does not count, unlike the client's own idle
 * counters.
 *
 * Idle time is reported in client ticks (20ms) to match the units used by
 * Client#getIdleTimeout().
 */
public class AfkHelperInputTracker implements MouseListener, KeyListener
{
    private static final int CLIENT_TICK_MILLIS = 20;

    private volatile long lastInputMillis = System.currentTimeMillis();

    /** Record that input just happened (also used when logging in). */
    public void markInput()
    {
        lastInputMillis = System.currentTimeMillis();
    }

    /** Start counting from an already-elapsed idle time. */
    public void reset(int idleTicks)
    {
        lastInputMillis = System.currentTimeMillis()
                - (long) Math.max(0, idleTicks) * CLIENT_TICK_MILLIS;
    }

    public int getIdleTicks()
    {
        long elapsed = System.currentTimeMillis() - lastInputMillis;

        return (int) Math.max(0, Math.min(
                Integer.MAX_VALUE, elapsed / CLIENT_TICK_MILLIS));
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        markInput();
        return event;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent event)
    {
        return event;
    }

    @Override
    public void keyPressed(KeyEvent event)
    {
        markInput();
    }

    @Override
    public void keyTyped(KeyEvent event)
    {
    }

    @Override
    public void keyReleased(KeyEvent event)
    {
    }
}