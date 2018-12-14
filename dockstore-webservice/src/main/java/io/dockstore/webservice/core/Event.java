package io.dockstore.webservice.core;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes events that occur on the Dockstore site
 *
 * @author agduncan94
 * @since 1.6.0
 */
@ApiModel(value = "Event", description = "This describes events that occur on the Dockstore site.")
@Entity
@Table(name = "event")
@SuppressWarnings("checkstyle:magicnumber")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the event in this web service", position = 0)
    private long id;

    @ManyToOne
    @JoinColumn(name = "userId", referencedColumnName = "id")
    @ApiModelProperty(value = "User that the event is acting on.", position = 1)
    private User user;

    @ManyToOne
    @JoinColumn(name = "organisationId", referencedColumnName = "id")
    @ApiModelProperty(value = "Organisation that the event is acting on.", position = 2)
    private Organisation organisation;

    @ManyToOne
    @JoinColumn(name = "toolId", referencedColumnName = "id")
    @ApiModelProperty(value = "Tool that the event is acting on.", position = 3)
    private Tool tool;

    @ManyToOne
    @JoinColumn(name = "workflowId", referencedColumnName = "id")
    @ApiModelProperty(value = "Workflow that the event is acting on.", position = 4)
    private Workflow workflow;

    @ManyToOne
    @JoinColumn(name = "initiatorUserId", referencedColumnName = "id")
    @ApiModelProperty(value = "User initiating the event.", position = 5)
    private User initiatorUser;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The event type.", required = true, position = 6)
    private EventType type;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Event(User user, Organisation organisation, Workflow workflow, Tool tool, User initiatorUser, EventType type) {
        this.user = user;
        this.organisation = organisation;
        this.workflow = workflow;
        this.tool = tool;
        this.initiatorUser = initiatorUser;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Organisation getOrganisation() {
        return organisation;
    }

    public void setOrganisation(Organisation organisation) {
        this.organisation = organisation;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public User getInitiatorUser() {
        return initiatorUser;
    }

    public void setInitiatorUser(User initiatorUser) {
        this.initiatorUser = initiatorUser;
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public enum EventType {
        CREATE_ORG,
        DELETE_ORG,
        MODIFY_ORG,
        APPROVE_ORG,
        ADD_USER_TO_ORG,
        REMOVE_USER_FROM_ORG,
        MODIFY_USER_ROLE_ORG,
        APPROVE_ORG_INVITE,
        REJECT_ORG_INVITE
    }
}
