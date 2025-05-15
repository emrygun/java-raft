package dev.emrygun.jraft.command;

public record RequestVoteResponse(long term, boolean voteGranted) implements CommandResponse {
}
