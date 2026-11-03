package io.github.davidx3d.tempest;

/** Runs shell commands inside the Shizuku user-service process (shell or root identity). */
interface IShellService {
    /** Reserved transaction id: Shizuku calls this when the service should stop. */
    void destroy() = 16777114;

    /** Returns the command's stdout, or null if it could not be started. */
    String exec(String command, long timeoutMs) = 1;
}
