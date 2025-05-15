package dev.emrygun.jraft;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;
import dev.emrygun.jraft.event.RaftEvent;
import dev.emrygun.jraft.handler.AppendEntriesCommandHandler;
import dev.emrygun.jraft.handler.RequestVoteCommandHandler;
import dev.emrygun.jraft.log.RaftLog;
import dev.emrygun.jraft.log.RaftLogEntry;
import dev.emrygun.jraft.rpc.RaftRPCServer;
import dev.emrygun.jraft.rpc.RaftRPCServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class RaftNodeImpl extends UnicastRemoteObject implements RaftNode, RaftRPCServer {
    private static final Logger LOG = LoggerFactory.getLogger(RaftNodeImpl.class);

    private final ExecutorService stateExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final RaftNodeEndpoint self;
    private RaftNodeEndpoint leader;

    private final List<RaftNodeEndpoint> peers;
    private final LinkedHashMap<RaftNodeEndpoint, RaftRPCServer> peerNodes = new LinkedHashMap<>();

    private final StateMachine stateMachine;
    private final RaftNodeState state = new RaftNodeState();

    // Raft states
    private final AtomicReference<RaftState> currentState = new AtomicReference<>();
    private final FollowerState followerState;
    private final CandidateState candidateState;
    private final LeaderState leaderState;

    // Raft Event Queue
    private final BlockingQueue<RaftEvent> eventQueue = new LinkedBlockingQueue<>();

    // Command Handlers
    private final RaftRPCServer rpcServer;
    private final AppendEntriesCommandHandler appendEntriesHandler;
    private final RequestVoteCommandHandler requestVoteCommandHandler;

    // Node votes
    private final AtomicInteger voteCount = new AtomicInteger(0);

    RaftNodeImpl(RaftNodeEndpoint self, List<RaftNodeEndpoint> peers, StateMachine stateMachine) throws RemoteException {
        super();
        this.stateMachine = stateMachine;
        this.self = self;
        this.peers = peers;

        // Initialize command handlers
        this.appendEntriesHandler = new AppendEntriesCommandHandler(this);
        this.requestVoteCommandHandler = new RequestVoteCommandHandler(this);

        // Initialize states
        this.followerState = new FollowerState(this);
        this.candidateState = new CandidateState(this);
        this.leaderState = new LeaderState(this);

        this.currentState.set(followerState);
        this.rpcServer = RaftRPCServerFactory.create(appendEntriesHandler, requestVoteCommandHandler);
        LOG.info("RaftNodeImpl created on: {}", self);
    }

    /**
     * Starts the Raft node and binds it to the RMI registry.
     */
    public void start() {
        // Start RPC Server
        try {
            Registry registry = LocateRegistry.createRegistry(self.getPort());
            registry.rebind("raft", rpcServer);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        for (RaftNodeEndpoint peer : peers) {
            RaftRPCServer peerNode = RaftRPCServerFactory.getRemoteNode(peer);
            LOG.info("Adding peer node: {}", peer);
            this.peerNodes.put(peer, peerNode);
        }

        toFollower();
        LOG.info("RaftNodeImpl started on: {}", self);
    }

    /**
     * Acts the current role of the node
     */
    private void act() {
        getStateExecutor().submit(() -> {
            try {
                currentState.get().start();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<RaftLogEntry> handleNewLogEntry(byte[] command) {
        if (isLeader()) {
            return ((LeaderState) this.currentState.get()).handleNewLogEntry(command);
        }
        throw new IllegalStateException("Node is not a leader");
    }

    public AppendEntriesResponse appendEntries(AppendEntries cmd) {
        AppendEntriesResponse response = appendEntriesHandler.handle(cmd);
        getEventQueue().offer(new RaftEvent.NewEntryEvent());
        return response;
    }

    public RequestVoteResponse requestVote(RequestVote cmd) {
        return requestVoteCommandHandler.handle(cmd);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Executor operations

    public ExecutorService getExecutor() {
        return executor;
    }

    private ExecutorService getStateExecutor() {
        return stateExecutor;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Event queue operations
    private BlockingQueue<RaftEvent> getEventQueue() {
        return eventQueue;
    }

    public RaftEvent pollEvent(Duration timeout) {
        try {
            return getEventQueue().poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // TODO: Exception handling
            throw new RuntimeException(e);
        }
    }

    public void newEntryEvent() {
        getEventQueue().offer(new RaftEvent.NewEntryEvent());
    }

    public void electionEvent() {
        getEventQueue().offer(new RaftEvent.ElectionEvent());
    }

    public void stepDownEvent(long index) {
        getEventQueue().offer(new RaftEvent.StepDownEvent(index));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // State change operations

    /**
     * Sets the state of the Raft node to a new state.
     *
     * @param newState the new state to set
     */
    synchronized void setState(RaftState newState) {
        LOG.info("Transitioning from {} to {}", currentState.get().getClass().getSimpleName(), newState.getClass().getSimpleName());
        // this.currentState.get().disable();
        this.currentState.set(newState);
        // this.currentState.get().enable();
    }

    void toFollower() {
        setState(followerState);
        state.setVotedFor(null);
        act();
    }

    void toCandidate() {
        setState(candidateState);
        resetVoteCount();
        increaseTerm();
        state.setVotedFor(self);
        act();
    }

    void toLeader() {
        setState(leaderState);
        act();
    }

    void stepDown(long term, String reason) {
        LOG.info("Step down term: {}, reason: {}", term, reason);
        state.setCurrentTerm(term);
        toFollower();
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Nodes

    public RaftNodeEndpoint getSelf() {
        return self;
    }

    public List<RaftNodeEndpoint> getPeers() {
        return peers;
    }

    public LinkedHashMap<RaftNodeEndpoint, RaftRPCServer> getPeerNodes() {
        return peerNodes;
    }

    public boolean isLeader() {
        return currentState.get() instanceof LeaderState;
    }

    public RaftNodeEndpoint getLeader() {
        return leader;
    }

    public void setLeader(RaftNodeEndpoint leader) {
        this.leader = leader;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // State operations

    public long getCurrentTerm() {
        return state.getCurrentTerm();
    }

    public void increaseTerm() {
        state.increaseTerm();
        voteCount.set(0);
    }

    public RaftNodeEndpoint getVotedFor() {
        return state.getVotedFor();
    }

    public void setVotedFor(RaftNodeEndpoint votedFor) {
        state.setVotedFor(votedFor);
    }

    public RaftLog getRaftLog() {
        return state.getLog();
    }

    public RaftLogEntry getLastLog() {
        return state.getLog().getLatest();
    }

    public long getCommitIndex() {
        return state.getCommitIndex();
    }

    public void updateCommitIndex(long commitIndex) {
        long newCommitIndex = Math.min(commitIndex, getLastLog().getIndex());

        state.setCommitIndex(newCommitIndex);
        applyEntries(state.getUnappliedEntries());
    }

    void updateNextIndex(RaftNodeEndpoint peer, long nextIndex) {
        state.setNextIndex(peer, nextIndex);
    }

    public long getMatchIndex(RaftNodeEndpoint peer) {
        return state.getMatchIndex(peer);
    }

    public void updateMatchIndex(RaftNodeEndpoint peer, long matchIndex) {
        state.setMatchIndex(peer, matchIndex);
        updateCommitIndex(state.getMajorityMatchIndex());
    }

    public int getVoteCount() {
        return voteCount.get();
    }

    public int increaseVoteCount() {
        return voteCount.incrementAndGet();
    }

    private void resetVoteCount() {
        voteCount.set(0);
    }

    public long getNextIndex(RaftNodeEndpoint peer) {
        return state.getNextIndex(peer);
    }

    public void initializeLeaderState() {
        state.initializeLeaderState(getPeers());
    }

    public void applyEntries(List<RaftLogEntry> entries) {
        stateMachine.apply(entries).handle((res, e) -> {
            if (res) {
                state.setLastApplied(entries.get(entries.size() - 1).getIndex());
            } else if (e != null) {
                // TODO(emre) handle the exception
                LOG.error("Failed to apply entries", e);
            } else {
                // TODO(emre) handle the case when apply returns false
                LOG.warn("Failed to apply entries");
            }
            return true;
        });
    }

}
