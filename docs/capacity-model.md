# Escrow Platform Workload and Capacity Model

**Version:** 1.0
**Status:** Initial engineering assumptions
**Target scale:** 100 million registered users
**Peak traffic objective:** Up to 1 million incoming requests per second

---

## 1. Purpose

This document defines the expected workload of the escrow platform.

It exists to prevent architecture decisions from being based on vague statements such as:

* The platform should be scalable.
* The system should support millions of users.
* We need Kafka because the system is large.
* We should use microservices because large companies use them.

Every major technical decision must be connected to a measurable workload assumption.

This document will guide decisions involving:

* Service boundaries
* Database topology
* Data partitioning
* Kafka partitions
* RabbitMQ queues
* Redis capacity
* Elasticsearch indexes
* Database connection pools
* Application replica counts
* Network bandwidth
* Storage growth
* Multi-region deployment
* Reliability targets

The numbers in this document are initial engineering assumptions. They will evolve as the system and load tests provide real measurements.

---

## 2. Scale Levels

The platform will be designed and tested using four workload levels.

| Level       | Description                                    |
| ----------- | ---------------------------------------------- |
| Development | Local environment used by one engineer         |
| Baseline    | Normal production traffic                      |
| Peak        | Predictable periods of elevated traffic        |
| Stress      | Extreme traffic used to discover system limits |

We will not configure production infrastructure for one million requests per second from the first deployment.

However, we will design the system so that scaling towards that target does not require rewriting the entire platform.

---

## 3. User Population

| Metric                           |  Assumption |
| -------------------------------- | ----------: |
| Registered users                 | 100 million |
| Monthly active users             |  30 million |
| Daily active users               |  10 million |
| Peak concurrently active users   |   2 million |
| Business or marketplace accounts |     500,000 |
| Internal operations users        |      20,000 |

Registered users are not the same as active users.

The system should therefore not calculate infrastructure requirements using:

```text
100 million users = 100 million simultaneous connections
```

Instead, capacity decisions must consider active users, concurrent sessions, request frequency, and transaction behaviour.

---

## 4. Regional Distribution

Initial long-term traffic distribution:

| Region        | Percentage |
| ------------- | ---------: |
| Africa        |        35% |
| Europe        |        20% |
| North America |        20% |
| Asia-Pacific  |        20% |
| Other regions |         5% |

These assumptions mean the platform will eventually require:

* Global traffic routing
* Regional API deployments
* Regional caching
* Data residency controls
* Cross-region disaster recovery
* Protection from a single-region outage

The first implementation may run in one region, but regional assumptions must remain visible in the architecture.

---

## 5. External Request Model

The long-term stress objective is:

```text
1,000,000 incoming requests per second
```

This does not mean one million database transactions per second.

A large percentage of traffic should be absorbed before reaching the primary transactional databases.

### Peak request distribution

```text
1,000,000 incoming requests/second
│
├── 350,000 rejected, limited, or served at the edge
│   ├── CDN responses
│   ├── Static configuration
│   ├── Invalid requests
│   ├── Bot traffic
│   └── Rate-limited traffic
│
└── 650,000 requests accepted by the platform
    │
    ├── 300,000 served from Redis or application caches
    ├── 150,000 served from Elasticsearch or read models
    ├── 150,000 service reads
    └── 50,000 state-changing commands
```

From the 50,000 state-changing commands:

```text
50,000 commands/second

├── 25,000 messaging and notification-related commands
├── 15,000 escrow lifecycle commands
├── 5,000 financial commands
└── 5,000 account, compliance, and administrative commands
```

The most sensitive workload is the financial command workload.

Examples include:

* Funding confirmation
* Ledger posting
* Fund reservation
* Fund release
* Refund
* Reversal
* Payout creation
* Payout completion

These operations prioritise correctness over maximum throughput.

---

## 6. Normal and Peak Traffic

