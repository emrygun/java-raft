# java-raft Playground
> ⚠️ **Warning:** This project is for educational and testing purposes only and is **not production-ready.**

This project provides a Java implementation of the Raft consensus algorithm. It serves as a playground for understanding and experimenting with the core concepts of Raft. The goal is to offer a tiny and understandable example of the fundamental algorithm and its components.


The implementation strictly adheres to the basic algorithm described in the original [Raft paper](https://raft.github.io/raft.pdf) by Diego Ongaro and John Ousterhout. 


It intentionally avoids optimizations and additional features to keep the core logic implementation clear.

## Features
This implementation focuses on the core aspects of Raft:

- Leader Election
- Log Replication
- State Machine Interaction (basic)

### Not Implemented Features
- Log Compaction
- Snapshotting
- Cluster Membership Changes

## Project Structure
- `java-raft-core`: Contains the core implementation of the Raft algorithm.
- `java-raft-example`: Provides a basic example demonstrating the usage of the `java-raft-core` library with a simple in-memory key-value store.

## Getting Started

### Prerequisites
To build and run this project, you need:

- Java Development Kit (JDK) 21 or later
- Apache Maven 3.6 or later

### Compiling
Navigate to the project root directory and run the following command to compile the project:
```bash
mvn clean install -DskipTests -T 12
````

### Running the Example
The `java-raft-example` module provides a simple in-memory key-value store implementation that utilizes the `java-raft-core` library.

The `RaftExampleTest` class within `java-raft-example` serves as the entry point for a basic demonstration. It sets up a cluster of multiple Raft nodes and simulates sending log entries (key-value operations) to the cluster.
```bash
mvn exec:java -Dexec.mainClass="com.emreuygun.raft.example.RaftExampleTest"
```

## Contributing
This project is primarily a personal playground. However, if you have suggestions for improvements or find issues, feel free to open an issue on the GitHub repository.

## Author
Emre Uygun - contact@emreuygun.dev - [emreuygun.dev](https://emreuygun.dev)

