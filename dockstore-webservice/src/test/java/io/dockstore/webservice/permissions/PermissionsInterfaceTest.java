package io.dockstore.webservice.permissions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class PermissionsInterfaceTest {

    public static final String JOHN_DOE_EXAMPLE_COM = "john.doe@example.com";
    public static final String JANE_DOE_EXAMPLE_COM = "jane.doe@example.com";
    private User userJohn;
    private Permission janeDoeOwnerPermission;
    private Permission janeDoeWriterPermission;
    private User userJane;
    private PermissionsInterface permissionsInterface;

    private Workflow workflow = Mockito.mock(Workflow.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        userJohn = new User();
        userJohn.setUsername(JOHN_DOE_EXAMPLE_COM);
        final User.Profile profile1 = new User.Profile();
        profile1.email = JOHN_DOE_EXAMPLE_COM;
        userJohn.getUserProfiles().put(TokenType.GOOGLE_COM.toString(), profile1);

        userJane = new User();
        userJane.setUsername(JANE_DOE_EXAMPLE_COM);
        final User.Profile profile2 = new User.Profile();
        profile2.email = JANE_DOE_EXAMPLE_COM;
        userJane.getUserProfiles().put(TokenType.GOOGLE_COM.toString(), profile2);

        janeDoeOwnerPermission = new Permission();
        janeDoeOwnerPermission.setRole(Role.OWNER);
        janeDoeOwnerPermission.setEmail(JANE_DOE_EXAMPLE_COM);
        janeDoeWriterPermission = new Permission();
        janeDoeWriterPermission.setEmail(JOHN_DOE_EXAMPLE_COM);
        janeDoeWriterPermission.setRole(Role.WRITER);

        permissionsInterface = new PermissionsInterface() {
            @Override
            public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
                return null;
            }

            @Override
            public Map<Role, List<String>> workflowsSharedWithUser(User user) {
                return null;
            }

            @Override
            public List<Role.Action> getActionsForWorkflow(User user, Workflow workflow) {
                return Collections.emptyList();
            }

            @Override
            public void removePermission(User user, Workflow workflow, String email, Role role) {
            }

            @Override
            public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
                return false;
            }
        };
    }

    @Test
    public void mergePermissions() {
        Assert.assertEquals(2, PermissionsInterface.mergePermissions(Arrays.asList(janeDoeOwnerPermission), Arrays.asList(
                janeDoeOwnerPermission, janeDoeWriterPermission)).size());
    }

    @Test
    public void getOriginalOwnersForWorkflow() {
        final Set<User> users = new HashSet<>(Arrays.asList(userJohn));
        workflow = Mockito.mock(Workflow.class);
        when(workflow.getUsers()).thenReturn(users);

        Assert.assertEquals(1, PermissionsInterface.getOriginalOwnersForWorkflow(workflow).size());
    }

    @Test
    public void unauthorizedGetPermissionsForWorkflow() {
        final Set<User> users = new HashSet<>(Arrays.asList(userJohn));
        when(workflow.getUsers()).thenReturn(users);
        thrown.expect(CustomWebApplicationException.class);
        permissionsInterface.getPermissionsForWorkflow(userJane, workflow);
    }

    @Test
    public void checkUserNotOriginalOwner() {
        final Set<User> users = new HashSet<>(Arrays.asList(userJohn));
        when(workflow.getUsers()).thenReturn(users);
        thrown.expect(CustomWebApplicationException.class);
        PermissionsInterface.checkUserNotOriginalOwner(JOHN_DOE_EXAMPLE_COM, workflow);
        PermissionsInterface.checkUserNotOriginalOwner(JANE_DOE_EXAMPLE_COM, workflow);
    }
}