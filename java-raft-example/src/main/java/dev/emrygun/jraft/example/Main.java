package dev.emrygun.jraft.example;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import dev.emrygun.jraft.RaftNode;
import dev.emrygun.jraft.log.RaftLogEntry;
import dev.emrygun.jraft.RaftNodeFactory;
import dev.emrygun.jraft.StateMachine;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    record ApplicationLog(String key, String value) {
        public static ApplicationLog from(RaftLogEntry entry) {
            String command = new String(entry.getCommand());
            String key = command.split(":")[0];
            String value = command.split(":")[1];
            return new ApplicationLog(key, value);
        }
    }

    public static class LoggingRaftStateMachine implements StateMachine {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingRaftStateMachine.class);

        @Override
        public CompletableFuture<Boolean> apply(List<RaftLogEntry> entry) {
            entry.forEach(logEntry -> {
                ApplicationLog applicationLog = ApplicationLog.from(logEntry);
                LOG.info("Applying log entry: {}", applicationLog);
                store.put(applicationLog.key, applicationLog.value);
            });
            return CompletableFuture.completedFuture(true);
        }
    }

    public static void main(String[] args) throws RemoteException {
        String hostName = System.getenv("HOST_NAME");
        String appPort = System.getenv("APP_PORT");
        String raftPort = System.getenv("RAFT_PORT");
        String peers = System.getenv("PEERS");

        LoggingRaftStateMachine stateMachine = new LoggingRaftStateMachine();
        List<String> peersList = List.of(peers.split(","));
        RaftNode raftNode = RaftNodeFactory.create(hostName + ":" + raftPort, stateMachine, peersList);

        Javalin app = Javalin.create().start(Integer.parseInt(appPort));

        // Get all log entries
        app.get("/logs/", ctx -> {
            ctx.json(store);
        });

        // Get log entry
        app.get("/logs/{key}", ctx -> {
            String key = ctx.pathParam("key");
            String value = store.get(key);
            if (value != null) {
                ctx.json(value);
            } else {
                ctx.status(404).result("Key not found");
            }
        });

        // Set log entries
        app.post("/logs", ctx -> {
            byte[] command = ctx.bodyAsBytes();

            if (!raftNode.isLeader()) {
                if (raftNode.getLeader() == null) {
                    ctx.status(503).result("No leader available");
                    return;
                }
                ctx.redirect("http://" + raftNode.getLeader().toString() + "/logs");
                return;
            }

            raftNode.handleNewLogEntry(command)
                    .orTimeout(1000, TimeUnit.MILLISECONDS)
                    .thenAccept(logEntry -> ctx.status(201))
                    .exceptionally(ex -> {
                        ctx.status(500).result("Failed to replicate log entry: " + ex.getMessage());
                        return null;
                    });
        });

        raftNode.start();
        LOG.info("Raft Example Server Started.");
    }


    /*
      This static block configures the logging levels.
     */
    static {
        LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("ROOT").setLevel(Level.INFO);
        loggerContext.getLogger("io.javalin").setLevel(Level.WARN);
    }
}
