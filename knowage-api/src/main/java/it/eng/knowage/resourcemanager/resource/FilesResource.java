/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.
 *
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.knowage.resourcemanager.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import it.eng.knowage.knowageapi.context.BusinessRequestContext;
import it.eng.knowage.knowageapi.error.KNRM001Exception;
import it.eng.knowage.knowageapi.error.KNRM002Exception;
import it.eng.knowage.knowageapi.error.KNRM003Exception;
import it.eng.knowage.knowageapi.error.KNRM008Exception;
import it.eng.knowage.knowageapi.error.KNRM010Exception;
import it.eng.knowage.knowageapi.error.KNRM011Exception;
import it.eng.knowage.knowageapi.error.KnowageBusinessException;
import it.eng.knowage.knowageapi.error.KnowageRuntimeException;
import it.eng.knowage.resourcemanager.resource.dto.DownloadFilesDTO;
import it.eng.knowage.resourcemanager.resource.dto.FileDTO;
import it.eng.knowage.resourcemanager.resource.dto.MetadataDTO;
import it.eng.knowage.resourcemanager.service.ResourceManagerAPI;
import it.eng.spagobi.services.security.SecurityServiceService;
import it.eng.spagobi.services.security.SpagoBIUserProfile;

@Path("/2.0/resources/files")
@Component
public class FilesResource {

	private static final Logger LOGGER = Logger.getLogger(FilesResource.class);

	@Autowired
	@Lazy
	SecurityServiceService securityServiceService;

	@Autowired
	ResourceManagerAPI resourceManagerAPIservice;

	@Autowired
	BusinessRequestContext businessContext;

	// Files Management

