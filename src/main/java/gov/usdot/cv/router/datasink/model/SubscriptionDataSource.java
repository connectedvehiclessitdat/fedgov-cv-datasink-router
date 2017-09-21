package gov.usdot.cv.router.datasink.model;

import javax.sql.DataSource;

public class SubscriptionDataSource {
	private DataSource dataSource;
	private String subscriberTableName;
	private String filterTableName;
	
	public DataSource getSQLDataSource() 	{ return dataSource; }
	public String getSubscriberTableName() 	{ return subscriberTableName; }
	public String getFilterTableName() 		{ return filterTableName; }
	
	public void setSQLDataSource(DataSource dataSource) 				{ this.dataSource = dataSource; }
	public void setSubscriberTableName(String subscriberTableName) 	{ this.subscriberTableName = subscriberTableName; }
	public void setFilterTableName(String filterTableName) 			{ this.filterTableName = filterTableName; }
}