-- this file is a last chance to modify any database schema in create mode with postgres-specific
-- changes not possible in JPA

-- column remark support in JPA does not seem to work for postgres, possible starting point https://stackoverflow.com/questions/28773022/jpa-column-annotation-to-create-comment-description 
COMMENT ON COLUMN sourcefile_verified.platformversion IS 'By default set to null';
COMMENT ON COLUMN validation.message IS 'A mapping of file path to message.';
-- postgres partial indexes seem unsupported https://stackoverflow.com/questions/12025844/how-to-annotate-unique-constraint-with-where-clause-in-jpa
CREATE UNIQUE INDEX full_workflow_name ON workflow USING btree (sourcecontrol, organization, repository, workflowname) WHERE workflowname IS NOT NULL;
CREATE UNIQUE INDEX full_tool_name ON tool USING btree (registry, namespace, name, toolname) WHERE toolname IS NOT NULL;
CREATE UNIQUE INDEX partial_workflow_name ON workflow USING btree (sourcecontrol, organization, repository) WHERE workflowname IS NULL;
CREATE UNIQUE INDEX partial_tool_name ON tool USING btree (registry, namespace, name) WHERE toolname IS NULL;
-- unable to convert these to JPA properly
ALTER TABLE token ADD CONSTRAINT fk_userid_with_enduser FOREIGN KEY (userid) REFERENCES public.enduser (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION;
-- https://liquibase.jira.com/browse/CORE-2895
CREATE UNIQUE INDEX organization_name_index on organization (LOWER(name));
CREATE UNIQUE INDEX collection_name_index on collection (LOWER(name), organizationid);
