package com.fogmeng.monitor.decoder;

import java.util.Map;

/**
 *
 */
public interface IDecode {
	
	public Map<String, Object> decode(String message);
	
	public Map<String, Object> decode(String message, String identify);

}
