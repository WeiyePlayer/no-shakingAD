// ISensorControlService.aidl
package com.shizukucontrol;

interface ISensorControlService {
    /**
     * Execute a shell command with Shizuku privileges.
     * @param command the shell command to execute
     * @return command output (stdout + stderr combined)
     */
    String executeCommand(String command);

    /**
     * Restrict sensors for |delayMs| milliseconds, then auto-enable.
     * Called with sensor filter string like "target.pkg|com.android.systemui"
     * The UserService handles the delay internally so it survives app process death.
     */
    String restrictSensors(String filter, int delayMs);

    /**
     * Cleanup and terminate the UserService process.
     * Transaction code 16777114 in AIDL (maps to 16777115 at runtime).
     */
    void destroy();
}
