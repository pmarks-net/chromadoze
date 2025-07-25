// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
//
// This file is part of ChromaDoze.
//
// ChromaDoze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ChromaDoze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ChromaDoze.  If not, see <http://www.gnu.org/licenses/>.

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
