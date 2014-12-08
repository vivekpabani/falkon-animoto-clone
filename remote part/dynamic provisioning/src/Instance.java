import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;

public class Instance {

	static String TABLE_NAME = "Instcances";
	static String DEFAULT_TIMEOUT = "200000";

	public static boolean initialInstance(String timeout) {


		String ImageID = "ami-3d50120d";
		//Gets the ImageID from the file
		try {
			for (String line : Files.readAllLines(Paths.get(System.getProperty("user.home")+"/imageid.txt"), Charset.defaultCharset())) {
			    ImageID = line;
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Image ID of worker is: " + ImageID);
		
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

		// Create the AmazonEC2Client object so we can call various APIs.
		AmazonEC2 ec2 = new AmazonEC2Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		ec2.setRegion(usWest2);

		// Initializes a Spot Instance Request
		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

		// Request 1 x t1.micro instance with a bid price of $0.03.
		requestRequest.setSpotPrice("0.4");
		requestRequest.setInstanceCount(Integer.valueOf(1));

		// Setup the specifications of the launch. This includes the instance
		// type (e.g. t1.micro)
		// and the latest Amazon Linux AMI id available. Note, you should always
		// use the latest
		// Amazon Linux AMI id or another of your choosing.
		LaunchSpecification launchSpecification = new LaunchSpecification();
		launchSpecification.setImageId(ImageID);
		launchSpecification.setInstanceType("m3.large");

		// Add the security group to the request.
		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add("SpotInstance");
		launchSpecification.setSecurityGroups(securityGroups);

		// Add the launch specifications to the request.
		requestRequest.setLaunchSpecification(launchSpecification);


		// Call the RequestSpotInstance API.
		RequestSpotInstancesResult requestResult = ec2
				.requestSpotInstances(requestRequest);
		List<SpotInstanceRequest> requestResponses = requestResult
				.getSpotInstanceRequests();

		// Setup an arraylist to collect all of the request ids we want to watch
		// hit the running
		// state.
		ArrayList<String> spotInstanceRequestIds = new ArrayList<String>();

		// Add all of the request ids to the hashset, so we can determine when
		// they hit the
		// active state.
		for (SpotInstanceRequest requestResponse : requestResponses) {
			System.out.println("Created Spot Request: "
					+ requestResponse.getSpotInstanceRequestId());
			spotInstanceRequestIds.add(requestResponse
					.getSpotInstanceRequestId());

		}

		boolean anyOpen;
		// Initialize variables.
		ArrayList<String> instanceIds = new ArrayList<String>();

		do {
			// Create the describeRequest with tall of the request id to monitor
			// (e.g. that we started).
			DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();
			describeRequest.setSpotInstanceRequestIds(spotInstanceRequestIds);

			// Initialize the anyOpen variable to false ??? which assumes there
			// are no requests open unless
			// we find one that is still open.
			anyOpen = false;

			try {
				// Retrieve all of the requests we want to monitor.
				DescribeSpotInstanceRequestsResult describeResult = ec2
						.describeSpotInstanceRequests(describeRequest);
				List<SpotInstanceRequest> describeResponses = describeResult
						.getSpotInstanceRequests();
				// System.out.println(describeResponses.size());
				// Look through each request and determine if they are all in
				// the active state.
				for (SpotInstanceRequest describeResponse : describeResponses) {

					// If the state is open, it hasn't changed since we
					// attempted to request it.
					// There is the potential for it to transition almost
					// immediately to closed or
					// cancelled so we compare against open instead of active.
					if (describeResponse.getState().equals("open")) {
						anyOpen = true;
						break;
					}
					System.out.println("instance id");
					System.out.println(describeResponse.getInstanceId());
					instanceIds.add(describeResponse.getInstanceId());
					addToDynamoDB(describeResponse.getInstanceId() , timeout);
				}
			} catch (AmazonServiceException e) {
				// If we have an exception, ensure we don't break out of the
				// loop.
				// This prevents the scenario where there was blip on the wire.
				anyOpen = true;
			}

			try {
				// Sleep for 60 seconds.
				Thread.sleep(60 * 1000);
			} catch (Exception e) {
				// Do nothing because it woke up early.
			}
		} while (anyOpen);

		try {
			// Cancel requests.
			CancelSpotInstanceRequestsRequest cancelRequest = new CancelSpotInstanceRequestsRequest(
					spotInstanceRequestIds);
			ec2.cancelSpotInstanceRequests(cancelRequest);
		} catch (AmazonServiceException e) {
			// Write out any exceptions that may have occurred.
			System.out.println("Error cancelling instances");
			System.out.println("Caught Exception: " + e.getMessage());
			System.out.println("Reponse Status Code: " + e.getStatusCode());
			System.out.println("Error Code: " + e.getErrorCode());
			System.out.println("Request ID: " + e.getRequestId());
		}
		return true;
	}

	public static String checkInstance(String ID, AmazonEC2 ec2) {

		DescribeInstanceStatusRequest describeInstanceRequest = new DescribeInstanceStatusRequest()
				.withInstanceIds(ID);
		DescribeInstanceStatusResult describeInstanceResult = ec2
				.describeInstanceStatus(describeInstanceRequest);
		List<InstanceStatus> state = describeInstanceResult
				.getInstanceStatuses();
		while (state.size() < 1) {
			describeInstanceResult = ec2
					.describeInstanceStatus(describeInstanceRequest);
			state = describeInstanceResult.getInstanceStatuses();
		}
		String status = state.get(0).getInstanceState().getName();
		return status;
	}

	public static boolean addToDynamoDB(String id, String timeout) {
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("Instance_ID", new AttributeValue(id));
		item.put("Timeout", new AttributeValue(timeout));
		PutItemRequest putItemRequest = new PutItemRequest(TABLE_NAME, item);
		MainClass.dynamoDB.putItem(putItemRequest);
		System.out.println(id + " is added to table!");
		return true;
	}
}
