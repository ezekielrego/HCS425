package zw.co.techinn.hcs425.server;

import java.time.Instant;

public record ServerLogEntry(Instant timestamp, String level, String message) {
    @Override public String toString() { return timestamp + " [" + level + "] " + message; }
}