| Traffic class           |   Baseline | Expected peak | Stress target |
| ----------------------- | ---------: | ------------: | ------------: |
| Incoming requests       | 50,000 RPS |   250,000 RPS | 1,000,000 RPS |
| Application reads       | 15,000 RPS |    75,000 RPS |   300,000 RPS |
| State-changing commands |  3,000 RPS |    15,000 RPS |    50,000 RPS |
| Financial commands      |    500 RPS |     2,000 RPS |     5,000 RPS |
| Kafka events            | 15,000/sec |   100,000/sec |   500,000/sec |
| RabbitMQ jobs           |  5,000/sec |    25,000/sec |   100,000/sec |
| WebSocket messages      | 20,000/sec |   100,000/sec |   500,000/sec |

Stress targets are intended for controlled performance testing.

They do not represent the expected average traffic on the first production release.

---

## 7. Escrow Transaction Volume

Initial daily transaction assumptions:

| Metric            |  Baseline |    Peak day |
| ----------------- | --------: | ----------: |
| Escrows created   | 1 million |   5 million |
| Escrows funded    |   700,000 | 3.5 million |
| Escrows completed |   600,000 |   3 million |
| Escrows disputed  |    30,000 |     150,000 |
| Refunds           |    20,000 |     100,000 |
| Payouts           |   500,000 | 2.5 million |

These numbers produce different workloads because one escrow transaction generates multiple operations.

A single completed escrow may involve:

1. Escrow creation
2. Counterparty invitation
3. Terms acceptance
4. Payment initiation
5. Payment provider callback
6. Funding confirmation
7. Ledger posting
8. Delivery submission
9. Buyer acceptance
10. Fund release
11. Fee posting
12. Payout creation
13. Payout confirmation
14. Notifications
15. Search index updates
16. Audit entries

One business transaction may therefore generate dozens of database operations and events.

---

## 8. API Read Distribution

Common read operations include:

| Operation                          | Estimated share |
| ---------------------------------- | --------------: |
| View escrow status                 |             30% |
| List user transactions             |             20% |
| Read messages                      |             15% |
| Search transactions                |             10% |
| View notifications                 |             10% |
| View account or wallet information |              5% |
| Public configuration               |              5% |
| Administrative reads               |              5% |

Not all reads require the same consistency.

### Strongly consistent reads

These should normally use the authoritative service or database:

* Available balance before a withdrawal
* Current escrow state before release
* Refundable amount
* Payout eligibility
* Compliance restriction status
* Ledger account balance used in a financial decision

### Eventually consistent reads

These may use Redis, Elasticsearch, or a read model:

* Transaction search results
* Dashboard summaries
* Historical transaction lists
* Notification feeds
* Analytics
* Administrative search
* User activity summaries

---

## 9. Cache Target

At peak load, at least 50% of eligible reads should be served without reaching an authoritative relational database.

Possible target:

```text
300,000 cacheable reads/second

├── 240,000 cache hits
└── 60,000 cache misses
```

This gives a target cache hit ratio of:

```text
240,000 ÷ 300,000 = 80%
```

The cache hit target should not be achieved by caching sensitive financial decisions incorrectly.

Redis may improve read performance, but PostgreSQL remains authoritative.

---

## 10. Kafka Event Volume

An escrow lifecycle command may create several durable domain events.

Example:

```text
BuyerAcceptedDelivery
        ↓
ReleaseRequested
        ↓
LedgerTransferCompleted
        ↓
EscrowReleased
        ↓
PayoutRequested
        ↓
SearchProjectionRequested
        ↓
NotificationRequested
        ↓
AuditEventRecorded
```

Assume:

```text
15,000 escrow commands/second
× 3 average published events
= 45,000 events/second
```

Financial commands may produce more events:

```text
5,000 financial commands/second
× 6 events
= 30,000 events/second
```

Other platform operations may produce:

```text
25,000 additional events/second
```

Estimated peak:

```text
45,000 + 30,000 + 25,000
= 100,000 Kafka events/second
```

The Kafka stress target will be:

```text
500,000 events/second
```

This provides room for:

* Traffic bursts
* Event replay
* Consumer recovery
* Fraud detection streams
* Analytics
* Audit processing
* Search indexing

