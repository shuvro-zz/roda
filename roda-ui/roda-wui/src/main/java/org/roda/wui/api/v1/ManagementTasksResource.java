/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.api.v1;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.roda.core.RodaCoreFactory;
import org.roda.core.common.LdapUtilityException;
import org.roda.core.common.UserUtility;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.ExportType;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.EmailAlreadyExistsException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.IllegalOperationException;
import org.roda.core.data.exceptions.JobAlreadyStartedException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.exceptions.UserAlreadyExistsException;
import org.roda.core.data.utils.JsonUtils;
import org.roda.core.data.v2.agents.Agent;
import org.roda.core.data.v2.formats.Format;
import org.roda.core.data.v2.index.SelectedItems;
import org.roda.core.data.v2.index.SelectedItemsAll;
import org.roda.core.data.v2.index.SelectedItemsList;
import org.roda.core.data.v2.index.SelectedItemsNone;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.log.LogEntry.LOG_ENTRY_STATE;
import org.roda.core.data.v2.notifications.Notification;
import org.roda.core.data.v2.risks.Risk;
import org.roda.core.data.v2.risks.RiskIncidence;
import org.roda.core.data.v2.user.Group;
import org.roda.core.data.v2.user.RodaUser;
import org.roda.core.data.v2.user.User;
import org.roda.core.plugins.plugins.base.ActionLogCleanerPlugin;
import org.roda.core.plugins.plugins.base.ExportAIPPlugin;
import org.roda.core.plugins.plugins.base.ReindexAIPPlugin;
import org.roda.core.plugins.plugins.base.ReindexActionLogPlugin;
import org.roda.core.plugins.plugins.base.ReindexJobPlugin;
import org.roda.core.plugins.plugins.base.ReindexRodaEntityPlugin;
import org.roda.core.plugins.plugins.base.ReindexTransferredResourcePlugin;
import org.roda.core.plugins.plugins.base.RemoveAIPPlugin;
import org.roda.wui.api.controllers.Jobs;
import org.roda.wui.api.v1.utils.ApiResponseMessage;
import org.roda.wui.common.RodaCoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;

