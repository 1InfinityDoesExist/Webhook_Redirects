package com.webooksredirects.webhooks.gmail;

import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;
import springfox.documentation.annotations.ApiIgnore;

@Slf4j
@RestController
public class GoogleWebhook {

	private static final String MessageData = "message.data";
	private static final String MessageDataValue = "messageDataValue";

	private static final ObjectMapper objectMapper;
	static {
		objectMapper = new ObjectMapper();
	}

	@PostMapping("/social-engagement/gmail/webhook")
	public ResponseEntity<?> eventHandler(HttpServletRequest request, String data) throws Exception {
		log.info("-----Webhook method to retrieve events. from the post-----");
		String pushedJsonAsString = IOUtils.toString(request.getInputStream(), "utf-8");

		log.info("------Event from webhook : {}", pushedJsonAsString);
		JsonNode jsonNode = objectMapper.readTree(pushedJsonAsString);
		Object read = JsonPath.read(objectMapper.writeValueAsString(jsonNode), MessageData);

		byte[] decode = Base64.getDecoder().decode((String) read);
		String showDecode = new String(decode, "UTF-8");

		JsonNode jsonNodePart = objectMapper.readTree(showDecode);
		ObjectNode objectNode = (ObjectNode) jsonNode;
		objectNode.put(MessageDataValue, jsonNodePart);
		jsonNode = (JsonNode) objectMapper.readTree(objectNode.toString());

		log.info("----Final message from gmail. : {}", jsonNode);

		return ResponseEntity.status(HttpStatus.OK).body(new ModelMap().addAttribute("msg", "Success"));
	}
}