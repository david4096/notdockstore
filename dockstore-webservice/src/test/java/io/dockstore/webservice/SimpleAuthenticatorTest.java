package io.dockstore.webservice;

import java.util.Optional;

import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.AuthenticationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class SimpleAuthenticatorTest {

    private TokenDAO tokenDAO;
    private UserDAO userDAO;
    private SimpleAuthenticator simpleAuthenticator;
    private final String credentials = "asdfafds";
    private static final Long USER_ID = new Long(1);
    private final Token token = Mockito.mock(Token.class);
    private final User user = Mockito.mock(User.class);
    private final Userinfoplus userinfoplus = Mockito.mock(Userinfoplus.class);
    private final static String USER_EMAIL = "jdoe@example.com";

    @Before
    public void setUp() {
        tokenDAO = Mockito.mock(TokenDAO.class);
        userDAO = Mockito.mock(UserDAO.class);
        simpleAuthenticator = spy(new SimpleAuthenticator(tokenDAO, userDAO));
        doNothing().when(simpleAuthenticator).initializeUserProfiles(user);
        doNothing().when(simpleAuthenticator).updateGoogleToken(credentials, user);
    }

    @Test
    public void authenticateDockstoreToken() {
        when(token.getUserId()).thenReturn(USER_ID);
        when(tokenDAO.findByContent(credentials)).thenReturn(token);
        when(userDAO.findById(USER_ID)).thenReturn(user);
        Assert.assertEquals(user, simpleAuthenticator.authenticate(credentials).get());
    }

    @Test
    public void authenticateGoogleTokenExistingUser() {
        when(tokenDAO.findByContent(credentials)).thenReturn(null);
        doReturn(Optional.of(userinfoplus)).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        when(userinfoplus.getEmail()).thenReturn(USER_EMAIL);
        when(userDAO.findByUsername(USER_EMAIL)).thenReturn(user);
        Assert.assertEquals(user, simpleAuthenticator.authenticate(credentials).get());
    }

    @Test
    public void authenticateGoogleTokenNewUser() {
        when(tokenDAO.findByContent(credentials)).thenReturn(null);
        doReturn(Optional.of(userinfoplus)).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        when(userinfoplus.getEmail()).thenReturn(USER_EMAIL);
        when(userDAO.findByUsername(USER_EMAIL)).thenReturn(null);
        doReturn(user).when(simpleAuthenticator).createUser(credentials, userinfoplus);
        Assert.assertEquals(user, simpleAuthenticator.authenticate(credentials).get());
    }

    @Test
    public void authenticateBadToken() {
        doReturn(Optional.empty()).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        Assert.assertFalse(simpleAuthenticator.authenticate(credentials).isPresent());
    }
}