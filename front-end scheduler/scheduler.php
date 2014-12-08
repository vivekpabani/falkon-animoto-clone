<?php

set_time_limit (0);

$address = '172.31.13.24';

$con = 1;

// Fetch Port Number, worker type and number of threads from command line
$port = $argv[1];
$worker_type = $argv[2];
$NUM_THREADS = $argv[3];

$sock = socket_create(AF_INET, SOCK_STREAM, 0);
$bind = socket_bind($sock, $address, $port);
socket_listen($sock);

// Check if the connection has been established between client and server
while ($con == 1){
      
    $total_task_sent_queue = 0;
    if($con == 1) {
     
        // Proceed below for local workers
        if ($worker_type == 'lw'){
           
            $next_batch = 1;
            
            // Define an in memory queue
            $queue = array();$queue_size = 0;
          
            // Continue till the last batch is received from the client
            while ($next_batch != 0) {

                $client = socket_accept($sock);
                $rcvd_input_batch = socket_read($client, 2024);
              
                //Decode recieved messages in a batch
                $rcvd_input_object = json_decode($rcvd_input_batch);
                        
                $msg = $rcvd_input_object->task_objects;
                $msg_length = $rcvd_input_object->batch_length;
                $next_batch = $rcvd_input_object->next_batch;
             
                $x = 0;

                // Continue adding messages to the queue till the batch is emopty
                while ($x < $msg_length) {
             
                    $object = new stdclass;
                    $object->task_result = $msg[$x]->task_data[0];
                    $object->task_id = $msg[$x]->task_id;
                    $object->type = $msg[$x]->task_type;
                    // Add messages to the queue
                    $queue[$queue_size] = $object;
                    $x++; $queue_size++;
                    $total_task_sent_queue++;
                }
            }

            // Get total queue length
            //print "queue length is";print $total_task_sent_queue;
            $res_queue = array();
              
            /* Local workers processing the data in the queue after messages are added to the queue */
            $childs = array(); 
            $queue_elements_counter = 0;$client_port = 0;

            // Tasks are executed by different threads
            for ($h = 1; $h <= $NUM_THREADS;$h++) {
   
                $pid = pcntl_fork();
                $childs[] = $pid;
                if(($pid)){
                    $y = 0;

                    // Each thread processing the data from the queue
                    while ($y < (($total_task_sent_queue)/$NUM_THREADS)) {

                        // Receieve message from the queue
                        // Execute the task and submit the response to result queue
                        $sleep_value = explode(" ", $queue[$queue_elements_counter]->task_result);
                        $delay = intval($sleep_value[1]);
                        sleep($delay);
                        $obj = new stdclass();
                        $obj->task_id = $queue[$queue_elements_counter]->task_id;
                        $obj->task_result = 'success';
                        $newobj = json_encode($obj);
                        $client_port = $obj->task_id;
                        array_push($res_queue,$newobj);
                        $y++;
                        $queue_elements_counter++;
                    }
                }
                else {
                    exit;
                };
           }
  
           foreach($childs as $key => $pid) {
           $res = pcntl_waitpid($pid, $status);
           // If the process has already exited
           if($res == -1 || $res > 0)
               unset($childs[$key]);
            
           }

            //Response array to be sent to the client
            $array = (array)($res_queue); $n_array = array();

            // Create batches of the messages to be sent to the client
            $response_array = (array_chunk($array, 10));
             
            for($y = 0; $y < count($response_array); $y++) {
               
                if ($y < (count($response_array)-1)) {
                
                    $worker_next_batch = 1;
               
                }

                else {$worker_next_batch = 0;}

                $next_batch_array['next_batch'] = $worker_next_batch;
                $batch[$y] = $response_array[$y];
                $array_tasks['task_results'] = $batch[$y];
                $merged_array = array_merge($array_tasks, $next_batch_array);
                //print " merged array is  "; print_r($merged_array);
                $n_batch = json_encode($merged_array, JSON_UNESCAPED_SLASHES);
                   
                //print "one batch is";print $n_batch; print "<br><br><br>";
                //print "client port is"; print $client_port;
                $client_info = (explode("_",$client_port,3));
                //print_r($client_info);
                // Send response back to client in batches
                send_response($n_batch, $client_info[0], $client_info[1]);
            }
        }
     
            /***************************
            * REMOTE WORKER PART
            **************************/

        else  if ($worker_type == 'rw'){
            
            require 'vendor/autoload.php';
            require_once 'AWSSDKforPHP/sdk.class.php';

            $queuename = 'RequestQueue';
            $sqs = new AmazonSQS();
            $sqs->set_region(AmazonSQS::REGION_US_W2);
            $newqueue = $sqs->create_queue($queuename);
            $queueurl = $sqs->get_queue_url($queuename);
            $url = (string) $queueurl->body->GetQueueUrlResult->QueueUrl;
            $next_batch = 1;
            $total_task_added = 0;
            $count_batch = 0;
            $sqs_array = array();
            $sqs_queue_size = 0;
            // Continue till the last batch is received from the client
            while ($next_batch != 0) {
                
                $client = socket_accept($sock);
                $rcvd_input_batch = socket_read($client, 6000);
                $count_batch++;
                $rcvd_input_object = json_decode($rcvd_input_batch);
                $obj = ($rcvd_input_object);
                $msg = $obj->task_objects;
                $msg_length = $obj->batch_length;
                $next_batch = $obj->next_batch;
                $x = 0;
                
                while($x < $msg_length){

                    $object = new stdclass;
                    $object->task_type = $msg[$x]->task_type;
                    $object->task_id  = $msg[$x]->task_id;
                    $object->task_data = $msg[$x]->task_data;
                    $client_port = $object->task_id;
                    $message = json_encode($object);
                    $sqs_array[$sqs_queue_size] = $object;
                    $sqs_queue_size++;
                    $x++;
                    $total_task_added++;
                 
                }
            
            }

            for ($x = 0; $x < $sqs_queue_size; $x++) {
                $sqs_array_encode= json_encode($sqs_array[$x]);
                $sndmessage = $sqs->send_message($url, $sqs_array_encode);
                $message = $sndmessage->header['x-aws-body'];
            }
              
               /***************************
                * RECEIVE MESSAGE
               **************************/
               
            $array = array();
            $responseurl = "https://sqs.us-west-2.amazonaws.com/254402429514/responseQueue";

            $queuesize = $sqs->get_queue_size( $responseurl );
            $received_messages = 0;

            while ($received_messages < $total_task_added) {
                $empty = 0;
                if ($queuesize == 0){
		        	usleep(100);
                    $empty = 1;
                }
                $queuesize = $sqs->get_queue_size( $responseurl );
                if($empty == 0){
                    $remaining_tasks =$total_task_added- $received_messages;
                    $count_it = min($remaining_tasks,$queuesize);
			        $i = 0;
                    while($i <= $count_it) {
                        $received = $sqs->receive_message($responseurl);
                        //Fetch messages from the response queue
                        $receipthandle = $received->body->ReceiveMessageResult->Message;
                        $rcpthandle = $received->body->ReceiveMessageResult->Message[0]->ReceiptHandle;
           
                        if(!empty($receipthandle )){
                            $received_messages++;
                            //Delete message from the reponse queue once the message has been recieved
                            $deletemessage = $sqs->delete_message($responseurl, $rcpthandle);
                            $message_recieved = $receipthandle->Body;
                            $obj = new stdclass();
                            $obj = json_decode($message_recieved);
                            array_push($array,$obj);
                        }
                        $i++;
                    }
                }
            }
            $batch = '';
            $obj = new stdClass();
            foreach ($array as $key => $value){
                $obj->$key = $value;

            }

            $array = (array)($obj); 
            
            // Create batches of the messages to be sent to the client
           
            $response_array = (array_chunk($array, 10));
            for($y = 0; $y < count($response_array); $y++) {

                if ($y < (count($response_array)-1)) {

                    $worker_next_batch = 1;

                }

                else {$worker_next_batch = 0;}
               
                $next_batch_array['next_batch'] = $worker_next_batch;
                $batch[$y] = $response_array[$y];
                $array_tasks['task_results'] = $batch[$y];
                $merged_array = array_merge($array_tasks, $next_batch_array);
                $n_batch = json_encode($merged_array, JSON_UNESCAPED_SLASHES);
                $client_info = (explode("_",$client_port,3));
                // Send response back to client in batches
                send_response($n_batch, $client_info[0], $client_info[1]);
            }
			 
          }
       }
    }


/***************************
* SEND RESPONSE TO THE CLIENT
**************************/

 
 
function send_response($batch,$IP_Address, $port) {
    if(!($sock = socket_create(AF_INET, SOCK_STREAM, 0))){
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);
     
        die("Couldn't create socket: [$errorcode] $errormsg \n");
    }
  
        //Connect socket to remote server
    if(!socket_connect($sock , $IP_Address , $port)){
        print_r($sock);
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);
     
        die("Could not connect: [$errorcode] $errormsg \n");
    }
  
//Send the message to the server 
    if( ! socket_send ( $sock , $batch, strlen($batch)  , 0)){
        $errorcode = socket_last_error();
        $errormsg = socket_strerror($errorcode);
     
        die("Could not send data: [$errorcode] $errormsg \n");
    }
 
} 
?>
