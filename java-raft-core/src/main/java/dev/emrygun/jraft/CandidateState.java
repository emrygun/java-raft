package dev.emrygun.jraft;

import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;
import dev.emrygun.jraft.event.RaftEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CandidateState extends RaftState {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateState.class);
    protected CandidateState(RaftNodeImpl node) {
        super(node);
    }

    @Override
    public synchronized void act() {
        while (true) {
            LOG.info("Starting election with term {}", getNode().getCurrentTerm());
            requestVoteFromPeers();

            RaftEvent event = pollEventWithTimeout(getElectionTimeout());
            switch (event) {
                case RaftEvent.StepDownEvent stepDownEvent -> {
                    getNode().stepDown(stepDownEvent.term(), "Step down event received");
                    return;
                }
                case RaftEvent.ElectionEvent electionEvent -> {
                    getNode().toLeader();
                    return;
                }
                case null -> {
                    getNode().increaseTerm();
                    continue;
                }
                default -> throw new IllegalStateException("Unexpected value: " + event);
            }

        }
    }

    @Override
    public void onTerminate() {
        // Nothing to do here
    }

    // Random election timeout between 150 300
    private Duration getElectionTimeout() {
        return Duration.ofMillis(300 + (long) (Math.random() * 150));
    }

    private LinkedHashMap<RaftNodeEndpoint, CompletableFuture<RequestVoteResponse>> requestVoteFromPeers() {
        LinkedHashMap<RaftNodeEndpoint, CompletableFuture<RequestVoteResponse>> futures = new LinkedHashMap<>();

        if (getNode().getPeers().isEmpty()) {
            getNode().electionEvent();
            return futures;
        }

        RequestVote requestVote = RequestVote.of(getNode());
        LOG.trace("Requesting votes from peers: {}", getNode().getPeers());

        for (RaftNodeEndpoint peerEndpoint : getNode().getPeers()) {
            CompletableFuture<RequestVoteResponse> future = sendRequestVoteToPeer(peerEndpoint, requestVote);
            futures.put(peerEndpoint, future);
        }

        return futures;
    }

    private CompletableFuture<RequestVoteResponse> sendRequestVoteToPeer(
            RaftNodeEndpoint peerEndpoint, RequestVote requestVote) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                var node = getNode().getPeerNodes().get(peerEndpoint);
                if (node == null) {
                    LOG.error("Peer node {} not found", peerEndpoint);
                    return null;
                }
                return node.requestVote(requestVote);
            } catch (RemoteException e) {
                throw new CompletionException("Failed to send RequestVote to peer", e);
            }
        }, getNode().getExecutor()).handle((response, error) -> {
            if (error != null) {
                LOG.error("Failed to send RequestVote to peer: {}", peerEndpoint, error);
                return null;
            }
            handleRequestVoteResponse(response);
            return response;
        });
    }

    private synchronized void handleRequestVoteResponse(RequestVoteResponse response) {
        if (response.term() > getNode().getCurrentTerm()) {
            getNode().stepDown(response.term(), String.format("RV Response term %d > current term %d", response.term(), getNode().getCurrentTerm()));
        }

        LOG.trace("Received request vote response: {}, {}, currentTerm: {}", response.voteGranted(), response.term(), getNode().getCurrentTerm());
        if (response.voteGranted()) {
            // Increment vote count and check if majority is reached
            int votes = getNode().increaseVoteCount();

            if (getNode().isLeader() && votes > (getNode().getPeers().size() + 1) / 2) {
                LOG.info("Majority votes received. Becoming leader.");
                getNode().toLeader();
            } else if (getNode().isLeader()) {
                LOG.trace("Already leader");
            }
        }
    }
}
