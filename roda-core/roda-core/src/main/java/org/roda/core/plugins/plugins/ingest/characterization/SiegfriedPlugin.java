/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest.characterization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.roda.core.data.PluginParameter;
import org.roda.core.data.Report;
import org.roda.core.data.common.InvalidParameterException;
import org.roda.core.data.v2.FileFormat;
import org.roda.core.data.v2.PluginType;
import org.roda.core.index.IndexService;
import org.roda.core.model.AIP;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceException;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.storage.Binary;
import org.roda.core.storage.StoragePath;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.StorageServiceException;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.storage.fs.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiegfriedPlugin implements Plugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SiegfriedPlugin.class);

  @Override
  public void init() throws PluginException {
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Sigefried characterization action";
  }

  @Override
  public String getDescription() {
    return "Update the premis files with the object characterization";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    return new ArrayList<>();
  }

  @Override
  public Map<String, String> getParameterValues() {
    return new HashMap<>();
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    // no params
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {

    for (AIP aip : list) {
      LOGGER.debug("Processing AIP " + aip.getId());
      for (String representationID : aip.getRepresentationIds()) {
        LOGGER.debug("Processing representation " + representationID + " of AIP " + aip.getId());
        try {
          Path data = Files.createTempDirectory("data");
          StorageService tempStorage = new FileStorageService(data);
          StoragePath representationPath = ModelUtils.getRepresentationPath(aip.getId(), representationID);
          tempStorage.copy(storage, representationPath, representationPath);
          String siegfriedOutput = SiegfriedPluginUtils.runSiegfriedOnPath(data.resolve(representationPath.asString()));

          final JSONObject obj = new JSONObject(siegfriedOutput);
          JSONArray files = (JSONArray) obj.get("files");
          List<org.roda.core.model.File> updatedFiles = new ArrayList<org.roda.core.model.File>();
          for (int i = 0; i < files.length(); i++) {
            JSONObject fileObject = files.getJSONObject(i);

            String fileName = fileObject.getString("filename");
            fileName = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
            long fileSize = fileObject.getLong("filesize");

            Path p = Files.createTempFile("temp", ".temp");
            Files.write(p, fileObject.toString().getBytes());
            Binary resource = (Binary) FSUtils.convertPathToResource(p.getParent(), p);
            LOGGER.debug("Creating other metadata (AIP: " + aip.getId() + ", REPRESENTATION: " + representationID
              + ", FILE: " + fileName + ")");
            try {
              model.createOtherMetadata(aip.getId(), representationID, fileName + ".xml", "Siegfried", resource);
            } catch (ModelServiceException e1) {
              e1.printStackTrace();
            }
            p.toFile().delete();

            JSONArray matches = (JSONArray) fileObject.get("matches");
            if (matches.length() > 0) {
              for (int j = 0; j < matches.length(); j++) {
                JSONObject match = (JSONObject) matches.get(j);
                if (match.getString("id").equalsIgnoreCase("pronom")) {
                  String pronom = match.getString("puid");
                  String mime = match.getString("mime");
                  String version = match.getString("version");
                  String extension = "";
                  if (fileName.contains(".")) {
                    extension = fileName.substring(fileName.lastIndexOf('.'));
                  }

                  System.out.println(fileName + " - " + pronom + " - " + mime);

                  try {
                    org.roda.core.model.File f = model.retrieveFile(aip.getId(), representationID, fileName);
                    FileFormat ff = new org.roda.core.data.v2.FileFormat();
                    ff.setPronom(pronom);
                    ff.setMimeType(mime);
                    ff.setVersion(version);
                    ff.setCreatedDate(new Date());
                    ff.setExtension(extension);
                    f.setFileFormat(ff);
                    f.setSize(fileSize);
                    updatedFiles.add(f);
                  } catch (ModelServiceException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                  }
                }
              }
            }
          }
          model.updateFileFormats(updatedFiles);
          FSUtils.deletePath(data);
        } catch (StorageServiceException | PluginException | IOException | ModelServiceException e) {
          e.printStackTrace();
          LOGGER.error("Error running SIEGFRIED " + aip.getId() + ": " + e.getMessage());
        }
      }

    }
    return null;
  }

  @Override
  public Report beforeExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Report afterExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Plugin<AIP> cloneMe() {
    return new DroidPlugin();
  }

  @Override
  public PluginType getType() {
    return PluginType.AIP_TO_AIP;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

}
