package dev.emrygun.jraft.log;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class RaftLogEntry implements Serializable {
    private final long index;
    private final long term;
    private final byte[] command;

    public RaftLogEntry(long index, long term, byte[] command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }

    public long getTerm() {
        return term;
    }

    public long getIndex() {
        return index;
    }

    public byte[] getCommand() {
        return command;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RaftLogEntry that = (RaftLogEntry) o;
        return index == that.index && term == that.term && Objects.deepEquals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, term, Arrays.hashCode(command));
    }

    @Override
    public String toString() {
        return "RaftLogEntry{" +
                "index=" + index +
                ", term=" + term +
                ", command=" + Arrays.toString(command) +
                '}';
    }
}

