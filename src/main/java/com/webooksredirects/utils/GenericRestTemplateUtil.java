package com.webooksredirects.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GenericRestTemplateUtil {

	@Qualifier("restTemplate")
	@Autowired
	private RestTemplate restTemplate;

	public <R> R performRestCall(HttpMethod method, String url, HttpHeaders headers, Object payload,
			Class<R> responseType) {

		log.info("-----Hitting {} {} with headers {} & payload {} ", method, url, headers, payload);
		try {
			ResponseEntity<R> response = restTemplate.exchange(url, method, new HttpEntity<>(payload, headers),
					responseType);
			log.debug("Response for {} {} with headers {} & payload {} : {} ", method, url, payload, response);
			if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
				throw new RuntimeException("----Failed to call the 3rd party api.-----");
			}
			return responseType.cast(response.getBody());

		} catch (Exception restException) {
			log.info("-----------RestException : {}", restException.getMessage());
			throw new RuntimeException("-----Rest Exception-----");
		}
	}
}