package com.webooksredirects.webhooks.gdrive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.StartPageToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class GoogleDriveWebhook {

	// @Value("${gmail.api.scopes:https://www.googleapis.com/auth/drive.readonly.metadata,https://www.googleapis.com/auth/drive.metadata.readonly,https://www.googleapis.com/auth/drive.appdata,https://www.googleapis.com/auth/drive.metadata,https://www.googleapis.com/auth/drive.photos.readonly,https://www.googleapis.com/auth/drive.appdata,https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/drive.readonly,https://www.googleapis.com/auth/forms.body,https://www.googleapis.com/auth/forms.body.readonly,https://www.googleapis.com/auth/forms.responses.readonly,https://mail.google.com/,https://www.googleapis.com/auth/gmail.modify,https://www.googleapis.com/auth/gmail.readonly,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/pubsub}")
	@Value("${gmail.api.scopes:https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/drive.file,https://www.googleapis.com/auth/drive.readonly,https://www.googleapis.com/auth/forms.body,https://www.googleapis.com/auth/forms.body.readonly,https://www.googleapis.com/auth/forms.responses.readonly,https://mail.google.com/,https://www.googleapis.com/auth/gmail.modify,https://www.googleapis.com/auth/gmail.readonly,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/pubsub,https://www.googleapis.com/auth/drive.appdata,https://www.googleapis.com/auth/drive.metadata,https://www.googleapis.com/auth/drive.metadata.readonly,https://www.googleapis.com/auth/drive.photos.readonly}")
	private String[] scopesForGmailApis;

	@Value("${google.drive.file.url:https://www.googleapis.com/drive/v3/changes}")
	private String fetchDriveFileUrl;

	@Value("${google.drive.file.url:https://docs.google.com/uc?export=download&id=}")
	private String downlaodFileUrl;

	@Autowired
	private com.webooksredirects.utils.GenericRestTemplateUtil genericRestCalls;

	@PostMapping("/social-engagement/g-drive/events")
	public ResponseEntity<?> eventHandler(HttpServletRequest request, @RequestParam String data) throws Exception {
		log.info("-----Webhook method to retrieve events. from the google drive-----");
		fetchStartPageToken(data);
		return ResponseEntity.status(HttpStatus.OK).body(new ModelMap().addAttribute("msg", "Success"));

	}

	/**
	 * Note : It works when you are are adding file one by one.
	 * 
	 * @param serviceAccFileUrl
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	public String fetchStartPageToken(String serviceAccFileUrl) throws IOException, ParseException {
		String filePath = getServiceAccountDestailsAsStream(serviceAccFileUrl);
		GoogleCredential driveService = getGoogleCredential(filePath);
		Drive service = new Drive.Builder(new NetHttpTransport(), new JacksonFactory(), driveService).build();
		try {
			StartPageToken response = service.changes().getStartPageToken().execute();
			log.info("Start token:  {}", response.getStartPageToken());

			String startPageToken = response.getStartPageToken();

			InputStream resourceAsStream = new FileInputStream(filePath.toString());
			String driveAccessToken = GoogleCredentials.fromStream(resourceAsStream)
					.createScoped(Arrays.asList(scopesForGmailApis)).refreshAccessToken().getTokenValue();
			log.info("----Drive Access Token : {}", driveAccessToken);

			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "Bearer " + driveAccessToken);

			UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(fetchDriveFileUrl)
					.queryParam("pageToken", Integer.valueOf(startPageToken) - 1); // page token is array to starts from
																					// 0
			String actualGetFileCallResponse = genericRestCalls.performRestCall(HttpMethod.GET,
					uriBuilder.toUriString(), headers, null, String.class);

			JSONObject jsonObject = (JSONObject) new JSONParser().parse(actualGetFileCallResponse);
			JSONArray jsonArray = (JSONArray) jsonObject.get("changes");
			for (Object obj : jsonArray) {
				JSONObject changedObject = (JSONObject) obj;

				log.info("----Downlaod Url : {}", downlaodFileUrl + changedObject.get("fileId"));
			}

			log.info("-----Response from google drive : {}", actualGetFileCallResponse);

			return "Success";
		} catch (GoogleJsonResponseException e) {
			System.err.println("Unable to fetch start page token: " + e.getDetails());
			throw e;
		} catch (ParseException e) {
			e.printStackTrace();
			throw e;
		}
	}

	// In case if the scope access is allowed to the service Account.
	public String fetchChanges(String savedStartPageToken, Drive service) throws IOException {
		log.info("-----Initial Token : {}", savedStartPageToken);

		try {
			String pageToken = savedStartPageToken;
			log.info("----PageToken : {}", pageToken);
			while (pageToken != null) {
				ChangeList changes = service.changes().list(pageToken).execute();
				log.info("changes.getChanges " + changes.getChanges().size());
				for (com.google.api.services.drive.model.Change change : changes.getChanges()) {
					log.info("-----Change found for file:  {}", change.getFileId());
				}
				if (changes.getNewStartPageToken() != null) {
					savedStartPageToken = changes.getNewStartPageToken();
					log.info("------Saved Start Page Token : {}", savedStartPageToken);
				}
				pageToken = changes.getNextPageToken();
				log.info("----Page Token : {}", pageToken);
			}
			return savedStartPageToken;
		} catch (GoogleJsonResponseException e) {
			System.err.println("Unable to fetch changes: " + e.getDetails());
			throw e;
		}
	}

	private String getServiceAccountDestailsAsStream(String serviceAccFileUrl) throws IOException {
		// TODO Auto-generated method stub
		log.info("----getServiceAccountDestailsAsStream : {}", serviceAccFileUrl);

		if (ObjectUtils.isEmpty(serviceAccFileUrl)) {
			throw new RuntimeException("File not found");
		}
		Path tempFile = Files.createTempFile("Google_Cred" + "_" + new Date().getTime(), ".json");
		String response = genericRestCalls.performRestCall(HttpMethod.GET, serviceAccFileUrl, null, null, String.class);
		Files.write(tempFile, response.getBytes(StandardCharsets.UTF_8));
		log.info("-----File path  :{}", tempFile.getParent() + File.separator + tempFile.getFileName());

		return tempFile.getParent() + File.separator + tempFile.getFileName();
	}

	private GoogleCredential getGoogleCredential(String filePath) throws FileNotFoundException, IOException {
		HttpTransport httpTransport = new NetHttpTransport();
		GoogleCredential googleCredential = GoogleCredential
				.fromStream(new FileInputStream(filePath), httpTransport, new JacksonFactory())
				.createScoped(Arrays.asList(scopesForGmailApis));
		return googleCredential;
	}

	public CredentialsProvider getCredentialsProvider(String filePath) throws IOException {
		log.info("--------InputStream : {}", filePath);
		CredentialsProvider credentialsProvider = FixedCredentialsProvider
				.create(ServiceAccountCredentials.fromStream(new FileInputStream(filePath)));
		log.info("-----CredentialsProvider : {}", credentialsProvider);
		return credentialsProvider;
	}

}
