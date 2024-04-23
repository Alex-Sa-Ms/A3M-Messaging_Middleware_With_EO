# Estrutura da tese

Passar as seccoes de “fault tolerance” e “ease of use” para uma seccao comparativa?

# 1. Abstract e introdução

**(ChatGPT)**
Here's a suggestion for structuring your abstract and introduction:
**Introduction:**

1. **Context:** Begin by introducing the broader context of distributed applications and the role of messaging middleware in simplifying development.
2. **Motivation:** Discuss the challenges associated with existing messaging middleware, specifically focusing on the limitations of ZeroMQ in dynamic environments and the need for a more robust solution.
3. **Problem Statement:** Clearly state the problems your research aims to address, including the challenges in selecting communication patterns and the limitations of existing middleware in handling network fault tolerances.
4. **Aims/Objectives:** Outline the main aims and objectives of your thesis, highlighting the development of a new messaging middleware with exactly-once delivery guarantees and improved adaptability in dynamic scenarios.

**Abstract:**
Compose a concise summary that covers the entire thesis, including key aspects such as the design and implementation of the new messaging middleware, the utilization of the Exon protocol, and the improvements over existing solutions. Mention the significance of your findings and how they contribute to the field of distributed applications.

By clearly distinguishing the introduction and abstract in this way, you provide readers with a structured overview of your thesis. The introduction sets the stage by explaining the context, motivation, and main aims, while the abstract provides a brief, comprehensive summary of the entire research.

# 2. Estado da arte

**Introduction:**

- **Context:** Briefly introduce the importance of messaging middleware in distributed systems and the role it plays in facilitating communication between components.
- **Scope:** Clearly outline the scope of your review, specifying that you'll be focusing on ZeroMQ, MQTT, AMQP, and nanomsg/nng.

**For each MOM:**

1. **Key Features and Architecture:**
    - Discuss the key features and architectural principles of messaging middleware solutions.
    - Explore how each middleware handles communication patterns, message queuing, and routing.
2. **Communication Patterns:**
    - Analyze the supported communication patterns of each middleware (e.g., publish-subscribe, request-reply, etc.).
    - Highlight the flexibility and adaptability of each solution in supporting different patterns.
3. **Fault Tolerance and Reliability:**
    - Examine how each middleware solution handles network faults, node failures, and message delivery guarantees.
    - Discuss mechanisms for ensuring reliability and fault tolerance.
    - Explore the quality of service levels offered by each middleware (e.g., exactly-once, at-least-once, at-most-once).
    - Discuss how QoS is implemented and the impact on reliability.
4. **Ease of Use and Integration:**
    - Evaluate the ease of use and the learning curve associated with each middleware.
    - Discuss integration capabilities with various programming languages and frameworks.
5. **Community and Support: *(Não me parece relevante)***
    - Assess the size and activity of the user community for each middleware.
    - Explore the availability of documentation, tutorials, and community support.
6. **Security:  *(Não é o foco da tese)***
    - Discuss the security features offered by each middleware, including encryption, authentication, and authorization.
7. **Use Cases and Applications:**
    - Provide real-world use cases and applications where each middleware is commonly employed.
    - Highlight any industry-specific preferences or trends.
8. **Limitations and Challenges:**
    - Identify limitations or challenges associated with each middleware solution.

**Individual mentions:**

1. **ZeroMQ:**
2. **MQTT (Message Queuing Telemetry Transport):**
    - **Introduction:** Introduce MQTT and its origins, emphasizing its lightweight and publish-subscribe nature.
    - **Brokers and Clients:** Describe the role of brokers and clients in the MQTT architecture.
    - **Use Cases:** Discuss typical use cases where MQTT is commonly applied, such as in IoT and real-time messaging.
3. **AMQP (Advanced Message Queuing Protocol):**
    - **Introduction:** Provide an introduction to AMQP, emphasizing its role in enabling interoperability between different messaging systems.
    - **Message Exchange Patterns:** Detail the message exchange patterns supported by AMQP, such as point-to-point and publish-subscribe.
    - **Features and Standards:** Discuss the key features of AMQP, including its message format, queuing models, and any relevant standards.
4. **nanomsg / nng:**
    - **Overview:** Introduce nanomsg/nng, emphasizing its focus on simplicity and performance.
    - **Communication Model:** Discuss the communication model supported by nanomsg/nng, and how it differs from other middlewares.
    - **Comparison with ZeroMQ:** Since nanomsg/nng is influenced by ZeroMQ, provide a brief comparison between the two, highlighting key differences.

**Comparative Analysis:**

(Utilizar os tópicos comuns a todos os middlewares para a comparacao)

- **Commonalities and Differences:**
    - Dedicate a section to compare the commonalities and differences between the reviewed middlewares. This could include aspects such as communication patterns, protocol support, ease of use, and fault tolerance.
    - Identify trends or common challenges in the usage of messaging middleware.
- **Limitations and Challenges:**
    - Discuss scenarios where one middleware may be more suitable than others.
- **Scalability and Performance:**
    - Compare the scalability and performance of each middleware under different scenarios and workloads.
    - Evaluate their performance, scalability, ease of use, and adaptability to dynamic environments.
    - Discuss any benchmarks or studies comparing these middleware solutions.
- **Fault Tolerance:** Assess how each middleware handles network fault tolerances, spontaneous disconnections, and other challenges.
- **Use Case Suitability:** Discuss the suitability of each middleware for different use cases, considering factors such as IoT, real-time applications, and dynamic network environments.

**Conclusion:**

- Summarize the importance of understanding the state of the art in messaging middleware.
- Summarize the key findings from the individual reviews and the comparative analysis.
    - Summarize the key findings from the analysis of each messaging middleware.
    - Identify any gaps or shortcomings in the existing solutions that your research aims to address.
    - Set the stage for the next chapters by highlighting the motivation for designing a new middleware with exactly-once delivery guarantees.
- Emphasize the relevance of your research in addressing challenges and advancing the field.
- Conclude by highlighting how the insights gained from studying existing middlewares will inform the design and implementation of your messaging middleware with exactly-once delivery guarantees.