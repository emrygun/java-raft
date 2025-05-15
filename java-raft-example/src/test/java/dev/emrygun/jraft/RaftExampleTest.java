package dev.emrygun.jraft;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class RaftExampleTest {
    private static final Logger LOG = LoggerFactory.getLogger(RaftExampleTest.class);
    ExecutorService executor = Executors.newFixedThreadPool(10);
    private static final Network RAFT_CLUSTER_NETWORK = Network.builder().build();

    @Test
    void testRaftNode() throws IOException, InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper();
        Cluster cluster = objectMapper.readValue(CLUSTER_METADATA, Cluster.class);
        List<GenericContainer<?>> containers = new ArrayList<>();

        for (ClusterNode clusterNode : cluster.cluster()) {
            executor.submit(() -> {
                LOG.info("Starting container for node: {}", clusterNode.host());

                GenericContainer<?> container = createContainer(cluster, clusterNode);
                container.start();
                containers.add(container);
                LOG.info("Container started for node: {}", clusterNode.host());
            });
        }

        // Wait until all containers are ready
        Thread.sleep(5000);

        for (int i = 0; ; i++) {
            int finalI = i;
            int finalI1 = i;
            Thread.sleep(1000);
            executor.submit(() -> {
                try {
                    // Randomly pick a container to send the log
                    GenericContainer<?> writeContainer = containers.get((int) (Math.random() * containers.size()));
                    int writePort = writeContainer.getMappedPort(7000); // adjust as needed
                    String writeUrl = "http://localhost" + ":" + writePort + "/logs";

                    // Post a log entry
                    String command = "key" + finalI + ":" + "value" + finalI1;
                    HttpUtils.post(writeUrl, command.getBytes());

                    // Randomly pick a container to query
                    GenericContainer<?> readContainer = containers.get((int) (Math.random() * containers.size()));
                    int readPort = readContainer.getMappedPort(7000);  // adjust as needed
                    String readUrl = "http://localhost" + ":" + readPort + "/logs";

                    // Query the log entry
                    String response = HttpUtils.get(readUrl);
                    LOG.info("Queried key{} from {} -> {}", finalI, readContainer.getHost(), response);
                } catch (Exception e) {
                    LOG.error("Error during log test", e);
                }
            });
        }


    }

    GenericContainer<?> createContainer(Cluster cluster, ClusterNode node) {
        List<String> peers = cluster.cluster().stream()
                .filter(clusterNode -> !clusterNode.host().equals(node.host()))
                .map(clusterNode -> clusterNode.host() + ":" + clusterNode.raftPort())
                .toList();

        return createContainer(node.host(), node.raftPort(), node.appPort(), peers);
    }

    GenericContainer<?> createContainer(String host, String raftPort, String appPort, List<String> peers) {
        Logger logger = LoggerFactory.getLogger(host);

        return new GenericContainer<>("jraft-example:latest")
                .withExposedPorts(Integer.parseInt(raftPort), Integer.parseInt(appPort))
                .withEnv("RAFT_PORT", raftPort)
                .withEnv("APP_PORT", appPort)
                .withEnv("HOST_NAME", host)
                .withEnv("PEERS", String.join(",", peers))
                .withExposedPorts(Integer.parseInt(raftPort), Integer.parseInt(appPort))
                .withNetwork(RAFT_CLUSTER_NETWORK)
                .withNetworkAliases(host)
                .waitingFor(Wait.forLogMessage(".*Started.*", 1))
                .withLogConsumer(outputFrame -> logger.info(outputFrame.getUtf8String()));
    }


    public static final class Cluster {
        private final List<ClusterNode> cluster;

        @JsonCreator
        public Cluster(
                @JsonProperty("cluster") List<ClusterNode> cluster
        ) {
            this.cluster = cluster;
        }

        public List<ClusterNode> cluster() {
            return cluster;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Cluster) obj;
            return Objects.equals(this.cluster, that.cluster);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cluster);
        }

        @Override
        public String toString() {
            return "Cluster[" +
                    "cluster=" + cluster + ']';
        }

    }

    public static final class ClusterNode {
        private final String host;
        private final String raftPort;
        private final String appPort;

        @JsonCreator
        public ClusterNode(
                @JsonProperty("host") String host,
                @JsonProperty("raftPort") String raftPort,
                @JsonProperty("appPort") String appPort
        ) {
            this.host = host;
            this.raftPort = raftPort;
            this.appPort = appPort;
        }

        public String host() {
            return host;
        }

        public String raftPort() {
            return raftPort;
        }

        public String appPort() {
            return appPort;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ClusterNode) obj;
            return Objects.equals(this.host, that.host) &&
                    Objects.equals(this.raftPort, that.raftPort) &&
                    Objects.equals(this.appPort, that.appPort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, raftPort, appPort);
        }

        @Override
        public String toString() {
            return "ClusterNode[" +
                    "host=" + host + ", " +
                    "raftPort=" + raftPort + ", " +
                    "appPort=" + appPort + ']';
        }

    }

    //language=json
    private static final String CLUSTER_METADATA = """
    {
      "cluster": [
        { "host": "node1", "raftPort": "8080", "appPort": "7000" },
        { "host": "node2", "raftPort": "8080", "appPort": "7000" },
        { "host": "node3", "raftPort": "8080", "appPort": "7000" },
        { "host": "node4", "raftPort": "8080", "appPort": "7000" },
        { "host": "node5", "raftPort": "8080", "appPort": "7000" }
      ]
    }
    """;

    public class HttpUtils {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .build();

        public static String get(String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }

        public static void post(String url, byte[] data) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();

            var response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 302) {
                String directedUrl = response.headers().firstValue("Location").orElseThrow();
                // replace host name with localhost
                directedUrl = directedUrl.replaceAll("(?<=://)([a-zA-Z]+\\d+)", "localhost");
                LOG.info("Redirected to: {}", directedUrl);

                request = HttpRequest.newBuilder()
                        .uri(URI.create(directedUrl))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                        .build();
                CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
                return;
            }
            LOG.info("{} : {}", response.statusCode(), response.body());
        }
    }

    /*
      This static block configures the logging level for the root logger to INFO.
     */
    static {
        LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
    }
}
