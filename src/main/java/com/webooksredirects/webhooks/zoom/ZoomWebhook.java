package com.webooksredirects.webhooks.zoom;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.HmacUtils;
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

/**
 * 
 * https://marketplace.zoom.us/docs/api-reference/webhook-reference/
 * https://marketplace.zoom.us/docs/api-reference/webhook-reference/#validate-your-webhook-endpoint
 * 
 * @author AP
 *
 */
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

		String plainToken = (String) (((JSONObject) payload.get("payload")).get("plainToken"));

		log.info("------Message : {}", plainToken);

		String hashForVerifyValue = hashForVerify(plainToken, secretKey);

		return ResponseEntity.status(HttpStatus.OK).body(new ModelMap().addAttribute("plainToken", plainToken)
				.addAttribute("encryptedToken", hashForVerifyValue));

	}

	private String hashForVerify(String payload, String secretKey)
			throws NoSuchAlgorithmException, InvalidKeyException {
		String hmac = new HmacUtils(encodingAlgorithm, secretKey).hmacHex(payload);
		log.info("------HmacSHA256 encoded Message : {}", hmac);
		return hmac;
	}

	/**
	 * 
	 * 
	 * @param request
	 * @param secretKey
	 * @return
	 * @throws Exception
	 */
	// @PostMapping("/api/sn_zoom_spoke/zoom_webhook_endpoint/webhook")
	public ResponseEntity<?> eventWebhookListener(HttpServletRequest request, @RequestParam String secretKey)
			throws Exception {
		log.info("-----Webhook method to retrieve events. from the post with secretKey as : {}", secretKey);
		String pushedJsonAsString = IOUtils.toString(request.getInputStream(), "utf-8");

		log.info("------Event from webhook : {}", pushedJsonAsString);
		JSONObject payload = (JSONObject) new JSONParser().parse(pushedJsonAsString);

		String plainToken = (String) (((JSONObject) payload.get("payload")).get("plainToken"));

		log.info("--------JsonObject : {}", payload);

		Enumeration<String> headerNames = request.getHeaderNames();

		if (headerNames != null) {
			while (headerNames.hasMoreElements()) {
				log.debug("Header: {}", request.getHeader(headerNames.nextElement()));
			}
		}

		String event = (String) payload.get("event");
		String payloadObject = new ObjectMapper().writeValueAsString(payload.get("payload"));
		long eventTS = (long) payload.get("event_ts");

		String headerValue = request.getHeader("x-zm-request-timestamp");

		log.debug("-----Event : {} , payloadObject : {}, and eventTS : {}  and headerValue :{}", event, payloadObject,
				eventTS, headerValue);
		String message = new StringBuilder().append("v0").append(":").append(headerValue).append(":").append(payload)
				.toString();

		log.info("------Message : {}", message);

		String hashForVerifyValue = hashForVerify(message, secretKey);
		log.info("-----HashForVerify : {}", hashForVerifyValue);

		String signature = new StringBuilder().append("v0").append("=").append(hashForVerifyValue).toString();
		log.info("-----Signature : {}", signature);

		log.info("----------request.getHeader(\"x-zm-signature\") :{}", request.getHeader("x-zm-signature"));
		if (request.getHeader("x-zm-signature").equals(signature)) {
			return ResponseEntity.status(HttpStatus.OK).body(new ModelMap().addAttribute("plainToken", plainToken)
					.addAttribute("encryptedToken", hashForVerifyValue));
		}

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
				new ModelMap().addAttribute("msg", "Something went wrong. Try validating the webhook url again."));
	}

}