/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.examples.userattributes;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating database operations when creating and updating user attributes.
 * 
 * This example shows the DELETE + INSERT pattern that Keycloak uses for attribute updates.
 * 
 * @author keycloak-team
 */
public class UserAttributeUpdateExample {

    /**
     * Example 1: Creating a user with attributes
     * 
     * Database operations:
     * - 1 INSERT into USER_ENTITY
     * - 3 INSERTs into USER_ATTRIBUTE (for 3 role values)
     * - 1 INSERT into USER_ATTRIBUTE (for department)
     * 
     * Total: 1 + 4 = 5 INSERTs
     */
    public void createUserWithAttributes(KeycloakSession session, RealmModel realm) {
        UserModel user = session.users().addUser(realm, "john.doe");
        user.setEmail("john.doe@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        
        // Multi-valued attribute: 3 INSERTs
        user.setAttribute("roles", Arrays.asList("admin", "user", "moderator"));
        
        // Single-valued attribute: 1 INSERT
        user.setSingleAttribute("department", "Engineering");
        
        // At this point, the database has:
        // - 1 row in USER_ENTITY for john.doe
        // - 4 rows in USER_ATTRIBUTE (3 for roles, 1 for department)
    }

    /**
     * Example 2: Updating multi-valued attributes using setAttribute()
     * 
     * Initial state: roles = ["admin", "user", "moderator"]
     * New state: roles = ["user", "developer", "reviewer"]
     * 
     * Database operations:
     * - 1 DELETE to remove ALL existing "roles" values
     * - 3 INSERTs for the new values
     * 
     * Total: 1 DELETE + 3 INSERTs
     * 
     * Note: Even though "user" appears in both old and new values, it still gets
     * deleted and re-inserted. This is the trade-off for concurrency safety.
     */
    public void updateMultiValuedAttribute(KeycloakSession session, RealmModel realm, String userId) {
        UserModel user = session.users().getUserById(realm, userId);
        
        // This triggers: 1 DELETE + 3 INSERTs
        user.setAttribute("roles", Arrays.asList("user", "developer", "reviewer"));
        
        // The database now has:
        // - 0 rows in USER_ATTRIBUTE with name='roles' (deleted)
        // - 3 new rows in USER_ATTRIBUTE with name='roles' (inserted)
    }

    /**
     * Example 3: Updating single-valued attributes using setSingleAttribute()
     * 
     * Initial state: department = "Engineering"
     * New state: department = "Sales"
     * 
     * Database operations:
     * - Reuses the existing UserAttributeEntity and updates its value
     * - More efficient than setAttribute()
     * 
     * Total: UPDATE in-place (or 1 DELETE + 1 INSERT if using setAttribute)
     */
    public void updateSingleValuedAttribute(KeycloakSession session, RealmModel realm, String userId) {
        UserModel user = session.users().getUserById(realm, userId);
        
        // Efficient: Reuses existing entity
        user.setSingleAttribute("department", "Sales");
        
        // Less efficient alternative (don't do this for single values):
        // user.setAttribute("department", Arrays.asList("Sales"));
        // This would trigger: 1 DELETE + 1 INSERT
    }

    /**
     * Example 4: REST API PUT request updating a user
     * 
     * Simulates: PUT /admin/realms/{realm}/users/{id}
     * {
     *   "attributes": {
     *     "roles": ["admin", "user"],
     *     "permissions": ["read", "write", "delete"],
     *     "department": ["IT"],
     *     "status": ["active"]
     *   }
     * }
     * 
     * Database operations:
     * - roles: 1 DELETE + 2 INSERTs
     * - permissions: 1 DELETE + 3 INSERTs
     * - department: 1 DELETE + 1 INSERT (or UPDATE if API uses setSingleAttribute)
     * - status: 1 DELETE + 1 INSERT (or UPDATE if API uses setSingleAttribute)
     * 
     * Total: 4 DELETEs + 7 INSERTs (or 2 DELETEs + 5 INSERTs + 2 UPDATEs if optimized)
     */
    public void updateUserViaRestApi(KeycloakSession session, RealmModel realm, String userId) {
        UserModel user = session.users().getUserById(realm, userId);
        
        // REST API typically uses setAttribute for all attributes
        user.setAttribute("roles", Arrays.asList("admin", "user"));
        user.setAttribute("permissions", Arrays.asList("read", "write", "delete"));
        user.setAttribute("department", Arrays.asList("IT"));
        user.setAttribute("status", Arrays.asList("active"));
        
        // More efficient for single-valued attributes would be:
        // user.setSingleAttribute("department", "IT");
        // user.setSingleAttribute("status", "active");
    }

    /**
     * Example 5: Performance scenario - user with many attributes
     * 
     * User has:
     * - 50 single-valued attributes
     * - 5 multi-valued attributes with 10 values each
     * 
     * Update operations:
     * - 50 single-valued: 50 DELETEs + 50 INSERTs (or 50 UPDATEs if using setSingleAttribute)
     * - 5 multi-valued: 5 DELETEs + 50 INSERTs
     * 
     * Total: 55 DELETEs + 100 INSERTs = 155 database operations
     * 
     * This is why keeping attribute counts reasonable is important for performance.
     */
    public void performanceScenario(KeycloakSession session, RealmModel realm, String userId) {
        UserModel user = session.users().getUserById(realm, userId);
        
        // Update 50 single-valued attributes
        for (int i = 0; i < 50; i++) {
            // Using setAttribute: 50 DELETEs + 50 INSERTs
            user.setAttribute("attr_" + i, Arrays.asList("new_value_" + i));
            
            // Better alternative using setSingleAttribute: ~50 UPDATEs
            // user.setSingleAttribute("attr_" + i, "new_value_" + i);
        }
        
        // Update 5 multi-valued attributes with 10 values each
        for (int i = 0; i < 5; i++) {
            List<String> values = Arrays.asList(
                "val0", "val1", "val2", "val3", "val4",
                "val5", "val6", "val7", "val8", "val9"
            );
            // 5 DELETEs + 50 INSERTs
            user.setAttribute("multi_" + i, values);
        }
        
        // Total: 55 DELETEs + 100 INSERTs = 155 database operations
        // With setSingleAttribute optimization: 5 DELETEs + 50 INSERTs + 50 UPDATEs = 105 operations
    }

    /**
     * Example 6: Why the DELETE + INSERT pattern exists
     * 
     * This demonstrates the concurrency safety aspect (KEYCLOAK-3296)
     */
    public void concurrencySafetyExample() {
        // Scenario: Two concurrent transactions updating the same attribute
        
        // Thread A and Thread B both load user with roles = ["admin", "user"]
        
        // If we used differential updates:
        // Thread A: wants to change to ["admin", "moderator"]
        //   - Would keep "admin", delete "user", insert "moderator"
        // Thread B: wants to change to ["user", "reviewer"]
        //   - Would keep "user", delete "admin", insert "reviewer"
        // 
        // Problem: Both threads try to modify the same entities
        // Result: StaleObjectStateException
        
        // With DELETE + INSERT pattern:
        // Thread A: DELETE all roles, INSERT ["admin", "moderator"]
        // Thread B: DELETE all roles, INSERT ["user", "reviewer"]
        // 
        // One transaction wins, the other is lost (last write wins)
        // No StaleObjectStateException, cleaner semantics
        
        // The trade-off: We delete and re-insert unchanged values
        // But we gain: Concurrency safety and transactional clarity
    }
}
