/*
 *    Copyright 2017 OICR
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

import java.util.List;
import java.util.Optional;

import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.hibernate.UnitOfWork;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public class SimpleAuthenticator implements Authenticator<String, User> {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticator.class);

    private final TokenDAO dao;
    private final UserDAO userDAO;

    SimpleAuthenticator(TokenDAO dao, UserDAO userDAO) {
        this.dao = dao;
        this.userDAO = userDAO;
    }

    /**
     * Authenticates the credentials.
     *
     * Valid credentials can either be a Dockstore token or a Google token, if the Google token
     * is issued against a whitelisted Google client id.
     *
     * @param credentials
     * @return an optional user
     */
    @UnitOfWork
    @Override
    public Optional<User> authenticate(String credentials) {
        LOG.debug("SimpleAuthenticator called with {}", credentials);
        final Token token = dao.findByContent(credentials);
        if (token != null) { // It's a valid Dockstore token
            User byId = userDAO.findById(token.getUserId());
            initializeUserProfiles(byId);
            return Optional.of(byId);
        } else { // It might be a Google token
            return userinfoPlusFromToken(credentials)
                    .map(userinfoPlus -> {
                        final String email = userinfoPlus.getEmail();
                        User user = userDAO.findByGoogleEmail(email);
                        if (user != null) {
                            updateGoogleToken(credentials, user);
                        } else {
                            user = createUser(credentials, userinfoPlus);
                        }
                        initializeUserProfiles(user);
                        return Optional.of(user);
                    })
                    .orElse(Optional.empty());
        }
    }

    void initializeUserProfiles(User user) {
        // Always eagerly load yourself (your User object)
        Hibernate.initialize(user.getUserProfiles());
    }

    Optional<Userinfoplus> userinfoPlusFromToken(String credentials) {
        return GoogleHelper.userinfoplusFromToken(credentials);
    }

    void updateGoogleToken(String credentials, User user) {
        List<Token> tokens = dao.findByUserId(user.getId());
        final Token googleToken = Token.extractToken(tokens, TokenType.GOOGLE_COM);
        if (googleToken == null) {
            dao.create(new Token(credentials, null, user.getId(), user.getUsername(), TokenType.GOOGLE_COM));
        } else {
            googleToken.setContent(credentials);
            dao.update(googleToken);
        }
    }

    User createUser(String credentials, Userinfoplus userinfoPlus) {
        User user = new User();
        GoogleHelper.updateUserFromGoogleUserinfoplus(userinfoPlus, user);
        user.setUsername(userinfoPlus.getEmail());
        final long userID = userDAO.create(user);
        dao.create(new Token(credentials, null, userID, user.getUsername(), TokenType.GOOGLE_COM));
        return user;
    }

}
