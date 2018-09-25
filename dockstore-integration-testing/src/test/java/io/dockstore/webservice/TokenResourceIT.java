/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.common.collect.Maps;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.TokensApi;
import io.swagger.client.api.UsersApi;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.mockStaticStrict;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.verify;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * @author gluu
 * @since 24/07/18
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ GoogleHelper.class, GitHubBuilder.class, GitHubHelper.class })
@Category(ConfidentialTest.class)
@PowerMockIgnore( { "javax.security.*", "org.apache.http.conn.ssl.*", "javax.net.ssl.*", "javax.crypto.*", "javax.management.*",
    "javax.net.*", "org.apache.http.impl.client.*", "org.apache.http.protocol.*", "org.apache.http.*", "com.sun.org.apache.xerces.*",
    "javax.xml.*", "org.xml.*", "org.w3c.*" })
public class TokenResourceIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public final static String GITHUB_ACCOUNT_USERNAME = "potato";
    private TokenDAO tokenDAO;
    private UserDAO userDAO;
    private long initialTokenCount;
    private final String satellizerJSON = "{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n";
    private final String satellizerJSONForRegistration = "{\"code\": \"fakeCode\", \"register\": true, \"redirectUri\": \"fakeRedirectUri\"}";
    private final static String GOOGLE_ACCOUNT_USERNAME1 = "potato@gmail.com";
    private final static String GOOGLE_ACCOUNT_USERNAME2 = "beef@gmail.com";
    private final static String CUSTOM_USERNAME1 = "tuber";
    private final static String CUSTOM_USERNAME2 = "fubar";

    private static TokenResponse getFakeTokenResponse() {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken("fakeAccessToken");
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken");
        return fakeTokenResponse;
    }

    private static Userinfoplus getFakeUserinfoplus(String username) {
        Userinfoplus fakeUserinfoplus = new Userinfoplus();
        fakeUserinfoplus.setEmail(username);
        fakeUserinfoplus.setGivenName("Beef");
        fakeUserinfoplus.setFamilyName("Stew");
        fakeUserinfoplus.setName("Beef Stew");
        fakeUserinfoplus.setGender("New classification");
        fakeUserinfoplus.setPicture("https://dockstore.org/assets/images/dockstore/logo.png");
        return fakeUserinfoplus;
    }

    private static Token getFakeExistingDockstoreToken() {
        Token fakeToken = new Token();
        fakeToken.setContent("fakeContent");
        fakeToken.setTokenSource(TokenType.DOCKSTORE);
        fakeToken.setUserId(100);
        fakeToken.setId(1);
        fakeToken.setUsername("admin@admin.com");
        return fakeToken;
    }

    private static User getFakeUser() {
        // user is user from test data database
        User fakeUser = new User();
        fakeUser.setUsername(GITHUB_ACCOUNT_USERNAME);
        fakeUser.setId(2);
        return fakeUser;
    }

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
        initialTokenCount = CommonTestUtilities.getTestingPostgres().runSelectStatement("select count(*) from token", new ScalarHandler<>());
    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser() {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi.addGoogleToken(satellizerJSONForRegistration);

        // check that the user has the correct two tokens
        List<Token> byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we take on the gmail username when no other is provided
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(100, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Collections.singletonList(TokenType.GOOGLE_COM.toString()));
        verify(GoogleHelper.class);

        // check that the tokens work
        ApiClient webClient = getWebClient(false, "n/a");
        UsersApi userApi = new UsersApi(webClient);
        tokensApi = new TokensApi(webClient);

        int expectedFailCount = 0;
        for(Token currToken : byUserId) {
            webClient.addDefaultHeader("Authorization", "Bearer " + currToken.getContent());
            assertNotNull(userApi.getUser());
            tokensApi.deleteToken(currToken.getId());

            // check that deleting a token invalidates it
            try {
                userApi.getUser();
            } catch (ApiException e) {
                expectedFailCount++;
            }
            // shouldn't be able to even get the token
            try {
                tokensApi.listToken(currToken.getId());
            } catch (ApiException e) {
                expectedFailCount++;
            }
        }
        assertEquals(4, expectedFailCount);
    }


    /**
     * When a user ninjas the username of the an existing github user.
     * We should generate something sane then let the user change their name.
     */
    @Test
    public void testNinjaedGitHubUser() throws Exception {
        mockGitHub(CUSTOM_USERNAME1);
        TokensApi tokensApi1 = new TokensApi(getWebClient(false, "n/a"));
        tokensApi1.addToken(satellizerJSONForRegistration);
        UsersApi usersApi1 = new UsersApi(getWebClient(true, CUSTOM_USERNAME1));

        // registering user 1 again should fail
        boolean shouldFail = false;
        try {
            tokensApi1.addToken(satellizerJSONForRegistration);
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);


        // ninja user2 by taking its name
        assertEquals(usersApi1.changeUsername(CUSTOM_USERNAME2).getUsername(), CUSTOM_USERNAME2);

        // registering user1 again should still fail
        shouldFail = false;
        try {
            tokensApi1.addToken(satellizerJSONForRegistration);
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);


        // now register user2, should autogenerate a name
        mockGitHub(CUSTOM_USERNAME2);
        TokensApi tokensApi2 = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi2.addToken(satellizerJSONForRegistration);
        UsersApi usersApi2 = new UsersApi(getWebClient(true, token.getUsername()));
        assertNotEquals(usersApi2.getUser().getUsername(), CUSTOM_USERNAME2);
        assertEquals(usersApi2.changeUsername("better.name").getUsername(), "better.name");
    }

    /**
     * Super large test that generally revolves around 3 accounts
     * Account 1 (primary account): Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     * Account 2: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME2 and has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Account 3: GitHub-created Dockstore account that is called GITHUB_ACCOUNT_USERNAME and has GITHUB_ACCOUNT_USERNAME GitHub account linked
     *
     * @throws Exception
     */
    @Test
    public void loginRegisterTestWithMultipleAccounts() throws Exception {
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        createAccount1(unAuthenticatedTokensApi);
        createAccount2(unAuthenticatedTokensApi);

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        // Change Account 1 username to CUSTOM_USERNAME2
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        io.swagger.client.model.User user = mainUsersApi.changeUsername(CUSTOM_USERNAME2);
        Assert.assertEquals(CUSTOM_USERNAME2, user.getUsername());

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        mockGitHub(CUSTOM_USERNAME1);
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);

        // Login with Google still works
        io.swagger.client.model.Token token = unAuthenticatedTokensApi.addGoogleToken(satellizerJSON);
        Assert.assertEquals(CUSTOM_USERNAME2, token.getUsername());
        Assert.assertEquals(TokenType.DOCKSTORE.toString(), token.getTokenSource());

        // Login with GitHub still works
        io.swagger.client.model.Token fakeGitHubCode = unAuthenticatedTokensApi.addToken(satellizerJSON);
        Assert.assertEquals(CUSTOM_USERNAME2, fakeGitHubCode.getUsername());
        Assert.assertEquals(TokenType.DOCKSTORE.toString(), fakeGitHubCode.getTokenSource());
    }

    private void registerAndLinkUnavailableTokens(TokensApi unAuthenticatedTokensApi) throws Exception {
        // Should not be able to register new Dockstore account when profiles already exist
        registerNewUsersWithExisting(unAuthenticatedTokensApi);
        // Can't link tokens to other Dockstore accounts
        addUnavailableGitHubTokenToGoogleUser();
        addUnavailableGoogleTokenToGitHubUser();
    }

    @Test
    public void recreateAccountsAfterSelfDestruct() throws Exception {
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        createAccount1(unAuthenticatedTokensApi);
        registerNewUsersAfterSelfDestruct(unAuthenticatedTokensApi);
    }

    /**
     * Creates the Account 1: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     * @param unAuthenticatedTokensApi
     * @throws Exception
     */
    private void createAccount1(TokensApi unAuthenticatedTokensApi) throws Exception {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        io.swagger.client.model.Token account1DockstoreToken = unAuthenticatedTokensApi
                .addGoogleToken(satellizerJSONForRegistration);
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, account1DockstoreToken.getUsername());
        mockGitHub(CUSTOM_USERNAME1);
        TokensApi mainUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        mainUserTokensApi.addGithubToken("fakeGitHubCode");
    }

    private void createAccount2(TokensApi unAuthenticatedTokensApi) throws Exception {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME2);
        io.swagger.client.model.Token otherGoogleUserToken = unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration);
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME2, otherGoogleUserToken.getUsername());
    }

    /**
     *
     * @throws Exception
     */
    private void registerNewUsersWithExisting(TokensApi unAuthenticatedTokensApi) throws Exception {
        mockGitHub(CUSTOM_USERNAME1);
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // Cannot create new user with the same Google account
        try {
            unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals("User already exists, cannot register new user", e.getMessage());;
            // Call should fail
        }

        // Cannot create new user with the same GitHub account
        try {
            unAuthenticatedTokensApi.addToken(satellizerJSONForRegistration);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertTrue(e.getMessage().contains("already exists"));
            // Call should fail
        }
    }

    /**
     * After self-destructing the GOOGLE_ACCOUNT_USERNAME1, its previous linked accounts can be used:
     * GOOGLE_ACCOUNT_USERNAME1 Google account and CUSTOM_USERNAME1 GitHub account
     * @throws Exception
     */
    private void registerNewUsersAfterSelfDestruct(TokensApi unAuthenticatedTokensApi) throws Exception {
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        Boolean aBoolean = mainUsersApi.selfDestruct();
        assertTrue(aBoolean);
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        io.swagger.client.model.Token recreatedGoogleToken = unAuthenticatedTokensApi
                .addGoogleToken(satellizerJSONForRegistration);
        mockGitHub(CUSTOM_USERNAME1);
        io.swagger.client.model.Token recreatedGitHubToken = unAuthenticatedTokensApi
                .addToken(satellizerJSONForRegistration);
        assertNotSame(recreatedGitHubToken.getUserId(), recreatedGoogleToken.getUserId());
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME1 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GOOGLE_ACCOUNT_USERNAME1 Google account to Dockstore account 2 should fail
     * @throws Exception
     */
    private void addUnavailableGoogleTokenToGitHubUser() {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        // Cannot add token to other user with the same Google account
        try {
            otherUserTokensApi.addGoogleToken(satellizerJSON);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            Assert.assertTrue(e.getMessage().contains("already exists"));;
            // Call should fail
        }
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GITHUB_ACCOUNT_USERNAME GitHub account to Dockstore account 1 should fail
     * @throws Exception
     */
    private void addUnavailableGitHubTokenToGoogleUser() throws Exception {
        mockGitHub(CUSTOM_USERNAME1);
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME2));
        try {
            otherUserTokensApi.addGithubToken("potato");
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            Assert.assertTrue(e.getMessage().contains("already exists"));;
            // Call should fail
        }
    }

    /**
     * Covers case 1, 3, and 5 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    @Ignore("this is probably different now, todo")
    public void getGoogleTokenCase135() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token case5Token = tokensApi
                .addGoogleToken(satellizerJSON);
        // Case 5 check (No Google account, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case5Token.getUsername());
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        io.swagger.client.model.Token case3Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 3 check (Google account with Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case3Token.getUsername());
        TokensApi googleTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        googleTokensApi.deleteToken(case3Token.getId());
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // Google account dockstore token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case1Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 1 check (Google account without Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case1Token.getUsername());
        verify(GoogleHelper.class);
    }

    /**
     * Covers case 2 and 4 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    @Ignore("this is probably different now, todo")
    public void getGoogleTokenCase24() {
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Check token properly added (redundant assertion)
        long googleUserID = token.getUserId();
        Assert.assertEquals(token.getUsername(), GOOGLE_ACCOUNT_USERNAME1);

        TokensApi gitHubTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        gitHubTokensApi.addGoogleToken(satellizerJSON);
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // GitHub account Google token, Google account dockstore token, Google account Google token
        checkTokenCount(initialTokenCount + 3);
        io.swagger.client.model.Token case4Token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Case 4 (Google account with Google token, GitHub account with Google token)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case4Token.getUsername());
        TokensApi googleUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));

        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(googleUserID);


        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        googleUserTokensApi.deleteToken(googleByUserId.get(0).getId());
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        io.swagger.client.model.Token case2Token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Case 2 Google account without Google token, GitHub account with Google token
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, case2Token.getUsername());
        verify(GoogleHelper.class);
    }

    /**
     * Covers case 6 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    public void getGoogleTokenCase6() {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        tokensApi.addGoogleToken(satellizerJSON);
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // GitHub account Google token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case6Token = unauthenticatedTokensApi.addGoogleToken(satellizerJSON);

        // Case 6 check (No Google account, have GitHub account with Google token)
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, case6Token.getUsername());
        verify(GoogleHelper.class);
    }


    private void mockGitHub(String username) throws Exception {
        GitHub githubMock = niceMock(GitHub.class);
        whenNew(GitHub.class).withAnyArguments().thenReturn(githubMock);
        try {
            mockStaticStrict(GitHubHelper.class,
                GitHubHelper.class.getMethod("getGitHubAccessToken", String.class, List.class, List.class));
        } catch (NoSuchMethodException e) {
            Assert.fail();
        }
        expect(GitHubHelper.getGitHubAccessToken(anyString(), anyObject(), anyObject())).andReturn("fakeCode").atLeastOnce();

        GHMyself myself = niceMock(GHMyself.class);
        expect(myself.getLogin()).andReturn(username).anyTimes();
        expect(myself.getAvatarUrl()).andReturn("https://dockstore.org/assets/images/dockstore/logo2.png").anyTimes();
        expect(githubMock.getMyself()).andReturn(myself).anyTimes();
        GHRateLimit value = new GHRateLimit();
        value.remaining = 100;
        expect(githubMock.rateLimit()).andReturn(value);
        expect(githubMock.getMyOrganizations()).andReturn(Maps.newHashMap());
        replay(GitHubHelper.class, githubMock, myself);
    }

    private void mockGoogleHelper(String username) {
        try {
            // mark which static class methods you need to mock here while leaving the others to work normally
            mockStaticStrict(GoogleHelper.class,
                    GoogleHelper.class.getMethod("getTokenResponse", String.class, String.class, String.class, String.class),
                    GoogleHelper.class.getMethod("userinfoplusFromToken", String.class));
        } catch (NoSuchMethodException e) {
            Assert.fail();
        }
        expect(GoogleHelper.getTokenResponse("<fill me in>", "<fill me in>", "fakeCode", "fakeRedirectUri"))
                .andReturn(getFakeTokenResponse());
        expect(GoogleHelper.userinfoplusFromToken("fakeAccessToken")).andReturn(Optional.of(getFakeUserinfoplus(username)));
        // kick off the mock and have it start to expect things
        replay(GoogleHelper.class);
    }

    /**
     * This is only to double-check that the precondition is sane.
     * @param size the number of tokens that we expect
     */
    private void checkTokenCount(long size) {
        long tokenCount = CommonTestUtilities.getTestingPostgres().runSelectStatement("select count(*) from token", new ScalarHandler<>());
        Assert.assertEquals(size, tokenCount);
    }

    /**
     * For an existing user without a Google token, checks that a token (Google) was created exactly once.
     */
    @Test
    public void getGoogleTokenExistingUserNoGoogleToken() {
        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        // check that the user has the correct one token
        List<Token> byUserId = tokenDAO.findByUserId(getFakeUser().getId());
        Assert.assertEquals(1, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        io.swagger.client.model.Token token = tokensApi.addGoogleToken(satellizerJSON);

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * For an existing user with a Google token, checks that no tokens were created
     */
    @Test
    public void getGoogleTokenExistingUserWithGoogleToken() throws Exception {
        // check that the user has the correct one token
        long id = getFakeUser().getId();
        List<Token> byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(1, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        mockGoogleHelper(GOOGLE_ACCOUNT_USERNAME1);
        TokensApi tokensApi = new TokensApi(getWebClient(true, getFakeUser().getUsername()));
        tokensApi.addGoogleToken(satellizerJSON);

        // fake user should start with the previously created google token
        byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        mockGitHub(GITHUB_ACCOUNT_USERNAME);
        // going back to the first user, we want to add a github token to their profile
        io.swagger.client.model.Token token = tokensApi.addGithubToken("fakeCode");

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(3, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GITHUB_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * Checks that the user profiles exist
     *
     * @param userId      Id of the user
     * @param profileKeys Profiles to check that it exists
     */
    private void checkUserProfiles(Long userId, List<String> profileKeys) {
        User user = userDAO.findById(userId);
        Map<String, User.Profile> userProfiles = user.getUserProfiles();
        profileKeys.forEach(profileKey -> assertTrue(userProfiles.containsKey(profileKey)));
        if (profileKeys.contains(TokenType.GOOGLE_COM.toString())) {
            checkGoogleUserProfile(userProfiles);
        }
    }

    /**
     * Checks that the Google user profile matches the Google Userinfoplus
     *
     * @param userProfiles the user profile to look into and validate
     */
    private void checkGoogleUserProfile(Map<String, User.Profile> userProfiles) {
        User.Profile googleProfile = userProfiles.get(TokenType.GOOGLE_COM.toString());
        assertTrue(googleProfile.email.equals(GOOGLE_ACCOUNT_USERNAME1) && googleProfile.avatarURL
                .equals("https://dockstore.org/assets/images/dockstore/logo.png") && googleProfile.company == null
                && googleProfile.location == null && googleProfile.name.equals("Beef Stew"));
    }
}
