# Answer to: "Is it really 100% certain that an update (PUT) on a user will then lead to N inserts for the attributes?"

## TL;DR

**Yes, it is 100% certain.** When you update user attributes using the REST API or `UserModel.setAttribute()`, Keycloak performs:

1. **1 DELETE** statement to remove all existing values for that attribute
2. **N INSERT** statements to add the new values

So for a user with 3 attributes, each having 5 values, updating all of them results in:
- 3 DELETE statements
- 15 INSERT statements
- **Total: 18 database operations**

## Why Does Keycloak Do This?

### 1. Concurrency Safety (KEYCLOAK-3296)

The DELETE + INSERT pattern prevents `StaleObjectStateException` in concurrent scenarios:

```
Scenario: Two requests update the same user's "roles" attribute simultaneously

Request A: Changes roles from ["admin", "user"] to ["admin", "moderator"]
Request B: Changes roles from ["admin", "user"] to ["user", "reviewer"]

With DELETE + INSERT:
✓ Request A: DELETE all roles, INSERT ["admin", "moderator"]
✓ Request B: DELETE all roles, INSERT ["user", "reviewer"]  
✓ Result: Last write wins (clean, predictable behavior)

With differential updates:
✗ Both requests try to modify the same entity objects
✗ Result: StaleObjectStateException, complex retry logic needed
```

### 2. Simplicity and Maintainability

- Clear transactional boundaries
- No complex set comparison logic
- Easy to debug and test
- Consistent behavior across all attribute types

### 3. Already Optimized

Keycloak includes optimizations:
- **Early return** when values haven't changed - Before performing any database operations, Keycloak compares the old and new attribute values using `CollectionUtil.collectionEquals()`. If they're identical, it skips the DELETE + INSERT entirely.
- **Batch HQL** for deletions (not per-entity operations) - Uses a single HQL DELETE query instead of removing entities one by one
- **setSingleAttribute()** reuses entities for single values - Updates the existing UserAttributeEntity in-place instead of DELETE + INSERT

## Is This a Performance Problem?

### For Most Users: NO

Typical scenarios work fine:
- **Small scale**: < 20 attributes per user, < 5 values per attribute
- **Low frequency**: User updates every few minutes or hours
- **Normal hardware**: Standard PostgreSQL/MySQL with connection pooling

**Performance**: User update with 10 attributes takes < 100ms

### For High-Scale Scenarios: MAYBE

Consider optimization if you have:
- **Large attribute count**: > 50 attributes per user
- **High update frequency**: Multiple updates per second
- **Extreme scale**: Millions of concurrent users

**Performance**: User update with 100 attributes could take 500ms+

## How to Optimize

### 1. Use `setSingleAttribute()` for Single Values

```java
// ✓ Good: Reuses existing entity (UPDATE)
user.setSingleAttribute("department", "Engineering");

// ✗ Avoid: Deletes and re-inserts (DELETE + INSERT)
user.setAttribute("department", Arrays.asList("Engineering"));
```

### 2. Reduce Attribute Count

- Combine related attributes into structured data
- Use custom user storage providers for specialized data
- Store non-critical data outside of user attributes

### 3. Batch Updates Wisely

- Spread bulk updates over time
- Use async processing for non-critical updates
- Consider event-driven architecture

### 4. Database Optimization

- Enable connection pooling
- Use prepared statement caching
- Optimize indices on USER_ATTRIBUTE table
- Consider read replicas for read-heavy workloads

## Is This a Best Practice?

**Yes, for general-purpose identity management.**

The trade-offs favor correctness and simplicity:
- ✓ Concurrency safety is critical
- ✓ Most users have reasonable attribute counts
- ✓ Updates are infrequent compared to reads
- ✓ Database hardware is usually sufficient
- ✓ Code is maintainable and debuggable

**No, for specialized high-performance scenarios.**

If you need:
- Extreme performance (< 10ms user updates)
- Users with hundreds of attributes
- High-frequency updates (multiple per second)

Then consider:
- Custom storage providers
- NoSQL databases for attributes
- Event-driven architecture
- External caching layers

## Detailed Analysis

See the comprehensive documentation for more details:

- **[User Attribute Database Operations](user-attribute-database-operations.md)** - In-depth analysis
- **[Examples](../examples/userattributes/)** - Code examples
- **[Tests](../testsuite/model/src/test/java/org/keycloak/testsuite/model/user/UserAttributeDatabaseOperationsTest.java)** - Comprehensive test suite

## Conclusion

The DELETE + INSERT pattern is **intentional, well-reasoned, and appropriate** for Keycloak's use case as a general-purpose identity and access management system.

It prioritizes:
1. **Correctness** - No data corruption or concurrency issues
2. **Simplicity** - Easy to understand and maintain
3. **Safety** - Predictable behavior in concurrent scenarios

For the vast majority of Keycloak deployments, this approach works well. If you hit performance limits, Keycloak provides extensibility points (custom storage providers) to implement specialized solutions.

---

**Want to measure this yourself?**

Enable Hibernate SQL logging:

**For Quarkus-based deployments (Keycloak 17+):**
```
quarkus.hibernate-orm.log.sql=true
quarkus.hibernate-orm.log.format-sql=true
```

**For WildFly-based deployments (Keycloak < 17):**
```xml
<logger category="org.hibernate.SQL">
    <level name="DEBUG"/>
</logger>
```

Then:
1. Update a user attribute via REST API
2. Count the DELETE and INSERT statements in the logs
3. You'll see exactly the pattern described above
