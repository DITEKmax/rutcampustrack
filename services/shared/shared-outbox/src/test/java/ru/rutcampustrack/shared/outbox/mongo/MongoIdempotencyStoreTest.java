package ru.rutcampustrack.shared.outbox.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M13 G24-fix-1 — regression guard для B2 (MongoIdempotencyStore
 * transactional binding).
 *
 * <p>До G24-fix-1 store использовал {@code mongoTemplate.getCollection(name)
 * .insertOne(doc)} — это раздаёт write напрямую в MongoCollection в обход
 * Spring's {@code MongoTransactionManager} session binding. Если caller
 * @Transactional handler throws после tryClaim, claim row коммитится,
 * domain rollback'ается → event lost при redelivery (false-positive duplicate).
 *
 * <p>Этот тест гарантирует что используется {@link MongoTemplate#insert(Object,
 * String)} — Spring routing path который участвует в активной session.
 * Если кто-то откатит fix → тест fail с явным reason.
 */
class MongoIdempotencyStoreTest {

    @Test
    void tryClaim_usesMongoTemplateInsert_notRawCollection() {
        MongoTemplate template = mock(MongoTemplate.class);
        // Если код case'нет на getCollection — тест увидит
        // через verify(template, never()).getCollection(anyString()).
        when(template.insert(any(Document.class), anyString())).thenReturn(new Document());

        MongoIdempotencyStore store = new MongoIdempotencyStore(template);
        boolean claimed = store.tryClaim("test-consumer", UUID.randomUUID());

        assertThat(claimed).isTrue();
        verify(template).insert(any(Document.class), eq("event_consumer_processed"));
        // КРИТИЧНО: getCollection НЕ должен вызываться в tryClaim, иначе
        // обходим MongoTransactionManager binding (regression B2 в G24).
        verify(template, never()).getCollection(anyString());
    }

    @Test
    void tryClaim_duplicateKey_returnsFalse() {
        MongoTemplate template = mock(MongoTemplate.class);
        doThrow(new DuplicateKeyException("E11000"))
                .when(template).insert(any(Document.class), anyString());

        MongoIdempotencyStore store = new MongoIdempotencyStore(template);
        boolean claimed = store.tryClaim("test-consumer", UUID.randomUUID());

        assertThat(claimed).isFalse();
    }

    @Test
    void tryClaim_otherException_propagates() {
        MongoTemplate template = mock(MongoTemplate.class);
        doThrow(new RuntimeException("network down"))
                .when(template).insert(any(Document.class), anyString());

        MongoIdempotencyStore store = new MongoIdempotencyStore(template);

        // Не-DuplicateKey ошибки должны проброситься чтобы caller видел сбой.
        assertThatThrownBy(() -> store.tryClaim("test-consumer", UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("network down");
    }

    @Test
    void tryClaim_nullConsumerId_throws() {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoIdempotencyStore store = new MongoIdempotencyStore(template);

        assertThatThrownBy(() -> store.tryClaim(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.tryClaim("", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tryClaim_nullEventId_throws() {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoIdempotencyStore store = new MongoIdempotencyStore(template);

        assertThatThrownBy(() -> store.tryClaim("consumer", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
