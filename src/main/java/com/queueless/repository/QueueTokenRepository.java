package com.queueless.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.queueless.model.QueueToken;
import com.queueless.model.TokenStatus;

public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {

    @Query("select count(t) from QueueToken t where t.user.id = :userId and t.queue.id = :queueId "
         + "and t.tokenDate = :date and t.status in :statuses")
    long countActiveForUserInQueue(@Param("userId") Long userId,
                                   @Param("queueId") Long queueId,
                                   @Param("date") LocalDate date,
                                   @Param("statuses") Collection<TokenStatus> statuses);

    @Query("select t from QueueToken t join fetch t.queue where t.user.id = :userId "
         + "and t.tokenDate = :date and t.status in :statuses order by t.tokenNumber")
    List<QueueToken> findActiveForUser(@Param("userId") Long userId,
                                       @Param("date") LocalDate date,
                                       @Param("statuses") Collection<TokenStatus> statuses);

    @Query("select coalesce(max(t.tokenNumber), 0) from QueueToken t "
         + "where t.queue.id = :queueId and t.tokenDate = :date")
    int findMaxTokenNumber(@Param("queueId") Long queueId, @Param("date") LocalDate date);

    @Query("select count(t) from QueueToken t where t.queue.id = :queueId and t.tokenDate = :date "
         + "and t.status = :status and t.tokenNumber < :number")
    long countBeforeNumberWithStatus(@Param("queueId") Long queueId,
                                     @Param("date") LocalDate date,
                                     @Param("status") TokenStatus status,
                                     @Param("number") int number);

    long countByQueueIdAndTokenDateAndStatus(Long queueId, LocalDate tokenDate, TokenStatus status);

    Optional<QueueToken> findFirstByQueueIdAndTokenDateAndStatusOrderByTokenNumberAsc(
            Long queueId, LocalDate tokenDate, TokenStatus status);

    Optional<QueueToken> findFirstByQueueIdAndTokenDateAndStatus(
            Long queueId, LocalDate tokenDate, TokenStatus status);

    List<QueueToken> findByQueueIdAndTokenDateAndStatusOrderByTokenNumberAsc(
            Long queueId, LocalDate tokenDate, TokenStatus status);
}