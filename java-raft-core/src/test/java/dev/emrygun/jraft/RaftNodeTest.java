package dev.emrygun.jraft;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.log.RaftLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

class RaftNodeTest {
    private final RaftNodeEndpoint self = new RaftNodeEndpoint("localhost");

    @Mock
    private StateMachine stateMachine;

    private RaftNodeImpl raftNode;

    @BeforeEach
    void setUp() throws RemoteException {
        MockitoAnnotations.openMocks(this);
        raftNode = spy(new RaftNodeImpl(self, List.of(), stateMachine));
    }

    @Test
    void shouldHandleFirstAppendEntriesAndApplyEntriesToStateMachine() {
        List<RaftLogEntry> entries = Arrays.asList(
            new RaftLogEntry(1, 1, "data1".getBytes()),
            new RaftLogEntry(2, 1, "data2".getBytes())
        );

        AppendEntries appendEntries = new AppendEntries(1L, new RaftNodeEndpoint("123"), 0L, 0L, entries, 0);
        AppendEntriesResponse response = raftNode.appendEntries(appendEntries);

        assertEquals(0L, response.term());
        assertTrue(response.success());
        assertEquals(2L, raftNode.getLastLog().getIndex());
        assertEquals(0L, raftNode.getCommitIndex());
    }

}