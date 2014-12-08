import org.springframework.core.task.TaskTimeoutException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.policy.resources.SQSQueueResource;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.s3.internal.Constants;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class MainClass {

	static AmazonSQS sqs;
	static String REQUEST_QUEUENAME = "testQueue";
	static AmazonDynamoDBClient dynamoDB;
	static String INSTANCE_TABLE_NAME = "Instcances";
	static int Threshold = 50;
	static String DEFAULTTIMEOUT = "200000"; 

	public static void main(String[] args) throws Exception {
		
		BasicAWSCredentials credentials = null;
		try {
			credentials = new BasicAWSCredentials("Update Manually","Update Manually");
					
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. "
							+ "Please make sure that your credentials file is at the correct "
							+ "location (C:\\Users\\Rojin\\.aws\\credentials), and is in valid format.",
					e);
		}

		AmazonEC2 ec2 = new AmazonEC2Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		ec2.setRegion(usWest2);

		sqs = new AmazonSQSClient(credentials);
		sqs.setRegion(usWest2);

		dynamoDB = new AmazonDynamoDBClient(credentials);
		dynamoDB.setRegion(usWest2);

		System.out.println("===========================================");
		System.out.println("Getting Started with Amazon AWS");
		System.out.println("===========================================\n");

		String reqQueueUrl = "https://sqs.us-west-2.amazonaws.com/254402429514/32Request";
		
		System.out.println("QUEUE URL: " + reqQueueUrl);
		int currentDensity = 0;

		//Static Allocation: 
		if(args.length>0){
			Instance.initialInstance(String.valueOf(args[0]));
			return;
		}

		while (true) {
			int messages = getSizeOfQueue(reqQueueUrl);
			System.out.println("Length of queue at first: " + messages );
			if (messages == 0) {
				System.out.println("Queue size is zero!");
			} else {
				if (getSizeOfTable() == 0) {
					Instance.initialInstance(DEFAULTTIMEOUT);
					System.out.println("One Instance is launched!");
				}
				int queueSize = getSizeOfQueue(reqQueueUrl);
				int numOfWorker = getSizeOfTable();
				currentDensity = queueSize / numOfWorker;
				System.out.println("QueueSize: " + queueSize + " Number of Worker: " + numOfWorker + " current density: " + currentDensity);
				
				if( numOfWorker < 32 ){ 
					if (currentDensity < Threshold) {
						System.out.println("No new Worker need!");
					} else {
						int additional_tasks = getSizeOfQueue(reqQueueUrl)
								- (getSizeOfTable() * Threshold);
						int additional_workers = additional_tasks / Threshold;
						System.out.println(additional_workers + " new worker need!");
						for (int i = 0; i < additional_workers; i++) {
							Instance.initialInstance(DEFAULTTIMEOUT);
							System.out.println("one instance is launched");
						}
					}
				}
				else{
					System.out.println("There is 32 instance launch");
				}
			}
			Thread.sleep(1000);
		}
	}

	public static int getSizeOfTable() {
		// Find size of table
		ScanResult result = null;
		ScanRequest req = new ScanRequest();
		req.setTableName(INSTANCE_TABLE_NAME);
		result = dynamoDB.scan(req);
		List<Map<String, AttributeValue>> rows = result.getItems();
		return rows.size();
	}

	public static int getSizeOfQueue(String reqQueueUrl) {
		List<String> attributeNames = new ArrayList<String>();
		attributeNames.add("All");
		GetQueueAttributesRequest request = new GetQueueAttributesRequest(
				reqQueueUrl);
		request.setAttributeNames(attributeNames);
		Map<String, String> attributes = sqs.getQueueAttributes(request)
				.getAttributes();
		int messages = Integer.parseInt(attributes
				.get("ApproximateNumberOfMessages"));
		return messages;
	}
}
