package gov.usdot.cv.router.datasink.model;

import com.deleidos.rtws.commons.util.DataModelBasedNames;
import com.deleidos.rtws.core.framework.parser.CanonicalFormat;

import net.sf.json.JSONObject;

public class DataModel {
	public static final String DIALOG_ID_KEY 	= "dialogId";
	public static final String SEQUENCE_ID_KEY 	= "sequenceId";
	public static final String ENCODED_MSG_KEY 	= "encodedMsg";
	
	protected JSONObject record;
	
	public static DataModel newInstance(JSONObject record) {
		DataModelBasedNames modelNames = CanonicalFormat.getModel(record);
		String modelName = modelNames.getModelName();
		if (modelName.equals(VehSitDataModel.MODEL_NAME)) {
			return new VehSitDataModel(record);
		} else if (modelName.equals(IntersectionSitDataModel.MODEL_NAME)) {
			return new IntersectionSitDataModel(record);
		} else {
			return null;
		}
	}
	
	protected DataModel(JSONObject record) {
		this.record = record;
	}
	
	public String getModelName() {
		DataModelBasedNames modelNames = CanonicalFormat.getModel(record);
		return modelNames.getModelName();
	}
	
	public String getEncodedMsg() {
		if (record.has(ENCODED_MSG_KEY)) {
			return record.getString(ENCODED_MSG_KEY);
		}
		return null;
	}
	
	@Override
	public String toString() {
		return this.record.toString();
	}
}