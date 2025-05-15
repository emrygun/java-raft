package dev.emrygun.jraft;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class RaftNodeEndpoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 0L;
    private final String id;

    public RaftNodeEndpoint(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }

    public String getHost() {
        return id.split(":")[0];
    }

    public Integer getPort() {
        return Integer.valueOf(id.split(":")[1]);
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RaftNodeEndpoint that = (RaftNodeEndpoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
