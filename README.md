# A3M – Exactly-Once Messaging Middleware

**A3M** is a lightweight messaging middleware that combines the benefits of messaging patterns (like those found in ZeroMQ) with the **exactly-once (EO)** delivery guarantees of the [**Exon**](https://github.com/Alex-Sa-Ms/Exon-Mobility) transport library.

## 🚀 Overview

Distributed systems often require flexible communication patterns and reliable message delivery. While ZeroMQ is popular for its ease of use and support for multiple messaging patterns, it does **not** provide EO delivery—especially in the presence of connection loss (e.g., due to IP address changes).

**A3M** bridges this gap by:
- Providing messaging patterns similar to ZeroMQ (e.g., request-reply, pipeline, pub-sub)
- Building on **Exon** to ensure **exactly-once delivery**, even in scenarios involving disconnections and mobility (e.g., roaming between networks)

## 🔧 Features

- **Exactly-once delivery** (via the [Exon](https://github.com/Alex-Sa-Ms/Exon-Mobility) transport library)
- **Mobility support**, such as seamless reconnections after IP address changes
- **Flexible messaging patterns** inspired by ZeroMQ
- **Custom socket support**: Developers can define and register their own custom socket types, enabling advanced and application-specific communication semantics within the exactly-once framework.
- **Pattern adaptations** for EO semantics (e.g., synchronized pub-sub)
- **EO Pub-Sub adaptation**: Traditional pub-sub patterns are incompatible with exactly-once semantics. A3M introduces a **synchronized publish-subscribe** model where the **slowest subscriber dictates** the pace of message publishing.

## 🏁 Getting Started

You can quickly test A3M by importing the pre-built `A3M.jar` into your Java project.

### Steps:
1. Download `A3M.jar` from the repository.
2. Add the JAR to your project’s classpath.
3. Start using the A3M API to define messaging flows with **exactly-once delivery**.

Example usage can be found in the chapter `Usage Patterns` in `Thesis.pdf`.

---
