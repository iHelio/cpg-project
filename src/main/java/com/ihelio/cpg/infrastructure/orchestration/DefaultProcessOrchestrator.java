/*
 * Copyright 2026 ihelio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihelio.cpg.infrastructure.orchestration;

import com.ihelio.cpg.application.orchestration.ContextAssembler;
import com.ihelio.cpg.application.orchestration.InstanceOrchestrator;
import com.ihelio.cpg.domain.execution.ExecutionContext;
import com.ihelio.cpg.domain.execution.ProcessInstance;
import com.ihelio.cpg.domain.model.ProcessGraph;
import com.ihelio.cpg.domain.orchestration.DecisionTrace;
import com.ihelio.cpg.domain.orchestration.DecisionTracer;
import com.ihelio.cpg.domain.orchestration.NavigationDecision;
import com.ihelio.cpg.domain.orchestration.OrchestrationEvent;
import com.ihelio.cpg.domain.orchestration.ProcessOrchestrator;
import com.ihelio.cpg.domain.orchestration.RuntimeContext;
import com.ihelio.cpg.domain.repository.ProcessGraphRepository;
import com.ihelio.cpg.domain.repository.ProcessInstanceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultProcessOrchestrator is the main implementation of the ProcessOrchestrator port.
 *
 * <p>It implements an event-driven architecture where:
 * <ul>
 *   <li>Events are queued for processing</li>
 *   <li>A background event loop processes events</li>
 *   <li>Each event triggers reevaluation of affected instances</li>
 *   <li>Decisions are made and traced for each evaluation</li>
 * </ul>
 *
 * <p>Uses Java 21 virtual threads for efficient concurrent processing.
 */
