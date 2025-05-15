package dev.emrygun.jraft;

import dev.emrygun.jraft.log.RaftLog;
import dev.emrygun.jraft.log.RaftLogEntry;

import java.io.Serializable;
import java.util.*;

public class RaftNodeState implements Serializable {
    // Persistent states
    private volatile long currentTerm = 0;
    private volatile RaftNodeEndpoint votedFor;
    private volatile RaftLog log = new RaftLog(new ArrayList<>(List.of(new RaftLogEntry(0, 0, null))));

    // Volatile states
    private transient long commitIndex = 0;
    private transient long lastApplied = 0;

    // Volatile states on leaders
    private transient Map<String, Long> nextIndex;
    private transient Map<String, Long> matchIndex;

    public synchronized void increaseTerm() {
        this.currentTerm++;
        votedFor = null;
    }

    void initializeLeaderState(List<RaftNodeEndpoint> peers) {
        this.nextIndex = new HashMap<>();
        this.matchIndex = new HashMap<>();

        for (RaftNodeEndpoint peer : peers) {
            nextIndex.put(peer.id(), log.getLatest().getIndex() + 1);
            matchIndex.put(peer.id(), 0L);
        }
    }

    long getNextIndex(RaftNodeEndpoint peer) {
        return nextIndex.get(peer.id());
    }

    long setNextIndex(RaftNodeEndpoint peer, long index) {
        return nextIndex.put(peer.id(), index);
    }

    long getMatchIndex(RaftNodeEndpoint peer) {
        return matchIndex.get(peer.id());
    }

    long setMatchIndex(RaftNodeEndpoint peer, long index) {
        return matchIndex.put(peer.id(), index);
    }

    long getMajorityMatchIndex() {
        List<Long> matchIndexes = new ArrayList<>();

        // Collect matchIndex values for all peers
        for (Map.Entry<String, Long> entry : matchIndex.entrySet()) {
            matchIndexes.add(entry.getValue());
        }

        // Include the local node's matchIndex
        matchIndexes.add(getLog().getLatest().getIndex());

        // Sort the matchIndexes
        matchIndexes.sort(Comparator.reverseOrder());

        // Return the majority matchIndex
        return matchIndexes.get(matchIndexes.size() / 2);
    }

    public RaftNodeEndpoint getVotedFor() {
        return votedFor;
    }

    public synchronized void setVotedFor(RaftNodeEndpoint votedFor) {
        this.votedFor = votedFor;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
    }

    public RaftLog getLog() {
        return log;
    }

    public List<RaftLogEntry> getUncommittedEntries() {
        return log.getFrom(commitIndex + 1);
    }

    public List<RaftLogEntry> getUnappliedEntries() {
        return log.getFrom(lastApplied + 1);
    }

    public long getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }

    public void setLastApplied(long lastApplied) {
        this.lastApplied = lastApplied;
    }
}
