package net.pmarks.chromadoze;

import android.content.Intent;

// Read-only view of a PhononMutable
public interface Phonon {
    public String toJSON();

    public boolean isSilent();

    public float getBar(int band);

    public int getMinVol();

    public String getMinVolText();

    public int getPeriod();

    public String getPeriodText();

    public PhononMutable makeMutableCopy();

    public void writeIntent(Intent intent);

    public boolean fastEquals(Phonon other);
}
