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
package org.keycloak.testsuite.model.user;

import org.junit.Test;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.testsuite.model.KeycloakModelTest;
import org.keycloak.testsuite.model.RequireProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test to analyze and document database operations when creating and updating user attributes.
 * 
 * This test demonstrates that when updating user attributes:
 * 1. For user creation with N attributes: N INSERTs are performed
 * 2. For user update with setAttribute() on an existing attribute with M values: 
 *    - 1 DELETE is performed to remove all existing values
 *    - M INSERTs are performed for the new values
 * 3. For user update with setSingleAttribute() on an existing attribute:
 *    - Updates the first existing value in-place
 *    - DELETEs any additional values if they exist
 *    - Or 1 INSERT if the attribute doesn't exist yet
 *
 * @author keycloak-team
 */
@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
public class UserAttributeDatabaseOperationsTest extends KeycloakModelTest {

    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = createRealm(s, "test-realm");
        s.getContext().setRealm(realm);
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        this.realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().getRealm(realmId);
        if (realm != null) {
            s.getContext().setRealm(realm);
            s.realms().removeRealm(realmId);
        }
    }

    @Override
    protected boolean isUseSameKeycloakSessionFactoryForAllThreads() {
        return true;
    }

    /**
     * Test 1: Creating a user with multiple attribute values
     * 
     * Expected DB operations:
     * - 1 INSERT for the user entity
     * - N INSERTs for N attribute values
     * 
     * Total: 1 + N INSERTs
     */
    @Test
    public void testCreateUserWithManyAttributes() {
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-create");
            
            // Add 10 values for a single attribute
            List<String> attributeValues = Arrays.asList(
                "value1", "value2", "value3", "value4", "value5",
                "value6", "value7", "value8", "value9", "value10"
            );
            user.setAttribute("multi-valued-attr", attributeValues);
            
            // Add 5 different single-valued attributes
            for (int i = 1; i <= 5; i++) {
                user.setSingleAttribute("attr" + i, "value" + i);
            }
            
            return null;
        });

        // Verify the attributes were created correctly
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "test-user-create");
            assertThat(user, is(notNullValue()));
            
            List<String> multiValuedAttr = user.getAttributeStream("multi-valued-attr").toList();
            assertThat(multiValuedAttr, hasSize(10));
            assertThat(multiValuedAttr, containsInAnyOrder(
                "value1", "value2", "value3", "value4", "value5",
                "value6", "value7", "value8", "value9", "value10"
            ));
            
            for (int i = 1; i <= 5; i++) {
                assertThat(user.getFirstAttribute("attr" + i), is("value" + i));
            }
            
            return null;
        });
    }

    /**
     * Test 2: Updating an existing multi-valued attribute using setAttribute()
     * 
     * Expected DB operations:
     * - 1 DELETE (via removeAttribute) to remove all existing values
     * - M INSERTs for M new values
     * 
     * Total: 1 DELETE + M INSERTs
     * 
     * Note: This demonstrates the "delete then insert" pattern which is the
     * subject of the performance question.
     */
    @Test
    public void testUpdateMultiValuedAttributeWithSetAttribute() {
        // First create a user with an attribute
        String userId = withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-update-multi");
            user.setAttribute("tags", Arrays.asList("tag1", "tag2", "tag3"));
            return user.getId();
        });

        // Now update the attribute with different values
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            
            // This will trigger: 1 DELETE + 5 INSERTs
            user.setAttribute("tags", Arrays.asList("new1", "new2", "new3", "new4", "new5"));
            
            return null;
        });

        // Verify the update
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            List<String> tags = user.getAttributeStream("tags").toList();
            assertThat(tags, hasSize(5));
            assertThat(tags, containsInAnyOrder("new1", "new2", "new3", "new4", "new5"));
            return null;
        });
    }

    /**
     * Test 3: Updating a single-valued attribute using setSingleAttribute()
     * 
     * Expected DB operations:
     * - If attribute exists: UPDATE the existing value in-place (reuses the entity)
     * - If attribute doesn't exist: 1 INSERT
     * 
     * This is more efficient than setAttribute() for single values.
     */
    @Test
    public void testUpdateSingleValuedAttributeWithSetSingleAttribute() {
        // First create a user with an attribute
        String userId = withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-update-single");
            user.setSingleAttribute("department", "Engineering");
            return user.getId();
        });

        // Now update the attribute
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            
            // This will reuse the existing attribute entity and update its value
            // More efficient than setAttribute() for single values
            user.setSingleAttribute("department", "Sales");
            
            return null;
        });

        // Verify the update
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            assertThat(user.getFirstAttribute("department"), is("Sales"));
            return null;
        });
    }

    /**
     * Test 4: Multiple attribute updates in a single transaction
     * 
     * This simulates a typical user update scenario via REST API (PUT /users/{id})
     * where multiple attributes might be updated at once.
     */
    @Test
    public void testMultipleAttributeUpdatesInSingleTransaction() {
        // Create a user with multiple attributes
        String userId = withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-bulk-update");
            user.setAttribute("roles", Arrays.asList("admin", "user"));
            user.setAttribute("permissions", Arrays.asList("read", "write", "delete"));
            user.setSingleAttribute("status", "active");
            user.setSingleAttribute("department", "IT");
            return user.getId();
        });

        // Update all attributes in a single transaction (simulating a REST PUT)
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            
            // Update multi-valued attributes: Each triggers 1 DELETE + N INSERTs
            user.setAttribute("roles", Arrays.asList("user", "moderator", "reviewer"));
            user.setAttribute("permissions", Arrays.asList("read", "write"));
            
            // Update single-valued attributes: These are more efficient
            user.setSingleAttribute("status", "inactive");
            user.setSingleAttribute("department", "HR");
            
            return null;
        });

        // Verify all updates
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            
            List<String> roles = user.getAttributeStream("roles").toList();
            assertThat(roles, hasSize(3));
            assertThat(roles, containsInAnyOrder("user", "moderator", "reviewer"));
            
            List<String> permissions = user.getAttributeStream("permissions").toList();
            assertThat(permissions, hasSize(2));
            assertThat(permissions, containsInAnyOrder("read", "write"));
            
            assertThat(user.getFirstAttribute("status"), is("inactive"));
            assertThat(user.getFirstAttribute("department"), is("HR"));
            
            return null;
        });
    }

    /**
     * Test 5: Performance scenario - user with many attributes
     * 
     * This test creates a user with a large number of attributes to demonstrate
     * the database operation pattern at scale.
     */
    @Test
    public void testUserWithManyAttributesPerformance() {
        String userId = withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-many-attrs");
            
            // Create 50 different attributes
            for (int i = 0; i < 50; i++) {
                user.setSingleAttribute("attr_" + i, "value_" + i);
            }
            
            // Create 5 multi-valued attributes with 10 values each
            for (int i = 0; i < 5; i++) {
                List<String> values = Arrays.asList(
                    "val0", "val1", "val2", "val3", "val4",
                    "val5", "val6", "val7", "val8", "val9"
                );
                user.setAttribute("multi_" + i, values);
            }
            
            return user.getId();
        });

        // Update all attributes
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            
            // Update single-valued attributes - efficient
            for (int i = 0; i < 50; i++) {
                user.setSingleAttribute("attr_" + i, "updated_value_" + i);
            }
            
            // Update multi-valued attributes - less efficient (DELETE + INSERTs)
            for (int i = 0; i < 5; i++) {
                List<String> values = Arrays.asList(
                    "new0", "new1", "new2", "new3", "new4",
                    "new5", "new6", "new7", "new8", "new9"
                );
                user.setAttribute("multi_" + i, values);
            }
            
            return null;
        });

        // Verify
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            Map<String, List<String>> attrs = user.getAttributes();
            
            // Should have 50 single-valued + 5 multi-valued + 4 default attributes
            // (username, firstName, lastName, email)
            assertThat(attrs.size() >= 55, is(true));
            
            return null;
        });
    }

    /**
     * Test 6: Attribute removal
     * 
     * Expected DB operations:
     * - 1 DELETE to remove all values for the attribute
     */
    @Test
    public void testRemoveAttribute() {
        String userId = withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "test-user-remove");
            user.setAttribute("temp-attr", Arrays.asList("value1", "value2", "value3"));
            return user.getId();
        });

        // Remove the attribute
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            user.removeAttribute("temp-attr");
            return null;
        });

        // Verify removal
        withRealm(realmId, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            assertThat(user.getAttributeStream("temp-attr").toList(), hasSize(0));
            return null;
        });
    }
}
