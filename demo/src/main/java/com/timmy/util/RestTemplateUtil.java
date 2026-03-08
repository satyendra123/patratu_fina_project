package com.timmy.util;

import javax.json.Json;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;

public class RestTemplateUtil {
	
	public static void postDeviceInfo(JSONObject jsonNode) {
		String url="http://39.105.187.247/index.php/index/ai06/heart";
		RestTemplate restTemplate=new RestTemplate();
		
		  ResponseEntity<String> response = restTemplate.postForEntity(url, jsonNode, String.class);
		 
		  System.out.println("响应信息"+response);
		
	}

	public static void postLog(JSONObject jsonNode) {
		String url="http://39.105.187.247/index.php/index/ai06/center";
		RestTemplate restTemplate=new RestTemplate();
		
		  ResponseEntity<String> response = restTemplate.postForEntity(url, jsonNode, String.class);
		 
		  System.out.println("响应信息"+response);
		
	}
}