public class DefaultProcessOrchestrator implements ProcessOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProcessOrchestrator.class);

    private final InstanceOrchestrator instanceOrchestrator;
    private final ContextAssembler contextAssembler;
    private final ProcessGraphRepository graphRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final DecisionTracer decisionTracer;

    private final BlockingQueue<OrchestrationEvent> eventQueue;
    private final Map<ProcessInstance.ProcessInstanceId, OrchestrationStatus> instanceStatuses;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    private final OrchestratorConfigProperties config;

    /**
     * Creates a DefaultProcessOrchestrator with all dependencies.
     *
     * @param instanceOrchestrator the instance orchestrator
     * @param contextAssembler the context assembler
     * @param graphRepository repository for process graphs
     * @param instanceRepository repository for process instances
     * @param decisionTracer the decision tracer
     * @param config configuration properties
     */
    public DefaultProcessOrchestrator(
            InstanceOrchestrator instanceOrchestrator,
            ContextAssembler contextAssembler,
            ProcessGraphRepository graphRepository,
            ProcessInstanceRepository instanceRepository,
            DecisionTracer decisionTracer,
            OrchestratorConfigProperties config) {
        this.instanceOrchestrator = Objects.requireNonNull(instanceOrchestrator);
        this.contextAssembler = Objects.requireNonNull(contextAssembler);
        this.graphRepository = Objects.requireNonNull(graphRepository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.decisionTracer = Objects.requireNonNull(decisionTracer);
        this.config = Objects.requireNonNull(config);

        this.eventQueue = new LinkedBlockingQueue<>(config.eventQueueCapacity());
        this.instanceStatuses = new ConcurrentHashMap<>();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the orchestrator's event processing loop.
     */
    @PostConstruct
    public void startEventLoop() {
        if (running.compareAndSet(false, true)) {
            LOG.info("Starting orchestrator event loop");
            executor.submit(this::eventLoop);
        }
    }

    /**
     * Stops the orchestrator's event processing loop.
     */
    @PreDestroy
    public void stopEventLoop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping orchestrator event loop");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public ProcessInstance start(ProcessGraph graph, RuntimeContext initialContext) {
        Objects.requireNonNull(graph, "graph is required");
        Objects.requireNonNull(initialContext, "initialContext is required");

        LOG.info("Starting new process instance for graph: {}", graph.id());

        // Create new process instance
        ProcessInstance instance = ProcessInstance.builder()
            .id(UUID.randomUUID().toString())
            .processGraphId(graph.id())
            .processGraphVersion(graph.version())
            .context(initialContext.toExecutionContext())
            .startedAt(Instant.now())
            .build();

        // Save instance
        instanceRepository.save(instance);

        // Orchestrate entry
        var result = instanceOrchestrator.orchestrateEntry(instance, graph, initialContext);

        // Update status
        instanceStatuses.put(instance.id(), new OrchestrationStatus(
            result.instance(),
            result.decision(),
            result.trace(),
            result.isExecuted() || result.isWaiting()
        ));

        // Save updated instance
        instanceRepository.save(result.instance());

        LOG.info("Process instance {} started with status: {}",
            instance.id(), result.status());

        return result.instance();
    }

    @Override
    public void signal(OrchestrationEvent event) {
        Objects.requireNonNull(event, "event is required");

        LOG.debug("Received event: {} ({})", event.eventType(), event.eventId());

        if (!eventQueue.offer(event)) {
            LOG.warn("Event queue full, dropping event: {}", event.eventId());
        }
    }

    @Override
    public void suspend(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");

        LOG.info("Suspending process instance: {}", instanceId);

        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.suspend();
            instanceRepository.save(instance);

            instanceStatuses.computeIfPresent(instanceId, (id, status) ->
                new OrchestrationStatus(instance, status.lastDecision(), status.lastTrace(), false));
        });
    }

    @Override
    public void resume(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");

        LOG.info("Resuming process instance: {}", instanceId);

        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.resume();
            instanceRepository.save(instance);

            // Trigger reevaluation
            ProcessGraph graph = graphRepository.findById(instance.processGraphId())
                .orElseThrow(() -> new IllegalStateException(
                    "Process graph not found: " + instance.processGraphId()));

            RuntimeContext context = contextAssembler.assemble(instance, null);
            var result = instanceOrchestrator.orchestrate(instance, graph, null);

            instanceStatuses.put(instanceId, new OrchestrationStatus(
                result.instance(),
                result.decision(),
                result.trace(),
                result.isExecuted() || result.isWaiting()
            ));

            instanceRepository.save(result.instance());
        });
    }

    @Override
    public void cancel(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");

        LOG.info("Cancelling process instance: {}", instanceId);

        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.fail();
            instanceRepository.save(instance);

            instanceStatuses.computeIfPresent(instanceId, (id, status) ->
                new OrchestrationStatus(instance, status.lastDecision(), status.lastTrace(), false));
        });
    }

    @Override
    public OrchestrationStatus getStatus(ProcessInstance.ProcessInstanceId instanceId) {
        Objects.requireNonNull(instanceId, "instanceId is required");

        OrchestrationStatus cached = instanceStatuses.get(instanceId);
        if (cached != null) {
            return cached;
        }

        // Load from repository
        return instanceRepository.findById(instanceId)
            .map(instance -> {
                Optional<DecisionTrace> lastTrace = decisionTracer.findLatestByInstanceId(instanceId);
                return new OrchestrationStatus(
                    instance,
                    null,
                    lastTrace.orElse(null),
                    instance.isRunning()
                );
            })
            .orElse(null);
    }

    private void eventLoop() {
        LOG.info("Event loop started");

        while (running.get()) {
            try {
                OrchestrationEvent event = eventQueue.poll(
                    config.evaluationIntervalMs(), TimeUnit.MILLISECONDS);

                if (event != null) {
                    processEvent(event);
                } else {
                    // Periodic evaluation for time-based triggers
                    performPeriodicEvaluation();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Error in event loop", e);
            }
        }

        LOG.info("Event loop stopped");
    }

    private void processEvent(OrchestrationEvent event) {
        LOG.debug("Processing event: {} ({})", event.eventType(), event.eventId());

        // Find affected instances
        var affectedInstances = findAffectedInstances(event);

        for (ProcessInstance instance : affectedInstances) {
            try {
                processInstanceEvent(instance, event);
            } catch (Exception e) {
                LOG.error("Error processing event {} for instance {}",
                    event.eventId(), instance.id(), e);
            }
        }
    }

    private void processInstanceEvent(ProcessInstance instance, OrchestrationEvent event) {
        ProcessGraph graph = graphRepository.findById(instance.processGraphId())
            .orElseThrow(() -> new IllegalStateException(
                "Process graph not found: " + instance.processGraphId()));

        // Add event to context
        RuntimeContext context = contextAssembler.assemble(instance, null);
        ExecutionContext.ReceivedEvent receivedEvent = new ExecutionContext.ReceivedEvent(
            event.eventType(),
            event.eventId(),
            event.timestamp(),
            Map.of()
        );
        context = contextAssembler.addEvent(context, receivedEvent);
        instance.updateContext(context.toExecutionContext());

        // Reevaluate
        var result = instanceOrchestrator.reevaluateAfterEvent(
            instance, graph, context, event.eventType());

        instanceStatuses.put(instance.id(), new OrchestrationStatus(
            result.instance(),
            result.decision(),
            result.trace(),
            result.isExecuted() || result.isWaiting()
        ));

        instanceRepository.save(result.instance());

        LOG.debug("Instance {} reevaluated after event {}: {}",
            instance.id(), event.eventId(), result.status());
    }

    private java.util.List<ProcessInstance> findAffectedInstances(OrchestrationEvent event) {
        // Handle specific event types that have explicit instanceId first
        return switch (event) {
            case OrchestrationEvent.NodeCompleted nc ->
                instanceRepository.findById(nc.instanceId())
                    .filter(ProcessInstance::isRunning)
                    .map(java.util.List::of)
                    .orElse(java.util.List.of());

            case OrchestrationEvent.NodeFailed nf ->
                instanceRepository.findById(nf.instanceId())
                    .filter(ProcessInstance::isRunning)
                    .map(java.util.List::of)
                    .orElse(java.util.List.of());

            case OrchestrationEvent.Approval approval ->
                instanceRepository.findById(approval.instanceId())
                    .filter(ProcessInstance::isRunning)
                    .map(java.util.List::of)
                    .orElse(java.util.List.of());

            case OrchestrationEvent.TimerExpired timer ->
                instanceRepository.findById(timer.instanceId())
                    .filter(ProcessInstance::isRunning)
                    .map(java.util.List::of)
                    .orElse(java.util.List.of());

            case OrchestrationEvent.DomainEvent domainEvent -> {
                // For domain events, first try to find by instance ID (correlationId may be instanceId)
                if (domainEvent.correlationId() != null && !domainEvent.correlationId().isBlank()) {
                    // Try to find by instance ID first
                    var byInstanceId = instanceRepository.findById(
                            new ProcessInstance.ProcessInstanceId(domainEvent.correlationId()))
                        .filter(ProcessInstance::isRunning)
                        .map(java.util.List::of)
                        .orElse(java.util.List.of());

                    if (!byInstanceId.isEmpty()) {
                        yield byInstanceId;
                    }

                    // Fall back to correlation ID lookup
                    var byCorrelation = instanceRepository.findByCorrelationId(domainEvent.correlationId())
                        .stream()
                        .filter(ProcessInstance::isRunning)
                        .toList();

                    if (!byCorrelation.isEmpty()) {
                        yield byCorrelation;
                    }
                }
                // Broadcast to all running instances
                yield instanceRepository.findRunning();
            }

            case OrchestrationEvent.PolicyUpdate policyUpdate ->
                // Find all running instances that might use this policy
                instanceRepository.findRunning();

            default -> {
                // For data changes and failures, try correlation ID first
                if (event.correlationId() != null && !event.correlationId().isBlank()) {
                    var byCorrelation = instanceRepository.findByCorrelationId(event.correlationId())
                        .stream()
                        .filter(ProcessInstance::isRunning)
                        .toList();
                    if (!byCorrelation.isEmpty()) {
                        yield byCorrelation;
                    }
                }
                // Fall back to all running instances
                yield instanceRepository.findRunning();
            }
        };
    }

    private void performPeriodicEvaluation() {
        // Check for expired timers and SLAs
        for (var entry : instanceStatuses.entrySet()) {
            ProcessInstance.ProcessInstanceId instanceId = entry.getKey();
            OrchestrationStatus status = entry.getValue();

            if (!status.isActive()) {
                continue;
            }

            ProcessInstance instance = status.instance();

            // Check for overdue obligations
            boolean hasOverdue = instance.context().obligations().stream()
                .anyMatch(ExecutionContext.Obligation::isOverdue);

            if (hasOverdue) {
                LOG.debug("Instance {} has overdue obligations, triggering reevaluation",
                    instanceId);
                signal(OrchestrationEvent.TimerExpired.slaExpired(
                    instanceId,
                    "periodic-check",
                    Instant.now(),
                    null
                ));
            }
        }
    }
}