	/**
	 * @param path
	 * @return list of files, one of them could be "metadata.json", it will be excluded
	 * @throws KNRM001Exception
	 * @throws KNRM003Exception
	 * @throws KNRM002Exception
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public List<FileDTO> getFiles(@QueryParam("key") String key) throws KNRM001Exception, KNRM003Exception, KNRM002Exception {
		SpagoBIUserProfile profile = businessContext.getUserProfile();

		List<FileDTO> files = resourceManagerAPIservice.getListOfFiles(key, profile);
		return files;
	}

	@POST
	@Path("/download")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response downloadFiles(DownloadFilesDTO dto) throws KNRM001Exception, KNRM008Exception, KNRM002Exception {
		SpagoBIUserProfile profile = businessContext.getUserProfile();

		List<String> listOfPaths = new ArrayList<String>();
		String folderPath = resourceManagerAPIservice.getFolderByKey(dto.getKey(), profile);
		for (String name : dto.getSelectedFilesNames()) {
			listOfPaths.add(folderPath + File.separator + name);
		}

		if (listOfPaths.size() == 1) {
			java.nio.file.Path path = resourceManagerAPIservice.getDownloadFilePath(listOfPaths, profile, false);
			try {
				File f = path.toFile();
				MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
				String mimeType = fileTypeMap.getContentType(f.getName());
				return Response.ok(path.toFile()).header("Content-length", "" + Files.size(path))
						.header("Content-Disposition", String.format("attachment; filename=\"%s\"", path.getFileName())).header("Content-type", mimeType)
						.build();
			} catch (IOException e) {
				throw new KnowageRuntimeException("Error calculating file size for " + path, e);
			}
		} else {
			java.nio.file.Path zipFile = resourceManagerAPIservice.getDownloadFilePath(listOfPaths, profile, true);
			String filename = zipFile.getFileName() + ".zip";
			try {
				return Response.ok(zipFile.toFile()).header("Content-length", "" + Files.size(zipFile))
						.header("Content-Disposition", String.format("attachment; filename=\"%s\"", filename)).header("Content-Type", "application/zip")
						.build();
			} catch (IOException e) {
				throw new KnowageRuntimeException("Error calculating file size for " + zipFile, e);
			}
		}
	}

	@POST
	@Path("/uploadFile")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadFile(MultipartFormDataInput multipartFormDataInput) throws KnowageBusinessException, KNRM001Exception {

		Response response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		SpagoBIUserProfile profile = businessContext.getUserProfile();
		Map<String, List<InputPart>> formDataMap = multipartFormDataInput.getFormDataMap();

		if (!formDataMap.containsKey("file")) {
			throw new KnowageBusinessException("Cannot find the file part in input");
		}

		if (!formDataMap.containsKey("key")) {
			throw new KnowageBusinessException("Cannot find key part in input");
		}

		try {
			String key = formDataMap.get("key").get(0).getBodyAsString();

			Boolean extract = false;
			if (formDataMap.containsKey("extract")) {
				extract = Boolean.valueOf(formDataMap.get("extract").get(0).getBodyAsString());
			}

			InputPart inputPart = formDataMap.get("file").get(0);
			MediaType mediaType = inputPart.getMediaType();

			MultivaluedMap<String, String> multivaluedMap = inputPart.getHeaders();
			String FILENAME_REGEX = "(form-data; name=\"file\"; filename=\")([a-zA-Z0-9-&_. ]*)(\")";
			Pattern p = Pattern.compile(FILENAME_REGEX);
			Matcher m = p.matcher(multivaluedMap.get("Content-Disposition").get(0));
			String fileName = null;
			if (m.matches() && m.groupCount() > 1) {
				fileName = m.group(2);
			}

			if (null != fileName && !"".equalsIgnoreCase(fileName)) {

				String path = resourceManagerAPIservice.getFolderByKey(key, profile) + File.separator + fileName;

				if (!Arrays.asList("application/x-zip-compressed", "application/zip").contains(mediaType.toString())) {
					try (InputStream is = inputPart.getBody(InputStream.class, null)) {

						resourceManagerAPIservice.importFile(is, path, profile);
						return Response.status(Response.Status.OK).build();

					} catch (IOException e) {
						throw new KnowageRuntimeException(e.getMessage());
					}
				} else {
					try (InputStream is = inputPart.getBody(InputStream.class, null)) {

						if (extract) {
							resourceManagerAPIservice.importFileAndExtract(is, path, profile);

						} else {
							resourceManagerAPIservice.importFile(is, path, profile);
						}

						return Response.status(Response.Status.OK).build();
					} catch (IOException e) {
						throw new KnowageRuntimeException(e.getMessage());
					}
				}

			} else {
				String message = "Cannot find file name inside uploading file/package";
				response = Response.status(Response.Status.NOT_MODIFIED).entity(message).build();
			}
			return response;
		} catch (IOException e) {
			throw new KnowageRuntimeException(e.getMessage());
		}

	}

	@GET
	@Path("/metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public MetadataDTO getMetadata(@QueryParam("key") String key) throws KNRM001Exception, KNRM011Exception, KNRM002Exception {
		SpagoBIUserProfile profile = businessContext.getUserProfile();
		String path = resourceManagerAPIservice.getFolderByKey(key, profile);
		MetadataDTO file = resourceManagerAPIservice.getMetadata(path, profile);
		return file;
	}

	@POST
	@Path("/metadata")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public MetadataDTO saveMetadata(MetadataDTO fileDTO, @QueryParam("key") String key) throws KNRM001Exception, KNRM010Exception, KNRM002Exception {
		SpagoBIUserProfile profile = businessContext.getUserProfile();
		String path = resourceManagerAPIservice.getFolderByKey(key, profile);
		MetadataDTO file = resourceManagerAPIservice.saveMetadata(fileDTO, path, profile);
		return file;
	}

	// Common methods

	@DELETE
	@Path("/{path}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response delete(DownloadFilesDTO dto) {
		Response response = null;
		try {
			SpagoBIUserProfile profile = businessContext.getUserProfile();
			String path = resourceManagerAPIservice.getFolderByKey(dto.getKey(), profile);
			for (String fileName : dto.getSelectedFilesNames()) {
				String completePath = path + File.separator + fileName;

				boolean ok = resourceManagerAPIservice.delete(completePath, profile);
				if (ok)
					response = Response.status(Response.Status.OK).build();
				else {
					response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
					break;
				}
			}
		} catch (Exception e) {
			throw new KnowageRuntimeException(e.getMessage());
		}
		return response;

	}

}