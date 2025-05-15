package dev.emrygun.jraft.event;

public interface RaftEvent {
    record NewEntryEvent() implements RaftEvent { }
    record ElectionEvent() implements RaftEvent { }
    record StepDownEvent(long term) implements RaftEvent { }
}
