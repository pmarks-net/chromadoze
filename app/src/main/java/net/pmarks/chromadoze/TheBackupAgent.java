// Copyright (C) 2016  Paul Marks  http://www.pmarks.net/
//
// This file is part of Chroma Doze.
//
// Chroma Doze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Chroma Doze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Chroma Doze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

// This implements a BackupAgent, not because the data is particularly
// valuable, but because Android 6.0 will randomly kill the service in the
// middle of the night to perform a "fullbackup" if we don't offer an
// alternative (or disable backups entirely.)
public class TheBackupAgent extends BackupAgentHelper {
    private static final String PREF_BACKUP_KEY = "pref";

    @Override
    public void onCreate() {
        addHelper(PREF_BACKUP_KEY, new SharedPreferencesBackupHelper(
                this, ChromaDoze.PREF_NAME));
    }
}
