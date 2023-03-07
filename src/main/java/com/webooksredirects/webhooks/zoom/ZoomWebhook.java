package com.webooksredirects.webhooks.zoom;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Enumeration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ZoomWebhook {

	private String encodingAlgorithm = "HmacSHA256";

	@PostMapping("/api/sn_zoom_spoke/zoom_webhook_endpoint/webhook")
	public ResponseEntity<?> eventHandler(HttpServletRequest request, @RequestParam String secretKey) throws Exception {
		log.info("-----Webhook method to retrieve events. from the post with secretKey as : {}", secretKey);
		String pushedJsonAsString = IOUtils.toString(request.getInputStream(), "utf-8");

		log.info("------Event from webhook : {}", pushedJsonAsString);
		JSONObject payload = (JSONObject) new JSONParser().parse(pushedJsonAsString);

		log.info("--------JsonObject : {}", payload);

		Enumeration<String> headerNames = request.getHeaderNames();

		if (headerNames != null) {
			while (headerNames.hasMoreElements()) {
				log.info("Header: {}", request.getHeader(headerNames.nextElement()));
			}
		}

		String event = (String) payload.get("event");
		String payloadObject = new ObjectMapper().writeValueAsString(payload.get("payload"));
		long eventTS = (long) payload.get("event_ts");

		String headerValue = request.getHeader("x-zm-request-timestamp");

		log.info("-----Event : {} , payloadObject : {}, and eventTS : {}  and headerValue :{}", event, payloadObject,
				eventTS, headerValue);

		String message = new StringBuilder().append("v0").append(":").append(headerValue).append(":")
				.append(payloadObject).toString();

		String hashForVerifyValue = hashForVerify(message, secretKey);
		log.info("-----HashForVerify : {}", hashForVerifyValue);

		String signature = new StringBuilder().append("v0").append("=").append(hashForVerifyValue).toString();
		log.info("-----Signature : {}", signature);
		if (request.getHeader("x-zm-signature").equals(signature)) {
			return ResponseEntity.status(HttpStatus.OK).body(new ModelMap().addAttribute("msg", "Validation Success."));
		}

		return ResponseEntity.status(HttpStatus.OK).body(
				new ModelMap().addAttribute("msg", "Something went wrong. Try validating the webhook url again."));
	}

	private String hashForVerify(String payload, String secretKey)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac sha256_HMAC = Mac.getInstance(encodingAlgorithm);
		SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), encodingAlgorithm);
		sha256_HMAC.init(secretKeySpec);

		byte[] hash = sha256_HMAC.doFinal(payload.getBytes());
		String message = Base64.getEncoder().encodeToString(hash);
		log.info("------HmacSHA256 encoded Message : {}", message);
		return message;
	}
}