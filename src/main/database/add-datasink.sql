-- Run the following statements to make System Builder UI tool aware of the datasink.
INSERT INTO APPLICATION.PROCESS_GROUP_CONFIG VALUES(
	'datasink.cvrouter', 
	'datasink.default', 
	'private', 
	'cv-router?.@build.domain@', 
	'ingest.rtws.saic.com', 
	null, 
	'm1.large', 
	'instance', 
	null, 
	'ingest.ini', 
	'services.cvrouter.xml', 
	'{"default-num-volumes" : 0, "default-volume-size" : 0, "config-volume-size" : false, "config-persistent-ip" : false, "config-instance-size" : false, "config-min-max" : true, "config-scaling" : true, "config-jms-persistence" : false }'
);

INSERT INTO APPLICATION.PROCESS_GROUP_CONFIG VALUES(
	'pubsub.server', 
	'datasink.default', 
	'private', 
	'pubsub-server.@build.domain@', 
	'ingest.rtws.saic.com', 
	null, 
	'm1.large', 
	'instance', 
	null, 
	'pubsub-server.ini', 
	null, 
	'{"default-num-volumes" : 2, "default-volume-size" : 15, "config-volume-size" : true, "config-persistent-ip" : true, "config-instance-size" : true, "config-min-max" : false, "config-scaling" : false, "config-jms-persistence" : false }'
);
	
INSERT INTO APPLICATION.DATASINK_CONFIG VALUES(
	'gov.usdot.cv.router.datasink.SituationDataRouter',
	'Y',
	'N',
	0.75,
	'',
	'',
	'',
	'pubsub.server,datasink.cvrouter'
);	