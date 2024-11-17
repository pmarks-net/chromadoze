package net.pmarks.chromadoze;

public interface TimerListener {
    void onTimerTick(int remainingTime);
    void onTimerComplete();
}