---

## 11. Kafka Record Size

Assume an average event size of:

```text
2 KB
```

At 100,000 events per second:

```text
100,000 × 2 KB
= 200 MB/second
```

Daily unreplicated event volume:

```text
200 MB × 86,400 seconds
= approximately 17.3 TB/day
```

This demonstrates why every event should not contain an entire database entity.

Events should carry the information required by consumers without copying unnecessary data.

Kafka retention must vary by topic.

Examples:

| Topic category             | Example retention                     |
| -------------------------- | ------------------------------------- |
| Operational commands       | Hours or days                         |
| Domain events              | Days or weeks                         |
| Audit events               | Longer retention or external archival |
| Retry topics               | Hours or days                         |
| Dead-letter topics         | Until investigated                    |
| Compacted reference topics | Indefinite logical retention          |

Large historical event volumes may be archived to lower-cost object storage.

---

## 12. Kafka Ordering Requirements

Ordering will not be required globally.

It will be required only within a specific business key.

Potential partition keys include:

```text
escrowId
paymentId
ledgerAccountId
payoutId
userId
```

For escrow lifecycle events:

```text
partition key = escrowId
```

This allows events for one escrow to remain ordered within a partition.

Events belonging to different escrows may be processed concurrently.

Choosing a constant key would preserve global order but create one hot partition and destroy scalability.

---

## 13. RabbitMQ Job Volume

RabbitMQ will handle work items that should normally be processed by one worker.

Examples include:

* Send email
* Send SMS
* Send push notification
* Generate receipt
* Scan uploaded evidence
* Retry provider payout
* Export statement
* Produce compliance report
* Process image or document metadata

Estimated workload:

| Job type              | Peak jobs/second |
| --------------------- | ---------------: |
| Email                 |           10,000 |
| Push notification     |           25,000 |
| SMS                   |            2,000 |
| Receipt generation    |            5,000 |
| Evidence scanning     |            3,000 |
| Provider retry tasks  |            1,000 |
| Other background work |            4,000 |

Expected peak:

```text
50,000 jobs/second
```

Stress target:

```text
100,000 jobs/second
```

RabbitMQ consumers must use:

* Manual acknowledgements
* Bounded prefetch
* Dead-letter exchanges
* Retry queues
* Exponential backoff
* Poison-message handling
* Idempotent job processing

---

## 14. Ledger Write Volume

A double-entry operation creates at least two ledger entries.

A practical escrow release may produce three or more entries.

Example: release £100 with a £2 platform fee.

```text
Debit:  Escrow held account       £100
Credit: Seller available account   £98
Credit: Platform fee account        £2
```

The operation remains balanced:

```text
Total debit  = £100
Total credit = £100
```

Assume an average of six ledger entries per completed financial workflow.

At 5,000 peak financial commands per second:

```text
5,000 × 6
= 30,000 ledger entries/second
```

Stress tests should include at least:

```text
50,000 ledger entries/second
```

The ledger architecture must prevent:

* Duplicate entries
* Missing entries
* Unbalanced journals
* Concurrent overspending
* Invalid reversals
* Mutable financial history

---

## 15. Relational Data Growth

### User records

Assume:

```text
100 million users
× 2 KB average indexed storage
= 200 GB
```

After indexes, versioning, audit information, and storage overhead, the user domain may require several hundred gigabytes.

### Escrow records

Assume:

```text
1 million escrows/day
× 365 days
= 365 million escrows/year
```

If an escrow and its indexes require an average of 3 KB:

```text
365 million × 3 KB
= approximately 1.1 TB/year
```

### Ledger entries

Assume:

```text
4 million ledger entries/day
× 365
= 1.46 billion entries/year
```

At an effective indexed storage cost of 1 KB per entry:

```text
approximately 1.46 TB/year
```

At higher financial volumes, the ledger can grow by several terabytes per year.

### Messages

Assume:

```text
6 messages per escrow
× 1 million escrows/day
= 6 million messages/day
```

At 1 KB per message before index and replication overhead:

