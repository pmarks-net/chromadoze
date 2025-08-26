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
