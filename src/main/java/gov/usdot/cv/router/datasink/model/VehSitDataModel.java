package gov.usdot.cv.router.datasink.model;

import net.sf.json.JSONObject;

/**
 * Expecting the situation data input data model to be of the form.
 * {
 *   dialogId 	: <number>,
 *   sequenceId : <number>,
 *   vsmType	: <number>,
 *   year 		: <number>,
 *   month 		: <number>,
 *   day 		: <number>,
 *   hour 		: <number>,
 *   minute 	: <number>,
 *   second 	: <number>,
 *   long 		: <number>,
 *   lat 		: <number>,
 *   elevation	: <number>,
 *   location	: <string>,
 *   count		: <number>,
 *   pathHistoryPoints : <array-of-string>,
 *   vehSitRcdBundle : <array-of-string>,
 *   encodedMsg : <string>
 * }
 */
public class VehSitDataModel extends DataModel {
	public static final String MODEL_NAME 		= "vehSitDataMessage";
	public static final String VSM_TYPE_KEY 	= "vsmType";
	public static final String LAT_KEY 			= "lat";
	public static final String LONG_KEY 		= "long";
	public static final String YEAR_KEY 		= "year";
	public static final String MONTH_KEY 		= "month";
	public static final String DAY_KEY 			= "day";
	public static final String HOUR_KEY 		= "hour";
	public static final String MINUTE_KEY 		= "minute";
	public static final String SECOND_KEY 		= "second";
	
	public VehSitDataModel(JSONObject record) {
		super(record);
	}
	
	public Integer getVsmType() {
		if (record.has(VSM_TYPE_KEY)) {
			return record.getInt(VSM_TYPE_KEY);
		}
		return null;
	}
	
	public Double getLat() {
		if (record.has(LAT_KEY)) {
			return record.getDouble(LAT_KEY);
		}
		return null;
	}
	
	public Double getLon() {
		if (record.has(LONG_KEY)) {
			return record.getDouble(LONG_KEY);
		}
		return null;
	}
	
	public Integer getYear() {
		if (record.has(YEAR_KEY)) {
			return record.getInt(YEAR_KEY);
		}
		return null;
	}
	
	public Integer getMonth() {
		if (record.has(MONTH_KEY)) {
			return record.getInt(MONTH_KEY);
		}
		return null;
	}
	
	public Integer getDay() {
		if (record.has(DAY_KEY)) {
			return record.getInt(DAY_KEY);
		}
		return null;
	}
	
	public Integer getHour() {
		if (record.has(HOUR_KEY)) {
			return record.getInt(HOUR_KEY);
		}
		return null;
	}
	
	public Integer getMinute() {
		if (record.has(MINUTE_KEY)) {
			return record.getInt(MINUTE_KEY);
		}
		return null;
	}
	
	public Integer getSecond() {
		if (record.has(SECOND_KEY)) {
			return record.getInt(SECOND_KEY);
		}
		return null;
	}
}