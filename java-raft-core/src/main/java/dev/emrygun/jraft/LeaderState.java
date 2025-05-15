package dev.emrygun.jraft;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.event.RaftEvent;
import dev.emrygun.jraft.log.RaftLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LeaderState extends RaftState {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderState.class);

    // Append log buffers
    private final Map<Long, CompletableFuture<RaftLogEntry>> appendLogBuffer = new ConcurrentHashMap<>();

    LeaderState(RaftNodeImpl node) {
        super(node);
    }

    @Override
    public synchronized void act() {
        getNode().initializeLeaderState();

        while (true) {
            sendHeartbeats();

            RaftEvent event = pollEventWithTimeout(getHeartbeatTimeout());
            switch (event) {
                case RaftEvent.StepDownEvent stepDownEvent -> {
                    getNode().stepDown(stepDownEvent.term(), "Step down event received");
                    return;
                }
                case null -> { continue; }
                default -> throw new IllegalStateException("Unexpected value: " + event);
            }
        }
    }

    @Override
    public void onTerminate() {
        appendLogBuffer.clear();
    }

    private Duration getHeartbeatTimeout() {
        return Duration.ofMillis(50);
    }

    // -----------------------------------------------------------------------------------------------------------------


    public CompletableFuture<RaftLogEntry> handleNewLogEntry(byte[] command) {
        long startTime = System.nanoTime(); // Start timing

        // Create a new log entry
        RaftLogEntry latestLog = getNode().getRaftLog().getLatest();
        long nextIndex = latestLog != null ? latestLog.getIndex() + 1 : 0;
        RaftLogEntry newEntry = new RaftLogEntry(nextIndex, getNode().getCurrentTerm(), command);

        // Append the log entry to the leader's log
        getNode().getRaftLog().appendCommand(command, getNode().getCurrentTerm());
        LOG.info("New log entry: {}", newEntry);

        // Create a CompletableFuture for the new log entry
        CompletableFuture<RaftLogEntry> future = new CompletableFuture<>();
        appendLogBuffer.put(newEntry.getIndex(), future);

        // Send AppendEntries RPC to all followers
        LOG.info("Replicating the log: {}", newEntry);
        sendHeartbeats();

        long endTime = System.nanoTime(); // End timing
        long durationInSeconds = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        LOG.info("handleNewLogEntry took {} millis", durationInSeconds);

        return future;
    }

    private LinkedHashMap<RaftNodeEndpoint, CompletableFuture<AppendEntriesResponse>> sendHeartbeats() {
        LinkedHashMap<RaftNodeEndpoint, CompletableFuture<AppendEntriesResponse>> futures = new LinkedHashMap<>();

        LOG.trace("Sending heartbeats to peers: {}", getNode().getPeers());

        if (getNode().getPeers().isEmpty()) {
            handleEmptyPeerList();
            return futures;
        }

        for (RaftNodeEndpoint peerEndpoint : getNode().getPeers()) {
            AppendEntries appendEntries = AppendEntries.of(getNode(), peerEndpoint);
            CompletableFuture<AppendEntriesResponse> future = sendAppendEntriesToPeer(peerEndpoint, appendEntries);
            futures.put(peerEndpoint, future);
        }

        return futures;
    }

    private CompletableFuture<AppendEntriesResponse> sendAppendEntriesToPeer(
            RaftNodeEndpoint peerEndpoint, AppendEntries appendEntries) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return getNode().getPeerNodes().get(peerEndpoint).appendEntries(appendEntries);
            } catch (RemoteException e) {
                throw new CompletionException("Failed to send AppendEntries to peer", e);
            }
        }, getNode().getExecutor()).handle((response, error) -> {
            try {
                if (error != null) {
                    LOG.error("Failed to send AppendEntries to peer: {}", peerEndpoint, error);
                    return null;
                }
                handleAppendEntriesResponse(peerEndpoint, appendEntries, response);
                return response;
            } catch (Exception e) {
                LOG.error("Error handling AppendEntries response: {}", peerEndpoint, e);
                return null;
            }
        });
    }

    private void handleEmptyPeerList() {
        RaftLogEntry lastLog = getNode().getRaftLog().getLatest();
        if (lastLog != null) {
            getNode().updateCommitIndex(lastLog.getIndex());
        }
    }

    private void handleAppendEntriesResponse(RaftNodeEndpoint peer, AppendEntries request, AppendEntriesResponse response) {
        LOG.trace("Handling AE response. Peer: {}, MatchIndex: {}, NextIndex: {}, CommitIndex: {}, Success: {}",
                peer,
                getNode().getMatchIndex(peer),
                getNode().getNextIndex(peer),
                getNode().getCommitIndex(),
                response.success());

        if (response.term() > getNode().getCurrentTerm()) {
            getNode().stepDown(response.term(), String.format("AE Response term %d > current term %d", response.term(), getNode().getCurrentTerm()));
        }

        if (response.success()) {
            getNode().updateMatchIndex(peer, request.prevLogIndex() + request.entries().size());
            getNode().updateNextIndex(peer, getNode().getMatchIndex(peer) + 1); // Increment nextIndex
        } else {
            getNode().updateNextIndex(peer, Math.max(getNode().getNextIndex(peer) - 1, 1)); // Decrement nextIndex
        }

        // Check if any log entry in the buffer is replicated and committed
        appendLogBuffer.forEach((logIndex, future) -> {
            if (!future.isDone() && isMajorityReplicated(logIndex) && logIndex <= getNode().getCommitIndex()) {
                future.complete(getNode().getRaftLog().get(logIndex));
            }
        });
    }

    private boolean isMajorityReplicated(long logIndex) {
        int count = 1; // Include the leader itself
        for (RaftNodeEndpoint peer : getNode().getPeers()) {
            if (getNode().getMatchIndex(peer) >= logIndex) {
                count++;
            }
        }
        return count > (getNode().getPeers().size() + 1) / 2; // Majority check
    }

}
