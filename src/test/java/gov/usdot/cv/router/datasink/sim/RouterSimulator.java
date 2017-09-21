package gov.usdot.cv.router.datasink.sim;

import gov.usdot.cv.router.datasink.SituationDataRouter;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;
import net.sf.json.JSONObject;

public class RouterSimulator implements Runnable {
	
	private SituationDataRouter router;
	private JSONObject record;
	private int num;
	
	public RouterSimulator(JSONObject record, SubscriptionDataSource ds) {
		this.router = new SituationDataRouter();
		this.router.setDataSource(ds.getSQLDataSource());
		this.router.setDatabaseSubscriberTableName(ds.getSubscriberTableName());
		this.router.setDatabaseFilterTableName(ds.getFilterTableName());
		this.router.setModelsToFilter("vehSitDataMessage,intersectionSitData");
		this.router.initialize();
	}
	
	public void setRecord(JSONObject record) {
		this.record = record;
	}
	
	public void setNumRecordsToSend(int num) {
		this.num = num;
	}
	
	public void run() {
		System.out.println("Router simulator [" + Thread.currentThread().getId() + "] sending data ...");
		int count = 0;
		for (int i = 1; i <= this.num; i++) try {
			this.router.process(this.record);
			count++;
			Thread.sleep(10);
		} catch (Exception ex) {
			System.out.println("Failed to send record " + i + ".");
			ex.printStackTrace();
		}
		System.out.println("Router simulator [" + Thread.currentThread().getId() + "] sent " + count + " record(s).");
	}
	
}