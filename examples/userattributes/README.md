# User Attribute Database Operations Examples

This directory contains examples demonstrating how Keycloak handles user attribute database operations.

## Overview

When you create or update user attributes in Keycloak, the system performs specific database operations. Understanding these operations helps you:

1. **Optimize performance** for your use case
2. **Design better data models** with appropriate attribute counts
3. **Troubleshoot** performance issues
4. **Make informed decisions** about custom storage providers

## Key Concepts

### The DELETE + INSERT Pattern

Keycloak uses a "DELETE all + INSERT new" pattern when updating attributes:

```java
// Updating an attribute from ["old1", "old2"] to ["new1", "new2", "new3"]
user.setAttribute("tags", Arrays.asList("new1", "new2", "new3"));

// Database operations:
// 1. DELETE all existing "tags" values (removes "old1" and "old2")
// 2. INSERT "new1"
// 3. INSERT "new2"
// 4. INSERT "new3"
```

### Why This Pattern?

- **Concurrency Safety**: Avoids `StaleObjectStateException` in multi-threaded environments (KEYCLOAK-3296)
- **Simplicity**: Clear transactional semantics, easy to understand and debug
- **Correctness**: Ensures no orphaned attribute values

### Optimization for Single Values

For single-valued attributes, use `setSingleAttribute()` instead of `setAttribute()`:

```java
// Efficient - reuses existing entity
user.setSingleAttribute("department", "Engineering");

// Less efficient - deletes and re-inserts
user.setAttribute("department", Arrays.asList("Engineering"));
```

## Examples

### [UserAttributeUpdateExample.java](UserAttributeUpdateExample.java)

Demonstrates six scenarios:

1. **Creating a user with attributes** - Shows initial INSERT operations
2. **Updating multi-valued attributes** - Shows DELETE + INSERT pattern
3. **Updating single-valued attributes** - Shows efficient UPDATE pattern
4. **REST API updates** - Simulates typical API usage
5. **Performance scenario** - Large-scale attribute updates
6. **Concurrency safety** - Why the DELETE + INSERT pattern exists

## Running the Examples

These are educational examples demonstrating the concepts. They are not executable tests but rather code snippets showing how the API works.

To see the actual database operations in your environment:

1. Enable Hibernate SQL logging in your Keycloak configuration
2. Run user creation/update operations
3. Observe the SQL statements in the logs

## Performance Considerations

### Acceptable Performance
- Users with < 20 custom attributes
- Attributes with < 5 values each
- Updates less frequent than once per minute

### May Need Optimization
- Users with 50+ attributes
- Attributes with 20+ values
- High-frequency updates (multiple times per second)
- Batch operations updating thousands of users

## Further Reading

- [User Attribute Database Operations Documentation](../../docs/user-attribute-database-operations.md)
- [User Model Tests](../../testsuite/model/src/test/java/org/keycloak/testsuite/model/user/)
- [UserAdapter Implementation](../../model/jpa/src/main/java/org/keycloak/models/jpa/UserAdapter.java)

## Related Issues

- [KEYCLOAK-3296](https://issues.redhat.com/browse/KEYCLOAK-3296) - StaleObjectStateException fix
- [KEYCLOAK-3494](https://issues.redhat.com/browse/KEYCLOAK-3494) - Attribute removal fix