```text
6 GB/day
≈ 2.2 TB/year raw
```

Attachments must not be stored directly inside PostgreSQL rows.

---

## 16. Object Storage Growth

Evidence may include:

* Images
* Receipts
* Contracts
* Delivery documents
* Videos
* Identity documents
* Dispute files

Assume:

```text
30% of escrows contain one file
Average file size = 1.5 MB
```

Daily evidence storage:

```text
1 million escrows
× 30%
× 1.5 MB
= 450 GB/day
```

Annual storage:

```text
450 GB × 365
= approximately 164 TB/year
```

Evidence should be stored in object storage.

PostgreSQL should store:

* Object identifier
* File type
* Checksum
* Size
* Owner
* Upload status
* Scan status
* Retention status

---

## 17. Elasticsearch Growth

Elasticsearch will index searchable projections, not every field from every database table.

Assume one searchable escrow document averages:

```text
2 KB
```

For 365 million escrows per year:

```text
365 million × 2 KB
= approximately 730 GB raw/year
```

After:

* Index structures
* Replicas
* Segment overhead
* Deleted document overhead

The actual cluster requirement may exceed:

```text
1.5–2 TB/year
```

The platform will require:

* Time-based or lifecycle-managed indexes
* Shard size monitoring
* Replica planning
* Index aliases
* Zero-downtime reindexing
* Hot, warm, and cold storage tiers
* Archived historical data

Deep pagination must not rely on large offset values.

We will use approaches such as:

* `search_after`
* Point-in-time searches
* Cursor-based application pagination

---

## 18. Redis Memory Model

Redis will store temporary or derived information.

Potential cached data:

* Active escrow summaries
* Frequently viewed transaction details
* Rate-limit counters
* Session records
* Idempotency responses
* Fraud velocity counters
* Short-lived verification information

Assume:

```text
20 million active cached objects
× 2 KB each
= 40 GB raw data
```

Redis memory usage includes additional overhead for:

* Keys
* Internal structures
* Expiration metadata
* Replication
* Fragmentation
* Cluster operation

A 40 GB logical dataset may require more than 80 GB of physical memory.

Initial long-term Redis planning assumption:

```text
128–256 GB distributed across multiple Redis Cluster nodes
```

Redis must have eviction and TTL policies appropriate to each data category.

Financial source-of-truth data must not depend exclusively on Redis.

---

## 19. Database Connection Budget

Application replicas must not scale without considering database connection limits.

Example of an unsafe configuration:

```text
200 application replicas
× 30 connections each
= 6,000 database connections
```

Increasing application replicas could overload PostgreSQL before CPU or memory limits are reached.

Assume a PostgreSQL cluster can safely support:

```text
2,000 backend connections through controlled pooling
```

A possible connection budget:

| Workload                  | Connection budget |
| ------------------------- | ----------------: |
| Escrow writes             |               350 |
| Ledger writes             |               400 |
| Payment writes            |               250 |
| User and identity         |               150 |
| Disputes                  |               100 |
| Read workloads            |               400 |
| Administrative jobs       |               100 |
| Migrations and operations |                50 |
| Reserved safety capacity  |               200 |

Total:

```text
2,000 connections
```

Services must use:

* Bounded HikariCP pools
* PgBouncer where appropriate
* Query timeouts
* Connection acquisition timeouts
* Slow-query monitoring
* Pool saturation metrics
* Backpressure

Initial pool formula:

```text
Pool per replica
=
Service connection budget
÷ Maximum expected replicas
```

Example:

```text
Escrow service budget = 350 connections
Maximum replicas = 50

350 ÷ 50 = 7 connections per replica
```

A connection pool of 7–10 may be more appropriate than an arbitrary pool of 50 or 100.

---

## 20. WebSocket Connections

The platform may use WebSockets for:

* Transaction chat
* Real-time status updates
* Delivery updates
* Dispute communication
* Notifications

Assume:

```text
2 million concurrently active users
40% maintaining a WebSocket connection
= 800,000 concurrent connections
```

