package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateCommentRequest;
import com.thinklab.domain.exception.WorkOrderNotFoundException;
import com.thinklab.domain.model.WorkOrder.Comment;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.WorkOrderRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for adding a Comment to a WorkOrder (BIAN Behavior Qualifier: {@code comment/initiate}).
 *
 * <p>A {@code REQUESTER}-role caller can never author an {@code internal} note - the request field is
 * ignored and forced to {@code false} whenever {@code role} is {@code REQUESTER}, so a requester
 * never accidentally hides their own comment from their own future reads.
 */
@Singleton
public class InitiateCommentUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateCommentUseCase.class);
    private static final String REQUESTER_ROLE = "REQUESTER";

    private final HashServicePort hashServicePort;
    private final WorkOrderRepository workOrderRepository;

    public InitiateCommentUseCase(HashServicePort hashServicePort, WorkOrderRepository workOrderRepository) {
        this.hashServicePort = hashServicePort;
        this.workOrderRepository = workOrderRepository;
    }

    public Mono<Void> execute(UUID id, InitiateCommentRequest request, String executor, String role) {
        log.info("[USE CASE] Adding comment to WorkOrder ID: {}", id);

        boolean internal = !REQUESTER_ROLE.equals(role) && request.internal();

        return workOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new WorkOrderNotFoundException(id)))
                .flatMap(workOrder -> hashServicePort.generateSovereignId("workorder-comment-creation")
                        .flatMap(commentId -> {
                            Comment comment = new Comment(commentId, executor, request.text(), internal, Instant.now());
                            var entry = workOrder.addComment(comment, executor);
                            return workOrderRepository.addComment(id, comment, entry);
                        }));
    }
}
