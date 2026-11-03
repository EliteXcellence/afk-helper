package com.afkhelper;

import net.runelite.api.Client;
import net.runelite.api.GameState;

public class AfkHelperAfkCountdown
{
    // Client ticks are 20ms, so there are 50 per second.
    private static final int TICKS_PER_SECOND = 50;

    private final Client client;
    private final AfkHelperInputTracker inputTracker;

    public AfkHelperAfkCountdown(
            Client client,
            AfkHelperInputTracker inputTracker)
    {
        this.client = client;
        this.inputTracker = inputTracker;
    }

    public int getIdleSeconds()
    {
        return Math.max(0, inputTracker.getIdleTicks() / TICKS_PER_SECOND);
    }

    public int getSecondsUntilLogout(int idleTimeoutTicks)
    {
        int timeoutSeconds =
                Math.max(0, idleTimeoutTicks / TICKS_PER_SECOND);
        return Math.max(0, timeoutSeconds - getIdleSeconds());
    }

    public boolean isLogoutWarningDue(
            int idleTimeoutTicks,
            int warningSeconds)
    {
        int remaining = getSecondsUntilLogout(idleTimeoutTicks);

        return remaining > 0 && remaining <= warningSeconds;
    }

    public String getIdleTimeText()
    {
        return formatTime(getIdleSeconds());
    }

    public String getLogoutCountdownText(int warningSeconds)
    {
        return getLogoutCountdownText(15000, warningSeconds);
    }

    public String getLogoutCountdownText(
            int idleTimeoutTicks,
            int warningSeconds)
    {
        int remaining = getSecondsUntilLogout(idleTimeoutTicks);

        if (remaining > warningSeconds)
        {
            return "Not imminent";
        }

        return formatTime(remaining);
    }

    public boolean isPlayerActive()
    {
        return client.getGameState() == GameState.LOGGED_IN
                && client.getLocalPlayer() != null;
    }

    public String formatTime(int seconds)
    {
        int safeSeconds = Math.max(0, seconds);
        int minutes = safeSeconds / 60;
        int remainingSeconds = safeSeconds % 60;

        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
}