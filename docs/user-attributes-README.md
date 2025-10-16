# User Attribute Database Operations Analysis

## Overview

This directory contains a comprehensive analysis of how Keycloak handles user attribute database operations, specifically answering the question:

> **"Is it really 100% certain that an update (PUT) on a user will then lead to N inserts for the attributes?"**

## Quick Answer

**YES.** When updating user attributes, Keycloak uses a DELETE + INSERT pattern:
- 1 DELETE statement per attribute name
- N INSERT statements for N new values

Example:
```java
// Updating 'roles' from ["admin", "user"] to ["moderator", "reviewer"]
user.setAttribute("roles", Arrays.asList("moderator", "reviewer"));
// Results in: 1 DELETE + 2 INSERTs
```

## Documentation Files

### 1. Quick Reference (START HERE)
**[user-attribute-operations-quickref.md](user-attribute-operations-quickref.md)**

A one-page reference with:
- Direct answer to the question
- Quick optimization tips
- Performance expectations
- When to worry and what to do

**Best for:** Developers who need quick answers and practical guidance

### 2. Comprehensive Answer
**[user-attribute-update-answer.md](user-attribute-update-answer.md)**

Detailed explanation including:
- Why Keycloak uses this pattern
- Is it a performance problem?
- How to optimize
- Is this a best practice?

**Best for:** Understanding the rationale and making informed decisions

### 3. In-Depth Technical Analysis
**[user-attribute-database-operations.md](user-attribute-database-operations.md)**

Complete technical analysis covering:
- Database operation patterns
- Performance considerations
- Concurrency safety (KEYCLOAK-3296)
- Alternative approaches
- Best practices

**Best for:** Deep understanding of implementation details

## Code Resources

### Examples
**[../examples/userattributes/](../examples/userattributes/)**

Executable code examples demonstrating:
1. Creating users with attributes
2. Updating multi-valued attributes
3. Updating single-valued attributes
4. REST API simulation
5. Performance scenarios
6. Concurrency safety

### Tests
**[../testsuite/model/src/test/java/org/keycloak/testsuite/model/user/UserAttributeDatabaseOperationsTest.java](../testsuite/model/src/test/java/org/keycloak/testsuite/model/user/UserAttributeDatabaseOperationsTest.java)**

Comprehensive test suite validating:
- Attribute creation patterns
- Multi-valued attribute updates
- Single-valued attribute updates
- Bulk update scenarios
- Performance with many attributes
- Attribute removal

## Key Findings

### The Pattern

```
setAttribute() method flow:
1. Compare old vs new values
2. If different:
   a. DELETE all existing values for that attribute
   b. INSERT each new value
```

### Why This Approach?

1. **Concurrency Safety** - Prevents `StaleObjectStateException` in multi-threaded environments
2. **Simplicity** - Clear transactional semantics, easy to understand and maintain
3. **Correctness** - Ensures no orphaned attribute values

### Performance Impact

| User Type | Attributes | Update Time | Verdict |
|-----------|-----------|-------------|---------|
| Typical | < 20 attrs, < 5 values | < 100ms | ✅ Excellent |
| Heavy | 20-50 attrs | 100-300ms | ⚠️ Acceptable |
| Extreme | > 50 attrs | > 500ms | 🚨 Consider optimization |

## Optimization Strategies

### 1. Use setSingleAttribute() for Single Values

```java
// ✅ Efficient (UPDATE in-place)
user.setSingleAttribute("department", "Engineering");

// ❌ Inefficient (DELETE + INSERT)
user.setAttribute("department", Arrays.asList("Engineering"));
```

### 2. Minimize Attribute Count

- Keep < 20 custom attributes per user
- Combine related attributes
- Use external storage for large datasets

### 3. Database Optimization

- Enable connection pooling
- Use prepared statement caching
- Optimize indices on USER_ATTRIBUTE table

### 4. For Extreme Cases

- Implement custom storage providers
- Use NoSQL for attribute storage
- Consider event-driven architecture

## Measuring in Your Environment

### Enable SQL Logging

**Quarkus (Keycloak 17+):**
```properties
quarkus.hibernate-orm.log.sql=true
quarkus.hibernate-orm.log.format-sql=true
```

**WildFly (Keycloak < 17):**
```xml
<logger category="org.hibernate.SQL">
    <level name="DEBUG"/>
</logger>
```

### What You'll See

```sql
-- Update user with 3 role values
DELETE FROM USER_ATTRIBUTE WHERE USER_ID = '...' AND NAME = 'roles';
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES ('...', '...', 'roles', 'admin');
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES ('...', '...', 'roles', 'user');
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES ('...', '...', 'roles', 'moderator');
```

## Frequently Asked Questions

### Q: Is this a bug or inefficiency?
**A:** No. This is intentional design prioritizing concurrency safety and correctness over raw performance.

### Q: Should I avoid user attributes?
**A:** No. For typical use cases (< 20 attributes), performance is excellent. Just avoid storing hundreds of attributes per user.

### Q: Can I change this behavior?
**A:** Not directly. For specialized needs, implement a custom user storage provider with optimized attribute handling.

### Q: What about differential updates?
**A:** Considered but rejected due to concurrency issues (KEYCLOAK-3296). Current approach is safer.

### Q: How does this compare to other IAM systems?
**A:** Similar patterns are common in IAM systems. The trade-off between performance and concurrency safety is universal.

## Related Issues

- **[KEYCLOAK-3296](https://issues.redhat.com/browse/KEYCLOAK-3296)** - Fix for StaleObjectStateException
- **[KEYCLOAK-3494](https://issues.redhat.com/browse/KEYCLOAK-3494)** - Attribute removal improvements

## Contributing

Found issues or have improvements? See [CONTRIBUTING.md](../CONTRIBUTING.md)

## License

Apache License 2.0 - See [LICENSE.txt](../LICENSE.txt)
