package net.pmarks.chromadoze;

// This interface is for receiving a callback when the state
// of the Input Lock has changed.
public interface LockListener {
    enum LockEvent { TOGGLE, BUSY };
    void onLockStateChange(LockEvent e);
}
