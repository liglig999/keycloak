# User Attribute Database Operations - Quick Reference

## Question

> "Is it really 100% certain that an update (PUT) on a user will then lead to N inserts for the attributes?"

## Answer

**YES.** Updating user attributes uses a DELETE + INSERT pattern:
- 1 DELETE per attribute name
- N INSERTs for N new values

## Example

```java
// Before: user has roles = ["admin", "user"]
user.setAttribute("roles", Arrays.asList("moderator", "reviewer", "developer"));
// After: 1 DELETE + 3 INSERTs
```

## Why?

1. **Concurrency safety** - Prevents StaleObjectStateException
2. **Simplicity** - Clear semantics, easy to debug
3. **Already optimized** - Batch operations, early returns

## Quick Optimization Tips

### ✅ DO: Use setSingleAttribute for single values
```java
user.setSingleAttribute("department", "Engineering");  // UPDATE in-place
```

### ❌ DON'T: Use setAttribute for single values
```java
user.setAttribute("department", Arrays.asList("Engineering"));  // DELETE + INSERT
```

### ✅ DO: Keep attributes reasonable
- < 20 custom attributes per user
- < 10 values per multi-valued attribute
- Updates less frequent than once per minute

### ❌ DON'T: Store large datasets as attributes
- Don't use for log data (use events instead)
- Don't use for frequently-changing data (use cache instead)
- Don't use for large binary data (use external storage)

## Performance Expectations

| Scenario | Attributes | Values | Ops | Time |
|----------|-----------|--------|-----|------|
| Small | 5 | 2 each | 5 DEL + 10 INS | ~50ms |
| Medium | 20 | 3 each | 20 DEL + 60 INS | ~200ms |
| Large | 50 | 5 each | 50 DEL + 250 INS | ~500ms |

*Times are estimates for typical database hardware*

## When to Worry

🚨 Consider optimization if you have:
- Users with > 50 attributes
- Attributes with > 20 values
- Update frequency > 1/second
- User updates taking > 1 second

## How to Investigate

### 1. Enable SQL Logging

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

### 2. Check Logs

Look for patterns like:
```sql
-- 1 DELETE
DELETE FROM USER_ATTRIBUTE WHERE USER_ID = ? AND NAME = ?

-- N INSERTs  
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES (?, ?, ?, ?)
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES (?, ?, ?, ?)
INSERT INTO USER_ATTRIBUTE (ID, USER_ID, NAME, VALUE) VALUES (?, ?, ?, ?)
```

### 3. Count Operations

Use database query logs or monitoring tools to count:
- DELETE statements per user update
- INSERT statements per user update
- Total time per user update

## Solutions for High-Scale Scenarios

If standard approach doesn't meet your needs:

### Option 1: Reduce Attributes
- Combine related attributes
- Remove unnecessary attributes
- Store non-critical data elsewhere

### Option 2: Custom Storage Provider
```java
public class CustomUserStorageProvider implements UserStorageProvider {
    // Implement optimized attribute handling
    // e.g., using NoSQL, caching, batching, etc.
}
```

### Option 3: External Attribute Store
- Use Redis/Memcached for frequently-changing attributes
- Sync critical attributes to Keycloak
- Keep non-critical attributes external

### Option 4: Event-Driven Architecture
- Emit events on attribute changes
- Process updates asynchronously
- Use eventual consistency

## Related Resources

- [Detailed Analysis](user-attribute-database-operations.md)
- [Comprehensive Answer](user-attribute-update-answer.md)
- [Code Examples](../examples/userattributes/)
- [Test Suite](../testsuite/model/src/test/java/org/keycloak/testsuite/model/user/UserAttributeDatabaseOperationsTest.java)

## Common Misconceptions

### ❌ MYTH: "This is inefficient and should be changed"
✅ **Reality:** The pattern is intentional, well-reasoned, and appropriate for general-purpose IAM. It prioritizes correctness and concurrency safety over raw performance.

### ❌ MYTH: "Differential updates would be better"
✅ **Reality:** Differential updates were considered but rejected due to concurrency issues (StaleObjectStateException). The current approach is safer and simpler.

### ❌ MYTH: "This will cause performance problems"
✅ **Reality:** For typical use cases (< 20 attributes, < 5 values, infrequent updates), performance is excellent. Problems only arise with extreme attribute counts or update frequencies.

## Bottom Line

**The DELETE + INSERT pattern is working as designed and is appropriate for most Keycloak deployments.**

Only optimize if you have measured performance issues and confirmed attributes are the bottleneck.
