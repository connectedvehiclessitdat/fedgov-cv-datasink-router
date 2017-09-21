package gov.usdot.cv.router.datasink.model;

import net.sf.json.JSONObject;

/**
 * Expecting the situation data input data model to be of the form.
 * {
 *   dialogId 		: <number>,
 *   sequenceId 	: <number>,
 *   bundleNumber	: <number>,
 *   timeToLive 	: <number>,
 *   nwPos			:
 *   {
 *     lat : <number>,
 *     lon : <number>
 *   },
 *   sePos			:
 *   {
 *     lat : <number>,
 *     lon : <number>
 *   }
 *   timestamp		: <date>,
 *   encodedMsg 	: <string>
 * }
 */
public class IntersectionSitDataModel extends DataModel {	
	public static final String MODEL_NAME 			= "intersectionSitData";
	public static final String BUNDLE_NUMBER_KEY 	= "bundleNumber";
	public static final String TIME_TO_LIVE_KEY 	= "timeToLive";
	public static final String NW_POS_KEY			= "nwPos";
	public static final String SE_POS_KEY			= "sePos";
	public static final String LAT_KEY				= "lat";
	public static final String LON_KEY				= "lon";
	public static final String TIMESTAMP			= "timestamp";
	
	public IntersectionSitDataModel(JSONObject record) {
		super(record);
	}
	
	public Integer getBundleNumber() {
		if (record.has(BUNDLE_NUMBER_KEY)) {
			return record.getInt(BUNDLE_NUMBER_KEY);
		}
		return null;
	}
	
	public Integer getTimeToLive() {
		if (record.has(TIME_TO_LIVE_KEY)) {
			return record.getInt(TIME_TO_LIVE_KEY);
		}
		return null;
	}
	
	public Double getNWLat() {
		if (record.has(NW_POS_KEY)) {
			JSONObject pos = record.getJSONObject(NW_POS_KEY);
			if (pos.has(LAT_KEY)) {
				return pos.getDouble(LAT_KEY);
			}
		}
		return null;
	}
	
	public Double getNWLon() {
		if (record.has(NW_POS_KEY)) {
			JSONObject pos = record.getJSONObject(NW_POS_KEY);
			if (pos.has(LON_KEY)) {
				return pos.getDouble(LON_KEY);
			}
		}
		return null;
	}
	
	public Double getSELat() {
		if (record.has(SE_POS_KEY)) {
			JSONObject pos = record.getJSONObject(SE_POS_KEY);
			if (pos.has(LAT_KEY)) {
				return pos.getDouble(LAT_KEY);
			}
		}
		return null;
	}
	
	public Double getSELon() {
		if (record.has(SE_POS_KEY)) {
			JSONObject pos = record.getJSONObject(SE_POS_KEY);
			if (pos.has(LON_KEY)) {
				return pos.getDouble(LON_KEY);
			}
		}
		return null;
	}
	
	public String getTimestamp() {
		if (record.has(TIMESTAMP)) {
			return record.getString(TIMESTAMP);
		}
		return null;
	}
}