@Api(value = ManagementTasksResource.SWAGGER_ENDPOINT)
@Path(ManagementTasksResource.ENDPOINT)
public class ManagementTasksResource extends RodaCoreService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManagementTasksResource.class);

  public static final String ENDPOINT = "/v1/management_tasks";
  public static final String SWAGGER_ENDPOINT = "v1 management tasks";

  @Context
  private HttpServletRequest request;

  @POST
  @Path("/index/reindex")
  public Response executeIndexReindexTask(
    @ApiParam(value = "", allowableValues = "aip,job,risk,riskincidence,agent,format,notification,actionlogs,transferred_resources,users_and_groups", defaultValue = "aip") @QueryParam("entity") String entity,
    @QueryParam("params") List<String> params) throws AuthorizationDeniedException {
    Date startDate = new Date();

    // get user & check permissions
    RodaUser user = UserUtility.getApiUser(request);
    // FIXME see if this is the proper way to ensure that the user can execute
    // this task
    if (!user.getAllGroups().contains("administrators")) {
      throw new AuthorizationDeniedException(
        "User \"" + user.getId() + "\" doesn't have permission the execute the requested task!");
    }

    return executeReindex(user, startDate, entity, params);

  }

  @POST
  @Path("/index/actionlogclean")
  public Response executeIndexActionLogCleanTask(
    @ApiParam(value = "Amount of days to keep action information in the index", defaultValue = "30") @QueryParam("daysToKeep") String daysToKeep)
    throws AuthorizationDeniedException {
    Date startDate = new Date();

    // get user & check permissions
    RodaUser user = UserUtility.getApiUser(request);
    // FIXME see if this is the proper way to ensure that the user can execute
    // this task
    if (!user.getAllGroups().contains("administrators")) {
      throw new AuthorizationDeniedException(
        "User \"" + user.getId() + "\" doesn't have permission the execute the requested task!");
    }

    return Response.ok().entity(createJobForRunningActionlogCleaner(user, daysToKeep, startDate)).build();
  }

  private Response executeReindex(RodaUser user, Date startDate, String entity, List<String> params) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    if ("aip".equals(entity)) {
      if (params.isEmpty()) {
        response = createJobToReindexAllAIPs(user, startDate);
      } else {
        response = createJobToReindexAIPs(user, params, startDate);
      }
    } else if ("job".equals(entity)) {
      response = createJobToReindexAllJobs(user, startDate);
    } else if ("risk".equals(entity)) {
      response = createJobToReindexAllRisks(user, startDate);
    } else if ("riskincidence".equals(entity)) {
      response = createJobToReindexAllRiskIncidences(user, startDate);
    } else if ("agent".equals(entity)) {
      response = createJobToReindexAllAgents(user, startDate);
    } else if ("format".equals(entity)) {
      response = createJobToReindexAllFormats(user, startDate);
    } else if ("notification".equals(entity)) {
      response = createJobToReindexAllNotifications(user, startDate);
    } else if ("transferred_resources".equals(entity)) {
      response = createJobToReindexTransferredResources(user, startDate, params);
    } else if ("actionlogs".equals(entity)) {
      response = createJobToReindexActionlogs(user, startDate, params);
    } else if ("users_and_groups".equals(entity)) {
      response = reindexUsersAndGroups(user, startDate, params);
    }
    return Response.ok().entity(response).build();
  }

  private ApiResponseMessage createJobToExportAIPs(RodaUser user, Date startDate, String outputFolder, String selected,
    ExportType type, String removeIfAlreadyExists) {
    ApiResponseMessage response;
    response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    try {
      Job job = new Job();
      SelectedItems selectedItems = JsonUtils.getObjectFromJson(selected, SelectedItems.class);
      job.setName("Management Task | Export 'AIPs' job").setPlugin(ExportAIPPlugin.class.getName())
        .setSourceObjects(selectedItems);
      Map<String, String> parameters = new HashMap<String, String>();
      parameters.put(ExportAIPPlugin.PLUGIN_PARAM_EXPORT_FOLDER_PARAMETER, outputFolder);
      parameters.put(ExportAIPPlugin.PLUGIN_PARAM_EXPORT_TYPE, type.toString());
      parameters.put(ExportAIPPlugin.PLUGIN_PARAM_EXPORT_REMOVE_IF_ALREADY_EXISTS, removeIfAlreadyExists);
      job.setPluginParameters(parameters);
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Export AIPs job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "remove aips", null, duration, LOG_ENTRY_STATE.SUCCESS, "selected",
        selected);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating export AIPs job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToRemoveAIPs(RodaUser user, Date startDate, String selected) {
    ApiResponseMessage response;
    response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    try {
      Job job = new Job();
      SelectedItems selectedItems = JsonUtils.getObjectFromJson(selected, SelectedItems.class);
      job.setName("Management Task | Remove 'AIPs' job").setPlugin(RemoveAIPPlugin.class.getName())
        .setSourceObjects(selectedItems);
      Map<String, String> parameters = new HashMap<String, String>();
      job.setPluginParameters(parameters);
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Remove AIPs job created (" + jobCreated + ")");
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "remove aips", null, duration, LOG_ENTRY_STATE.SUCCESS, "selected",
        selected);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating remove AIPs job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllJobs(RodaUser user, Date startDate) {
    ApiResponseMessage response;
    response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Jobs' job").setSourceObjects(SelectedItemsNone.create())
      .setPlugin(ReindexJobPlugin.class.getName());
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex Jobs job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex jobs", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Jobs job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllRisks(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Risks' job").setSourceObjects(SelectedItemsNone.create())
      .setPlugin(ReindexRodaEntityPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME, Risk.class.getName());
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex risks", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Risks job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllRiskIncidences(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Risk Incidences' job")
      .setSourceObjects(SelectedItemsNone.create()).setPlugin(ReindexRodaEntityPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME, RiskIncidence.class.getName());
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex risk incidences", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Risk Incidences job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllAgents(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Agents' job").setSourceObjects(SelectedItemsNone.create())
      .setPlugin(ReindexRodaEntityPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME, Agent.class.getName());
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex agents", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Agents job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllFormats(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Formats' job").setSourceObjects(SelectedItemsNone.create())
      .setPlugin(ReindexRodaEntityPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME, Format.class.getName());
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex formats", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Formats job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllNotifications(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'Notifications' job")
      .setSourceObjects(SelectedItemsNone.create()).setPlugin(ReindexRodaEntityPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLASS_CANONICAL_NAME, Notification.class.getName());
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex notifications", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Notifications job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexTransferredResources(RodaUser user, Date startDate,
    List<String> params) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'TransferredResources' job")
      .setSourceObjects(SelectedItemsNone.create()).setPlugin(ReindexTransferredResourcePlugin.class.getName());
    if (!params.isEmpty()) {
      Map<String, String> pluginParameters = new HashMap<>();
      pluginParameters.put(RodaConstants.PLUGIN_PARAMS_STRING_VALUE, params.get(0));
      job.setPluginParameters(pluginParameters);
    }
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex transferred resources", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex TransferredResources job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexActionlogs(RodaUser user, Date startDate, List<String> params) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job().setName("Management Task | Reindex 'ActionLogs' job")
      .setSourceObjects(SelectedItemsNone.create()).setPlugin(ReindexActionLogPlugin.class.getName());
    if (!params.isEmpty()) {
      Map<String, String> pluginParameters = new HashMap<>();
      pluginParameters.put(RodaConstants.PLUGIN_PARAMS_INT_VALUE, params.get(0));
      job.setPluginParameters(pluginParameters);
    }
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex action logs", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex Action logs job", e);
    }
    return response;
  }

  private ApiResponseMessage reindexUsersAndGroups(RodaUser user, Date startDate, List<String> params) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    try {
      for (User ldapUser : UserUtility.getLdapUtility().getUsers(new Filter())) {
        LOGGER.debug("User to be indexed: {}", ldapUser);
        RodaCoreFactory.getModelService().addUser(ldapUser, false, true);
      }
      for (Group ldapGroup : UserUtility.getLdapUtility().getGroups(new Filter())) {
        LOGGER.debug("Group to be indexed: {}", ldapGroup);
        RodaCoreFactory.getModelService().addGroup(ldapGroup, false, true);
      }

      response.setMessage("Ended users and groups reindex");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex users and groups", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (NotFoundException | GenericException | AlreadyExistsException | EmailAlreadyExistsException
      | UserAlreadyExistsException | IllegalOperationException | LdapUtilityException e) {
      response.setMessage("Error reindexing users and groups: " + e.getMessage());
      LOGGER.error("Error reindexing users and groups", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAllAIPs(RodaUser user, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job();
    job.setName("Management Task | Reindex 'all AIPs' job")
      .setSourceObjects(new SelectedItemsAll<>(AIP.class.getName())).setPlugin(ReindexAIPPlugin.class.getName());
    Map<String, String> pluginParameters = new HashMap<>();
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CLEAR_INDEXES, "true");
    job.setPluginParameters(pluginParameters);
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex all aips", null, duration, LOG_ENTRY_STATE.SUCCESS);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobToReindexAIPs(RodaUser user, List<String> params, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job();
    job.setName("Management Task | Reindex 'AIPs' job").setPlugin(ReindexAIPPlugin.class.getName())
      .setSourceObjects(SelectedItemsList.create(AIP.class, params));
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Reindex job created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "reindex aips", null, duration, LOG_ENTRY_STATE.SUCCESS, "params",
        params);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating reindex job", e);
    }
    return response;
  }

  private ApiResponseMessage createJobForRunningActionlogCleaner(RodaUser user, String daysToKeep, Date startDate) {
    ApiResponseMessage response = new ApiResponseMessage(ApiResponseMessage.OK, "Action done!");
    Job job = new Job();
    job.setName("Management Task | Log cleaner job").setSourceObjects(SelectedItemsNone.create())
      .setPlugin(ActionLogCleanerPlugin.class.getName());
    if (!daysToKeep.isEmpty()) {
      Map<String, String> pluginParameters = new HashMap<String, String>();
      pluginParameters.put(RodaConstants.PLUGIN_PARAMS_INT_VALUE, daysToKeep);
      job.setPluginParameters(pluginParameters);
    }
    try {
      Job jobCreated = Jobs.createJob(user, job);
      response.setMessage("Log cleaner created (" + jobCreated + ")");
      // register action
      long duration = new Date().getTime() - startDate.getTime();
      registerAction(user, "ManagementTasks", "action log clean", null, duration, LOG_ENTRY_STATE.SUCCESS, "daysToKeep",
        daysToKeep);
    } catch (AuthorizationDeniedException | RequestNotValidException | NotFoundException | GenericException
      | JobAlreadyStartedException e) {
      LOGGER.error("Error creating log cleaner job", e);
    }

    return response;
  }
}
