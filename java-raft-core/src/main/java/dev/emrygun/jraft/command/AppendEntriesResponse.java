package dev.emrygun.jraft.command;

public record AppendEntriesResponse(Long term, boolean success) implements CommandResponse {
}