WebSocket connections should be distributed across specialised gateway or messaging nodes.

Application services should not store essential connection state only in memory.

The system must support:

* Connection heartbeats
* Reconnection
* Authentication expiry
* Node failure
* Message replay or catch-up
* Backpressure
* Per-user connection limits
* Regional connection routing

---

## 21. Network Bandwidth

Assume the average accepted API response is:

```text
2 KB
```

At 650,000 accepted requests per second:

```text
650,000 × 2 KB
= 1.3 GB/second
```

This is approximately:

```text
10.4 gigabits/second
```

This excludes:

* Request bodies
* TLS overhead
* Kafka replication traffic
* Database replication
* Elasticsearch replication
* Object downloads
* WebSocket traffic
* Observability data

Large files must be uploaded and downloaded directly through object storage using secure, time-limited URLs where possible.

They should not pass through Spring Boot application memory unnecessarily.

---

## 22. Availability Targets

| System capability       | Target |
| ----------------------- | -----: |
| Authentication          | 99.99% |
| Escrow reads            | 99.99% |
| Financial commands      | 99.99% |
| Search                  |  99.9% |
| Messaging               |  99.9% |
| Internal administration |  99.9% |

At 99.99% monthly availability, the approximate permitted downtime is only a few minutes per month.

Availability targets require more than adding replicas.

They require:

* Failure isolation
* Redundancy
* Health checks
* Disaster recovery
* Safe deployment
* Monitoring
* Capacity headroom
* Data recovery
* Provider failover
* Tested operational procedures

---

## 23. Latency Targets

| Operation                             |          Target |
| ------------------------------------- | --------------: |
| Cached read p95                       |    Under 100 ms |
| Standard service read p95             |    Under 250 ms |
| Internal state change p95             |    Under 500 ms |
| Financial command acknowledgement p95 |  Under 1 second |
| Search query p95                      |    Under 750 ms |
| Real-time message delivery p95        | Under 2 seconds |
| Notification job creation             | Under 2 seconds |

External provider completion time must be measured separately.

A payout provider taking 10 seconds does not necessarily mean the internal platform itself has 10-second processing latency.

Long-running operations should normally return an accepted or processing response and expose a status resource.

---

## 24. Failure Capacity

The platform should retain spare capacity for failures.

Normal production traffic should not operate at 100% resource utilisation.

Initial target:

```text
Normal sustained utilisation: 50–60%
Peak sustained utilisation: below 75%
Emergency headroom: at least 25%
```

This allows the platform to survive:

* Loss of one application availability zone
* Consumer restarts
* Database failover
* Broker rebalancing
* Sudden traffic bursts
* Deployment overlap
* Payment provider failure
* Cache degradation

---

## 25. Backpressure Requirements

When downstream systems become slow, upstream services must not create unlimited work.

The platform must support:

* Bounded queues
* Request rejection
* Rate limiting
* Consumer pause and resume
* Kafka lag alerts
* RabbitMQ queue-depth alerts
* Connection pool saturation alerts
* Circuit breakers
* Load shedding
* Retry limits
* Dead-letter handling

Retries must not be immediate and unlimited.

A failing dependency combined with aggressive retries can multiply traffic and cause a complete system outage.

---

## 26. Data Retention

Initial retention assumptions:

| Data                     | Retention                              |
| ------------------------ | -------------------------------------- |
| Ledger records           | Permanent or legally required maximum  |
| Escrow records           | Minimum 7 years after completion       |
| Payment records          | Minimum 7 years                        |
| Audit logs               | Minimum 7 years                        |
| Dispute evidence         | According to legal and business policy |
| Application logs         | 30–90 days online                      |
| Metrics                  | Aggregated over time                   |
| Kafka operational events | Topic-specific                         |
| Dead-letter records      | Until resolved and archived            |

Retention must eventually vary by jurisdiction.

Deletion requests must not remove records that the platform is legally required to preserve.

---

## 27. Capacity Testing Stages

The project will not jump directly to one million requests per second.

We will test in stages:

### Stage 1

