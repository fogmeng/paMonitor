package com.fogmeng.monitor.decoder;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class JsonDecoder implements IDecode {
    private static Logger logger = LoggerFactory.getLogger(JsonDecoder.class);

	private static ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings({ "unchecked", "serial" })
    @Override
    public Map<String, Object> decode(final String message) {
        Map<String, Object> event = null;
        try {
            event = objectMapper.readValue(message, Map.class);
            if(!event.containsKey("message")){
            	event.put("message", message);
            } 
        } catch (Exception e) {
            logger.error(e.getMessage());
            event = new HashMap<String, Object>() {
                {
                    put("message", message);
                }
            };
            return event;
        }
        return event;
    }

	@Override
	public Map<String, Object> decode(String message, String identify) {
		return null;
	}
}
