/* SpagoBI, the Open Source Business Intelligence suite

 * Copyright (C) 2012 Engineering Ingegneria Informatica S.p.A. - SpagoBI Competency Center
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0, without the "Incompatible With Secondary Licenses" notice.
 * If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package it.eng.knowage.api.dossier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import commonj.work.Work;
import it.eng.knowage.api.dossier.utils.FileUtilities;
import it.eng.knowage.engines.dossier.template.AbstractDossierTemplate;
import it.eng.knowage.engines.dossier.template.parameter.Parameter;
import it.eng.knowage.engines.dossier.template.placeholder.PlaceHolder;
import it.eng.knowage.engines.dossier.template.report.Report;
import it.eng.spago.error.EMFUserError;
import it.eng.spago.security.IEngUserProfile;
import it.eng.spagobi.analiticalmodel.document.bo.BIObject;
import it.eng.spagobi.analiticalmodel.document.bo.DocumentMetadataProperty;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.BIObjectParameter;
import it.eng.spagobi.commons.SingletonConfig;
import it.eng.spagobi.commons.bo.UserProfile;
import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.commons.utilities.ObjectsAccessVerifier;
import it.eng.spagobi.commons.utilities.SpagoBIUtilities;
import it.eng.spagobi.tenant.Tenant;
import it.eng.spagobi.tenant.TenantManager;
import it.eng.spagobi.tools.massiveExport.dao.IProgressThreadDAO;
import it.eng.spagobi.tools.objmetadata.bo.ObjMetacontent;
import it.eng.spagobi.tools.objmetadata.bo.ObjMetadata;
import it.eng.spagobi.tools.objmetadata.dao.IObjMetacontentDAO;
import it.eng.spagobi.tools.objmetadata.dao.IObjMetadataDAO;
import it.eng.spagobi.utilities.ParametersDecoder;
import it.eng.spagobi.utilities.exceptions.SpagoBIRuntimeException;
import it.eng.spagobi.utilities.exceptions.SpagoBIServiceException;

public class DocumentExecutionWorkForDoc extends DossierExecutionClient implements Work {

	private static transient Logger logger = Logger.getLogger(DocumentExecutionWorkForDoc.class);

	public static final String PREPARED = "PREPARED";
	public static final String STARTED = "STARTED";
	public static final String DOWNLOAD = "DOWNLOAD";
	public static final String ERROR = "ERROR";

	private IEngUserProfile userProfile;
	private List<BIObjectPlaceholdersPair> documents;

	private Integer progressThreadId;
	private String randomKey;

	private boolean completeWithoutError = false;
	IProgressThreadDAO progressThreadDAO;

	private AbstractDossierTemplate dossierTemplate = null;
	private List<String> imageNames = new ArrayList<String>();

	public DocumentExecutionWorkForDoc(AbstractDossierTemplate dossierTemplate, List<BIObjectPlaceholdersPair> documents, IEngUserProfile userProfile,
			Integer progressThreadId, String randomKey) {
		super();
		this.documents = documents;
		this.userProfile = userProfile;
		this.progressThreadId = progressThreadId;
		this.randomKey = randomKey;
		this.dossierTemplate = dossierTemplate;
	}

	@Override
	public void run() {
		try {
			this.setTenant();
			this.runInternal();
		} finally {
			TenantManager.unset();
		}
	}

	private void setTenant() {
		logger.debug("IN");
		UserProfile profile = (UserProfile) this.getProfile();
		String tenant = profile.getOrganization();
		LogMF.debug(logger, "Tenant : [{0}]", tenant);
		TenantManager.setTenant(new Tenant(tenant));
		logger.debug("OUT");
	}

	private void runInternal() {
		logger.debug("IN");

		ProgressThreadManager progressThreadManager = null;
		IObjMetadataDAO metaDAO = null;
		IObjMetacontentDAO contentDAO = null;

		Thread thread = Thread.currentThread();
		Long threadId = thread.getId();

		logger.debug("Started thread Id " + threadId + " from user id: " + ((UserProfile) userProfile).getUserId());

		Integer totalDocs = documents.size();
		logger.debug("# of documents: " + totalDocs);

		progressThreadManager = new ProgressThreadManager();
		progressThreadManager.setStatusStarted(progressThreadId);
		try {

			metaDAO = DAOFactory.getObjMetadataDAO();
			contentDAO = DAOFactory.getObjMetacontentDAO();

		} catch (Exception e) {
			logger.error("Error setting DAO");
			progressThreadManager.deleteThread(progressThreadId);
			throw new SpagoBIServiceException("Error setting DAO", e);
		}

		try {
			/**/
			String userUniqueIdentifier = (String) userProfile.getUserUniqueIdentifier();

			ObjectMapper objectMapper = new ObjectMapper();
			String dossierTemplateJson = objectMapper.writeValueAsString(dossierTemplate);
			Map<String, String> imagesMap = null;

			Set<String> executedDocuments = new HashSet<String>();

			for (Report reportToUse : dossierTemplate.getReports()) {

				String cockpitDocument = reportToUse.getLabel();
				if (executedDocuments.contains(cockpitDocument)) {
					progressThreadManager.incrementPartial(progressThreadId);
					continue;
				}

				String imageName = reportToUse.getImageName(); // image format is .png

				this.validImage(imageName);

				List<Parameter> parameter = reportToUse.getParameters();

				logger.debug("executing post service to execute documents");
				BIObject biObject = DAOFactory.getBIObjectDAO().loadBIObjectByLabel(cockpitDocument);
				Integer docId = biObject.getId();

				Collection<String> roles = userProfile.getRoles();
				for (String role : roles) {

					if (!ObjectsAccessVerifier.canExec(biObject, userProfile)) {
						String message = "user " + ((UserProfile) userProfile).getUserName() + " cannot execute document " + biObject.getName();
						throw new SpagoBIRuntimeException(message);
					}

					List<BIObjectParameter> drivers = biObject.getDrivers();

					String docName = biObject.getName();

					String hostUrl = getServiceHostUrl();

					StringBuilder serviceUrlBuilder = new StringBuilder();
					serviceUrlBuilder.append(hostUrl);
					serviceUrlBuilder.append("/knowagecockpitengine/api/1.0/pages/execute?");
					serviceUrlBuilder.append("user_id=");
					serviceUrlBuilder.append(userUniqueIdentifier);
					serviceUrlBuilder.append("&DOCUMENT_LABEL=");
					serviceUrlBuilder.append(cockpitDocument);
					serviceUrlBuilder.append("&DOCUMENT_OUTPUT_PARAMETERS=%5B%5D&DOCUMENT_IS_VISIBLE=true&SBI_EXECUTION_ROLE=");
					serviceUrlBuilder.append(URLEncoder.encode(role, StandardCharsets.UTF_8.toString()));
					serviceUrlBuilder.append("&DOCUMENT_DESCRIPTION=&document=");
					serviceUrlBuilder.append(docId);
					serviceUrlBuilder.append("&IS_TECHNICAL_USER=true&DOCUMENT_NAME=");
					serviceUrlBuilder.append(docName);
					serviceUrlBuilder.append("&NEW_SESSION=TRUE&SBI_ENVIRONMENT=DOCBROWSER&IS_FOR_EXPORT=true&documentMode=VIEW&export=true&outputType=PNG");

					String serviceUrl = addParametersToServiceUrl(drivers, parameter, serviceUrlBuilder);

					// Images creation
					Response images = executePostService(null, serviceUrl, userUniqueIdentifier, MediaType.TEXT_HTML, dossierTemplateJson);
					byte[] responseAsByteArray = images.readEntity(byte[].class);

					List<Object> list = images.getMetadata().get("Content-Type");
					Iterator<Object> it = list.iterator();
					boolean isZipped = false;
					while (it.hasNext()) {
						String contentType = (String) it.next();

						isZipped = contentType.contains("application/zip");
						break;

					}

					// DOC with images replaced creation
					imagesMap = new HashMap<String, String>();
					if (isZipped) {
						String message = "Document has more than one single sheet. Screenshot is replaced with an empty image.";
						logger.debug(message);

						handleAllPicturesFromZipFile(responseAsByteArray, randomKey, imagesMap, reportToUse);
						executedDocuments.add(cockpitDocument);

					} else {
						String path = SpagoBIUtilities.getResourcePath() + File.separator + "dossierExecution" + File.separator;
						File f = FileUtilities.createFile(imageName, ".png", randomKey, new ArrayList<PlaceHolder>());
						FileOutputStream outputStream = new FileOutputStream(f);
						outputStream.write(responseAsByteArray);
						outputStream.close();
						imagesMap.put(imageName, path);
					}

					progressThreadManager.incrementPartial(progressThreadId);
					logger.debug("progress Id incremented");

					break;
				}

			}
			// Activity creation

			imageNames.clear();

			progressThreadManager.setStatusDownload(progressThreadId);
			logger.debug("Thread row in database set as download state");

			logger.debug("OUT");

			/**/
		} catch (Exception e) {
			logger.error("Error while creating dossier activity", e);
			progressThreadManager.setStatusError(progressThreadId);
			throw new SpagoBIRuntimeException(e.getMessage(), e);
		} finally {
			logger.debug("OUT");
		}

	}

	public void validImage(String image) {
		if (!imageNames.contains(image)) {
			imageNames.add(image);
		} else {
			throw new SpagoBIRuntimeException("Image names must be different inside template");
		}
	}

	public String addParametersToServiceUrl(List<BIObjectParameter> drivers, List<Parameter> parameter, StringBuilder serviceUrlBuilder)
			throws UnsupportedEncodingException {
		if (drivers.size() != parameter.size()) {
			throw new SpagoBIRuntimeException("There are a different number of parameters/drivers between document and template");
		}
		int i = 0;
		for (Parameter parameterToFind : parameter) {
			if (parameterToFind.getValue() != null && !parameterToFind.getValue().isEmpty()) {
				i++;
			}
		}
		if (i == parameter.size()) { // if a parameter is dynamic it is already filled
			ParametersDecoder decoder = new ParametersDecoder();
			for (BIObjectParameter biObjectParameter : drivers) {
				boolean found = false;
				for (Parameter templateParameter : parameter) {
					if (biObjectParameter.getParameterUrlName().equals(templateParameter.getUrlName())) {
						String value = templateParameter.getValue();
						value = decoder.decodeParameter(value);
						if (decoder.isMultiValues(value) && value.contains("STRING"))
							value.replaceAll("'", "");
						serviceUrlBuilder
								.append("&" + biObjectParameter.getParameterUrlName() + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.toString()));
						found = true;
						break;
					}
				}
				if (!found) {
					throw new SpagoBIRuntimeException("There is no match between document parameters and template parameters.");
				}
			}
			return serviceUrlBuilder.toString();
		}
		for (BIObjectParameter biObjectParameter : drivers) {
			boolean found = false;
			for (Parameter templateParameter : parameter) {
				if (biObjectParameter.getParameterUrlName().equals(templateParameter.getUrlName())) {
					serviceUrlBuilder.append("&" + biObjectParameter.getParameterUrlName() + "="
							+ URLEncoder.encode(templateParameter.getValue(), StandardCharsets.UTF_8.toString()));
					found = true;
					break;
				}
			}
			if (!found) {
				throw new SpagoBIRuntimeException("There is no match between document parameters and template parameters.");
			}
		}
		return serviceUrlBuilder.toString();
	}

	public String getServiceHostUrl() {
		String serviceURL = SpagoBIUtilities.readJndiResource(SingletonConfig.getInstance().getConfigValue("SPAGOBI.SPAGOBI_SERVICE_JNDI"));
		serviceURL = serviceURL.substring(0, serviceURL.lastIndexOf('/'));
		return serviceURL;
	}

	private void handleAllPicturesFromZipFile(byte[] responseAsByteArray, String randomKey, Map<String, String> imagesMap, Report reportToUse)
			throws IOException {

		String outFolderPath = SpagoBIUtilities.getResourcePath() + File.separator + "dossierExecution" + File.separator + randomKey + File.separator;

		File outFolder = new File(outFolderPath);

		byte[] buffer = new byte[1024];
		ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseAsByteArray));
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			File newFile = FileUtilities.createFile(FilenameUtils.removeExtension(zipEntry.getName()), ".png", randomKey, new ArrayList<PlaceHolder>());
			FileOutputStream fos = new FileOutputStream(newFile);
			int len;
			while ((len = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		zis.close();

		// array of supported extensions (use a List if you prefer)
		String[] EXTENSIONS = new String[] { "gif", "png", "bmp" // and other formats you need
		};
		// filter to identify images based on their extensions
		FilenameFilter IMAGE_FILTER = new FilenameFilter() {

			@Override
			public boolean accept(final File dir, final String name) {
				for (final String ext : EXTENSIONS) {
					if (name.endsWith("." + ext)) {
						return (true);
					}
				}
				return (false);
			}
		};

		String documentLabel = reportToUse.getLabel();

		if (outFolder.isDirectory()) {
			for (final File f : outFolder.listFiles(IMAGE_FILTER)) {

				try {

					File to = FileUtilities.createFile(FilenameUtils.removeExtension(documentLabel + "_" + f.getName()), ".png", randomKey,
							new ArrayList<PlaceHolder>());

					FileUtils.copyFile(f, to);

					imagesMap.put(reportToUse.getImageName() + "_" + f.getName(), to.getAbsolutePath());

					FileUtils.deleteQuietly(f);

				} catch (final IOException e) {
					// handle errors here
				}
			}

		}
	}

	public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	private List<DocumentMetadataProperty> getMetaDataAndContent(IObjMetadataDAO metaDao, IObjMetacontentDAO metaContentDAO, BIObject obj) throws Exception {
		logger.debug("IN");
		List toReturn = null;

		try {
			DocumentMetadataProperty objMetaDataAndContent = null;
			List<ObjMetadata> allMetas = metaDao.loadAllObjMetadata();
			Map<Integer, ObjMetacontent> values = new HashMap<Integer, ObjMetacontent>();

			List list = metaContentDAO.loadObjOrSubObjMetacontents(obj.getId(), null);
			for (Iterator iterator = list.iterator(); iterator.hasNext();) {
				ObjMetacontent content = (ObjMetacontent) iterator.next();
				Integer metaid = content.getObjmetaId();
				values.put(metaid, content);
			}

			for (Iterator iterator = allMetas.iterator(); iterator.hasNext();) {
				ObjMetadata meta = (ObjMetadata) iterator.next();
				objMetaDataAndContent = new DocumentMetadataProperty();
				objMetaDataAndContent.setMetadataPropertyDefinition(meta);
				objMetaDataAndContent.setMetadataPropertyValue(values.get(meta.getObjMetaId()));
				if (toReturn == null)
					toReturn = new ArrayList<DocumentMetadataProperty>();
				toReturn.add(objMetaDataAndContent);
			}

		} catch (Exception e) {
			logger.error("error in retrieving metadata and metacontent for biobj id " + obj.getId(), e);
			throw e;
		}
		logger.debug("OUT");
		return toReturn;
	}

	@Override
	public boolean isDaemon() {
		return false;
	}

	@Override
	public void release() {
	}

	public List getBiObjects() {
		return documents;
	}

	public void setBiObjects(List biObjects) {
		this.documents = biObjects;
	}

	/**
	 * Checks if is complete without error.
	 *
	 * @return true, if is complete without error
	 */
	public boolean isCompleteWithoutError() {
		return completeWithoutError;
	}

	public IEngUserProfile getProfile() {
		return userProfile;
	}

	public void setProfile(IEngUserProfile profile) {
		this.userProfile = profile;
	}

	public void deleteDBRowInCaseOfError(IProgressThreadDAO threadDAO, Integer progressThreadId) {
		logger.debug("IN");
		try {
			threadDAO.deleteProgressThread(progressThreadId);
		} catch (EMFUserError e1) {
			logger.error("Error in deleting the row with the progress id " + progressThreadId);
		}
		logger.debug("OUT");

	}

}