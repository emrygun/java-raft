package dev.emrygun.jraft.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaftLog {
    // TODO(emre): Do not implement ArrayRingBuffer by yourself. Find it elsewhere.
    private final List<RaftLogEntry> log;

    public RaftLog(List<RaftLogEntry> log) {
        this.log = log;
    }

    public RaftLogEntry get(long index) {
        if (index < 0) {
            return null;
        }
        if (log.isEmpty()) {
            return null;
        }
        return log.get((int) index);
    }

    public RaftLogEntry getLatest(long index) {
        if (index < 0) {
            return null;
        }
        if (log.isEmpty()) {
            return null;
        }
        return log.get(Math.min((int) index, log.size() - 1));
    }

    public RaftLogEntry getLatest() {
        if (log.isEmpty()) {
            return null;
        }
        return log.get(log.size() - 1);
    }

    public List<RaftLogEntry> getEntriesFrom(long index) {
        if (index > log.size() - 1)  {
            return Collections.emptyList();
        }

        index = Math.max(index, 0);
        return new ArrayList<>(log.subList((int) index, Math.max(log.size() - 1, 0)));
    }

    public void deleteEntriesFrom(long index) {
        if (index < 0 || index >= log.size()) {
            return;
        }
        log.subList((int) index, log.size()).clear();
    }

    public RaftLogEntry getEntry(long index) {
        if (index < 0 || index >= log.size()) {
            return null;
        }
        return log.get((int) index);
    }

    public void appendEntries(List<RaftLogEntry> entries) {
        for (RaftLogEntry entry : entries) {
            if (entry.getIndex() < log.size()) {
                log.set((int) entry.getIndex(), entry);
            } else {
                log.add(entry);
            }
        }
    }

    public void appendCommand(byte[] cmd, long term) {
        RaftLogEntry entry = new RaftLogEntry(log.size(), term, cmd);
        log.add(entry);
    }

    public List<RaftLogEntry> getFrom(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        if (index >= log.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(log.subList((int) index, log.size()));
    }

}

