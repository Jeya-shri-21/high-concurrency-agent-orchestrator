# Virtual Threads vs. Platform Threads Benchmark

## Objective
Compare execution time, throughput, and approximate heap-memory change for Platform Threads and Virtual Threads.

## Test Configuration
- Number of tasks: 1,000
- Task workload: Includes waiting using sleep
- Platform Threads: Fixed thread pool of 100 threads
- Virtual Threads: Virtual thread per task
- Benchmark runs: 3

## Results

| Metric | Platform Threads | Virtual Threads |
|---|---:|---:|
| Average execution time | 1.12 seconds | 0.133 seconds |
| Throughput (observed range) | 890.01–892.76 tasks/second | 7,212.12–7,973.78 tasks/second |
| Heap-memory change (observed range) | 31,390–33,010 KB | 2,533–2,730 KB |

## Observations
1. Virtual Threads completed the workload in less time across all three runs.
2. Virtual Threads achieved higher throughput for this waiting-oriented workload.
3. The measured heap-memory change was lower for Virtual Threads in all three runs.

## Conclusion
In this experiment, Virtual Threads performed better for the simulated waiting-oriented workload than a fixed pool of 100 Platform Threads.

## Limitations
- Results depend on the machine, JVM, and system load.
- Heap-memory change is an approximate measurement and does not represent total thread memory consumption.
- The benchmark includes waiting time and should not be interpreted as proof that Virtual Threads are faster for CPU-intensive tasks.
- More rigorous profiling and repeated measurements would improve reliability.