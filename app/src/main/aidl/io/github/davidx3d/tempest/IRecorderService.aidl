package io.github.davidx3d.tempest;

/** The background recorder that Shizuku keeps running while Tempest is closed. */
interface IRecorderService {
    /** Reserved transaction id: Shizuku calls this when the service should stop. */
    void destroy() = 16777114;

    /** How often to sample while the phone is awake. Samples once right away when it changes. */
    void setInterval(int seconds) = 1;

    /** One "time,battery,soc" line per sample newer than [sinceMillis]; blank where a value is missing. */
    String samplesSince(long sinceMillis) = 2;

    /** Number of samples kept. */
    int count() = 3;

    /** Forgets every sample, in memory and on disk. */
    void clear() = 4;
}
