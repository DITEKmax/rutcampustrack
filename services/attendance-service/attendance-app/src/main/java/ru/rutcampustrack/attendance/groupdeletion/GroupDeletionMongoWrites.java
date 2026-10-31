package ru.rutcampustrack.attendance.groupdeletion;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * Includes repository saves and direct template updates/removals. Each mutation shares a Mongo
 * transaction with its group fence, including previously standalone cleanup writes. Bulk lesson
 * materialization already takes the same fence through PairWriteCoordinator before its batch.
 */
@Aspect
@Component
public class GroupDeletionMongoWrites {
    private final ObjectProvider<MongoTemplate> templates;
    private final ObjectProvider<GroupDeletionMongoFence> fences;
    private final TransactionTemplate tx;

    public GroupDeletionMongoWrites(
            ObjectProvider<MongoTemplate> templates,
            ObjectProvider<GroupDeletionMongoFence> fences,
            @Qualifier("mongoTransactionManager") PlatformTransactionManager manager) {
        this.templates = templates;
        this.fences = fences;
        this.tx = new TransactionTemplate(manager);
    }

    @Around(
            "execution(* org.springframework.data.mongodb.core.MongoTemplate.save(..)) ||"
                + " execution(* org.springframework.data.mongodb.core.MongoTemplate.insert(..)) ||"
                + " execution(* org.springframework.data.mongodb.core.MongoTemplate.insertAll(..))"
                + " || execution(*"
                + " org.springframework.data.mongodb.core.MongoTemplate.updateFirst(..)) ||"
                + " execution(*"
                + " org.springframework.data.mongodb.core.MongoTemplate.updateMulti(..)) ||"
                + " execution(* org.springframework.data.mongodb.core.MongoTemplate.upsert(..)) ||"
                + " execution(*"
                + " org.springframework.data.mongodb.core.MongoTemplate.findAndModify(..)) ||"
                + " execution(*"
                + " org.springframework.data.mongodb.core.MongoTemplate.findAndRemove(..)) ||"
                + " execution(*"
                + " org.springframework.data.mongodb.core.MongoTemplate.findAllAndRemove(..)) ||"
                + " execution(* org.springframework.data.mongodb.core.MongoTemplate.remove(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MongoTemplate mongo = templates.getObject();
        Object[] args = point.getArgs();
        String collection = null;
        Class<?> entity = null;
        for (Object arg : args)
            if (arg instanceof String s) collection = s;
            else if (arg instanceof Class<?> c) entity = c;
        if (collection == null && entity != null) collection = mongo.getCollectionName(entity);
        Object object = args.length > 0 ? args[0] : null;
        if (collection == null
                && object != null
                && !(object instanceof Query)
                && !(object instanceof Collection<?>))
            collection = mongo.getCollectionName(object.getClass());
        if (collection == null && object instanceof Collection<?> many && !many.isEmpty())
            collection = mongo.getCollectionName(many.iterator().next().getClass());
        if (collection == null || !GroupDeletionMongoFence.COLLECTIONS.contains(collection))
            return point.proceed();
        String selected = collection;
        try {
            return tx.execute(
                    status -> {
                        Set<Long> groups = new TreeSet<>();
                        Set<Long> lessons = new TreeSet<>();
                        Map<Long, Long> expectedByLesson = new TreeMap<>();
                        if (object instanceof Query query) {
                            var matches = mongo.find(query, Document.class, selected);
                            boolean mayCreate =
                                    point.getSignature().getName().equals("upsert")
                                            || java.util.Arrays.stream(args)
                                                    .anyMatch(
                                                            arg ->
                                                                    arg
                                                                                    instanceof
                                                                                    org
                                                                                                    .springframework
                                                                                                    .data
                                                                                                    .mongodb
                                                                                                    .core
                                                                                                    .FindAndModifyOptions
                                                                                            options
                                                                            && options.isUpsert());
                            if (matches.isEmpty() && !mayCreate) {
                                try {
                                    return point.proceed();
                                } catch (Throwable failure) {
                                    throw new ProceedFailure(failure);
                                }
                            }
                            collect(query.getQueryObject(), groups, lessons, expectedByLesson);
                            for (Document row : matches)
                                collect(row, groups, lessons, expectedByLesson);
                        } else if (object instanceof Collection<?> many)
                            for (Object value : many)
                                collect(document(mongo, value), groups, lessons, expectedByLesson);
                        else if (object != null)
                            collect(document(mongo, object), groups, lessons, expectedByLesson);
                        for (Object arg : args)
                            if (arg instanceof Update update)
                                collect(
                                        update.getUpdateObject(),
                                        groups,
                                        lessons,
                                        expectedByLesson);
                        for (long lesson : lessons) {
                            Long expected = expectedByLesson.get(lesson);
                            if (expected == null && groups.size() == 1)
                                expected = groups.iterator().next();
                            groups.add(fences.getObject().lessonGroup(lesson, null, expected));
                        }
                        if (groups.isEmpty())
                            throw new ru.rutcampustrack.attendance.exception.ConflictException(
                                    "Неизвестная группа записи; повторите действие");
                        for (long group : groups) fences.getObject().write(group);
                        try {
                            return point.proceed();
                        } catch (Throwable failure) {
                            throw new ProceedFailure(failure);
                        }
                    });
        } catch (ProceedFailure failure) {
            throw failure.getCause();
        }
    }

    private static Document document(MongoTemplate mongo, Object value) {
        if (value instanceof Document d) return d;
        Document d = new Document();
        mongo.getConverter().write(value, d);
        return d;
    }

    private static void collect(
            Document document, Set<Long> groups, Set<Long> lessons, Map<Long, Long> expected) {
        Set<Long> owners = new TreeSet<>(), ids = new TreeSet<>();
        group(document, owners, ids);
        groups.addAll(owners);
        lessons.addAll(ids);
        if (owners.size() == 1)
            for (long id : ids) {
                Long prior = expected.putIfAbsent(id, owners.iterator().next());
                if (prior != null && !owners.contains(prior))
                    throw new ru.rutcampustrack.attendance.exception.ConflictException(
                            "Противоречивые группы урока");
            }
    }

    private static void group(Document document, Set<Long> groups, Set<Long> lessons) {
        Object lesson = document.get("lesson_id");
        if (lesson instanceof Number number && number.longValue() > 0)
            lessons.add(number.longValue());
        if (lesson instanceof Document condition) {
            if (condition.get("$eq") instanceof Number number && number.longValue() > 0)
                lessons.add(number.longValue());
            if (condition.get("$in") instanceof Collection<?> values)
                for (Object item : values)
                    if (item instanceof Number number && number.longValue() > 0)
                        lessons.add(number.longValue());
        }
        Object value = document.get("group_id");
        if (value instanceof Number number && number.longValue() > 0)
            groups.add(number.longValue());
        if (value instanceof Document condition) {
            if (condition.get("$eq") instanceof Number number && number.longValue() > 0)
                groups.add(number.longValue());
            if (condition.get("$in") instanceof Collection<?> values)
                for (Object item : values)
                    if (item instanceof Number number && number.longValue() > 0)
                        groups.add(number.longValue());
        }
        for (Object nested : document.values()) {
            if (nested instanceof Document child) group(child, groups, lessons);
            else if (nested instanceof Collection<?> values)
                for (Object item : values)
                    if (item instanceof Document child) group(child, groups, lessons);
        }
    }

    private static final class ProceedFailure extends RuntimeException {
        ProceedFailure(Throwable cause) {
            super(cause);
        }
    }
}
