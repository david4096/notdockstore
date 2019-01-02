package io.dockstore.webservice.resources;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organisation;
import io.dockstore.webservice.core.OrganisationUser;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganisationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Collection of organisation endpoints
 * @author aduncan
 */
@Path("/organisations")
@Api("/organisations")
@Produces(MediaType.APPLICATION_JSON)
public class OrganisationResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(OrganisationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organisations, authentication can be provided for unapproved organisations";

    private final OrganisationDAO organisationDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;
    private final SessionFactory sessionFactory;

    public OrganisationResource(SessionFactory sessionFactory) {
        this.organisationDAO = new OrganisationDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all available organisations.", notes = "NO Authentication", responseContainer = "List", response = Organisation.class)
    public List<Organisation> getApprovedOrganisations() {
        return organisationDAO.findAllApproved();
    }

    @POST
    @Timed
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Path("/approve/{organisationId}")
    @ApiOperation(value = "Approves an organisation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin/curator only", response = Organisation.class)
    public Organisation approveOrganisation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long id) {
        Organisation organisation = organisationDAO.findById(id);
        if (organisation == null) {
            String msg = "Organisation not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        organisation.setApproved(true);

        Event createOrganisationEvent = new Event(null, organisation, null, null, user, Event.EventType.APPROVE_ORG);
        eventDAO.create(createOrganisationEvent);

        return organisationDAO.findById(id);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organisationId}")
    @ApiOperation(value = "Retrieves an organisation by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organisation.class)
    public Organisation getOrganisationById(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long id) {
        if (!user.isPresent()) {
            // No user given, only show approved organisations
            Organisation organisation = organisationDAO.findApprovedById(id);
            if (organisation == null) {
                String msg = "Organisation not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
            return organisation;
        } else {
            // User is given, check if organisation is either approved or the user has access
            // Admins and curators should be able to see unapproved organisations
            boolean doesOrgExist = doesOrganisationExistToUser(id, user.get().getId()) || user.get().getIsAdmin() || user.get().isCurator();
            if (!doesOrgExist) {
                String msg = "Organisation not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }

            Organisation organisation = organisationDAO.findById(id);
            return organisation;
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/all")
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "List all organisations.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin/curator only", responseContainer = "List", response = Organisation.class)
    public List<Organisation> getAllOrganisations() {
        return organisationDAO.findAll();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/create")
    @ApiOperation(value = "Create an organisation.", notes = "Organisation requires approval by an admin before being made public.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organisation.class)
    public Organisation createOrganisation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation to register.", required = true) Organisation organisation) {

        // Check if any other organisations exist with that name
        Organisation matchingOrg = organisationDAO.findByName(organisation.getName());
        if (matchingOrg != null) {
            String msg = "An organisation already exists with the name '" + organisation.getName() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Save organisation
        organisation.setApproved(false); // should not be approved by default
        long id = organisationDAO.create(organisation);

        User foundUser = userDAO.findById(user.getId());

        // Create Role for user creating the organisation
        OrganisationUser organisationUser = new OrganisationUser(foundUser, organisationDAO.findById(id), OrganisationUser.Role.MAINTAINER);
        organisationUser.setAccepted(true);
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.persist(organisationUser);

        Event createOrganisationEvent = new Event(null, organisation, null, null, foundUser, Event.EventType.CREATE_ORG);
        eventDAO.create(createOrganisationEvent);

        return organisationDAO.findById(id);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/update/{organisationId}")
    @ApiOperation(value = "Update an organisation.", notes = "Currently only name, description, email, link and location can be updated.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organisation.class)
    public Organisation updateOrganisation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation to register.", required = true) Organisation organisation,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long id) {

        boolean doesOrgExist = doesOrganisationExistToUser(id, user.getId());
        if (!doesOrgExist) {
            String msg = "Organisation not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Organisation oldOrganisation = organisationDAO.findById(id);

        // Ensure that the user is a member of the organisation
        OrganisationUser organisationUser = getUserOrgRole(oldOrganisation, user.getId());
        if (organisationUser == null) {
            String msg = "You do not have permissions to update the organisation.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Check if new name is valid
        if (!Objects.equals(oldOrganisation.getName(), organisation.getName())) {
            Organisation duplicateName = organisationDAO.findByName(organisation.getName());
            if (duplicateName != null) {
                String msg = "An organisation already exists with the name '" + organisation.getName() + "', please try another one.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
        }

        // Update organisation
        oldOrganisation.setName(organisation.getName());
        oldOrganisation.setDescription(organisation.getDescription());
        oldOrganisation.setEmail(organisation.getEmail());
        oldOrganisation.setLink(organisation.getLink());
        oldOrganisation.setLocation(organisation.getLocation());

        Event updateOrganisationEvent = new Event(null, oldOrganisation, null, null, user, Event.EventType.MODIFY_ORG);
        eventDAO.create(updateOrganisationEvent);

        return organisationDAO.findById(id);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{organisationId}/user")
    @ApiOperation(value = "Adds a user role to an organisation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganisationUser.class)
    public OrganisationUser addUserToOrg(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Role of user.", required = true, allowableValues = "ADMIN, MAINTAINER, MEMBER") @QueryParam("role") String role,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {

        // Basic checks to ensure that action can be taken
        Pair<Organisation, User> organisationAndUserToAdd = commonUserOrg(organisationId, userId, user);

        // Check for existing roles the user has
        OrganisationUser existingRole = getUserOrgRole(organisationAndUserToAdd.getLeft(), userId);
        OrganisationUser organisationUser = null;
        if (existingRole == null) {
            organisationUser = new OrganisationUser(organisationAndUserToAdd.getRight(), organisationAndUserToAdd.getLeft(), OrganisationUser.Role.valueOf(role));
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.persist(organisationUser);
        } else {
            String msg = "The user with id '" + userId + "' already has a role in the organisation with id ." + organisationId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Event addUserOrganisationEvent = new Event(organisationAndUserToAdd.getRight(), organisationAndUserToAdd.getLeft(), null, null, user, Event.EventType.ADD_USER_TO_ORG);
        eventDAO.create(addUserOrganisationEvent);

        return organisationUser;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{organisationId}/user")
    @ApiOperation(value = "Updates a user role in an organisation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganisationUser.class)
    public OrganisationUser updateUserRole(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Role of user.", required = true, allowableValues = "ADMIN, MAINTAINER, MEMBER") @QueryParam("role") String role,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId) {

        // Basic checks to ensure that action can be taken
        Pair<Organisation, User> organisationAndUserToUpdate = commonUserOrg(organisationId, userId, user);

        // Check for existing roles the user has
        OrganisationUser existingRole = getUserOrgRole(organisationAndUserToUpdate.getLeft(), userId);
        if (existingRole == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organisation with id '" + organisationId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            existingRole.setRole(OrganisationUser.Role.valueOf(role));
        }

        Event updateUserOrganisationEvent = new Event(organisationAndUserToUpdate.getRight(), organisationAndUserToUpdate.getLeft(), null, null, user, Event.EventType.MODIFY_USER_ROLE_ORG);
        eventDAO.create(updateUserOrganisationEvent);

        return existingRole;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{organisationId}/user")
    @ApiOperation(value = "Remove a user from an organisation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = void.class)
    public void deleteUserRole(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId) {

        // Basic checks to ensure that action can be taken
        Pair<Organisation, User> organisationAndUserToDelete = commonUserOrg(organisationId, userId, user);

        // Check for existing roles the user has
        OrganisationUser existingRole = getUserOrgRole(organisationAndUserToDelete.getLeft(), userId);
        if (existingRole == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organisation with id '" + organisationId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.delete(existingRole);
        }

        Event deleteUserOrganisationEvent = new Event(organisationAndUserToDelete.getRight(), organisationAndUserToDelete.getLeft(), null, null, user, Event.EventType.REMOVE_USER_FROM_ORG);
        eventDAO.create(deleteUserOrganisationEvent);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{organisationId}/invitation")
    @ApiOperation(value = "Accept or reject an organisation invitation.", notes = "True accepts the invitation, false rejects the invitation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User acceptOrRejectInvitation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Accept or reject", required = true) @QueryParam("accept") boolean accept) {

        // Check that the organisation exists
        boolean doesOrgExist = doesOrganisationExistToUser(organisationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organisation not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Organisation organisation = organisationDAO.findById(organisationId);

        // Check that the role exists
        OrganisationUser organisationUser = getUserOrgRole(organisation, user.getId());
        if (organisationUser == null) {
            String msg = "The user with id '" + user.getId() + "' does not have a role in the organisation with id '" + organisation.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Check that the role is not already accepted
        if (organisationUser.isAccepted()) {
            String msg = "The user with id '" + user.getId() + "' has already accepted a role in the organisation with id '" + organisation.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        if (accept) {
            // Set to accepted if true
            organisationUser.setAccepted(true);
        } else {
            // Delete role if false
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.delete(organisationUser);
        }

        Event.EventType eventType = accept ? Event.EventType.APPROVE_ORG_INVITE : Event.EventType.REJECT_ORG_INVITE;
        Event addUserOrganisationEvent = new Event(null, organisation, null, null, user, eventType);
        eventDAO.create(addUserOrganisationEvent);

        return user;
    }

    /**
     * Determine the role of a user in an organisation
     * @param organisation
     * @param userId
     * @return OrganisationUser role
     */
    private OrganisationUser getUserOrgRole(Organisation organisation, Long userId) {
        Set<OrganisationUser> organisationUserSet = organisation.getUsers();
        Optional<OrganisationUser> matchingUser = organisationUserSet.stream().filter(organisationUser -> Objects.equals(organisationUser.getUser().getId(), userId)).findFirst();
        if (matchingUser.isPresent()) {
            return matchingUser.get();
        } else {
            return null;
        }
    }

    /**
     * Checks if a user has the given role type in the organisation
     * Throws an error if the user has no roles or the wrong roles
     * @param organisation
     * @param userId
     * @return Role for the organisationUser
     */
    private OrganisationUser checkUserOrgRole(Organisation organisation, Long userId, OrganisationUser.Role role) {
        OrganisationUser organisationUser = getUserOrgRole(organisation, userId);
        if (organisationUser == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organisation with id '" + organisation.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else if (!Objects.equals(organisationUser.getRole(), role)) {
            String msg = "The user with id '" + userId + "' does not have the required role in the organisation with id '" + organisation.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            return organisationUser;
        }
    }

    /**
     * Checks if the given user should know of the existence of the organisation
     * For a user to see an organsation, either it must be approved or the user must have a role in the organisation
     * @param organisationId
     * @param userId
     * @return True if organisation exists to user, false otherwise
     */
    private boolean doesOrganisationExistToUser(Long organisationId, Long userId) {
        Organisation organisation = organisationDAO.findById(organisationId);
        if (organisation == null) {
            return false;
        }
        OrganisationUser organisationUser = getUserOrgRole(organisation, userId);
        return organisation.isApproved() || (organisationUser != null);
    }

    /**
     * Common checks done by the user add/edit/delete endpoints
     * @param organisationId
     * @param userId
     * @param user
     * @return A pair of organistion to edit and user add/edit/delete role
     */
    private Pair<Organisation, User> commonUserOrg(Long organisationId, Long userId, User user) {
        // Check that the organisation exists
        boolean doesOrgExist = doesOrganisationExistToUser(organisationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organisation not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Organisation organisation = organisationDAO.findById(organisationId);

        // Check that the user exists
        User userToAdd = userDAO.findById(userId);
        if (userToAdd == null) {
            String msg = "No user exists with the ID '" + userId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Check that you are not applying action on yourself
        if (Objects.equals(user.getId(), userId)) {
            String msg = "You cannot add yourself to an organisation.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Ensure that the calling user is a maintainer of the organisation
        checkUserOrgRole(organisation, user.getId(), OrganisationUser.Role.MAINTAINER);

        return new ImmutablePair<>(organisation, userToAdd);
    }

}