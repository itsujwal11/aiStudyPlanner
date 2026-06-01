package com.aasa.controller;

import com.aasa.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminController {

    private static final Logger logger = Logger.getLogger(AdminController.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private com.aasa.service.AuthService authService;

    private static final Map<String, Class<?>> ENTITY_MAP = new LinkedHashMap<>();
    static {
        ENTITY_MAP.put("User", User.class);
        ENTITY_MAP.put("PdfDocument", PdfDocument.class);
        ENTITY_MAP.put("Topic", Topic.class);
        ENTITY_MAP.put("Quiz", Quiz.class);
        ENTITY_MAP.put("QuizAttempt", QuizAttempt.class);
        ENTITY_MAP.put("StudyProgress", StudyProgress.class);
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null) throw new RuntimeException("Not authenticated");
        com.aasa.entity.User user = authService.getUserByEmail(auth.getName());
        if (!"ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Admin access required");
        }
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<?> dashboard(Authentication authentication) {
        try {
            checkAdmin(authentication);
            Map<String, Object> stats = new LinkedHashMap<>();
            for (Map.Entry<String, Class<?>> entry : ENTITY_MAP.entrySet()) {
                Long count = (Long) entityManager.createQuery(
                        "SELECT COUNT(e) FROM " + entry.getKey() + " e").getSingleResult();
                stats.put(entry.getKey(), count);
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/entities")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listEntities(Authentication authentication) {
        try {
            checkAdmin(authentication);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, Class<?>> entry : ENTITY_MAP.entrySet()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", entry.getKey());
                String tableName = entry.getValue().getSimpleName();
                jakarta.persistence.Table table = entry.getValue().getAnnotation(jakarta.persistence.Table.class);
                if (table != null) tableName = table.name();
                info.put("tableName", tableName);

                Long count = (Long) entityManager.createQuery(
                        "SELECT COUNT(e) FROM " + entry.getKey() + " e").getSingleResult();
                info.put("rowCount", count);

                List<Map<String, String>> fields = new ArrayList<>();
                for (java.lang.reflect.Field field : entry.getValue().getDeclaredFields()) {
                    String fn = field.getName();
                    if (fn.equals("serialVersionUID") || fn.startsWith("$")) continue;
                    if (fn.endsWith("_HIBERNATE") || fn.contains("hibernate")) continue;
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("name", fn);
                    f.put("type", field.getType().getSimpleName());
                    fields.add(f);
                }
                info.put("fields", fields);
                result.add(info);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.severe("Admin listEntities error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/entities/{entityName}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listRecords(
            Authentication authentication,
            @PathVariable String entityName,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            checkAdmin(authentication);
            Class<?> clazz = ENTITY_MAP.get(entityName);
            if (clazz == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity: " + entityName));
            }
            List<?> records = entityManager.createQuery(
                            "SELECT e FROM " + entityName + " e ORDER BY e.id DESC")
                    .setFirstResult(offset)
                    .setMaxResults(limit)
                    .getResultList();
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object record : records) {
                data.add(toFlatMap(record));
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.severe("Admin listRecords error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/entities/{entityName}/{id}")
    @Transactional
    public ResponseEntity<?> deleteRecord(Authentication authentication, @PathVariable String entityName, @PathVariable Long id) {
        try {
            checkAdmin(authentication);
            Class<?> clazz = ENTITY_MAP.get(entityName);
            if (clazz == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity: " + entityName));
            }
            Object record = entityManager.find(clazz, id);
            if (record == null) {
                return ResponseEntity.notFound().build();
            }

            deleteCascade(entityName, id);

            record = entityManager.find(clazz, id);
            if (record != null) {
                entityManager.remove(record);
                entityManager.flush();
            }
            logger.info("Admin deleted " + entityName + " #" + id);
            return ResponseEntity.ok(Map.of("success", true, "message", entityName + " #" + id + " deleted"));
        } catch (Exception e) {
            logger.severe("Admin delete error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private void deleteCascade(String entityName, Long id) {
        switch (entityName) {
            case "User" -> {
                entityManager.createQuery("DELETE FROM QuizAttempt qa WHERE qa.user.id = :uid").setParameter("uid", id).executeUpdate();
                entityManager.createQuery("DELETE FROM StudyProgress sp WHERE sp.user.id = :uid").setParameter("uid", id).executeUpdate();
                List<Long> pdfIds = entityManager.createQuery("SELECT p.id FROM PdfDocument p WHERE p.user.id = :uid", Long.class)
                        .setParameter("uid", id).getResultList();
                for (Long pid : pdfIds) {
                    deleteCascade("PdfDocument", pid);
                }
                entityManager.createQuery("DELETE FROM PdfDocument p WHERE p.user.id = :uid").setParameter("uid", id).executeUpdate();
            }
            case "PdfDocument" -> {
                List<Long> topicIds = entityManager.createQuery("SELECT t.id FROM Topic t WHERE t.pdfDocument.id = :pid", Long.class)
                        .setParameter("pid", id).getResultList();
                for (Long tid : topicIds) {
                    deleteCascade("Topic", tid);
                }
                entityManager.createQuery("DELETE FROM Topic t WHERE t.pdfDocument.id = :pid").setParameter("pid", id).executeUpdate();
                entityManager.createQuery("DELETE FROM StudyProgress sp WHERE sp.pdfDocument.id = :pid").setParameter("pid", id).executeUpdate();
            }
            case "Topic" -> {
                entityManager.createQuery("DELETE FROM QuizAttempt qa WHERE qa.quiz.topic.id = :tid").setParameter("tid", id).executeUpdate();
                entityManager.createQuery("DELETE FROM Quiz q WHERE q.topic.id = :tid").setParameter("tid", id).executeUpdate();
                entityManager.createQuery("DELETE FROM StudyProgress sp WHERE sp.topic.id = :tid").setParameter("tid", id).executeUpdate();
            }
            case "Quiz" -> {
                entityManager.createQuery("DELETE FROM QuizAttempt qa WHERE qa.quiz.id = :qid").setParameter("qid", id).executeUpdate();
            }
        }
    }

    private Map<String, Object> toFlatMap(Object obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(obj));
        Class<?> clazz = obj.getClass();
        while (clazz != null && !clazz.getName().startsWith("java")) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                String fn = field.getName();
                if (fn.equals("serialVersionUID") || fn.startsWith("$") || fn.contains("hibernate") || fn.endsWith("_HIBERNATE"))
                    continue;
                field.setAccessible(true);
                try {
                    Object val = field.get(obj);
                    if (val == null) {
                        map.put(fn, null);
                    } else if (val.getClass().getName().startsWith("com.aasa.entity")) {
                        try {
                            java.lang.reflect.Field idField = val.getClass().getDeclaredField("id");
                            idField.setAccessible(true);
                            Object idVal = idField.get(val);
                            map.put(fn, idVal);
                        } catch (Exception e) {
                            map.put(fn, val.getClass().getSimpleName());
                        }
                    } else if (val instanceof Collection) {
                        map.put(fn, ((Collection<?>) val).size() + " items");
                    } else if (val instanceof String && ((String) val).length() > 120) {
                        map.put(fn, ((String) val).substring(0, 120) + "...");
                    } else {
                        map.put(fn, val);
                    }
                } catch (Exception ignored) { }
            }
            clazz = clazz.getSuperclass();
        }
        return map;
    }
}