```text
100 RPS
```

Purpose:

* Functional correctness
* Basic bottleneck discovery
* Test setup validation

### Stage 2

```text
1,000 RPS
```

Purpose:

* Connection pool behaviour
* Cache effectiveness
* Consumer throughput
* Database query performance

### Stage 3

```text
10,000 RPS
```

Purpose:

* Horizontal scaling
* Broker partitioning
* Lock contention
* Queue backpressure

### Stage 4

```text
100,000 RPS
```

Purpose:

* Distributed bottlenecks
* Cache hot keys
* Kafka lag
* Elasticsearch saturation
* Database partition effectiveness

### Stage 5

```text
1,000,000 RPS
```

Purpose:

* Architecture stress testing
* Edge and gateway capacity
* Regional traffic distribution
* Failure behaviour
* Cost and operational evaluation

Each stage must produce measurements rather than assumptions.

---

## 28. Metrics Required

The platform must collect:

### API metrics

* Requests per second
* Error rate
* p50, p95, and p99 latency
* Rate-limited requests
* Active requests
* Request payload size

### Database metrics

* Active connections
* Waiting connections
* Query duration
* Deadlocks
* Lock waits
* Transaction duration
* Replication lag
* Rows read and written
* Cache hit ratio

### Kafka metrics

* Producer throughput
* Consumer throughput
* Consumer lag
* Partition skew
* Failed publications
* Retry volume
* Dead-letter volume
* Rebalance frequency

### RabbitMQ metrics

* Queue depth
* Publish rate
* Delivery rate
* Redelivery rate
* Consumer utilisation
* Unacknowledged messages
* Dead-letter rate

### Redis metrics

* Cache hit ratio
* Memory usage
* Evictions
* Expired keys
* Hot keys
* Command latency
* Replication health

### Elasticsearch metrics

* Search latency
* Indexing latency
* Rejected requests
* Shard size
* Heap pressure
* Disk usage
* Refresh time
* Search projection lag

### Business metrics

* Escrows created
* Escrows funded
* Funds held
* Funds released
* Refunds
* Payout failures
* Dispute rate
* Reconciliation differences
* Ledger imbalance count

The expected ledger imbalance count is:

```text
0
```

---

## 29. Architecture Implications

This capacity model suggests the following architectural requirements:

1. Read and write workloads must be treated differently.
2. PostgreSQL cannot serve every read directly.
3. Financial writes require strong transactional controls.
4. Redis is required for temporary high-speed access, not financial truth.
5. Elasticsearch is required for advanced search and investigation.
6. Kafka is required for durable, high-volume event distribution.
7. RabbitMQ is appropriate for worker-oriented tasks.
8. Object storage is required for evidence and attachments.
9. Database connections must be budgeted globally.
10. Data must eventually be partitioned.
11. Services must support horizontal scaling.
12. Backpressure is required throughout the architecture.
13. The system must be observable before high-load testing begins.
14. Multi-region design must separate availability from financial correctness.
15. One million requests per second must be reached through staged measurement, not assumed from architecture diagrams.

---

## 30. Decisions Not Yet Finalised

The following decisions will be addressed in later design documents:

* Exact PostgreSQL partitioning key
* Number of database clusters
* Kafka topic design
* Kafka partition count
* Event serialisation format
* RabbitMQ exchange and queue topology
* Redis cluster layout
* Elasticsearch shard strategy
* Multi-region write ownership
* Data residency boundaries
* Search index retention
* Ledger account locking strategy
* Financial database isolation level
* Exact service boundaries

These decisions require the domain model, state machine, and business invariants.

---

## 31. Next Document

The next document is:

```text
docs/invariants.md
```

It will define conditions that must always remain true, including:

* Money cannot be created or destroyed accidentally.
* Ledger entries must balance.
* Funds cannot be released twice.
* Refunds cannot exceed the refundable amount.
* Disputed funds cannot be automatically released.
* Duplicate events must not create duplicate business effects.
* A transaction must follow valid state transitions.
* Every confirmed financial operation must remain auditable.
