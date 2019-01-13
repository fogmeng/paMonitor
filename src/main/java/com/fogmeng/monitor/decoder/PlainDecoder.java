package com.fogmeng.monitor.decoder;

import java.util.HashMap;
import java.util.Map;

public class PlainDecoder implements IDecode {

	@SuppressWarnings("serial")
	@Override
	public Map<String, Object> decode(final String message) {
		HashMap<String, Object> event = new HashMap<String, Object>() {
			{
				put("message", message);
			}
		};
		return event;
	}

	@Override
	public Map<String, Object> decode(String message, String identify) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
